package com.example.exotube.settings

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.IntentCompat
import com.example.exotube.ExoTubeApp
import com.example.exotube.R
import com.example.exotube.share.ShareActivity

/**
 * Abre el menú de compartir de Android con la invitación a ExoTube.
 *
 * Para saber si de verdad se compartió, se le pide a Android que avise a [ShareChosenReceiver]
 * cuando la persona elige una app del menú (WhatsApp, Instagram...). Si cierra el menú sin elegir,
 * no cuenta.
 */
fun shareExoTube(context: Context) {
    val invite = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_app_text))
    // Mutable: Android le añade a este aviso qué app se eligió.
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
    val chosen = PendingIntent.getBroadcast(context, 0, Intent(context, ShareChosenReceiver::class.java), flags)
    val chooser = Intent.createChooser(invite, context.getString(R.string.share_app_title), chosen.intentSender)
        // ExoTube también recibe textos ("Descargar con ExoTube"): compartírselo a sí misma no es invitar a nadie.
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, ShareActivity::class.java)))
    context.startActivity(chooser)
}

/** Recibe el aviso de Android "eligió una app para compartir" y lo suma en Ajustes. */
class ShareChosenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Por si algún teléfono ignora la lista de excluidas: elegir la propia ExoTube no cuenta.
        val chosen = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        if (chosen?.packageName == context.packageName) return
        (context.applicationContext as ExoTubeApp).container.settings.addShare()
    }
}
