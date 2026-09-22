package com.example.exotube.data.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.exotube.ExoTubeApp
import java.util.concurrent.TimeUnit

/**
 * Mira una vez al día si hay una versión nueva de ExoTube y, si la hay, lo notifica.
 *
 * WorkManager la ejecuta aunque la app esté cerrada y solo cuando hay internet, igual que la
 * tarea que actualiza yt-dlp. Es el único motivo por el que ExoTube habla con GitHub por su
 * cuenta: una petición diaria, sin enviar nada del usuario.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ExoTubeApp).container
        val update = container.updateRepository.findUpdate().getOrElse { error ->
            // Sin internet no es un fallo del que informar: mañana se vuelve a mirar.
            Log.i(TAG, "No se pudo consultar si hay versión nueva: ${error.message}")
            return Result.success()
        }
        if (update == null) return Result.success()
        // Si el usuario ya dijo "ahora no" a esta versión, no se insiste todos los días.
        if (container.updateSettings.isDismissed(update.versionName)) return Result.success()

        UpdateNotifier(applicationContext).showAvailable(update.versionName)
        return Result.success()
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "app-update-check"

        /** Idempotente: KEEP evita duplicar la tarea cada vez que se abre la app. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
