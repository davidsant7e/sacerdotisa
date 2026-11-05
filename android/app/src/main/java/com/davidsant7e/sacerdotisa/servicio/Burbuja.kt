package com.davidsant7e.sacerdotisa.servicio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.davidsant7e.sacerdotisa.motor.Aviso
import com.davidsant7e.sacerdotisa.ui.Vistas

/**
 * La burbuja flotante: pequeña («II · 2») junto al campo donde escribes; al tocarla se abre
 * el panel con cada error y su porqué. No quita el foco al teclado (FLAG_NOT_FOCUSABLE).
 * Usa la ventana de accesibilidad: no necesita el permiso «Mostrar sobre otras apps».
 */
class Burbuja(
    private val c: Context,
    private val alCorregir: (Aviso) -> Unit,
    private val alCorregirTodo: () -> Unit,
    private val alExcluirApp: () -> Unit,
) {
    private val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val raiz = FrameLayout(c)
    private var agregada = false
    private var abierta = false
    private var avisos: List<Aviso> = emptyList()
    private var campo = Rect()
    private var nombreApp = ""

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "La Sacerdotisa"
    }

    init {
        @SuppressLint("ClickableViewAccessibility")
        raiz.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE && abierta) { cerrarPanel(); true } else false
        }
    }

    val visible: Boolean get() = agregada

    /** Muestra (o actualiza) la burbuja junto al campo de texto (coordenadas de pantalla). */
    fun mostrar(nuevos: List<Aviso>, campoEnPantalla: Rect, app: String) {
        avisos = nuevos
        campo = Rect(campoEnPantalla)
        nombreApp = app
        if (avisos.isEmpty()) { ocultar(); return }
        dibujar()
    }

    fun ocultar() {
        abierta = false
        if (agregada) {
            runCatching { wm.removeView(raiz) }
            agregada = false
        }
    }

    private fun cerrarPanel() {
        abierta = false
        dibujar()
    }

    private fun dibujar() {
        raiz.removeAllViews()
        val pantalla = c.resources.displayMetrics
        val vista: View
        if (abierta) {
            vista = panel()
            val ancho = pantalla.widthPixels - Vistas.dp(c, 24)
            val maxAlto = (pantalla.heightPixels * 0.5).toInt()
            vista.measure(
                View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxAlto, View.MeasureSpec.AT_MOST),
            )
            lp.width = ancho
            lp.height = vista.measuredHeight
            lp.x = Vistas.dp(c, 12)
            // Campo abajo (WhatsApp, Messenger): el panel va encima. Campo arriba (Notas): debajo de su borde superior.
            val arriba = campo.top - Vistas.dp(c, 8) - lp.height
            lp.y = if (arriba > Vistas.dp(c, 32)) arriba else (campo.top + Vistas.dp(c, 48)).coerceAtMost(pantalla.heightPixels - lp.height)
        } else {
            vista = chip()
            vista.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            lp.x = (campo.right - vista.measuredWidth - Vistas.dp(c, 8)).coerceIn(0, pantalla.widthPixels - vista.measuredWidth)
            val arriba = campo.top - vista.measuredHeight - Vistas.dp(c, 6)
            lp.y = if (arriba > Vistas.dp(c, 32)) arriba else campo.top + Vistas.dp(c, 6)
        }
        raiz.addView(vista)
        if (agregada) runCatching { wm.updateViewLayout(raiz, lp) }
        else agregada = runCatching { wm.addView(raiz, lp) }.isSuccess
    }

    /** «II · 2» */
    private fun chip(): View {
        val n = avisos.size
        val t = Vistas.texto(c, "II  ·  $n ${if (n == 1) "aviso" else "avisos"}", 13f, Vistas.NOCHE, negrita = true)
        t.background = Vistas.fondo(c, Vistas.DURAZNO, 18)
        t.setPadding(Vistas.dp(c, 14), Vistas.dp(c, 7), Vistas.dp(c, 14), Vistas.dp(c, 7))
        t.elevation = Vistas.dp(c, 6).toFloat()
        t.contentDescription = "La Sacerdotisa: $n avisos. Toca para ver por qué."
        t.setOnClickListener { abierta = true; dibujar() }
        return t
    }

    private fun panel(): View {
        val col = Vistas.columna(c)
        col.background = Vistas.fondo(c, Vistas.NOCHE, 18, Vistas.DURAZNO)
        col.elevation = Vistas.dp(c, 8).toFloat()
        col.setPadding(Vistas.dp(c, 14), Vistas.dp(c, 12), Vistas.dp(c, 14), Vistas.dp(c, 12))

        val cabeza = Vistas.fila(c)
        val titulo = Vistas.etiqueta(c, "II · La Sacerdotisa")
        cabeza.addView(titulo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val cerrar = Vistas.texto(c, "✕", 18f, Vistas.DURAZNO)
        cerrar.setPadding(Vistas.dp(c, 12), Vistas.dp(c, 4), Vistas.dp(c, 4), Vistas.dp(c, 4))
        cerrar.contentDescription = "Cerrar"
        cerrar.setOnClickListener { cerrarPanel() }
        cabeza.addView(cerrar)
        col.addView(cabeza)

        val lista = Vistas.columna(c)
        for (a in avisos) lista.addView(Vistas.margen(Vistas.tarjeta(c, a) { alCorregir(it) }, c, arriba = 8))
        val scroll = ScrollView(c)
        scroll.addView(lista)
        col.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).also {
            it.height = LinearLayout.LayoutParams.WRAP_CONTENT
        })

        val pie = Vistas.fila(c)
        pie.addView(Vistas.boton(c, "No revisar en $nombreApp", false) { ocultar(); alExcluirApp() })
        pie.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        if (avisos.count { it.sugerencia != null } > 1) pie.addView(Vistas.boton(c, "Corregir todo", true) { alCorregirTodo() })
        col.addView(Vistas.margen(pie, c, arriba = 10))
        return col
    }
}
