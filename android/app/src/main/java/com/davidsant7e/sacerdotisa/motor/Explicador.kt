package com.davidsant7e.sacerdotisa.motor

/**
 * Motor de explicaciones: recibe un error (de LanguageTool o de las reglas locales) y devuelve
 * el «por qué» en español claro. Es el mismo motor de extension/explicar.js y windows/src/Explicador.cs;
 * los tres se prueban con compartido/casos.json.
 */
object Explicador {
    private const val VOCALES = "aeiouáéíóúü"
    private const val FUERTES = "aeoáéóíú" // í y ú con tilde se comportan como fuertes (hiato)
    private const val CON_TILDE = "áéíóú"
    private val TILDES = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u')
    private val GRUPOS = setOf("pr", "pl", "br", "bl", "fr", "fl", "tr", "dr", "cr", "cl", "gr", "gl", "kr", "kl", "ch", "ll", "rr")

    fun sinTildes(s: String): String = buildString(s.length) { for (c in s) append(TILDES[c] ?: c) }

    private fun tieneTilde(s: String) = s.any { it in CON_TILDE }

    private fun esVocal(p: String, i: Int): Boolean {
        val c = p[i]
        if (c == 'y') return i == p.length - 1 && i > 0 && p[i - 1] in VOCALES // «rey», «hoy»
        if (c !in VOCALES) return false
        if (c == 'u' && i > 0 && (p[i - 1] == 'q' || p[i - 1] == 'g') && i + 1 < p.length && (p[i + 1] == 'e' || p[i + 1] == 'i')) return false
        return true
    }

    private fun fuerte(c: Char) = c in FUERTES

    private class Nucleo(val ini: Int, val fin: Int, val tilde: Boolean)

    private fun nucleos(p: String): List<Nucleo> {
        val res = ArrayList<Nucleo>()
        var i = 0
        while (i < p.length) {
            if (!esVocal(p, i)) { i++; continue }
            val ini = i
            while (i + 1 < p.length && esVocal(p, i + 1)) {
                if (fuerte(p[i]) && fuerte(p[i + 1])) break // hiato: dos fuertes seguidas
                i++
            }
            res.add(Nucleo(ini, i + 1, tieneTilde(p.substring(ini, i + 1))))
            i++
        }
        return res
    }

    /** Separa en sílabas (aproximado, suficiente para explicar la tilde). */
    fun silabas(p: String): List<String> {
        val ns = nucleos(p)
        if (ns.size <= 1) return listOf(p)
        val cortes = ArrayList<Int>()
        for (k in 0 until ns.size - 1) {
            val a = ns[k].fin
            val b = ns[k + 1].ini
            val corte = when (b - a) {
                0, 1 -> a
                2 -> if (p.substring(a, b) in GRUPOS) a else a + 1
                else -> if (p.substring(b - 2, b) in GRUPOS) b - 2 else b - 1
            }
            cortes.add(corte)
        }
        val out = ArrayList<String>()
        var desde = 0
        for (c in cortes) { out.add(p.substring(desde, c)); desde = c }
        out.add(p.substring(desde))
        return out
    }

    /** Posición del acento desde el final: 1 aguda, 2 llana, 3+ esdrújula. */
    fun acentoDesdeFinal(p: String): Int {
        val ns = nucleos(p)
        if (ns.isEmpty()) return 1
        for (k in ns.indices) if (ns[k].tilde) return ns.size - k
        val termina = p.isNotEmpty() && p.last() in "aeiouns"
        return if (ns.size == 1) 1 else if (termina) 2 else 1
    }

    fun silabasMarcadas(p: String): String {
        val ss = silabas(p)
        val idx = ss.size - acentoDesdeFinal(p)
        return ss.mapIndexed { i, s -> if (i == idx) s.uppercase() else s }.joinToString("-")
    }

    private fun plantilla(t: String, vars: Map<String, String>): String =
        Regex("\\{(\\w+)\\}").replace(t) { vars[it.groupValues[1]] ?: "" }

    private fun categoriaDe(m: Match, d: Datos): String {
        if (d.categorias.containsKey(m.categoryId)) return m.categoryId
        return d.tiposProblema[m.issueType] ?: "MISC"
    }

    /** Explica por qué una palabra lleva o no tilde (correcta = forma correcta). */
    fun explicarTilde(original: String, correcta: String, d: Datos): String {
        val t = d.tildes
        val p = correcta.lowercase()
        val dia = d.diacriticas[sinTildes(p)]
        val ponTilde = tieneTilde(p) && !tieneTilde(original.lowercase())
        if (dia != null) {
            return if (ponTilde) "${dia.con} Aquí corresponde esa forma. (${dia.sin})"
            else "${dia.sin} Aquí corresponde esa forma. (${dia.con})"
        }
        if (p.any { it.isWhitespace() }) return t["general"] ?: ""
        val ns = nucleos(p)
        val marcadas = silabasMarcadas(p)
        if (ns.size <= 1) return t["monosilabo"] ?: ""
        val i = p.indexOfFirst { it == 'í' || it == 'ú' }
        if (i >= 0) {
            val vecina = (i > 0 && p[i - 1] in VOCALES) || (i + 1 < p.length && p[i + 1] in VOCALES)
            if (vecina) return plantilla(t["hiato"] ?: "", mapOf("vocal" to p[i].toString(), "silabas" to marcadas))
        }
        val ac = acentoDesdeFinal(p)
        val tiene = tieneTilde(p)
        val v = mapOf("silabas" to marcadas)
        if (ac >= 3) return plantilla(t["esdrujula"] ?: "", v)
        if (ac == 1) return plantilla(t[if (tiene) "aguda_con" else "aguda_sin"] ?: "", v)
        return plantilla(t[if (tiene) "llana_con" else "llana_sin"] ?: "", v)
    }

    fun inicioDeOracion(texto: String, offset: Int): Boolean {
        var i = offset - 1
        while (i >= 0 && (texto[i].isWhitespace() || texto[i] in "\"'«“(¿¡")) i--
        return i < 0 || texto[i] in ".!?…"
    }

    private fun buscarConfusion(a0: String, b0: String, d: Datos): String? {
        val a = a0.lowercase().trim()
        val b = b0.lowercase().trim()
        return d.confusiones.firstOrNull { (it.a == a && it.b == b) || (it.a == b && it.b == a) }?.porque
    }

    /** idioma: "es", "en-US"… */
    fun explicar(m: Match, texto: String, idioma: String?, d: Datos): Explicacion {
        val ini = m.offset.coerceIn(0, texto.length)
        val original = texto.substring(ini, (ini + m.length).coerceAtMost(texto.length))
        val sugerencias = m.replacements
        val rep = sugerencias.firstOrNull() ?: ""
        val esEspanol = idioma.isNullOrEmpty() || idioma.lowercase().startsWith("es")
        val cat = categoriaDe(m, d)
        fun res(tipo: String, porque: String) =
            Explicacion(tipo, d.categoria(tipo).titulo, porque, m.message, original, sugerencias)

        // 1) Solo cambia mayúscula/minúscula
        if (rep.isNotEmpty() && original.isNotEmpty() && original != rep && original.lowercase() == rep.lowercase()) {
            val my = d.mayusculas
            val aMayuscula = rep[0] != rep[0].lowercaseChar() && original[0] == original[0].lowercaseChar()
            val porque = if (aMayuscula) {
                val inicio = inicioDeOracion(texto, ini)
                my[if (esEspanol) (if (inicio) "es_inicio" else "es_propio") else (if (inicio) "en_inicio" else "en_propio")] ?: ""
            } else {
                plantilla(my[if (esEspanol) "es_minuscula" else "en_minuscula"] ?: "", mapOf("palabra" to original))
            }
            return res("CASING", porque)
        }

        // 2) Solo cambia la tilde (español)
        if (esEspanol && rep.isNotEmpty() && original != rep && sinTildes(original.lowercase()) == sinTildes(rep.lowercase())) {
            return res("DIACRITICS", explicarTilde(original, rep, d))
        }

        // 3) Palabras que se confunden (español)
        if (esEspanol && rep.isNotEmpty()) {
            buscarConfusion(original, rep, d)?.let { return res("CONFUSED_WORDS", it) }
        }

        // 4) Palabra repetida
        val palabras = original.trim().split(Regex("\\s+"))
        if (m.issueType == "duplication" || (palabras.size == 2 && palabras[0].lowercase() == palabras[1].lowercase())) {
            return res("REDUNDANCY", plantilla(d.repetida, mapOf("palabra" to palabras[0])))
        }

        // 5) Espacios
        if (m.issueType == "whitespace") return res("TYPOGRAPHY", d.espacios)

        // 6) La explicación de la categoría
        return res(cat, d.categoria(cat).porque)
    }

    /** Aplica los cambios elegidos (de atrás hacia adelante, para no mover las posiciones). */
    fun aplicar(texto: String, cambios: List<Cambio>): String {
        var t = texto
        for (c in cambios.sortedByDescending { it.offset }) {
            if (c.offset < 0 || c.offset + c.length > t.length) continue
            t = t.substring(0, c.offset) + c.valor + t.substring(c.offset + c.length)
        }
        return t
    }
}
