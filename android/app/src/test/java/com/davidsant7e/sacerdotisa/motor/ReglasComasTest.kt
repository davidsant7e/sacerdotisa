package com.davidsant7e.sacerdotisa.motor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasComasTest {

    private val datos = Datos.desdeJson(
        listOf(File("src/main/assets/explicaciones.json"), File("app/src/main/assets/explicaciones.json"))
            .first { it.exists() }.readText(),
    )
    private val revisor = Revisor(datos)

    /** "original→sugerencia" de los avisos de coma. */
    private fun comas(texto: String) = revisor.locales(texto)
        .filter { it.match.ruleId.startsWith(ReglasComas.PREFIJO) }
        .map { "${it.explicacion.original}→${it.sugerencia}" }

    @Test
    fun `sobra la coma entre sujeto y verbo`() {
        assertEquals(listOf("vecino, es→vecino es"), comas("El perro de mi vecino, es muy bravo."))
        assertEquals(listOf("trabajo, me→trabajo me"), comas("Mi jefe del trabajo, me dijo que mañana."))
        val e = revisor.locales("Los niños del barrio, juegan fútbol. El carro, no arranca.")
        assertEquals(listOf("carro, no→carro no"), e.map { "${it.explicacion.original}→${it.sugerencia}" })
        assertTrue(e.single().explicacion.porque.contains("sujeto"))
    }

    @Test
    fun `sobra la coma antes de la y final de una enumeracion`() {
        assertEquals(listOf("leche, y→leche y"), comas("Compré pan, leche, y huevos."))
        assertEquals(listOf("rojo, o→rojo o"), comas("Puede ser azul, verde, rojo, o negro."))
    }

    @Test
    fun `falta coma antes de pero y aunque`() {
        assertEquals(listOf("ir pero→ir, pero"), comas("Yo quería ir pero empezó a llover."))
        assertEquals(listOf("hago aunque→hago, aunque"), comas("Mañana lo hago aunque no tenga tiempo."))
    }

    @Test
    fun `conectores al inicio y en medio`() {
        assertEquals(listOf("Sin embargo→Sin embargo,"), comas("Sin embargo no llegó a tiempo."))
        assertEquals(listOf("Por lo tanto→Por lo tanto,"), comas("Llovió. Por lo tanto nos quedamos."))
        assertEquals(listOf("intenté sin embargo→intenté, sin embargo,"), comas("Lo intenté sin embargo no pude."))
        assertEquals(listOf("es decir→es decir,"), comas("Vengo el lunes, es decir mañana."))
    }

    @Test
    fun `falta la coma del vocativo`() {
        assertEquals(listOf("Hola Juan→Hola, Juan"), comas("Hola Juan, ¿cómo vas?"))
        assertEquals(listOf("Gracias María→Gracias, María"), comas("Gracias María por todo."))
    }

    @Test
    fun `sobra la coma antes de que`() {
        assertEquals(listOf("dijo, que→dijo que"), comas("Ella me dijo, que vendría."))
        assertEquals(listOf("Creo, que→Creo que"), comas("Creo, que sí."))
    }

    @Test
    fun `comas bien puestas no dan avisos`() {
        for (t in listOf(
            "Compré pan, leche y huevos.",
            "Quería ir, pero empezó a llover.",
            "Es pobre pero honrado.",
            "Sin embargo, no llegó.",
            "Lo intenté, sin embargo, no pude.",
            "Lo intenté; sin embargo, no pude.",
            "Mi hermano, que vive en Cali, llega hoy.",
            "Esta semana, tengo mucho trabajo.",
            "Un día, llegó tarde.",
            "Hola, Juan.",
            "Juan, es tarde.",
            "Dijo que vendría.",
            "Además de eso, compré pan.",
            "O sea que no vienes.",
            "Llegué tarde, comí algo y me acosté temprano porque estaba cansado.",
            "Fui al mercado, compré frutas, y después pasé por la casa de mi tía a dejarle el regalo.",
            "hola juan", // chat en minúscula: aquí no se adivina el nombre
            "",
        )) assertEquals("«$t»", emptyList<String>(), comas(t))
    }

    @Test
    fun `se pueden apagar y corregir todo las arregla`() {
        val t = "El perro de mi vecino, es muy bravo pero es noble."
        assertEquals(2, comas(t).size)
        assertEquals(0, revisor.locales(t, Filtros(comas = false)).count { it.match.ruleId.startsWith(ReglasComas.PREFIJO) })
        assertEquals("El perro de mi vecino es muy bravo, pero es noble.", revisor.corregirTodo(t, revisor.locales(t)))
    }
}
