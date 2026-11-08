package com.davidsant7e.sacerdotisa.motor

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeminiTest {

    private val revisor = Revisor(
        Datos.desdeJson(
            listOf(File("src/main/assets/explicaciones.json"), File("app/src/main/assets/explicaciones.json"))
                .first { it.exists() }.readText(),
        ),
    )

    /** Respuesta con el formato de generateContent: el JSON pedido va como texto dentro de parts. */
    private fun respuesta(avisos: List<Map<String, String>>) = Json.escribir(
        mapOf("candidates" to listOf(mapOf("content" to mapOf("parts" to listOf(mapOf("text" to Json.escribir(mapOf("avisos" to avisos))))), "finishReason" to "STOP"))),
    )

    private val texto = "Los niños de la escuela, fueron al parque ayer y la profesora estaban feliz."

    @Test
    fun `cuerpo pide JSON con esquema`() {
        val c = Json.objeto(Json.leer(Gemini.cuerpo(texto)))
        val gen = Json.objeto(c["generationConfig"])
        assertEquals("application/json", gen["responseMimeType"])
        val texto0 = Json.texto(Json.objeto(Json.lista(Json.objeto(Json.lista(c["contents"]).first())["parts"]).first())["text"])!!
        assertTrue(texto0.endsWith(texto))
        assertTrue(Json.escribir(c).contains("\"fragmento\""))
    }

    @Test
    fun `ubica cada fragmento y descarta lo que no esta en el texto`() {
        val r = Gemini.leer(respuesta(listOf(
            mapOf("fragmento" to "la profesora estaban feliz", "sugerencia" to "la profesora estaba feliz", "tipo" to "gramatica", "porque" to "El verbo concuerda con «la profesora» (singular)."),
            mapOf("fragmento" to "escuela, fueron", "sugerencia" to "escuela fueron", "tipo" to "coma", "porque" to "No va coma entre sujeto y verbo."),
            mapOf("fragmento" to "esto no aparece", "sugerencia" to "x", "tipo" to "sentido", "porque" to "inventado"),
            mapOf("fragmento" to "parque", "sugerencia" to "parque", "tipo" to "ortografia", "porque" to "igual: no es cambio"),
        )), texto)
        assertEquals(listOf("escuela, fueron", "la profesora estaban feliz"), r.map { it.original })
        assertEquals(texto.indexOf("escuela, fueron"), r[0].offset)

        val avisos = revisor.deIA(r)
        assertEquals("Coma", avisos[0].explicacion.titulo)
        assertEquals("GRAMMAR", avisos[1].explicacion.tipo)
        assertTrue(avisos.all { it.deIA })
        assertEquals(
            "Los niños de la escuela fueron al parque ayer y la profesora estaba feliz.",
            revisor.corregirTodo(texto, avisos),
        )
    }

    @Test
    fun `no repite lo que ya marcaron las reglas y se reubica al corregir`() {
        val propias = revisor.locales(texto) // la regla de sujeto y verbo ya marca «escuela, fueron»
        assertEquals(listOf("escuela, fueron→escuela fueron"), propias.map { "${it.explicacion.original}→${it.sugerencia}" })
        val ia = revisor.deIA(Gemini.leer(respuesta(listOf(
            mapOf("fragmento" to "escuela, fueron", "sugerencia" to "escuela fueron", "tipo" to "coma", "porque" to "…"),
            mapOf("fragmento" to "la profesora estaban feliz", "sugerencia" to "la profesora estaba feliz", "tipo" to "gramatica", "porque" to "…"),
        )), texto))
        val juntos = revisor.conIA(propias, ia)
        assertEquals(2, juntos.size)
        // Corrijo la coma: el aviso de la IA sobre «estaban» sigue, en su nueva posición.
        val (nuevo, _) = revisor.corregir(texto, juntos.first())!!
        val reubicados = revisor.reubicarIA(nuevo, ia)
        assertEquals(listOf("la profesora estaban feliz"), reubicados.map { it.explicacion.original })
        assertEquals(nuevo.indexOf("la profesora"), reubicados.single().match.offset)
    }

    @Test
    fun `respuesta vacia o bloqueada da error claro`() {
        try {
            Gemini.leer("""{"candidates":[{"content":{"parts":[]},"finishReason":"SAFETY"}]}""", texto)
            fail("debía fallar")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("SAFETY"))
        }
        assertEquals(0, Gemini.leer(respuesta(emptyList()), texto).size)
        assertEquals(0, Gemini.leer("""{"candidates":[{"content":{"parts":[{"text":"```json\n{\"avisos\":[]}\n```"}]}}]}""", texto).size)
    }

    @Test
    fun `sin clave no consulta`() {
        try {
            Gemini("http://127.0.0.1:9/").revisar(texto, null)
            fail("debía fallar")
        } catch (e: Gemini.SinClaveException) {
            assertTrue(e.message!!.contains("clave"))
        }
    }
}
