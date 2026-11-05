package com.davidsant7e.sacerdotisa.motor

/**
 * Lector de JSON mínimo, sin dependencias: lo usan el motor (explicaciones.json), la respuesta
 * de LanguageTool y las pruebas en la PC (donde org.json de Android no existe).
 * Devuelve Map<String, Any?>, List<Any?>, String, Double, Boolean o null.
 */
object Json {

    fun leer(texto: String): Any? {
        val l = Lector(texto)
        l.espacios()
        val v = l.valor()
        l.espacios()
        if (l.i != texto.length) throw IllegalArgumentException("JSON: sobra texto en la posición ${l.i}")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun objeto(v: Any?): Map<String, Any?> = v as? Map<String, Any?> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    fun lista(v: Any?): List<Any?> = v as? List<Any?> ?: emptyList()

    fun texto(v: Any?): String? = v as? String

    fun entero(v: Any?, defecto: Int = 0): Int = (v as? Double)?.toInt() ?: defecto

    private class Lector(val s: String) {
        var i = 0

        fun espacios() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun valor(): Any? {
            if (i >= s.length) throw IllegalArgumentException("JSON: terminó antes de tiempo")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> palabra("true", true)
                'f' -> palabra("false", false)
                'n' -> palabra("null", null)
                else -> num()
            }
        }

        private fun palabra(p: String, v: Any?): Any? {
            if (!s.startsWith(p, i)) throw IllegalArgumentException("JSON: valor raro en la posición $i")
            i += p.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            espacios()
            if (s[i] == '}') { i++; return m }
            while (true) {
                espacios()
                val k = str()
                espacios()
                if (s[i] != ':') throw IllegalArgumentException("JSON: falta ':' en la posición $i")
                i++
                espacios()
                m[k] = valor()
                espacios()
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw IllegalArgumentException("JSON: falta ',' o '}' en la posición $i")
                }
            }
        }

        private fun arr(): List<Any?> {
            val a = ArrayList<Any?>()
            i++
            espacios()
            if (s[i] == ']') { i++; return a }
            while (true) {
                espacios()
                a.add(valor())
                espacios()
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return a }
                    else -> throw IllegalArgumentException("JSON: falta ',' o ']' en la posición $i")
                }
            }
        }

        private fun str(): String {
            if (s[i] != '"') throw IllegalArgumentException("JSON: se esperaba texto en la posición $i")
            i++
            val b = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        val e = s[i++]
                        when (e) {
                            'n' -> b.append('\n')
                            't' -> b.append('\t')
                            'r' -> b.append('\r')
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000C')
                            'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> b.append(e) // \" \\ \/
                        }
                    }
                    else -> b.append(c)
                }
            }
        }

        private fun num(): Double {
            val ini = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(ini, i).toDoubleOrNull() ?: throw IllegalArgumentException("JSON: número raro en la posición $ini")
        }
    }
}
