package com.davidsant7e.sacerdotisa

import android.content.Context
import android.content.SharedPreferences
import com.davidsant7e.sacerdotisa.motor.Filtros

/** Preferencias del usuario (se leen en cada revisión: los cambios aplican al instante). */
class Ajustes(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var burbuja: Boolean
        get() = p.getBoolean("burbuja", true)
        set(v) = p.edit().putBoolean("burbuja", v).apply()

    /** Enviar el texto a LanguageTool (encuentra muchos más errores; necesita internet). */
    var languageTool: Boolean
        get() = p.getBoolean("languageTool", true)
        set(v) = p.edit().putBoolean("languageTool", v).apply()

    /** "es" o "auto" (detecta español o inglés). */
    var idioma: String
        get() = p.getString("idioma", "es") ?: "es"
        set(v) = p.edit().putString("idioma", v).apply()

    var mayusculaInicio: Boolean
        get() = p.getBoolean("mayusculaInicio", true)
        set(v) = p.edit().putBoolean("mayusculaInicio", v).apply()

    var puntuacion: Boolean
        get() = p.getBoolean("puntuacion", false)
        set(v) = p.edit().putBoolean("puntuacion", v).apply()

    var estilo: Boolean
        get() = p.getBoolean("estilo", false)
        set(v) = p.edit().putBoolean("estilo", v).apply()

    /** Apps donde no se revisa (paquetes). */
    var excluidas: Set<String>
        get() = p.getStringSet("excluidas", DEFECTO_EXCLUIDAS) ?: DEFECTO_EXCLUIDAS
        set(v) = p.edit().putStringSet("excluidas", HashSet(v)).apply()

    fun excluir(paquete: String) { excluidas = excluidas + paquete }
    fun incluir(paquete: String) { excluidas = excluidas - paquete }

    fun filtros() = Filtros(
        puntuacion = puntuacion,
        estilo = estilo,
        ignorarReglas = if (mayusculaInicio) emptySet() else setOf("SACERDOTISA_INICIO", "UPPERCASE_SENTENCE_START"),
    )

    companion object {
        /** Donde nunca tiene sentido revisar: el teclado, la configuración y el propio sistema. */
        val DEFECTO_EXCLUIDAS = setOf("com.android.settings", "com.android.systemui")
    }
}
