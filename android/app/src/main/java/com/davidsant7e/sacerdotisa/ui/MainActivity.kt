package com.davidsant7e.sacerdotisa.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.davidsant7e.sacerdotisa.Ajustes
import com.davidsant7e.sacerdotisa.Motor
import com.davidsant7e.sacerdotisa.R
import com.davidsant7e.sacerdotisa.motor.Aviso
import com.davidsant7e.sacerdotisa.servicio.SacerdotisaService
import java.util.concurrent.Executors

/** Pantalla principal: activar la burbuja, ajustes y una caja para probar. */
class MainActivity : Activity() {

    private lateinit var ajustes: Ajustes
    private lateinit var estado: TextView
    private lateinit var botonActivar: View
    private lateinit var excluidas: LinearLayout
    private lateinit var resultados: LinearLayout
    private lateinit var prueba: EditText
    private val principal = Handler(Looper.getMainLooper())
    private val trabajo = Executors.newSingleThreadExecutor()
    private var version = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ajustes = Ajustes(this)
        val c = this

        val col = Vistas.columna(c)
        col.setPadding(Vistas.dp(c, 20), Vistas.dp(c, 20), Vistas.dp(c, 20), Vistas.dp(c, 32))

        // Encabezado
        val cabeza = Vistas.fila(c)
        val icono = ImageView(c)
        icono.setImageResource(R.mipmap.ic_launcher)
        cabeza.addView(icono, LinearLayout.LayoutParams(Vistas.dp(c, 56), Vistas.dp(c, 56)))
        val titulos = Vistas.columna(c)
        titulos.addView(Vistas.etiqueta(c, "II · La Sacerdotisa"))
        titulos.addView(Vistas.texto(c, "El corrector que explica", 20f, Vistas.BLANCO, negrita = true))
        cabeza.addView(Vistas.margen(titulos, c, izq = 14))
        col.addView(cabeza)
        col.addView(Vistas.margen(Vistas.texto(c,
            "Mientras escribes en WhatsApp, Notas, Messenger o cualquier app, aparece una burbuja si algo está mal. " +
                "Tócala para ver qué cambiar y por qué. También puedes seleccionar un texto y elegir «Explicar» en el menú.",
            15f, Vistas.CREMA), c, arriba = 14))

        // 1. Activar
        col.addView(seccion("1. Activar la burbuja"))
        estado = Vistas.texto(c, "", 15f, Vistas.BLANCO, negrita = true)
        col.addView(Vistas.margen(estado, c, arriba = 6))
        botonActivar = Vistas.boton(c, "Abrir Accesibilidad", true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        col.addView(Vistas.margen(botonActivar, c, arriba = 8))
        col.addView(Vistas.margen(Vistas.texto(c,
            "En Accesibilidad → Apps instaladas (o Servicios) → La Sacerdotisa → activar. " +
                "Android avisa que la app puede ver lo que escribes: lo necesita para revisarlo. " +
                "Nunca lee campos de contraseña y no guarda nada.\n\n" +
                "Si el interruptor aparece gris («ajuste restringido»): Ajustes → Apps → La Sacerdotisa → ⋮ (arriba a la derecha) → " +
                "Permitir ajustes restringidos, y vuelve a intentarlo.",
            13f, Vistas.CREMA), c, arriba = 8))
        col.addView(interruptor("Mostrar la burbuja", ajustes.burbuja) { ajustes.burbuja = it })

        // 2. Qué revisar
        col.addView(seccion("2. Qué revisar"))
        col.addView(interruptor("Usar LanguageTool (encuentra muchos más errores)", ajustes.languageTool) { ajustes.languageTool = it })
        col.addView(Vistas.margen(Vistas.texto(c,
            "Con LanguageTool, el texto que escribes se envía a languagetool.org para revisarlo (necesita internet). " +
                "Sin él, solo se usan las reglas del celular: mayúsculas, tildes comunes, preguntas sin tilde, palabras repetidas y espacios.",
            13f, Vistas.CREMA), c, arriba = 2))
        col.addView(interruptor("Detectar inglés (si no, siempre español)", ajustes.idioma == "auto") { ajustes.idioma = if (it) "auto" else "es" })
        col.addView(interruptor("Avisar si empiezo en minúscula", ajustes.mayusculaInicio) { ajustes.mayusculaInicio = it })
        col.addView(interruptor("Puntuación (¿ ¡, comas)", ajustes.puntuacion) { ajustes.puntuacion = it })
        col.addView(interruptor("Sugerencias de estilo", ajustes.estilo) { ajustes.estilo = it })

        // 3. Apps sin revisar
        col.addView(seccion("3. Apps donde no se revisa"))
        excluidas = Vistas.columna(c)
        col.addView(excluidas)
        col.addView(Vistas.margen(Vistas.texto(c, "Se agregan desde la burbuja: «No revisar en …».", 13f, Vistas.CREMA), c, arriba = 4))

        // 4. Probar
        col.addView(seccion("4. Pruébala aquí"))
        prueba = EditText(c).apply {
            hint = "Escribe, por ejemplo: Si la Vida fuera facil"
            setHintTextColor(0x99FFFFFF.toInt())
            setTextColor(Vistas.BLANCO)
            background = Vistas.fondo(c, Vistas.NOCHE_CLARA, 12, Vistas.DURAZNO)
            setPadding(Vistas.dp(c, 12), Vistas.dp(c, 10), Vistas.dp(c, 12), Vistas.dp(c, 10))
            minLines = 2
            gravity = Gravity.TOP
        }
        col.addView(Vistas.margen(prueba, c, arriba = 8))
        resultados = Vistas.columna(c)
        col.addView(resultados)
        prueba.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) = Unit
            override fun afterTextChanged(s: Editable?) = programarPrueba()
        })

        col.addView(Vistas.margen(Vistas.texto(c,
            "Las explicaciones son las mismas de la extensión y de la app de Windows de La Sacerdotisa.",
            12f, Vistas.DURAZNO), c, arriba = 24))

        val scroll = ScrollView(c)
        scroll.setBackgroundColor(Vistas.NOCHE)
        scroll.addView(col)
        // Android 15+ dibuja detrás de las barras: dejamos su espacio.
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        scroll.clipToPadding = false
        setContentView(scroll)
        pintarBarras()
    }

    /** En Android 14 o menos las barras no son transparentes: las pintamos del mismo azul. */
    @Suppress("DEPRECATION")
    private fun pintarBarras() {
        window.statusBarColor = Vistas.NOCHE
        window.navigationBarColor = Vistas.NOCHE
    }

    override fun onResume() {
        super.onResume()
        val activo = servicioActivo()
        estado.text = if (activo) "✓ La burbuja está activa." else "La burbuja está apagada: actívala en Accesibilidad."
        estado.setTextColor(if (activo) Vistas.DURAZNO else Vistas.BLANCO)
        botonActivar.visibility = if (activo) View.GONE else View.VISIBLE
        pintarExcluidas()
    }

    override fun onDestroy() {
        trabajo.shutdownNow()
        super.onDestroy()
    }

    private fun servicioActivo(): Boolean {
        val lista = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val yo = ComponentName(this, SacerdotisaService::class.java)
        return lista.split(':').any { ComponentName.unflattenFromString(it) == yo }
    }

    private fun pintarExcluidas() {
        excluidas.removeAllViews()
        val lista = ajustes.excluidas - Ajustes.DEFECTO_EXCLUIDAS
        if (lista.isEmpty()) {
            excluidas.addView(Vistas.margen(Vistas.texto(this, "Ninguna: revisa en todas.", 14f, Vistas.BLANCO), this, arriba = 6))
            return
        }
        for (pkg in lista.sorted()) {
            val f = Vistas.fila(this)
            val nombre = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            f.addView(Vistas.texto(this, nombre, 15f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            f.addView(Vistas.boton(this, "Volver a revisar", false) { ajustes.incluir(pkg); pintarExcluidas() })
            excluidas.addView(Vistas.margen(f, this, arriba = 6))
        }
    }

    private fun programarPrueba() {
        version++
        val v = version
        val texto = prueba.text.toString()
        val revisor = Motor.revisor(this)
        pintarResultados(revisor.locales(texto, ajustes.filtros()), texto)
        if (!ajustes.languageTool || texto.isBlank()) return
        principal.postDelayed({
            if (v != version) return@postDelayed
            val idioma = ajustes.idioma
            trabajo.execute {
                val r = runCatching { Motor.languageTool.revisar(texto, idioma) }.getOrNull() ?: return@execute
                principal.post { if (v == version) pintarResultados(revisor.combinados(texto, r, ajustes.filtros()), texto) }
            }
        }, 1500)
    }

    private fun pintarResultados(avisos: List<Aviso>, texto: String) {
        resultados.removeAllViews()
        if (texto.isBlank()) return
        if (avisos.isEmpty()) {
            resultados.addView(Vistas.margen(Vistas.texto(this, "✓ Sin avisos.", 14f, Vistas.DURAZNO), this, arriba = 8))
            return
        }
        for (a in avisos) {
            resultados.addView(Vistas.margen(Vistas.tarjeta(this, a) { elegido ->
                val (nuevo, cursor) = Motor.revisor(this).corregir(prueba.text.toString(), elegido) ?: return@tarjeta
                prueba.setText(nuevo)
                prueba.setSelection(cursor.coerceAtMost(nuevo.length))
            }, this, arriba = 8))
        }
    }

    private fun seccion(t: String): View = Vistas.margen(Vistas.etiqueta(this, t), this, arriba = 26)

    @Suppress("DEPRECATION")
    private fun interruptor(t: String, valor: Boolean, cambio: (Boolean) -> Unit): View {
        val s = Switch(this)
        s.text = t
        s.setTextColor(Vistas.BLANCO)
        s.textSize = 15f
        s.isChecked = valor
        s.setOnCheckedChangeListener { _: CompoundButton, v: Boolean -> cambio(v) }
        return Vistas.margen(s, this, arriba = 10)
    }
}
