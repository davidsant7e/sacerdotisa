package com.davidsant7e.sacerdotisa.motor

/** Un aviso listo para mostrar en la burbuja. */
data class Aviso(
    val match: Match,
    val explicacion: Explicacion,
    val deLanguageTool: Boolean,
    /** Lo encontró Gemini al revisar el texto completo. */
    val deIA: Boolean = false,
) {
    val sugerencia: String? get() = match.replacements.firstOrNull()
}

/** Qué revisar. Puntuación y estilo vienen apagados: en el chat casi nadie abre «¿» y molestaría. */
data class Filtros(
    val puntuacion: Boolean = false,
    /** Comas mal puestas o que faltan, mirando la frase completa (ReglasComas). */
    val comas: Boolean = true,
    val estilo: Boolean = false,
    val ignorarReglas: Set<String> = emptySet(),
)

/**
 * Junta las reglas locales (al instante, sin internet) con LanguageTool (cuando hay conexión),
 * filtra lo que no interesa y explica cada error.
 */
class Revisor(private val datos: Datos) {

    fun locales(texto: String, filtros: Filtros = Filtros()): List<Aviso> =
        avisos(texto, propias(texto).map { it to false }, "es", filtros)

    /** Reglas del celular: palabras (ReglasLocales) y frase completa (ReglasComas). */
    private fun propias(texto: String): List<Match> {
        val palabras = ReglasLocales.revisar(texto)
        val comas = ReglasComas.revisar(texto).filter { c ->
            palabras.none { c.offset < it.offset + it.length && it.offset < c.offset + c.length }
        }
        return palabras + comas
    }

    /** Combina: LanguageTool manda; las reglas locales completan lo que LanguageTool no marcó. */
    fun combinados(texto: String, lt: LanguageTool.Respuesta?, filtros: Filtros = Filtros()): List<Aviso> {
        if (lt == null) return locales(texto, filtros)
        val deLt = lt.matches.map { it to true }
        val locales = propias(texto).filter { l ->
            lt.matches.none { l.offset < it.offset + it.length && it.offset < l.offset + l.length }
        }.map { it to false }
        return avisos(texto, deLt + locales, lt.idioma, filtros)
    }

    private fun avisos(texto: String, lista: List<Pair<Match, Boolean>>, idioma: String, filtros: Filtros): List<Aviso> =
        lista.sortedBy { it.first.offset }
            .filter { (m, _) -> m.offset >= 0 && m.offset + m.length <= texto.length && m.ruleId !in filtros.ignorarReglas }
            .map { (m, lt) -> Aviso(m, Explicador.explicar(m, texto, idioma, datos), lt) }
            .filter { a ->
                if (a.match.ruleId.startsWith(ReglasComas.PREFIJO)) return@filter filtros.comas
                when (a.explicacion.tipo) {
                    "PUNCTUATION" -> filtros.puntuacion
                    "STYLE", "COLLOCATIONS" -> filtros.estilo
                    else -> true
                }
            }

    /** Avisos de «Revisar texto completo» (Gemini). */
    fun deIA(hallazgos: List<Gemini.Hallazgo>): List<Aviso> = hallazgos.map { h ->
        val cat = Gemini.categoria(h.tipo)
        val titulo = if (h.tipo.lowercase() == "coma") "Coma" else datos.categoria(cat).titulo
        Aviso(
            Match(h.offset, h.length, "", listOf(h.sugerencia), "GEMINI_${h.tipo.uppercase()}", "", cat),
            Explicacion(cat, titulo, h.porque, "", h.original, listOf(h.sugerencia)),
            deLanguageTool = false,
            deIA = true,
        )
    }

    /** Suma los avisos de la IA a los demás, sin repetir uno que ya esté (mismo lugar y mismo cambio). */
    fun conIA(avisos: List<Aviso>, ia: List<Aviso>): List<Aviso> {
        val nuevos = ia.filter { i ->
            avisos.none { a -> a.match.offset == i.match.offset && a.match.length == i.match.length && a.sugerencia == i.sugerencia }
        }
        return (avisos + nuevos).sortedBy { it.match.offset }
    }

    /**
     * Después de corregir algo, el texto cambió: cada aviso de la IA se vuelve a buscar por su
     * fragmento (el más cercano a donde estaba). Los que ya no aparecen se descartan.
     */
    fun reubicarIA(texto: String, ia: List<Aviso>): List<Aviso> {
        val usados = ArrayList<IntRange>()
        return ia.mapNotNull { a ->
            val frag = a.explicacion.original
            var mejor: Int? = null
            var i = texto.indexOf(frag)
            while (i >= 0) {
                val libre = usados.none { i <= it.last && it.first < i + frag.length }
                if (libre && (mejor == null || kotlin.math.abs(i - a.match.offset) < kotlin.math.abs(mejor - a.match.offset))) mejor = i
                i = texto.indexOf(frag, i + 1)
            }
            val pos = mejor ?: return@mapNotNull null
            usados += pos until pos + frag.length
            a.copy(match = a.match.copy(offset = pos))
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
