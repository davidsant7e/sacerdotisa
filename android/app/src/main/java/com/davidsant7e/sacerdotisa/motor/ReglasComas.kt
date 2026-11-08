package com.davidsant7e.sacerdotisa.motor

/**
 * Reglas de coma que miran la frase completa, no la palabra: si la coma está en un sitio donde
 * no va (entre sujeto y verbo, antes de la «y» de una enumeración, antes de «que»…) o si falta
 * donde sí va (antes de «pero», después de «sin embargo», con el nombre de a quien le hablas).
 *
 * Son reglas conservadoras: solo marcan los casos claros. Lo que necesita entender el sentido
 * lo revisa Gemini con «Revisar texto completo».
 */
object ReglasComas {

    const val PREFIJO = "SACERDOTISA_COMA"

    /** El «por qué» de cada regla (lo usa el Explicador). */
    val PORQUE: Map<String, String> = mapOf(
        "${PREFIJO}_Y" to "En una enumeración, el último elemento se une con «y» (o «e», «ni», «o») sin coma antes: «pan, leche y huevos». La coma antes de la «y» solo va cuando lo que sigue no es un elemento más de la lista.",
        "${PREFIJO}_PERO" to "Antes de «pero» y «aunque» va coma cuando unen dos ideas: «Quería ir, pero llovió». Solo se omite en expresiones muy cortas, como «pobre pero honrado».",
        "${PREFIJO}_CONECTOR" to "Los conectores como «sin embargo», «por lo tanto», «es decir» o «por ejemplo» van seguidos de coma cuando abren la oración: «Sin embargo, no llegó».",
        "${PREFIJO}_INCISO" to "En medio de la oración, conectores como «sin embargo», «es decir» o «por ejemplo» van entre comas: «Lo intenté, sin embargo, no pude». Si une dos oraciones completas, también sirve punto y coma: «Lo intenté; sin embargo, no pude».",
        "${PREFIJO}_SUJETO" to "No se pone coma entre el sujeto y su verbo, aunque el sujeto sea largo: «El perro de mi vecino es muy bravo». La coma ahí corta la idea por la mitad. Solo hay comas si se mete un inciso entre dos comas: «Mi hermano, que vive en Cali, llega hoy».",
        "${PREFIJO}_VOCATIVO" to "El nombre de la persona a la que le hablas (el vocativo) se separa con coma: «Hola, Juan», «Gracias, María», «Feliz cumpleaños, Ana».",
        "${PREFIJO}_QUE" to "No va coma entre el verbo y lo que se dice o se piensa con «que»: «Dijo que vendría», «Creo que sí». Ese «que» completa al verbo; la coma lo separaría.",
    )

    private const val L = "\\p{L}"

    /** Fin de oración o signo que corta la frase. */
    private val CORTE = ".!?…;:\n¿¡()\""

    private val CONECTORES_INICIO = listOf(
        "sin embargo", "no obstante", "por lo tanto", "por tanto", "por consiguiente", "por ende", "es decir",
        "o sea", "por ejemplo", "en cambio", "por otro lado", "por otra parte", "de hecho", "en primer lugar",
        "en segundo lugar", "por último", "en conclusión", "en resumen", "en fin", "además", "asimismo",
    )
    private val CONECTORES_MEDIO = listOf("sin embargo", "no obstante", "es decir", "por ejemplo", "por lo tanto", "por consiguiente", "o sea", "en cambio")

    private val DETERMINANTES = setOf(
        "el", "la", "los", "las", "mi", "mis", "tu", "tus", "su", "sus", "este", "esta", "estos", "estas", "ese", "esa",
        "esos", "esas", "aquel", "aquella", "nuestro", "nuestra", "nuestros", "nuestras", "un", "una", "unos", "unas",
    )
    /** «Esta semana, tengo…», «Un día, llegó…»: complementos de tiempo al inicio, ahí la coma se admite. */
    private val TIEMPO = setOf(
        "año", "años", "mes", "meses", "semana", "semanas", "día", "días", "dia", "mañana", "tarde", "noche", "vez",
        "veces", "fin", "momento", "rato", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo",
        "hora", "horas", "verano", "invierno", "época", "temporada", "mientras",
    )
    private val VERBOS = setOf(
        "es", "son", "era", "eran", "fue", "fueron", "será", "serán", "sería", "está", "están", "estaba", "estaban",
        "estuvo", "tiene", "tienen", "tenía", "tenían", "tuvo", "va", "van", "iba", "iban", "hace", "hacen", "hizo",
        "puede", "pueden", "podía", "pudo", "debe", "deben", "quiere", "quieren", "quería", "necesita", "necesitan",
        "parece", "parecen", "sigue", "siguen", "llegó", "llegaron", "llega", "llegan", "dijo", "dijeron", "dice",
        "dicen", "sale", "salió", "cuesta", "cuestan", "vale", "queda", "quedan", "funciona", "funcionan", "trabaja",
        "vive", "viven", "come", "ha", "han", "había", "habían", "hay",
        // pronombre o «no» justo antes del verbo: «El jefe, me dijo…», «El carro, no arranca»
        "me", "te", "se", "le", "les", "nos", "no",
    )
    private val SALUDOS = listOf(
        "muchas gracias", "buenos días", "buenas tardes", "buenas noches", "buen día", "feliz cumpleaños",
        "hola", "gracias", "chao", "adiós", "bienvenido", "bienvenida", "felicitaciones", "tranquilo", "tranquila",
    )
    private val VERBOS_QUE = setOf(
        "dijo", "dije", "dijeron", "dice", "dicen", "digo", "creo", "cree", "creen", "creí", "pienso", "piensa",
        "piensan", "pensé", "espero", "espera", "esperan", "sé", "sabe", "saben", "sabía", "quiero", "quiere",
        "quieren", "parece", "parecía", "siento", "supongo", "recuerdo", "recuerda", "imagino", "veo", "entiendo",
        "opino", "considero", "acepto", "confirmo", "afirmo", "comentó", "explicó", "contó", "avisó",
    )

    fun revisar(texto: String): List<Match> {
        val out = ArrayList<Match>()
        comaAntesDeY(texto, out)
        faltaComaPero(texto, out)
        conectores(texto, out)
        sujetoVerbo(texto, out)
        vocativo(texto, out)
        comaAntesDeQue(texto, out)
        return ReglasLocales.sinSolapes(out)
    }

    private fun match(ini: Int, fin: Int, mensaje: String, sugerencia: String, regla: String) =
        Match(ini, fin - ini, mensaje, listOf(sugerencia), "${PREFIJO}_$regla", "typographical", "PUNCTUATION")

    /** Inicio de la frase que contiene `pos` (después del último signo que corta). */
    private fun inicioFrase(t: String, pos: Int, tambienComa: Boolean = false): Int {
        var i = pos - 1
        while (i >= 0 && t[i] !in CORTE && !(tambienComa && t[i] == ',')) i--
        return i + 1
    }

    private fun finFrase(t: String, pos: Int, tambienComa: Boolean = false): Int {
        var i = pos
        while (i < t.length && t[i] !in CORTE && !(tambienComa && t[i] == ',')) i++
        return i
    }

    private fun palabras(s: String) = Regex("$L+").findAll(s).count()

    // «pan, leche, y huevos» → «pan, leche y huevos»
    private fun comaAntesDeY(t: String, out: MutableList<Match>) {
        Regex("($L+),\\s+(y|e|ni|o|u)(?=\\s+$L)", RegexOption.IGNORE_CASE).findAll(t).forEach { m ->
            val coma = m.groups[1]!!.range.last + 1
            // Debe haber otro elemento antes, separado por coma, en la misma frase y corto (≤ 3 palabras).
            val desde = inicioFrase(t, coma)
            val antes = t.substring(desde, coma)
            val ultimaComa = antes.lastIndexOf(',')
            if (ultimaComa < 0) return@forEach
            val elemento = antes.substring(ultimaComa + 1)
            if (palabras(elemento) !in 1..3) return@forEach
            // Y lo que sigue a la «y» también corto: es el último elemento, no una oración nueva.
            val despues = t.substring(m.range.last + 1, finFrase(t, m.range.last + 1, tambienComa = true))
            if (palabras(despues) > 4) return@forEach
            out += match(m.range.first, m.range.last + 1, "Sobra la coma antes de «${m.groupValues[2]}».",
                "${m.groupValues[1]} ${m.groupValues[2]}", "Y")
        }
    }

    // «Quería ir pero llovió» → «Quería ir, pero llovió»
    private fun faltaComaPero(t: String, out: MutableList<Match>) {
        Regex("($L+) (pero|aunque)(?= $L)", RegexOption.IGNORE_CASE).findAll(t).forEach { m ->
            val previa = m.groupValues[1].lowercase()
            if (previa in setOf("y", "o", "ni", "que", "pero", "sino", "aunque", "si", "porque")) return@forEach
            val conj = m.groups[2]!!
            val desde = inicioFrase(t, conj.range.first, tambienComa = true)
            val hasta = finFrase(t, conj.range.last + 1, tambienComa = true)
            if (palabras(t.substring(desde, conj.range.first)) < 3) return@forEach // «pobre pero honrado»
            if (palabras(t.substring(conj.range.last + 1, hasta)) < 2) return@forEach
            out += match(m.range.first, m.range.last + 1, "Falta una coma antes de «${conj.value}».",
                "${m.groupValues[1]}, ${conj.value}", "PERO")
        }
    }

    private fun conectores(t: String, out: MutableList<Match>) {
        val bajo = t.lowercase()
        for (c in CONECTORES_INICIO) {
            var desde = 0
            while (true) {
                val i = bajo.indexOf(c, desde)
                if (i < 0) break
                desde = i + c.length
                val fin = i + c.length
                // palabra completa
                if (i > 0 && t[i - 1].isLetter()) continue
                if (fin < t.length && t[fin].isLetter()) continue
                val siguiente = t.substring(fin).trimStart()
                val sigueComa = siguiente.startsWith(",") || siguiente.startsWith(":") || siguiente.startsWith(";")
                val sigueLetra = siguiente.firstOrNull()?.isLetter() == true
                val alInicio = Explicador.inicioDeOracion(t, i)
                if (alInicio) {
                    if (sigueComa || !sigueLetra) continue
                    if (c == "además" && Regex("^(de|del|que)\\b", RegexOption.IGNORE_CASE).containsMatchIn(siguiente)) continue
                    if (c == "o sea" && siguiente.startsWith("que", ignoreCase = true)) continue
                    out += match(i, fin, "Falta la coma después de «${t.substring(i, fin)}».", "${t.substring(i, fin)},", "CONECTOR")
                } else if (c in CONECTORES_MEDIO) {
                    if (c == "o sea" && siguiente.startsWith("que", ignoreCase = true)) continue
                    if (!sigueLetra && !sigueComa) continue
                    // ¿Qué hay antes? Signo (bien) o una palabra sin coma (falta).
                    var j = i - 1
                    while (j >= 0 && t[j] == ' ') j--
                    val antesSigno = j >= 0 && t[j] in ",;:(—–-"
                    if (antesSigno && sigueComa) continue
                    val conector = t.substring(i, fin)
                    if (antesSigno) {
                        out += match(i, fin, "Falta la coma después de «$conector».", "$conector,", "INCISO")
                    } else {
                        // incluir la palabra anterior para poner la coma antes
                        var k = j
                        while (k >= 0 && t[k].isLetter()) k--
                        val previa = t.substring(k + 1, j + 1)
                        if (previa.isEmpty() || previa.lowercase() in setOf("y", "o", "que", "pero", "ni")) continue
                        out += match(k + 1, fin, "«$conector» va entre comas.",
                            "$previa, $conector" + if (sigueComa) "" else ",", "INCISO")
                    }
                }
            }
        }
    }

    // «El perro de mi vecino, es bravo» → «El perro de mi vecino es bravo»
    private fun sujetoVerbo(t: String, out: MutableList<Match>) {
        Regex("$L+").findAll(t).forEach { primera ->
            if (primera.value.lowercase() !in DETERMINANTES || !Explicador.inicioDeOracion(t, primera.range.first)) return@forEach
            val coma = t.indexOf(',', primera.range.last + 1).takeIf { it >= 0 } ?: return@forEach
            val sujeto = t.substring(primera.range.first, coma)
            if (sujeto.any { it in CORTE }) return@forEach
            val ps = Regex("$L+").findAll(sujeto).map { it.value.lowercase() }.toList()
            if (ps.size !in 2..8 || ps[1] in TIEMPO) return@forEach
            if (ps.any { it in setOf("si", "cuando", "como", "porque", "aunque", "mientras") }) return@forEach
            val resto = t.substring(coma + 1)
            val verbo = Regex("^\\s+($L+)").find(resto) ?: return@forEach
            if (verbo.groupValues[1].lowercase() !in VERBOS) return@forEach
            // Si lo que sigue a la coma vuelve a cerrar con coma antes del verbo real, es un inciso: no tocar.
            val ultima = Regex("($L+)\\s*$").find(sujeto)!!.groups[1]!!
            val ini = primera.range.first + ultima.range.first
            val fin = coma + 1 + verbo.range.last + 1
            out += match(ini, fin, "Sobra la coma entre el sujeto y el verbo.",
                "${ultima.value} ${verbo.groupValues[1]}", "SUJETO")
        }
    }

    // «Hola Juan» → «Hola, Juan»
    private fun vocativo(t: String, out: MutableList<Match>) {
        val saludos = SALUDOS.joinToString("|")
        Regex("(?<!$L)((?i:$saludos)) (\\p{Lu}\\p{Ll}+)(?!$L)").findAll(t).forEach { m ->
            val nombre = m.groupValues[2]
            if (nombre.lowercase() in setOf("a", "por", "de", "que", "y", "mi", "tu", "su")) return@forEach
            out += match(m.range.first, m.range.last + 1, "Falta la coma antes del nombre.",
                "${m.groupValues[1]}, $nombre", "VOCATIVO")
        }
    }

    // «Dijo, que vendría» → «Dijo que vendría»
    private fun comaAntesDeQue(t: String, out: MutableList<Match>) {
        Regex("($L+)\\s*,\\s*(que)(?!$L)", RegexOption.IGNORE_CASE).findAll(t).forEach { m ->
            if (m.groupValues[1].lowercase() !in VERBOS_QUE) return@forEach
            out += match(m.range.first, m.range.last + 1, "Sobra la coma antes de «que».",
                "${m.groupValues[1]} ${m.groupValues[2]}", "QUE")
        }
    }
}
