package com.example.exotube.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.exotube.ExoTubeApp
import com.example.exotube.domain.model.DownloadProgress
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaError
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Ejecuta UNA descarga en segundo plano. Orquesta a los demás componentes:
 *   1. Pasa a primer plano (Foreground Service) con notificación de progreso.
 *   2. MediaDownloader (yt-dlp) descarga a una carpeta temporal privada.
 *   3. MediaStoreSaver copia el resultado a la galería / música.
 *   4. DownloadNotifier avisa del éxito o del error.
 *   5. DownloadHistoryRepository guarda el registro en Supabase.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val container = (context.applicationContext as ExoTubeApp).container
    private val notifier = DownloadNotifier(context)

    // Id de la notificación en curso; la final usa otro para que no la borre WorkManager al terminar.
    private val progressNotificationId = id.hashCode()
    private val resultNotificationId = progressNotificationId + 1
    private var canRunInForeground = true

    override suspend fun doWork(): Result {
        val request = inputData.toDownloadRequest() ?: return Result.failure()
        // Lo primero: pasar a primer plano, así Android no mata el proceso a mitad de descarga.
        updateNotification(request.title, DownloadProgress.Preparing)

        // Carpeta exclusiva de este trabajo (el id es único): nada choca con otras descargas.
        val workDir = File(applicationContext.cacheDir, "downloads/$id")
        return try {
            val file = downloadWithProgress(request, workDir)
            val saved = container.mediaSaver.save(file, request.format.type)
            notifier.showCompleted(resultNotificationId, request.title, saved)

            // Historial anónimo en Supabase. Si falla (sin red, sin configurar…) la descarga
            // sigue siendo un éxito: el archivo ya está en el teléfono.
            container.historyRepository.record(request, saved.sizeBytes)
                .onFailure { Log.w(TAG, "No se guardó en el historial", it) }
            Result.success()
        } catch (e: CancellationException) {
            throw e // el usuario tocó "Cancelar": WorkManager quita la notificación
        } catch (e: Exception) {
            Log.w(TAG, "Descarga fallida", e)
            notifier.showFailed(resultNotificationId, request.title, e as? MediaError ?: MediaError.Unknown(e))
            Result.failure()
        } finally {
            workDir.deleteRecursively()
        }
    }

    private suspend fun downloadWithProgress(request: DownloadRequest, workDir: File): File = coroutineScope {
        // yt-dlp informa desde otro hilo muchas veces por segundo. StateFlow guarda solo el ÚLTIMO
        // valor, así que el colector puede ir más lento sin acumular trabajo pendiente.
        val progress = MutableStateFlow<DownloadProgress>(DownloadProgress.Preparing)
        val reporter = launch {
            progress.collect { current ->
                updateNotification(request.title, current)
                delay(NOTIFICATION_THROTTLE_MS) // Android descarta notificaciones si actualizas >5 veces/s
            }
        }
        try {
            container.mediaDownloader.download(request, workDir) { progress.value = it }
        } finally {
            reporter.cancel() // sin esto, coroutineScope esperaría para siempre al colector
        }
    }

    private suspend fun updateNotification(title: String, progress: DownloadProgress) {
        if (!canRunInForeground) return
        val notification = notifier.progress(id, title, progress)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(progressNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(progressNotificationId, notification)
        }
        try {
            setForeground(info)
        } catch (e: IllegalStateException) {
            // Android 12+ prohíbe iniciar un Foreground Service desde segundo plano (p. ej. si el
            // trabajo arrancó tarde). La descarga sigue igual, solo que sin notificación de progreso.
            Log.w(TAG, "No se pudo pasar a primer plano", e)
            canRunInForeground = false
        }
    }

    companion object {
        const val TAG_DOWNLOAD = "download"
        private const val TAG = "DownloadWorker"
        private const val NOTIFICATION_THROTTLE_MS = 500L
    }
}
