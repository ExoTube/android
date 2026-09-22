package com.example.exotube.data.update

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.exotube.domain.model.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Descarga el APK de la versión nueva.
 *
 * Va en un Worker y no en la pantalla porque son unos 65 MB: si el usuario sale de ExoTube o
 * bloquea el teléfono a mitad, la descarga sigue. Al terminar deja una notificación que abre el
 * instalador de Android.
 *
 * Se usa HttpURLConnection, que viene en el propio Java, en vez de una librería: aquí no hay que
 * interpretar nada, solo copiar bytes a un archivo contando cuántos van.
 */
class ApkDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val installer = ApkInstaller(context)
    private val notifier = UpdateNotifier(context)

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val versionName = inputData.getString(KEY_VERSION) ?: return Result.failure()

        val target = installer.destinationFor(versionName)
        // Una descarga a medias de un intento anterior no sirve: se empieza de cero.
        installer.clearOldDownloads()
        updateNotification(versionName, UNKNOWN_PERCENT)

        return try {
            withContext(Dispatchers.IO) { download(url, target, versionName) }
            notifier.showReadyToInstall(versionName, target, installer)
            Result.success(workDataOf(KEY_FILE to target.absolutePath))
        } catch (e: CancellationException) {
            target.delete() // el usuario canceló: no dejamos 30 MB a medias
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo descargar la actualización", e)
            target.delete()
            Result.failure()
        }
    }

    private suspend fun download(url: String, target: File, versionName: String) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("GitHub respondió $code")
            // -1 cuando el servidor no dice cuánto pesa: entonces la barra va indeterminada.
            val total = connection.contentLengthLong

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    copyReportingProgress(input.buffered(), output, total, versionName)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun copyReportingProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        versionName: String,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        var lastPercent = UNKNOWN_PERCENT

        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read

            val percent = if (totalBytes > 0) (copied * 100.0 / totalBytes).roundToInt() else UNKNOWN_PERCENT
            // Solo al cambiar de punto porcentual: Android descarta las notificaciones de una app
            // que actualiza más de cinco veces por segundo, y aquí pasaríamos de largo.
            if (percent != lastPercent) {
                lastPercent = percent
                setProgress(workDataOf(KEY_PERCENT to percent))
                updateNotification(versionName, percent)
            }
        }
        output.flush()
    }

    private suspend fun updateNotification(versionName: String, percent: Int) {
        val notification = notifier.downloading(versionName, percent)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(UpdateNotifier.ID_DOWNLOADING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(UpdateNotifier.ID_DOWNLOADING, notification)
        }
        try {
            setForeground(info)
        } catch (e: IllegalStateException) {
            // Android 12+ prohíbe iniciar un servicio en primer plano desde segundo plano. La
            // descarga sigue igual, solo que sin barra de progreso en la persiana.
            Log.w(TAG, "No se pudo pasar a primer plano", e)
        }
    }

    companion object {
        private const val TAG = "ApkDownloadWorker"
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024

        const val WORK_NAME = "app-update-download"
        const val KEY_PERCENT = "porcentaje"
        const val KEY_FILE = "archivo"

        private const val KEY_URL = "url"
        private const val KEY_VERSION = "version"

        /** Mientras no se sabe cuánto pesa el archivo. */
        const val UNKNOWN_PERCENT = -1

        fun dataFor(update: AppUpdate): Data =
            workDataOf(KEY_URL to update.downloadUrl, KEY_VERSION to update.versionName)

        fun requestFor(update: AppUpdate) = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setInputData(dataFor(update))
            .build()
    }
}
