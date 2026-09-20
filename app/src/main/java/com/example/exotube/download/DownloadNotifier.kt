package com.example.exotube.download

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.example.exotube.R
import com.example.exotube.domain.model.DownloadProgress
import com.example.exotube.domain.model.MediaError
import com.example.exotube.ui.messageRes
import java.util.UUID
import kotlin.math.roundToInt

/** Construye y muestra las notificaciones de descarga. No sabe nada de yt-dlp ni de WorkManager. */
class DownloadNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        // Desde Android 8 toda notificación necesita un canal. Crearlo varias veces no hace nada.
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_PROGRESS, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.channel_downloads_progress))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_RESULT, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_downloads_result))
                    .build(),
            ),
        )
    }

    /** Notificación persistente del Foreground Service, con barra de progreso y botón Cancelar. */
    fun progress(workId: UUID, title: String, progress: DownloadProgress): Notification {
        // PendingIntent que WorkManager ya sabe manejar: cancela el trabajo con ese id.
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.download_cancel), cancelIntent)

        when (progress) {
            DownloadProgress.Preparing -> builder
                .setContentText(context.getString(R.string.download_preparing))
                .setProgress(0, 0, true)
            DownloadProgress.Processing -> builder
                .setContentText(context.getString(R.string.download_processing))
                .setProgress(0, 0, true)
            is DownloadProgress.Downloading -> {
                val percent = progress.percent.roundToInt()
                val text = progress.etaSeconds?.let {
                    context.getString(R.string.download_progress_eta, percent, DateUtils.formatElapsedTime(it))
                } ?: context.getString(R.string.download_progress, percent)
                builder.setContentText(text).setProgress(100, percent, false)
            }
        }
        return builder.build()
    }

    /** Al tocarla se abre el archivo con el reproductor que elija el usuario. */
    fun showCompleted(notificationId: Int, title: String, file: SavedFile) {
        val openFile = Intent(Intent.ACTION_VIEW)
            .setDataAndType(file.uri, file.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(
            context, notificationId, openFile, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notifyIfAllowed(
            notificationId,
            NotificationCompat.Builder(context, CHANNEL_RESULT)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.download_completed))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun showFailed(notificationId: Int, title: String, error: MediaError) {
        notifyIfAllowed(
            notificationId,
            NotificationCompat.Builder(context, CHANNEL_RESULT)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.download_failed, title))
                .setContentText(context.getString(error.messageRes()))
                .setAutoCancel(true)
                .build(),
        )
    }

    /** En Android 13+ el usuario puede haber negado las notificaciones: entonces no mostramos nada. */
    private fun notifyIfAllowed(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(notificationId, notification)
    }

    private companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_RESULT = "downloads_result"
    }
}
