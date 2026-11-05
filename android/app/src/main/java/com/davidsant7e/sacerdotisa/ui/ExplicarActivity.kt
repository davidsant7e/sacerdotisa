package com.davidsant7e.sacerdotisa.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.davidsant7e.sacerdotisa.Ajustes
import com.davidsant7e.sacerdotisa.Motor
import com.davidsant7e.sacerdotisa.motor.Aviso
import java.util.concurrent.Executors

/**
 * Menú «Explicar» al seleccionar texto en cualquier app (ACTION_PROCESS_TEXT).
 * Muestra los errores con su porqué y, si la app lo permite, reemplaza el texto corregido.
 */
class ExplicarActivity : Activity() {

    private val principal = Handler(Looper.getMainLooper())
    private val trabajo = Executors.newSingleThreadExecutor()
    private lateinit var lista: LinearLayout
    private lateinit var pie: LinearLayout
    private var texto = ""
    private var soloLectura = true
    private var avisos: List<Aviso> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        texto = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        soloLectura = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        val c = this
        val ajustes = Ajustes(c)

        val col = Vistas.columna(c)
        col.background = Vistas.fondo(c, Vistas.NOCHE, 18, Vistas.DURAZNO)
        col.setPadding(Vistas.dp(c, 16), Vistas.dp(c, 14), Vistas.dp(c, 16), Vistas.dp(c, 14))
        col.addView(Vistas.etiqueta(c, "II · La Sacerdotisa"))
        col.addView(Vistas.margen(Vistas.texto(c, "«${texto.take(300)}${if (texto.length > 300) "…" else ""}»", 14f, Vistas.CREMA), c, arriba = 6))
        lista = Vistas.columna(c)
        val scroll = ScrollView(c)
        scroll.addView(lista)
        col.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        pie = Vistas.fila(c)
        col.addView(Vistas.margen(pie, c, arriba = 10))
        setContentView(col)
        window.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val revisor = Motor.revisor(c)
        avisos = revisor.locales(texto, ajustes.filtros())
        pintar(if (ajustes.languageTool) "Revisando con LanguageTool…" else null)
        if (ajustes.languageTool && texto.isNotBlank()) {
            val idioma = ajustes.idioma
            trabajo.execute {
                val r = runCatching { Motor.languageTool.revisar(texto, idioma) }
                principal.post {
                    if (isFinishing) return@post
                    r.onSuccess { avisos = revisor.combinados(texto, it, ajustes.filtros()); pintar(null) }
                        .onFailure { pintar("Sin conexión con LanguageTool: solo reglas del celular.") }
                }
            }
        }
    }

    override fun onDestroy() {
        trabajo.shutdownNow()
        super.onDestroy()
    }

    private fun pintar(nota: String?) {
        val c = this
        lista.removeAllViews()
        if (nota != null) lista.addView(Vistas.margen(Vistas.texto(c, nota, 12f, Vistas.DURAZNO), c, arriba = 8))
        if (avisos.isEmpty()) {
            lista.addView(Vistas.margen(Vistas.texto(c, "✓ No encontré errores.", 15f, Vistas.BLANCO, negrita = true), c, arriba = 10))
        }
        for (a in avisos) {
            lista.addView(Vistas.margen(Vistas.tarjeta(c, a, if (soloLectura) null else { elegido -> corregir(elegido) }), c, arriba = 8))
        }
        pie.removeAllViews()
        pie.addView(Vistas.boton(c, "Cerrar", false) { finish() })
        pie.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        if (!soloLectura && avisos.any { it.sugerencia != null }) {
            pie.addView(Vistas.boton(c, "Corregir todo", true) { devolver(Motor.revisor(c).corregirTodo(texto, avisos)) })
        }
    }

    private fun corregir(a: Aviso) {
        val (nuevo, _) = Motor.revisor(this).corregir(texto, a) ?: return
        devolver(nuevo)
    }

    /** Devuelve el texto corregido a la app: lo reemplaza en la selección. */
    private fun devolver(nuevo: String) {
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, nuevo))
        finish()
    }
}
