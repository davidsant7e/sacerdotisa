package com.davidsant7e.sacerdotisa.motor

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Cliente del servicio gratuito de LanguageTool (api.languagetool.org).
 * Límites del servicio gratis: unas 20 consultas por minuto y 20 KB por texto. Por eso:
 * se consulta solo cuando dejas de escribir, se guarda la última respuesta y se espera
 * si el servicio pide calma (código 429).
 */
class LanguageTool(
    private val url: String = "https://api.languagetool.org/v2/check",
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    class Respuesta(val matches: List<Match>, val idioma: String)

    /** Consultas como máximo por minuto (dejamos margen bajo el límite del servicio). */
    var maxPorMinuto = 10
    private val recientes = ArrayDeque<Long>()
    private var esperarHasta = 0L

    private var ultimoTexto: String? = null
    private var ultimoIdioma: String? = null
    private var ultimaRespuesta: Respuesta? = null

    /** ¿Se puede consultar ahora sin pasarse del límite? */
    @Synchronized
    fun puedeConsultar(): Boolean {
        val ahora = reloj()
        if (ahora < esperarHasta) return false
        while (recientes.isNotEmpty() && ahora - recientes.first() > 60_000) recientes.removeFirst()
        return recientes.size < maxPorMinuto
    }

    /** idioma: "es", "en-US" o "auto". Lanza IOException si no hay conexión o el servicio falla. */
    fun revisar(texto: String, idioma: String = "es"): Respuesta {
        synchronized(this) {
            if (texto == ultimoTexto && idioma == ultimoIdioma) ultimaRespuesta?.let { return it }
            if (!puedeConsultar()) throw LimiteException()
            recientes.addLast(reloj())
        }
        val cuerpo = "text=" + URLEncoder.encode(texto, "UTF-8") +
            "&language=" + URLEncoder.encode(idioma, "UTF-8") +
            (if (idioma == "auto") "&preferredVariants=es,en-US" else "")
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 6000
            c.readTimeout = 10000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            val codigo = c.responseCode
            if (codigo == 429) {
                synchronized(this) { esperarHasta = reloj() + 60_000 }
                throw LimiteException()
            }
            if (codigo !in 200..299) throw IOException("LanguageTool respondió $codigo")
            val json = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val r = leerRespuesta(json)
            synchronized(this) {
                ultimoTexto = texto
                ultimoIdioma = idioma
                ultimaRespuesta = r
            }
            return r
        } finally {
            c.disconnect()
        }
    }

    class LimiteException : IOException("LanguageTool pide esperar un momento")

    companion object {
        /** Convierte la respuesta JSON de LanguageTool en errores. */
        fun leerRespuesta(json: String): Respuesta {
            val r = Json.objeto(Json.leer(json))
            val idioma = Json.texto(Json.objeto(Json.objeto(r["language"])["detectedLanguage"])["code"])
                ?: Json.texto(Json.objeto(r["language"])["code"]) ?: "es"
            val matches = Json.lista(r["matches"]).map {
                val m = Json.objeto(it)
                val regla = Json.objeto(m["rule"])
                Match(
                    offset = Json.entero(m["offset"]),
                    length = Json.entero(m["length"]),
                    message = Json.texto(m["message"]) ?: "",
                    replacements = Json.lista(m["replacements"]).mapNotNull { x -> Json.texto(Json.objeto(x)["value"]) }.take(5),
                    ruleId = Json.texto(regla["id"]) ?: "",
                    issueType = Json.texto(regla["issueType"]) ?: "",
                    categoryId = Json.texto(Json.objeto(regla["category"])["id"]) ?: "",
                )
            }
            return Respuesta(matches, idioma)
        }
    }
}
