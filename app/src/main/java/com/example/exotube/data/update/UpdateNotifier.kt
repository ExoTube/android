package com.example.exotube.data.update

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.exotube.MainActivity
import com.example.exotube.R
import java.io.File

/**
 * Las notificaciones de la actualización: "hay versión nueva" y "ya está lista para instalar".
 *
 * Son dos avisos y no uno porque la descarga son 65 MB: nadie quiere que su teléfono se los baje
 * sin preguntar, y tampoco tiene sentido dejarle mirando una barra de progreso. Se avisa, decide,
 * y cuando acaba se le vuelve a avisar.
 */
class UpdateNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_updates))
                    .build(),
            ),
        )
    }

    /** Al tocarla se abre ExoTube, que muestra las novedades y el botón de descargar. */
    fun showAvailable(versionName: String) {
        val openApp = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        show(
            ID_AVAILABLE,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_notification_title, versionName))
                .setContentText(context.getString(R.string.update_notification_text))
                .setContentIntent(pendingActivity(ID_AVAILABLE, openApp))
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Barra de progreso de la descarga, la que sostiene el servicio en primer plano. */
    fun downloading(versionName: String, percent: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.update_downloading, versionName))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // percent < 0 significa "todavía no se sabe cuánto pesa": barra indeterminada.
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .build()

    /** Al tocarla se abre directamente el instalador de Android. */
    fun showReadyToInstall(versionName: String, apk: File, installer: ApkInstaller) {
        show(
            ID_READY,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_ready_title, versionName))
                .setContentText(context.getString(R.string.update_ready_text))
                .setContentIntent(pendingActivity(ID_READY, installer.installIntent(apk)))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancelDownloadNotice() = manager.cancel(ID_DOWNLOADING)

    private fun pendingActivity(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** En Android 13+ el usuario puede haber negado las notificaciones: entonces no mostramos nada. */
    private fun show(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(id, notification)
    }

    companion object {
        private const val CHANNEL = "app_updates"
        private const val ID_AVAILABLE = 8_001
        private const val ID_READY = 8_002

        /** El de la barra de progreso lo usa el servicio en primer plano del Worker. */
        const val ID_DOWNLOADING = 8_003
    }
}
