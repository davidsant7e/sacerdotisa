package com.davidsant7e.sacerdotisa.motor

/**
 * Reglas propias que funcionan sin internet. Son pocas y seguras (pensadas para no molestar con
 * falsos avisos en un chat); LanguageTool encuentra el resto cuando hay conexión.
 * Devuelven errores con el formato de LanguageTool, así el mismo motor los explica.
 */
object ReglasLocales {

    private val PALABRA = Regex("[\\p{L}]+")

    /** Palabras que sin tilde siempre están mal (no hay otra palabra igual sin tilde). */
    val TILDES_SEGURAS: Map<String, String> = mapOf(
        "tambien" to "también", "despues" to "después", "aqui" to "aquí", "alli" to "allí", "ahi" to "ahí",
        "asi" to "así", "ademas" to "además", "jamas" to "jamás", "quizas" to "quizás", "atras" to "atrás",
        "detras" to "detrás", "adios" to "adiós", "perdon" to "perdón", "razon" to "razón", "corazon" to "corazón",
        "facil" to "fácil", "dificil" to "difícil", "util" to "útil", "arbol" to "árbol", "lapiz" to "lápiz",
        "azucar" to "azúcar", "debil" to "débil", "movil" to "móvil", "album" to "álbum", "cafe" to "café",
        "sofa" to "sofá", "mama" to "mamá", "musica" to "música", "rapido" to "rápido", "rapida" to "rápida",
        "ultimo" to "último", "ultima" to "última", "facilmente" to "fácilmente", "dia" to "día", "dias" to "días",
        "tio" to "tío", "tia" to "tía", "pais" to "país", "paises" to "países", "raiz" to "raíz", "maiz" to "maíz",
        "mio" to "mío", "mia" to "mía", "mios" to "míos", "mias" to "mías", "todavia" to "todavía",
        "alegria" to "alegría", "energia" to "energía", "policia" to "policía", "compania" to "compañía",
        "compañia" to "compañía", "economia" to "economía", "habia" to "había", "habian" to "habían",
        "tenia" to "tenía", "tenian" to "tenían", "podia" to "podía", "podian" to "podían", "queria" to "quería",
        "decia" to "decía", "vivia" to "vivía", "estan" to "están", "estaras" to "estarás", "podria" to "podría", "tendria" to "tendría", "haria" to "haría", "iria" to "iría",
        "telefonos" to "teléfonos", "numeros" to "números", "paginas" to "páginas", "rapidamente" to "rápidamente",
        // Monosílabos que NO llevan tilde
        "vió" to "vio", "dió" to "dio", "fué" to "fue", "fuí" to "fui", "dí" to "di", "ví" to "vi", "fé" to "fe",
    )

    /** Palabras comunes que en medio de la oración van en minúscula (para «Si la Vida…»). */
    private val COMUNES = setOf(
        "vida", "casa", "amor", "mundo", "gente", "trabajo", "familia", "tiempo", "dia", "día", "noche", "mañana",
        "tarde", "semana", "mes", "año", "persona", "personas", "amigo", "amiga", "amigos", "amigas", "hijo", "hija",
        "mamá", "papá", "mama", "papa", "hermano", "hermana", "novio", "novia", "esposo", "esposa", "señor", "señora",
        "comida", "agua", "dinero", "carro", "ciudad", "calle", "escuela", "colegio", "universidad", "oficina",
        "empresa", "jefe", "cliente", "proyecto", "reunión", "mensaje", "foto", "video", "música", "película",
        "libro", "juego", "partido", "equipo", "problema", "pregunta", "respuesta", "verdad", "mentira", "suerte",
        "felicidad", "guerra", "salud", "médico", "cita", "viaje", "playa", "fiesta", "cumpleaños",
        "navidad", "regalo", "perro", "gato", "corazón", "alma", "cuerpo", "cabeza", "mano", "ojos", "cosa", "cosas",
        "idea", "plan", "parte", "lugar", "momento", "vez", "forma", "manera", "historia", "palabra", "nombre",
    )

    /** Antes de estas palabras, una mayúscula no suele ser inicio de oración. */
    private val ANTES = setOf(
        "la", "el", "los", "las", "un", "una", "unos", "unas", "mi", "mis", "tu", "tus", "su", "sus", "nuestra",
        "nuestro", "de", "del", "en", "con", "por", "para", "sin", "esta", "este", "esa", "ese", "que", "y", "o",
    )

    private val INTERROGATIVOS = mapOf(
        "que" to "qué", "como" to "cómo", "cuando" to "cuándo", "donde" to "dónde", "quien" to "quién",
        "quienes" to "quiénes", "cual" to "cuál", "cuales" to "cuáles", "cuanto" to "cuánto", "cuanta" to "cuánta",
        "cuantos" to "cuántos", "cuantas" to "cuántas",
    )

    private val REPETIR_ESTA_BIEN = setOf("no", "si", "sí", "ya", "ja", "je", "jaja", "jeje", "muy", "bye", "chao", "dale")

    private val ABREVIATURAS = setOf("etc", "ej", "pág", "pag", "aprox", "sr", "sra", "srta", "dr", "dra", "ud", "uds", "vs", "no", "tel", "av", "cra", "cl")

    fun revisar(texto: String): List<Match> {
        val out = ArrayList<Match>()
        val enEnlace = zonasDeEnlaces(texto)
        fun libre(ini: Int, fin: Int) = enEnlace.none { ini < it.last + 1 && fin > it.first }

        for (m in PALABRA.findAll(texto)) {
            val w = m.value
            val ini = m.range.first
            val fin = m.range.last + 1
            if (!libre(ini, fin)) continue
            val bajo = w.lowercase()

            // 1) Tildes que siempre van (o que nunca van)
            TILDES_SEGURAS[bajo]?.let { buena ->
                out.add(Match(ini, w.length, "Falta o sobra una tilde.", listOf(conMismaMayuscula(w, buena)),
                    "SACERDOTISA_TILDE", "misspelling", "TYPOS"))
            }
            // -ion al final siempre lleva tilde (canción, televisión, camión, reunión)
            if (bajo.length > 4 && bajo.endsWith("ion") && out.none { it.offset == ini }) {
                val buena = bajo.dropLast(3) + "ión"
                out.add(Match(ini, w.length, "Falta una tilde.", listOf(conMismaMayuscula(w, buena)),
                    "SACERDOTISA_ION", "misspelling", "TYPOS"))
            }

            // 2) Minúscula al empezar la oración
            if (w[0].isLowerCase() && Explicador.inicioDeOracion(texto, ini) && !trasAbreviatura(texto, ini) && out.none { it.offset == ini }) {
                out.add(Match(ini, w.length, "La oración empieza con minúscula.", listOf(w.replaceFirstChar { it.uppercaseChar() }),
                    "SACERDOTISA_INICIO", "typographical", "CASING"))
            }

            // 3) Mayúscula en medio de la oración en una palabra común («Si la Vida…»)
            if (w.length > 1 && w[0].isUpperCase() && w.drop(1).all { it.isLowerCase() } && bajo in COMUNES &&
                !Explicador.inicioDeOracion(texto, ini)) {
                val previa = palabraAnterior(texto, ini)
                val siguiente = palabraSiguiente(texto, fin)
                val siguienteMayuscula = siguiente != null && siguiente[0].isUpperCase()
                if (previa != null && previa in ANTES && !siguienteMayuscula) { // previa en minúscula: «la Vida», no «La Paz»
                    out.add(Match(ini, w.length, "Mayúscula en medio de la oración.", listOf(bajo),
                        "SACERDOTISA_MAYUSCULA", "typographical", "CASING"))
                }
            }
        }

        // 4) Interrogativos y exclamativos sin tilde: ¿como estas? → ¿cómo…?
        Regex("[¿¡]\\s*([\\p{L}]+)").findAll(texto).forEach { m -> interrogativo(texto, m.groups[1]!!.range.first, m.groupValues[1], out) }
        // También sin «¿» de apertura: «como estas?» (muy común en el chat)
        Regex("(?:^|[.!?…]\\s+|\\n)([\\p{L}]+)[^.!?¿¡\\n]*\\?").findAll(texto).forEach { m ->
            interrogativo(texto, m.groups[1]!!.range.first, m.groupValues[1], out)
        }

        // 5) Palabra repetida: «el el»
        Regex("(?iu)(?<!\\p{L})(\\p{L}+)\\s+\\1(?!\\p{L})").findAll(texto).forEach { m ->
            val w = m.groupValues[1]
            if (libre(m.range.first, m.range.last + 1) && w.lowercase() !in REPETIR_ESTA_BIEN) {
                out.add(Match(m.range.first, m.value.length, "Palabra repetida.", listOf(w),
                    "SACERDOTISA_REPETIDA", "duplication", "MISC"))
            }
        }

        // 6) Espacios: antes de coma/punto, o falta después de coma
        Regex("(?<=\\S) +([,;:]|\\.(?!\\.))").findAll(texto).forEach { m ->
            if (!libre(m.range.first, m.range.last + 1)) return@forEach
            val signo = m.groupValues[1]
            val despues = m.range.last + 1
            val sigueLetra = despues < texto.length && texto[despues].isLetter()
            out.add(Match(m.range.first, m.value.length, "Sobra un espacio antes del signo.",
                listOf(if (sigueLetra) "$signo " else signo), "SACERDOTISA_ESPACIO", "whitespace", "TYPOGRAPHY"))
        }
        Regex("(?<=\\p{L}),(?=\\p{L})").findAll(texto).forEach { m ->
            if (libre(m.range.first, m.range.last + 1) && out.none { m.range.first in it.offset until it.offset + it.length }) {
                out.add(Match(m.range.first, 1, "Falta un espacio después de la coma.", listOf(", "),
                    "SACERDOTISA_ESPACIO", "whitespace", "TYPOGRAPHY"))
            }
        }

        // 7) «haber si» → «a ver si»
        Regex("(?i)\\bhaber(?= si\\b)").findAll(texto).forEach { m ->
            out.add(Match(m.range.first, m.value.length, "Se confunde «haber» con «a ver».",
                listOf(conMismaMayuscula(m.value, "a ver")), "SACERDOTISA_A_VER", "grammar", "CONFUSED_WORDS"))
        }

        return sinSolapes(out)
    }

    private fun interrogativo(texto: String, ini: Int, w: String, out: MutableList<Match>) {
        val buena = INTERROGATIVOS[w.lowercase()] ?: return
        if (out.any { it.offset == ini && it.ruleId == "SACERDOTISA_INTERROGATIVO" }) return
        out.removeAll { it.offset == ini } // la tilde manda sobre «empieza con minúscula»
        val conTilde = if (Explicador.inicioDeOracion(texto, ini)) buena.replaceFirstChar { it.uppercaseChar() } else conMismaMayuscula(w, buena)
        out.add(Match(ini, w.length, "En una pregunta o exclamación lleva tilde.", listOf(conTilde),
            "SACERDOTISA_INTERROGATIVO", "misspelling", "DIACRITICS"))
    }

    private fun conMismaMayuscula(original: String, nueva: String): String =
        if (original.isNotEmpty() && original[0].isUpperCase()) nueva.replaceFirstChar { it.uppercaseChar() } else nueva

    private fun palabraAnterior(texto: String, ini: Int): String? {
        var i = ini - 1
        while (i >= 0 && texto[i] == ' ') i--
        val fin = i + 1
        while (i >= 0 && texto[i].isLetter()) i--
        return if (fin > i + 1) texto.substring(i + 1, fin) else null
    }

    private fun palabraSiguiente(texto: String, fin: Int): String? {
        var i = fin
        while (i < texto.length && texto[i] == ' ') i++
        val ini = i
        while (i < texto.length && texto[i].isLetter()) i++
        return if (i > ini) texto.substring(ini, i) else null
    }

    private fun trasAbreviatura(texto: String, ini: Int): Boolean {
        var i = ini - 1
        while (i >= 0 && texto[i].isWhitespace()) i--
        if (i >= 0 && texto[i] == '…') return true // tras puntos suspensivos se puede seguir en minúscula
        if (i < 0 || texto[i] != '.') return false
        if (i >= 1 && texto[i - 1] == '.') return true // «...»
        val p = palabraAnterior(texto, i)?.lowercase() ?: return false
        return p in ABREVIATURAS || p.length == 1
    }

    /** Enlaces, correos, @menciones y #etiquetas: ahí no se revisa nada. */
    private fun zonasDeEnlaces(texto: String): List<IntRange> =
        Regex("(https?://\\S+|www\\.\\S+|\\S+@\\S+|[@#]\\S+|\\S+\\.(com|co|org|net|io)\\S*)").findAll(texto).map { it.range }.toList()

    /** Si dos avisos tocan el mismo texto, queda el primero. */
    fun sinSolapes(lista: List<Match>): List<Match> {
        val out = ArrayList<Match>()
        for (m in lista.sortedBy { it.offset }) {
            if (out.none { m.offset < it.offset + it.length && it.offset < m.offset + m.length }) out.add(m)
        }
        return out
    }
}
