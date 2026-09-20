package com.example.exotube.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.repository.DownloadScheduler

/**
 * Implementa [DownloadScheduler] con WorkManager: la descarga sobrevive aunque el usuario
 * cierre la app o el sistema mate el proceso, y espera a que haya internet.
 */
class WorkManagerDownloadScheduler(context: Context) : DownloadScheduler {

    private val workManager = WorkManager.getInstance(context)

    override fun enqueue(request: DownloadRequest) {
        val work = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(request.toWorkData())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(DownloadWorker.TAG_DOWNLOAD)
            .build()

        // Nombre único: si el usuario toca dos veces el mismo formato, KEEP ignora el duplicado.
        val uniqueName = "download:${request.url}:${request.format.formatId}:${request.format.extension}"
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, work)
    }
}
