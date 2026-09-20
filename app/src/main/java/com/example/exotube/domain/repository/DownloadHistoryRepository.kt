package com.example.exotube.domain.repository

import com.example.exotube.domain.model.DownloadRequest

/** Historial de descargas completadas. */
interface DownloadHistoryRepository {

    /**
     * Registra una descarga que ya terminó con éxito.
     * Devuelve [Result.failure] en vez de lanzar: un fallo aquí no debe arruinar la descarga.
     */
    suspend fun record(request: DownloadRequest, fileSizeBytes: Long): Result<Unit>
}
