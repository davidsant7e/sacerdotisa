package com.davidsant7e.sacerdotisa.motor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasLocalesTest {

    private val datos = Datos.desdeJson(
        listOf(File("src/main/assets/explicaciones.json"), File("app/src/main/assets/explicaciones.json"))
            .first { it.exists() }.readText(),
    )
    private val revisor = Revisor(datos)

    /** "original→sugerencia" de cada aviso. */
    private fun cambios(texto: String) = revisor.locales(texto).map { "${it.explicacion.original}→${it.sugerencia}" }

    private fun explicacion(texto: String, original: String) =
        revisor.locales(texto).first { it.explicacion.original == original }.explicacion

    @Test
    fun `el ejemplo del usuario - Si la Vida`() {
        assertEquals(listOf("Vida→vida"), cambios("Si la Vida es bella."))
        val e = explicacion("Si la Vida es bella.", "Vida")
        assertEquals("CASING", e.tipo)
        assertTrue(e.porque, e.porque.contains("minúscula"))
    }

    @Test
    fun `tildes con su regla`() {
        val e = explicacion("Una cancion bonita.", "cancion")
        assertEquals("DIACRITICS", e.tipo)
        assertTrue(e.porque, e.porque.contains("can-CIÓN"))
        assertEquals(listOf("facil→fácil"), cambios("Eso es facil."))
        assertTrue(explicacion("Eso es facil.", "facil").porque.contains("llana"))
        assertEquals(listOf("dia→día"), cambios("Buen dia."))
        assertEquals(listOf("vió→vio"), cambios("Lo vió ayer."))
        assertEquals(listOf("Television→Televisión"), cambios("Television nueva."))
        assertEquals(listOf("despues→después", "reunion→reunión"), cambios("Hablamos despues, tengo reunion."))
    }

    @Test
    fun `preguntas sin tilde, con o sin signo de apertura`() {
        assertEquals(listOf("como→Cómo"), cambios("¿como te fue?"))
        assertEquals(listOf("Como→Cómo"), cambios("Como estas?"))
        assertEquals(listOf("que→qué"), cambios("Oye, ¿que hora es?"))
        val e = explicacion("¿donde vives?", "donde")
        assertTrue(e.porque, e.porque.contains("pregunta"))
    }

    @Test
    fun `minuscula al empezar`() {
        assertEquals(listOf("hola→Hola"), cambios("hola, te escribo."))
        assertEquals(listOf("mañana→Mañana"), cambios("Hola. mañana voy."))
        assertTrue(explicacion("Hola. mañana voy.", "mañana").porque.contains("empieza la oración"))
    }

    @Test
    fun `repetidas, espacios y a ver`() {
        assertEquals(listOf("el el→el"), cambios("Fue el el mejor."))
        assertEquals(listOf(" ,→, "), cambios("Hola ,amigo."))
        assertEquals(listOf(",→, "), cambios("Hola,amigo."))
        assertEquals(listOf("Haber→A ver"), cambios("Haber si vienes."))
        assertEquals("CONFUSED_WORDS", explicacion("Haber si vienes.", "Haber").tipo)
    }

    @Test
    fun `texto bien escrito no tiene avisos`() {
        for (t in listOf(
            "Hola, ¿qué tal? Mañana nos vemos en la casa de Ana.",
            "La Paz es bonita. Vivo en El Salvador.",
            "Compré 1,5 kilos de café.",
            "Mira www.google.com y escríbeme a ana@correo.com.",
            "Trajo frutas, pan, etc. y nos fuimos.",
            "No, no quiero. Ya ya, tranquilo.",
            "Pensé que... bueno, luego te cuento.",
            "Las canciones de la semana.",
            "Sí, la vida es bella.",
            "Hay que ver cómo cambió todo.",
            "",
        )) assertEquals("«$t»", emptyList<String>(), cambios(t))
    }

    @Test
    fun `corregir y corregir todo`() {
        val texto = "Si la Vida es facil."
        val avisos = revisor.locales(texto)
        assertEquals("Si la vida es fácil.", revisor.corregirTodo(texto, avisos))
        val (nuevo, cursor) = revisor.corregir(texto, avisos.first())!!
        assertEquals("Si la vida es facil.", nuevo)
        assertEquals(10, cursor)
    }

    @Test
    fun `languagetool manda y las locales completan`() {
        val texto = "Si la Vida es facil."
        val lt = LanguageTool.Respuesta(listOf(Match(14, 5, "Falta tilde", listOf("fácil"), "X", "misspelling", "TYPOS")), "es")
        val avisos = revisor.combinados(texto, lt)
        assertEquals(listOf("Vida→vida" to false, "facil→fácil" to true), avisos.map { "${it.explicacion.original}→${it.sugerencia}" to it.deLanguageTool })
    }

    @Test
    fun `puntuacion y estilo apagados por defecto`() {
        val texto = "Vienes hoy?"
        val lt = LanguageTool.Respuesta(listOf(Match(0, 6, "Falta ¿", listOf("¿Vienes"), "ES_QUESTION_MARK", "typographical", "PUNCTUATION")), "es")
        assertEquals(0, revisor.combinados(texto, lt).size)
        assertEquals(1, revisor.combinados(texto, lt, Filtros(puntuacion = true)).size)
    }
}
