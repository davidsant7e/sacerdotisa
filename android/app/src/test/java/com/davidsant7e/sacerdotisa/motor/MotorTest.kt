package com.davidsant7e.sacerdotisa.motor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las mismas pruebas que la extensión (tests/explicar.test.js) y la app de Windows:
 * compartido/casos.json. Se corren desde android/app (carpeta de trabajo de Gradle).
 */
class MotorTest {

    private fun archivo(ruta: String): File =
        listOf(File(ruta), File("app", ruta), File("android/app", ruta)).firstOrNull { it.exists() }
            ?: error("No encuentro $ruta desde ${File(".").absolutePath}")

    private val datos = Datos.desdeJson(archivo("src/main/assets/explicaciones.json").readText())
    private val casos = Json.objeto(Json.leer(archivo("../../compartido/casos.json").readText()))

    @Test
    fun `las explicaciones del celular son las mismas de la extension`() {
        assertEquals(archivo("../../extension/datos/explicaciones.json").readText(), archivo("src/main/assets/explicaciones.json").readText())
    }

    @Test
    fun silabas() {
        for (c in Json.lista(casos["silabas"]).map(Json::objeto)) {
            val p = c["palabra"] as String
            assertEquals("sílabas de $p", c["marcadas"], Explicador.silabasMarcadas(p))
        }
    }

    @Test
    fun explicar() {
        for (c in Json.lista(casos["explicar"]).map(Json::objeto)) {
            val reemplazo = c["reemplazo"] as String
            val m = Match(
                offset = Json.entero(c["offset"]), length = Json.entero(c["length"]), message = "msg",
                replacements = if (reemplazo.isEmpty()) emptyList() else listOf(reemplazo),
                ruleId = "X", issueType = c["issueType"] as String, categoryId = c["categoria"] as String,
            )
            val texto = c["texto"] as String
            val r = Explicador.explicar(m, texto, c["idioma"] as String, datos)
            val nombre = c["nombre"] as String
            assertEquals("$nombre: tipo", c["tipo"], r.tipo)
            for (t in Json.lista(c["contiene"])) assertTrue("$nombre: «$t» no está en: ${r.porque}", r.porque.contains(t as String))
            assertEquals(texto.substring(m.offset, m.offset + m.length), r.original)
        }
    }

    @Test
    fun aplicar() {
        for (c in Json.lista(casos["aplicar"]).map(Json::objeto)) {
            val cambios = Json.lista(c["cambios"]).map(Json::objeto).map {
                Cambio(Json.entero(it["offset"]), Json.entero(it["length"]), it["valor"] as String)
            }
            assertEquals(c["resultado"], Explicador.aplicar(c["texto"] as String, cambios))
        }
    }

    @Test
    fun `lee la respuesta de LanguageTool`() {
        val json = """{"software":{"name":"LanguageTool"},"language":{"name":"Spanish","code":"es",
            "detectedLanguage":{"name":"Spanish","code":"es"}},
            "matches":[{"message":"Posible error.","offset":6,"length":4,
            "replacements":[{"value":"vida"},{"value":"vid"}],
            "rule":{"id":"MAYUSCULAS","issueType":"typographical","category":{"id":"CASING","name":"Mayúsculas"}}}]}"""
        val r = LanguageTool.leerRespuesta(json)
        assertEquals("es", r.idioma)
        assertEquals(1, r.matches.size)
        val m = r.matches[0]
        assertEquals(listOf("vida", "vid"), m.replacements)
        assertEquals("CASING", m.categoryId)
        assertEquals("MAYUSCULAS", m.ruleId)
        assertEquals(6, m.offset)
    }

    @Test
    fun `json con escapes y unicode`() {
        val v = Json.objeto(Json.leer("""{"a":"línea\n\"comillas\" á","b":[1,2.5,true,null]}"""))
        assertEquals("línea\n\"comillas\" á", v["a"])
        assertEquals(listOf(1.0, 2.5, true, null), v["b"])
    }

    @Test
    fun `limite de consultas a LanguageTool`() {
        var ahora = 0L
        val lt = LanguageTool(url = "http://127.0.0.1:9/no", reloj = { ahora })
        lt.maxPorMinuto = 2
        repeat(2) { runCatching { lt.revisar("hola $it") } } // fallan por red, pero cuentan
        assertTrue(!lt.puedeConsultar())
        ahora = 61_000
        assertTrue(lt.puedeConsultar())
    }
}
