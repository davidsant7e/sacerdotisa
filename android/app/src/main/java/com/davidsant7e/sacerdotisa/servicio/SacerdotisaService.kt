package com.davidsant7e.sacerdotisa.servicio

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.davidsant7e.sacerdotisa.Ajustes
import com.davidsant7e.sacerdotisa.Boveda
import com.davidsant7e.sacerdotisa.Motor
import com.davidsant7e.sacerdotisa.motor.Aviso
import com.davidsant7e.sacerdotisa.motor.LanguageTool
import com.davidsant7e.sacerdotisa.registro.Registro
import java.util.concurrent.Executors

/**
 * Lee lo que escribes en el campo de texto que tiene el foco (WhatsApp, Notas, Messenger…),
 * lo revisa y muestra la burbuja. Nunca lee campos de contraseña ni guarda el texto.
 *
 * Revisión: reglas locales a los 0,4 s de dejar de escribir; LanguageTool a los 1,5 s
 * (si está activado), sin pasar de 10 consultas por minuto.
 */
class SacerdotisaService : AccessibilityService() {

    private val principal = Handler(Looper.getMainLooper())
    private val trabajo = Executors.newSingleThreadExecutor()
    private lateinit var ajustes: Ajustes
    private lateinit var burbuja: Burbuja

    private var nodo: AccessibilityNodeInfo? = null
    private var paquete = ""
    private var texto = ""
    private var avisos: List<Aviso> = emptyList()
    /** Avisos de las reglas y LanguageTool (sin los de la IA). */
    private var avisosBase: List<Aviso> = emptyList()
    /** Avisos de «Revisar texto completo» (Gemini) para el texto actual. */
    private var avisosIA: List<Aviso> = emptyList()
    private var mensajeIA: String? = null
    private var revisandoIA = false
    private var version = 0

    private val revisarLocal = Runnable { revisarAhora() }
    private val revisarConLanguageTool = Runnable { consultarLanguageTool() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ajustes = Ajustes(this)
        burbuja = Burbuja(
            this,
            alCorregir = { corregir(it) },
            alCorregirTodo = { corregirTodo() },
            alExcluirApp = {
                ajustes.excluir(paquete)
                avisos = emptyList()
            },
            alRevisarCompleto = { revisarTextoCompleto() },
        )
        Interruptor.instancia = java.lang.ref.WeakReference(this)
        Log.i(TAG, "Servicio conectado")
        Registro.evento("SERVICIO", "conectado")
    }

    /** Se apaga del todo (sale de la lista de Accesibilidad); se vuelve a encender con el botón rápido. */
    fun apagarse() {
        Registro.evento("SERVICIO", "apagado")
        olvidar()
        Interruptor.instancia = null
        disableSelf()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!::burbuja.isInitialized) return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Se abrió una app de banco de la lista: apagarse para que el banco no nos detecte.
                if (ajustes.apagarConBancos && pkg in ajustes.protegidas) {
                    Log.i(TAG, "App protegida ($pkg): la burbuja se apaga")
                    Registro.evento("BANCO", "detectado", "app" to pkg)
                    val app = applicationContext
                    principal.post {
                        android.widget.Toast.makeText(app, "La Sacerdotisa se apagó para ${nombreDeApp(pkg)}. Enciéndela con el botón rápido.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    apagarse()
                    return
                }
                // Cambió de app o de pantalla: si no es el teclado, se esconde hasta que vuelvas a escribir.
                if (pkg != paquete && pkg != packageName && !esTeclado(pkg)) olvidar()
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val n = e.source ?: return
                if (pkg == packageName || !n.isEditable || n.isPassword || !ajustes.burbuja || pkg in ajustes.excluidas) {
                    if (e.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) olvidar()
                    return
                }
                val actual = leerTexto(n)
                nodo = n
                paquete = pkg
                if (actual != texto || e.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                    if (actual != texto) olvidarIA()
                    texto = actual
                    version++
                    principal.removeCallbacks(revisarLocal)
                    principal.removeCallbacks(revisarConLanguageTool)
                    if (texto.isBlank()) { avisos = emptyList(); avisosBase = emptyList(); burbuja.ocultar(); return }
                    principal.postDelayed(revisarLocal, 400)
                    if (ajustes.languageTool) principal.postDelayed(revisarConLanguageTool, 1500)
                } else if (burbuja.visible) {
                    mostrar() // mismo texto, pero el campo pudo moverse (se abrió el teclado): reubicar
                }
            }
        }
    }

    private fun leerTexto(n: AccessibilityNodeInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && n.isShowingHintText) return ""
        return n.text?.toString() ?: ""
    }

    private fun olvidar() {
        principal.removeCallbacks(revisarLocal)
        principal.removeCallbacks(revisarConLanguageTool)
        version++
        texto = ""
        avisos = emptyList()
        avisosBase = emptyList()
        olvidarIA()
        nodo = null
        if (::burbuja.isInitialized) burbuja.ocultar()
    }

    private fun olvidarIA() {
        avisosIA = emptyList()
        mensajeIA = null
        revisandoIA = false
    }

    private fun revisarAhora() {
        avisosBase = Motor.revisor(this).locales(texto, ajustes.filtros())
        juntar()
        mostrar()
    }

    private fun juntar() {
        avisos = Motor.revisor(this).conIA(avisosBase, avisosIA)
    }

    /** «Revisar texto completo»: Gemini lee todo el texto (comas, concordancia, sentido). */
    private fun revisarTextoCompleto() {
        val t = texto
        val v = version
        if (t.isBlank() || revisandoIA) return
        if (!Boveda.hay(this)) {
            mensajeIA = "Para revisar el texto completo, abre La Sacerdotisa y pega tu clave de Gemini."
            mostrar(abrir = true)
            return
        }
        revisandoIA = true
        mensajeIA = "Revisando el texto completo…"
        mostrar(abrir = true)
        val modelo = ajustes.modeloGemini
        trabajo.execute {
            val r = runCatching { Motor.gemini.revisar(t, Boveda.leer(this), modelo) }
            principal.post {
                if (v != version || t != texto) return@post // ya escribiste otra cosa
                revisandoIA = false
                r.onSuccess { hallazgos ->
                    avisosIA = Motor.revisor(this).deIA(hallazgos)
                    mensajeIA = if (avisosIA.isEmpty()) "✓ Gemini leyó todo el texto y no encontró más errores." else
                        "Gemini encontró ${avisosIA.size} ${if (avisosIA.size == 1) "detalle" else "detalles"} en el texto completo."
                }.onFailure { x ->
                    mensajeIA = "No se pudo revisar con Gemini: ${x.message}"
                }
                juntar()
                mostrar(abrir = true)
            }
        }
    }

    /** Se ofrece la revisión completa en textos de varias palabras (no en un «ok» o un «jaja»). */
    private fun ofrecerIA(): Boolean = Regex("\\p{L}+").findAll(texto).count() >= 6

    private fun consultarLanguageTool() {
        val lt = Motor.languageTool
        val t = texto
        val v = version
        if (t.isBlank() || t.length > 5000 || !lt.puedeConsultar()) return
        val idioma = ajustes.idioma
        trabajo.execute {
            val respuesta = try {
                lt.revisar(t, idioma)
            } catch (x: LanguageTool.LimiteException) {
                null
            } catch (x: Exception) {
                Log.i(TAG, "LanguageTool no respondió: ${x.message}")
                Registro.error("LANGUAGETOOL", x)
                null
            } ?: return@execute
            principal.post {
                if (v != version || t != texto) return@post // ya escribiste otra cosa
                avisosBase = Motor.revisor(this).combinados(t, respuesta, ajustes.filtros())
                juntar()
                mostrar()
            }
        }
    }

    private fun mostrar(abrir: Boolean = false) {
        val n = nodo ?: return
        val r = Rect()
        n.getBoundsInScreen(r)
        burbuja.mostrar(avisos, r, nombreDeApp(paquete), ofrecerIA(), mensajeIA, revisandoIA, abrir)
    }

    private fun corregir(a: Aviso) {
        val n = nodo ?: return
        n.refresh()
        val actual = leerTexto(n)
        val (nuevo, cursor) = Motor.revisor(this).corregir(actual, a) ?: run { revisarDeNuevo(actual); return }
        escribir(n, nuevo, cursor)
    }

    private fun corregirTodo() {
        val n = nodo ?: return
        n.refresh()
        val actual = leerTexto(n)
        if (actual != texto) { revisarDeNuevo(actual); return }
        val nuevo = Motor.revisor(this).corregirTodo(actual, avisos)
        escribir(n, nuevo, nuevo.length)
    }

    /** Reemplaza el texto del campo (como si lo hubieras escrito) y deja el cursor después del cambio. */
    private fun escribir(n: AccessibilityNodeInfo, nuevo: String, cursor: Int) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, nuevo)
        }
        if (!n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            Log.i(TAG, "Esta app no deja reemplazar el texto")
            Registro.evento("CORREGIR", "rechazado", "app" to n.packageName)
            return
        }
        val sel = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        texto = nuevo
        version++
        // Los avisos de la IA siguen valiendo: se vuelven a ubicar en el texto nuevo.
        avisosIA = Motor.revisor(this).reubicarIA(nuevo, avisosIA)
        revisarAhora()
        if (ajustes.languageTool) {
            principal.removeCallbacks(revisarConLanguageTool)
            principal.postDelayed(revisarConLanguageTool, 1500)
        }
    }

    private fun revisarDeNuevo(actual: String) {
        texto = actual
        version++
        revisarAhora()
    }

    private fun nombreDeApp(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun esTeclado(pkg: String): Boolean =
        pkg.contains("inputmethod") || pkg.contains("keyboard") || pkg == "com.google.android.inputmethod.latin"

    override fun onInterrupt() {
        if (::burbuja.isInitialized) burbuja.ocultar()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (Interruptor.instancia?.get() === this) Interruptor.instancia = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Registro.evento("SERVICIO", "detenido")
        if (Interruptor.instancia?.get() === this) Interruptor.instancia = null
        principal.removeCallbacksAndMessages(null)
        if (::burbuja.isInitialized) burbuja.ocultar()
        trabajo.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val TAG = "Sacerdotisa"
    }
}
