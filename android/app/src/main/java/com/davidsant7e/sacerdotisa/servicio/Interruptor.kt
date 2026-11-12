package com.davidsant7e.sacerdotisa.servicio

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import java.lang.ref.WeakReference

/**
 * Encender y apagar la burbuja (el servicio de accesibilidad) sin entrar a Ajustes.
 *
 * Por qué existe: muchas apps de bancos revisan si hay servicios de accesibilidad activos y, si encuentran
 * uno que no conocen, bloquean el ingreso o piden desinstalarlo. Basta con APAGAR el servicio mientras usas
 * el banco: la app puede seguir instalada.
 *
 * - Apagar siempre se puede: el propio servicio llama a disableSelf().
 * - Encender desde la app necesita el permiso WRITE_SECURE_SETTINGS, que Android solo deja dar por adb
 *   (una vez): adb shell pm grant com.davidsant7e.sacerdotisa android.permission.WRITE_SECURE_SETTINGS
 *   Sin ese permiso, encender abre la pantalla de Accesibilidad.
 */
object Interruptor {

    /** El servicio en marcha (para pedirle que se apague). */
    @Volatile
    var instancia: WeakReference<SacerdotisaService>? = null

    fun componente(c: Context) = ComponentName(c, SacerdotisaService::class.java)

    fun activo(c: Context): Boolean {
        val lista = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val yo = componente(c)
        return lista.split(':').any { ComponentName.unflattenFromString(it) == yo }
    }

    /** ¿Se dio el permiso por adb para encender sin pasar por Ajustes? */
    fun puedeEncender(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Devuelve true si quedó encendida; false si hace falta ir a Accesibilidad. */
    fun encender(c: Context): Boolean {
        if (activo(c)) return true
        if (!puedeEncender(c)) return false
        return runCatching {
            val yo = componente(c).flattenToString()
            val actual = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val nueva = (actual.split(':').filter { it.isNotBlank() && it != yo } + yo).joinToString(":")
            Settings.Secure.putString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nueva)
            Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }

    /** Apaga la burbuja. Devuelve true si quedó apagada. */
    fun apagar(c: Context): Boolean {
        instancia?.get()?.let { s ->
            s.apagarse()
            return true
        }
        if (!activo(c)) return true
        if (!puedeEncender(c)) return false
        return runCatching {
            val yo = componente(c).flattenToString()
            val actual = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            Settings.Secure.putString(
                c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                actual.split(':').filter { it.isNotBlank() && it != yo }.joinToString(":"),
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Apps que parecen de bancos o pagos, para sugerirlas en la lista (el usuario decide).
     * Se compara el nombre visible y el paquete; no es una lista cerrada.
     */
    fun pareceBanco(nombre: String, paquete: String): Boolean {
        val t = (nombre + " " + paquete).lowercase()
        return PISTAS.any { it in t }
    }

    private val PISTAS = listOf(
        "banco", "bank", "bancolombia", "nequi", "daviplata", "davivienda", "bbva", "itau", "itaú", "scotiabank",
        "colpatria", "occidente", "popular", "av villas", "avvillas", "falabella", "nu ", "nubank", "lulo", "movii",
        "rappipay", "dale", "pse", "billetera", "wallet", "pay", "finanz", "credit", "tarjeta",
    )
}
