package com.davidsant7e.sacerdotisa.motor

/** Un aviso listo para mostrar en la burbuja. */
data class Aviso(val match: Match, val explicacion: Explicacion, val deLanguageTool: Boolean) {
    val sugerencia: String? get() = match.replacements.firstOrNull()
}

/** Qué revisar. Puntuación y estilo vienen apagados: en el chat casi nadie abre «¿» y molestaría. */
data class Filtros(
    val puntuacion: Boolean = false,
    val estilo: Boolean = false,
    val ignorarReglas: Set<String> = emptySet(),
)

/**
 * Junta las reglas locales (al instante, sin internet) con LanguageTool (cuando hay conexión),
 * filtra lo que no interesa y explica cada error.
 */
class Revisor(private val datos: Datos) {

    fun locales(texto: String, filtros: Filtros = Filtros()): List<Aviso> =
        avisos(texto, ReglasLocales.revisar(texto).map { it to false }, "es", filtros)

    /** Combina: LanguageTool manda; las reglas locales completan lo que LanguageTool no marcó. */
    fun combinados(texto: String, lt: LanguageTool.Respuesta?, filtros: Filtros = Filtros()): List<Aviso> {
        if (lt == null) return locales(texto, filtros)
        val deLt = lt.matches.map { it to true }
        val locales = ReglasLocales.revisar(texto).filter { l ->
            lt.matches.none { l.offset < it.offset + it.length && it.offset < l.offset + l.length }
        }.map { it to false }
        return avisos(texto, deLt + locales, lt.idioma, filtros)
    }

    private fun avisos(texto: String, lista: List<Pair<Match, Boolean>>, idioma: String, filtros: Filtros): List<Aviso> =
        lista.sortedBy { it.first.offset }
            .filter { (m, _) -> m.offset >= 0 && m.offset + m.length <= texto.length && m.ruleId !in filtros.ignorarReglas }
            .map { (m, lt) -> Aviso(m, Explicador.explicar(m, texto, idioma, datos), lt) }
            .filter { a ->
                when (a.explicacion.tipo) {
                    "PUNCTUATION" -> filtros.puntuacion
                    "STYLE", "COLLOCATIONS" -> filtros.estilo
                    else -> true
                }
            }

    /** Aplica una sugerencia y devuelve el texto nuevo y dónde queda el cursor. */
    fun corregir(texto: String, aviso: Aviso): Pair<String, Int>? {
        val valor = aviso.sugerencia ?: return null
        val m = aviso.match
        if (m.offset + m.length > texto.length || texto.substring(m.offset, m.offset + m.length) != aviso.explicacion.original) return null
        return Explicador.aplicar(texto, listOf(Cambio(m.offset, m.length, valor))) to (m.offset + valor.length)
    }

    /** Aplica todas las sugerencias que no se pisan entre sí. */
    fun corregirTodo(texto: String, avisos: List<Aviso>): String =
        Explicador.aplicar(texto, ReglasLocales.sinSolapes(avisos.filter { it.sugerencia != null }.map { it.match })
            .map { Cambio(it.offset, it.length, it.replacements.first()) })
}
