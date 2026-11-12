package com.davidsant7e.sacerdotisa.servicio

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * Botón en los ajustes rápidos (la cortina de arriba): «Sacerdotisa» encendida o apagada.
 * Apágala antes de abrir el banco y vuelve a encenderla después.
 */
class Mosaico : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        pintar()
    }

    override fun onClick() {
        super.onClick()
        if (Interruptor.activo(this)) {
            if (!Interruptor.apagar(this)) abrirAccesibilidad()
        } else if (!Interruptor.encender(this)) {
            Toast.makeText(this, "Actívala en Accesibilidad → La Sacerdotisa", Toast.LENGTH_LONG).show()
            abrirAccesibilidad()
        }
        // El cambio tarda un instante en verse en Ajustes.
        qsTile?.let { it.state = Tile.STATE_UNAVAILABLE; it.updateTile() }
        android.os.Handler(mainLooper).postDelayed({ pintar() }, 600)
    }

    private fun pintar() {
        val t = qsTile ?: return
        val activa = Interruptor.activo(this)
        t.state = if (activa) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) t.subtitle = if (activa) "Encendida" else "Apagada"
        t.updateTile()
    }

    private fun abrirAccesibilidad() {
        val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(i)
        }
    }
}
