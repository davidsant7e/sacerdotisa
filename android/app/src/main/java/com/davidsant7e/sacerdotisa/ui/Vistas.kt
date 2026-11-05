package com.davidsant7e.sacerdotisa.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.davidsant7e.sacerdotisa.motor.Aviso

/** Colores y piezas de la interfaz, en el duotono de los Arcanos. Sin librerías: vistas de Android. */
object Vistas {
    val NOCHE = Color.rgb(0x1f, 0x2a, 0x5c)
    val NOCHE_CLARA = Color.rgb(0x2b, 0x38, 0x73)
    val DURAZNO = Color.rgb(0xf4, 0xa5, 0x82)
    val CREMA = Color.rgb(0xfb, 0xe9, 0xdf)
    val BLANCO = Color.WHITE

    fun dp(c: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics).toInt()

    fun fondo(c: Context, color: Int, radio: Int = 16, borde: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radio).toFloat()
            if (borde != null) setStroke(dp(c, 1), borde)
        }

    fun texto(c: Context, t: CharSequence, sp: Float = 15f, color: Int = BLANCO, negrita: Boolean = false): TextView =
        TextView(c).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (negrita) setTypeface(typeface, Typeface.BOLD)
        }

    fun etiqueta(c: Context, t: String): TextView =
        texto(c, t.uppercase(), 11f, DURAZNO, negrita = true).apply { letterSpacing = 0.08f }

    fun boton(c: Context, t: String, principal: Boolean, alPulsar: () -> Unit): Button =
        Button(c).apply {
            text = t
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(if (principal) NOCHE else DURAZNO)
            background = fondo(c, if (principal) DURAZNO else Color.TRANSPARENT, 20, DURAZNO)
            minHeight = dp(c, 40)
            minimumHeight = dp(c, 40)
            setPadding(dp(c, 16), 0, dp(c, 16), 0)
            stateListAnimator = null
            setOnClickListener { alPulsar() }
        }

    fun columna(c: Context): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    fun fila(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun margen(v: View, c: Context, arriba: Int = 0, abajo: Int = 0, izq: Int = 0, der: Int = 0): View {
        val lp = (v.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(c, izq), dp(c, arriba), dp(c, der), dp(c, abajo))
        v.layoutParams = lp
        return v
    }

    /**
     * Tarjeta de un aviso: tipo, «original» → «sugerencia», el porqué y (si hay) el botón «Corregir».
     */
    fun tarjeta(c: Context, a: Aviso, alCorregir: ((Aviso) -> Unit)?): View {
        val e = a.explicacion
        val col = columna(c)
        col.background = fondo(c, NOCHE_CLARA, 12)
        col.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
        col.addView(etiqueta(c, e.titulo))
        val cambio = if (a.sugerencia != null) "«${e.original.trim()}»  →  «${a.sugerencia}»" else "«${e.original.trim()}»"
        col.addView(margen(texto(c, cambio, 16f, BLANCO, negrita = true), c, arriba = 4))
        col.addView(margen(texto(c, e.porque, 14f, CREMA), c, arriba = 6))
        // Si la explicación es la general de la categoría, el mensaje de LanguageTool dice más.
        if (a.deLanguageTool && e.mensaje.isNotBlank() && e.mensaje != e.porque) {
            col.addView(margen(texto(c, "LanguageTool: ${e.mensaje}", 12f, DURAZNO), c, arriba = 6))
        }
        if (alCorregir != null && a.sugerencia != null) {
            val f = fila(c)
            f.gravity = Gravity.END
            f.addView(boton(c, "Corregir", true) { alCorregir(a) })
            col.addView(margen(f, c, arriba = 8))
        }
        return col
    }
}
