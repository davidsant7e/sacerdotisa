package com.davidsant7e.sacerdotisa

import android.content.Context
import com.davidsant7e.sacerdotisa.motor.Datos
import com.davidsant7e.sacerdotisa.motor.LanguageTool
import com.davidsant7e.sacerdotisa.motor.Revisor

/** Una sola copia del motor para toda la app (la burbuja, el menú «Explicar» y la pantalla de prueba). */
object Motor {
    @Volatile private var revisor: Revisor? = null
    val languageTool = LanguageTool()

    fun revisor(context: Context): Revisor = revisor ?: synchronized(this) {
        revisor ?: Revisor(Datos.desdeJson(context.assets.open("explicaciones.json").bufferedReader().use { it.readText() }))
            .also { revisor = it }
    }
}
