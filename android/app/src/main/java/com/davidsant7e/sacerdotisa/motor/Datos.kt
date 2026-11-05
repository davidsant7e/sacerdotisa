package com.davidsant7e.sacerdotisa.motor

/** Un error encontrado, con el formato de un "match" de LanguageTool (así lo entiende el motor). */
data class Match(
    val offset: Int,
    val length: Int,
    val message: String = "",
    val replacements: List<String> = emptyList(),
    val ruleId: String = "",
    val issueType: String = "",
    val categoryId: String = "",
)

/** El «por qué» de un error, listo para mostrar. */
data class Explicacion(
    val tipo: String,
    val titulo: String,
    val porque: String,
    val mensaje: String,
    val original: String,
    val sugerencias: List<String>,
)

/** Un cambio para aplicar al texto. */
data class Cambio(val offset: Int, val length: Int, val valor: String)

class TextoCategoria(val titulo: String, val porque: String)
class ParDiacritico(val sin: String, val con: String)
class Confusion(val a: String, val b: String, val porque: String)

/**
 * Las explicaciones (assets/explicaciones.json, el mismo archivo de la extensión y de la app de Windows).
 */
class Datos(
    val categorias: Map<String, TextoCategoria>,
    val tiposProblema: Map<String, String>,
    val diacriticas: Map<String, ParDiacritico>,
    val confusiones: List<Confusion>,
    val tildes: Map<String, String>,
    val mayusculas: Map<String, String>,
    val repetida: String,
    val espacios: String,
) {
    fun categoria(id: String): TextoCategoria = categorias[id] ?: categorias["MISC"] ?: TextoCategoria(id, "")

    companion object {
        fun desdeJson(texto: String): Datos {
            val r = Json.objeto(Json.leer(texto))
            fun mapaTextos(v: Any?) = Json.objeto(v).mapValues { (_, x) -> Json.texto(x) ?: "" }
            return Datos(
                categorias = Json.objeto(r["categorias"]).mapValues { (_, v) ->
                    val o = Json.objeto(v)
                    TextoCategoria(Json.texto(o["titulo"]) ?: "", Json.texto(o["porque"]) ?: "")
                },
                tiposProblema = mapaTextos(r["tiposProblema"]),
                diacriticas = Json.objeto(r["diacriticas"]).mapValues { (_, v) ->
                    val o = Json.objeto(v)
                    ParDiacritico(Json.texto(o["sin"]) ?: "", Json.texto(o["con"]) ?: "")
                },
                confusiones = Json.lista(r["confusiones"]).map {
                    val o = Json.objeto(it)
                    Confusion(Json.texto(o["a"]) ?: "", Json.texto(o["b"]) ?: "", Json.texto(o["porque"]) ?: "")
                },
                tildes = mapaTextos(r["tildes"]),
                mayusculas = mapaTextos(r["mayusculas"]),
                repetida = Json.texto(r["repetida"]) ?: "",
                espacios = Json.texto(r["espacios"]) ?: "",
            )
        }
    }
}
