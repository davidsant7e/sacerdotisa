package com.davidsant7e.sacerdotisa.ui

import android.app.Activity
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
import android.text.InputType
import android.widget.Toast
import com.davidsant7e.sacerdotisa.Ajustes
import com.davidsant7e.sacerdotisa.Boveda
import com.davidsant7e.sacerdotisa.Motor
import com.davidsant7e.sacerdotisa.R
import com.davidsant7e.sacerdotisa.motor.Aviso
import com.davidsant7e.sacerdotisa.servicio.Interruptor
import java.util.concurrent.Executors

/** Pantalla principal: activar la burbuja, ajustes y una caja para probar. */
class MainActivity : Activity() {

    private lateinit var ajustes: Ajustes
    private lateinit var estado: TextView
    private lateinit var botonActivar: View
    private lateinit var excluidas: LinearLayout
    private lateinit var resultados: LinearLayout
    private lateinit var prueba: EditText
    private lateinit var estadoGemini: TextView
    private lateinit var protegidas: LinearLayout
    private lateinit var estadoBancos: TextView
    private var avisosPrueba: List<Aviso> = emptyList()
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

        // Apps de bancos
        col.addView(seccion("Apps de bancos"))
        col.addView(Vistas.margen(Vistas.texto(c,
            "Muchos bancos bloquean el ingreso (o piden desinstalar) si hay un servicio de accesibilidad encendido que no conocen. " +
                "No hace falta desinstalar La Sacerdotisa: basta con APAGARLA mientras usas el banco.\n\n" +
                "• Agrega aquí tus apps de bancos: al abrirlas, la burbuja se apaga sola.\n" +
                "• Botón rápido: baja la cortina de arriba → ✎ editar → arrastra «La Sacerdotisa». Con él la apagas antes de abrir el banco y la vuelves a encender después.\n" +
                "• Si el banco sigue quejándose, apágala con el botón rápido ANTES de abrirlo (algunos revisan apenas arrancan).",
            13f, Vistas.CREMA), c, arriba = 4))
        col.addView(interruptor("Apagarse sola al abrir estas apps", ajustes.apagarConBancos) { ajustes.apagarConBancos = it })
        protegidas = Vistas.columna(c)
        col.addView(protegidas)
        val filaBancos = Vistas.fila(c)
        filaBancos.addView(Vistas.boton(c, "Agregar apps", true) { elegirBancos() })
        filaBancos.addView(View(c), LinearLayout.LayoutParams(Vistas.dp(c, 10), 1))
        filaBancos.addView(Vistas.boton(c, "Apagar ahora", false) {
            if (!Interruptor.apagar(c)) Toast.makeText(c, "Ya está apagada.", Toast.LENGTH_SHORT).show()
            principal.postDelayed({ refrescar() }, 600)
        })
        col.addView(Vistas.margen(filaBancos, c, arriba = 8))
        estadoBancos = Vistas.texto(c, "", 13f, Vistas.CREMA)
        estadoBancos.setTextIsSelectable(true)
        col.addView(Vistas.margen(estadoBancos, c, arriba = 10))

        // 2. Qué revisar
        col.addView(seccion("2. Qué revisar"))
        col.addView(interruptor("Usar LanguageTool (encuentra muchos más errores)", ajustes.languageTool) { ajustes.languageTool = it })
        col.addView(Vistas.margen(Vistas.texto(c,
            "Con LanguageTool, el texto que escribes se envía a languagetool.org para revisarlo (necesita internet). " +
                "Sin él, solo se usan las reglas del celular: mayúsculas, tildes comunes, preguntas sin tilde, palabras repetidas y espacios.",
            13f, Vistas.CREMA), c, arriba = 2))
        col.addView(interruptor("Detectar inglés (si no, siempre español)", ajustes.idioma == "auto") { ajustes.idioma = if (it) "auto" else "es" })
        col.addView(interruptor("Avisar si empiezo en minúscula", ajustes.mayusculaInicio) { ajustes.mayusculaInicio = it })
        col.addView(interruptor("Comas mal puestas o que faltan (mira la frase completa)", ajustes.comas) { ajustes.comas = it })
        col.addView(Vistas.margen(Vistas.texto(c,
            "Sin internet: coma entre sujeto y verbo, antes de la «y» final de una lista, antes de «que», " +
                "falta antes de «pero», después de «sin embargo», o con el nombre de a quien le hablas («Hola, Juan»).",
            13f, Vistas.CREMA), c, arriba = 2))
        col.addView(interruptor("Puntuación de LanguageTool (¿ ¡ y otras)", ajustes.puntuacion) { ajustes.puntuacion = it })
        col.addView(interruptor("Sugerencias de estilo", ajustes.estilo) { ajustes.estilo = it })

        // Revisión completa con Gemini
        col.addView(seccion("Revisar el texto completo (Gemini)"))
        col.addView(Vistas.margen(Vistas.texto(c,
            "En la burbuja aparece «Revisar texto completo»: Gemini lee todo lo que escribiste y explica comas, " +
                "concordancia y frases que no se entienden, no solo palabra por palabra. Solo se usa cuando lo tocas; " +
                "el texto se envía a Google. La clave es la misma de La Justicia y El Sol (aistudio.google.com → Get API key).",
            13f, Vistas.CREMA), c, arriba = 4))
        estadoGemini = Vistas.texto(c, "", 14f, Vistas.BLANCO, negrita = true)
        col.addView(Vistas.margen(estadoGemini, c, arriba = 8))
        val clave = campo("Pega aquí la clave de Gemini", secreto = true)
        col.addView(Vistas.margen(clave, c, arriba = 6))
        val modelo = campo("Modelo", secreto = false).apply { setText(ajustes.modeloGemini) }
        col.addView(Vistas.margen(modelo, c, arriba = 6))
        val filaClave = Vistas.fila(c)
        filaClave.addView(Vistas.boton(c, "Guardar", true) {
            if (clave.text.isNotBlank()) Boveda.guardar(c, clave.text.toString().trim())
            ajustes.modeloGemini = modelo.text.toString()
            clave.setText("")
            pintarGemini()
            Toast.makeText(c, "Guardado", Toast.LENGTH_SHORT).show()
        })
        filaClave.addView(View(c), LinearLayout.LayoutParams(Vistas.dp(c, 10), 1))
        filaClave.addView(Vistas.boton(c, "Borrar clave", false) { Boveda.borrar(c); pintarGemini() })
        col.addView(Vistas.margen(filaClave, c, arriba = 8))
        pintarGemini()

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
        col.addView(Vistas.margen(Vistas.boton(c, "Revisar texto completo (Gemini)", false) { revisarPruebaCompleta() }, c, arriba = 8))
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
        refrescar()
    }

    private fun refrescar() {
        val activo = servicioActivo()
        estado.text = if (activo) "✓ La burbuja está activa." else "La burbuja está apagada: actívala en Accesibilidad."
        estado.setTextColor(if (activo) Vistas.DURAZNO else Vistas.BLANCO)
        botonActivar.visibility = if (activo) View.GONE else View.VISIBLE
        pintarExcluidas()
        pintarProtegidas()
    }

    override fun onDestroy() {
        trabajo.shutdownNow()
        super.onDestroy()
    }

    private fun servicioActivo(): Boolean = Interruptor.activo(this)

    private fun nombreDe(pkg: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun pintarProtegidas() {
        protegidas.removeAllViews()
        val lista = ajustes.protegidas
        if (lista.isEmpty()) {
            protegidas.addView(Vistas.margen(Vistas.texto(this, "Ninguna todavía.", 14f, Vistas.BLANCO), this, arriba = 6))
        }
        for (pkg in lista.sortedBy { nombreDe(it).lowercase() }) {
            val f = Vistas.fila(this)
            f.addView(Vistas.texto(this, nombreDe(pkg), 15f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            f.addView(Vistas.boton(this, "Quitar", false) { ajustes.protegidas = ajustes.protegidas - pkg; pintarProtegidas() })
            protegidas.addView(Vistas.margen(f, this, arriba = 6))
        }
        estadoBancos.text = if (Interruptor.puedeEncender(this)) {
            "✓ El botón rápido puede encenderla y apagarla sin entrar a Ajustes."
        } else {
            "El botón rápido la apaga, pero para encenderla te lleva a Accesibilidad. Para que también la encienda, " +
                "da este permiso una vez desde el PC (con el celular conectado):\n" +
                "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        }
    }

    /** Lista de apps con ícono; las que parecen de bancos o pagos van primero. */
    private fun elegirBancos() {
        val pm = packageManager
        val lanzables = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.activityInfo.packageName }.distinct().filter { it != packageName }
        val apps = lanzables.map { it to nombreDe(it) }
            .sortedWith(compareBy({ !Interruptor.pareceBanco(it.second, it.first) }, { it.second.lowercase() }))
        val nombres = apps.map { (pkg, nombre) -> if (Interruptor.pareceBanco(nombre, pkg)) "🏦 $nombre" else nombre }.toTypedArray()
        val marcadas = BooleanArray(apps.size) { apps[it].first in ajustes.protegidas }
        android.app.AlertDialog.Builder(this)
            .setTitle("Apps de bancos y pagos")
            .setMultiChoiceItems(nombres, marcadas) { _, i, v -> marcadas[i] = v }
            .setPositiveButton("Guardar") { _, _ ->
                ajustes.protegidas = apps.filterIndexed { i, _ -> marcadas[i] }.map { it.first }.toSet()
                pintarProtegidas()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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

    private fun pintarGemini() {
        estadoGemini.text = if (Boveda.hay(this)) "✓ Hay una clave de Gemini guardada (cifrada en el celular)." else "Sin clave de Gemini: la burbuja no ofrecerá la revisión completa."
    }

    private fun campo(pista: String, secreto: Boolean) = EditText(this).apply {
        hint = pista
        setHintTextColor(0x99FFFFFF.toInt())
        setTextColor(Vistas.BLANCO)
        background = Vistas.fondo(this@MainActivity, Vistas.NOCHE_CLARA, 12, Vistas.DURAZNO)
        setPadding(Vistas.dp(this@MainActivity, 12), Vistas.dp(this@MainActivity, 10), Vistas.dp(this@MainActivity, 12), Vistas.dp(this@MainActivity, 10))
        isSingleLine = true
        inputType = if (secreto) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
    }

    private fun revisarPruebaCompleta() {
        val texto = prueba.text.toString()
        if (texto.isBlank()) return
        if (!Boveda.hay(this)) { Toast.makeText(this, "Primero guarda la clave de Gemini.", Toast.LENGTH_LONG).show(); return }
        val v = version
        val modelo = ajustes.modeloGemini
        Toast.makeText(this, "Revisando el texto completo…", Toast.LENGTH_SHORT).show()
        trabajo.execute {
            val r = runCatching { Motor.gemini.revisar(texto, Boveda.leer(this), modelo) }
            principal.post {
                if (v != version) return@post
                r.onSuccess { h ->
                    val revisor = Motor.revisor(this)
                    pintarResultados(revisor.conIA(avisosPrueba, revisor.deIA(h)), texto)
                    if (h.isEmpty()) Toast.makeText(this, "Gemini no encontró más errores.", Toast.LENGTH_LONG).show()
                }.onFailure { Toast.makeText(this, "No se pudo: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun pintarResultados(avisos: List<Aviso>, texto: String) {
        avisosPrueba = avisos
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
