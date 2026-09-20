package com.example.exotube.domain.repository

import com.example.exotube.domain.model.DownloadRequest

/** Encola descargas para que ocurran en segundo plano, aunque el usuario cierre la app. */
interface DownloadScheduler {

    /** Vuelve de inmediato: el trabajo real lo hace otro componente más tarde. */
    fun enqueue(request: DownloadRequest)
}
