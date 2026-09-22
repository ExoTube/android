package com.example.exotube.data.ytdlp

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.exotube.ExoTubeApp
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Tarea diaria de WorkManager que actualiza yt-dlp sin publicar una versión nueva de la app.
 * WorkManager la ejecuta aunque la app esté cerrada y solo cuando hay internet.
 */
class EngineUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val engine = (applicationContext as ExoTubeApp).container.ytDlpEngine
        return try {
            val status = engine.update()
            Log.i(TAG, "Actualización de yt-dlp: $status")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo actualizar yt-dlp (intento ${runAttemptCount + 1})", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "EngineUpdateWorker"
        private const val WORK_NAME = "yt-dlp-update"
        private const val URGENT_WORK_NAME = "yt-dlp-update-now"
        private const val MAX_RETRIES = 3

        /** Idempotente: KEEP evita duplicar la tarea cada vez que se abre la app. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EngineUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(withInternet())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /**
         * Actualiza yt-dlp ya, sin esperar a la vuelta diaria.
         *
         * Se usa cuando los videos en línea fallan una y otra vez: lo más probable es que
         * YouTube haya cambiado algo y el yt-dlp de este teléfono aún no lo sepa. KEEP hace que
         * pedirlo varias veces seguidas no lance varias descargas.
         */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<EngineUpdateWorker>()
                .setConstraints(withInternet())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(URGENT_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        private fun withInternet() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
