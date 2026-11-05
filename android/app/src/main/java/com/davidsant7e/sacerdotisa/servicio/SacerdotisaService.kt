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
import com.davidsant7e.sacerdotisa.Motor
import com.davidsant7e.sacerdotisa.motor.Aviso
import com.davidsant7e.sacerdotisa.motor.LanguageTool
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
        )
        Log.i(TAG, "Servicio conectado")
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!::burbuja.isInitialized) return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
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
                    texto = actual
                    version++
                    principal.removeCallbacks(revisarLocal)
                    principal.removeCallbacks(revisarConLanguageTool)
                    if (texto.isBlank()) { avisos = emptyList(); burbuja.ocultar(); return }
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
        nodo = null
        if (::burbuja.isInitialized) burbuja.ocultar()
    }

    private fun revisarAhora() {
        avisos = Motor.revisor(this).locales(texto, ajustes.filtros())
        mostrar()
    }

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
                null
            } ?: return@execute
            principal.post {
                if (v != version || t != texto) return@post // ya escribiste otra cosa
                avisos = Motor.revisor(this).combinados(t, respuesta, ajustes.filtros())
                mostrar()
            }
        }
    }

    private fun mostrar() {
        val n = nodo ?: return
        val r = Rect()
        n.getBoundsInScreen(r)
        burbuja.mostrar(avisos, r, nombreDeApp(paquete))
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
            return
        }
        val sel = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        texto = nuevo
        version++
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

    override fun onDestroy() {
        principal.removeCallbacksAndMessages(null)
        if (::burbuja.isInitialized) burbuja.ocultar()
        trabajo.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val TAG = "Sacerdotisa"
    }
}
