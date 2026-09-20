package com.example.exotube.domain.repository

import com.example.exotube.domain.model.DownloadProgress
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaError
import java.io.File

/** Descarga un formato a una carpeta temporal. No sabe nada de notificaciones ni de la galería. */
interface MediaDownloader {

    /**
     * Suspende hasta que el archivo está completo y lo devuelve.
     * [onProgress] puede llamarse desde un hilo de fondo, muchas veces por segundo.
     *
     * @throws MediaError si la descarga falla.
     */
    suspend fun download(
        request: DownloadRequest,
        outputDir: File,
        onProgress: (DownloadProgress) -> Unit,
    ): File
}
