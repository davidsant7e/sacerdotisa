package com.davidsant7e.sacerdotisa.motor

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * «Revisar texto completo»: le pide a Gemini que lea el texto entero (no palabra por palabra)
 * y diga qué comas sobran o faltan, qué frase no concuerda o no se entiende, y por qué.
 * Solo se llama cuando lo pides (botón), nunca mientras escribes.
 */
class Gemini(
    private val base: String = "https://generativelanguage.googleapis.com/v1beta/models/",
) {
    class SinClaveException : IOException("Falta la clave de Gemini")

    /** Un error que encontró Gemini, ya ubicado en el texto. */
    data class Hallazgo(val offset: Int, val length: Int, val original: String, val sugerencia: String, val tipo: String, val porque: String)

    /** Lanza IOException si no hay conexión, la clave es inválida o la respuesta no se entiende. */
    fun revisar(texto: String, clave: String?, modelo: String = MODELO): List<Hallazgo> {
        if (clave.isNullOrBlank()) throw SinClaveException()
        val url = base + URLEncoder.encode(modelo.trim(), "UTF-8") + ":generateContent"
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 45000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            c.setRequestProperty("x-goog-api-key", clave.trim())
            c.outputStream.use { it.write(cuerpo(texto).toByteArray(Charsets.UTF_8)) }
            val codigo = c.responseCode
            val json = (if (codigo in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (codigo !in 200..299) throw IOException(mensajeDeError(codigo, json))
            return leer(json, texto)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val MODELO = "gemini-3.5-flash"

        private val INSTRUCCIONES = """
            Eres La Sacerdotisa, un corrector que enseña. Lee el TEXTO COMPLETO (no palabra por palabra) y busca
            errores que dependen de la frase entera:
            - comas que sobran (entre sujeto y verbo, entre verbo y complemento, antes de «que» completivo,
              antes de la «y» final de una enumeración) o que faltan (vocativos, incisos, conectores como
              «sin embargo» o «es decir», antes de «pero», después de una subordinada larga antepuesta);
            - punto, punto y coma, dos puntos y signos de pregunta o exclamación mal usados;
            - concordancia (género, número, persona), tiempos verbales que no encajan, palabras que faltan o sobran;
            - frases ambiguas o que no se entienden;
            - ortografía y tildes que se te pasen.
            Respeta el estilo y el registro: si es un mensaje informal de chat, no lo vuelvas formal ni cambies
            palabras que están bien. Solo errores reales, sin preferencias de estilo. Si todo está bien, devuelve
            una lista vacía.
            Para cada error:
            - "fragmento": copia EXACTA del texto (mismos caracteres, mayúsculas, tildes y signos) que contenga el
              error y de 1 a 4 palabras alrededor, para que aparezca una sola vez en el texto.
            - "sugerencia": ese mismo fragmento ya corregido (solo el fragmento, no el texto entero).
            - "tipo": coma, puntuacion, gramatica, sentido u ortografia.
            - "porque": explicación breve (1 o 2 frases) y concreta para este caso, con la regla, en el idioma del texto.
        """.trimIndent()

        fun cuerpo(texto: String): String = Json.escribir(
            mapOf(
                "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to INSTRUCCIONES))),
                "contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to "TEXTO:\n$texto")))),
                "generationConfig" to mapOf(
                    "temperature" to 0.2,
                    "responseMimeType" to "application/json",
                    "responseSchema" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "avisos" to mapOf(
                                "type" to "ARRAY",
                                "items" to mapOf(
                                    "type" to "OBJECT",
                                    "properties" to mapOf(
                                        "fragmento" to mapOf("type" to "STRING"),
                                        "sugerencia" to mapOf("type" to "STRING"),
                                        "tipo" to mapOf("type" to "STRING", "enum" to listOf("coma", "puntuacion", "gramatica", "sentido", "ortografia")),
                                        "porque" to mapOf("type" to "STRING"),
                                    ),
                                    "required" to listOf("fragmento", "sugerencia", "tipo", "porque"),
                                ),
                            ),
                        ),
                        "required" to listOf("avisos"),
                    ),
                ),
            ),
        )

        /** Lee la respuesta de Gemini y ubica cada fragmento en el texto; descarta lo que no encuentre. */
        fun leer(respuesta: String, texto: String): List<Hallazgo> {
            val raiz = Json.objeto(Json.leer(respuesta))
            val candidato = Json.objeto(Json.lista(raiz["candidates"]).firstOrNull())
            val partes = Json.lista(Json.objeto(candidato["content"])["parts"])
            val interno = partes.joinToString("") { Json.texto(Json.objeto(it)["text"]).orEmpty() }.trim()
            if (interno.isEmpty()) {
                val motivo = Json.texto(candidato["finishReason"]) ?: Json.texto(Json.objeto(raiz["promptFeedback"])["blockReason"])
                throw IOException("Gemini no devolvió revisión" + (motivo?.let { " ($it)" } ?: ""))
            }
            val avisos = Json.lista(Json.objeto(Json.leer(sinCercoMarkdown(interno)))["avisos"])
            val usados = ArrayList<IntRange>()
            return avisos.mapNotNull { a ->
                val o = Json.objeto(a)
                val frag = Json.texto(o["fragmento"]).orEmpty()
                val sug = Json.texto(o["sugerencia"]).orEmpty()
                if (frag.isBlank() || sug == frag) return@mapNotNull null
                val pos = ubicar(texto, frag, usados) ?: return@mapNotNull null
                usados += pos until pos + frag.length
                Hallazgo(pos, frag.length, frag, sug, Json.texto(o["tipo"]) ?: "gramatica", Json.texto(o["porque"]).orEmpty())
            }.sortedBy { it.offset }
        }

        /** Primera aparición exacta que no se pise con otra ya usada (o, si no, ignorando espacios repetidos). */
        private fun ubicar(texto: String, frag: String, usados: List<IntRange>): Int? {
            var desde = 0
            while (true) {
                val i = texto.indexOf(frag, desde)
                if (i < 0) break
                if (usados.none { i <= it.last && it.first < i + frag.length }) return i
                desde = i + 1
            }
            return null
        }

        private fun sinCercoMarkdown(s: String): String =
            s.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        private fun mensajeDeError(codigo: Int, json: String): String {
            val detalle = runCatching { Json.texto(Json.objeto(Json.objeto(Json.leer(json))["error"])["message"]) }.getOrNull()
            return when (codigo) {
                400, 403 -> "Gemini rechazó la clave o la consulta" + (detalle?.let { ": $it" } ?: "")
                404 -> "No existe el modelo de Gemini indicado (revísalo en Ajustes)"
                429 -> "Se acabó el cupo de Gemini por ahora; intenta en un rato"
                else -> "Gemini respondió $codigo" + (detalle?.let { ": $it" } ?: "")
            }
        }

        /** Tipo de Gemini → categoría de explicaciones.json (para el título de la tarjeta). */
        fun categoria(tipo: String): String = when (tipo.lowercase()) {
            "coma", "puntuacion" -> "PUNCTUATION"
            "sentido" -> "SEMANTICS"
            "ortografia" -> "TYPOS"
            else -> "GRAMMAR"
        }
    }
}
