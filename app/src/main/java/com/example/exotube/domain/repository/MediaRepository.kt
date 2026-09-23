package com.example.exotube.domain.repository

import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.MediaInfo

/**
 * Contrato que el ViewModel conoce. No sabe si detrás hay yt-dlp, un servidor o datos falsos:
 * eso permite cambiar la implementación (Fase 3) sin tocar la UI.
 */
interface MediaRepository {

    /**
     * Obtiene título, miniatura y formatos de [url].
     * Es `suspend`: la implementación elige su hilo (IO), así la UI nunca se bloquea.
     *
     * @return [Result.success] con la metadata o [Result.failure] con un [MediaError].
     */
    suspend fun fetchMediaInfo(url: String): Result<MediaInfo>

    /**
     * Un primer vistazo rápido (título, miniatura, opciones básicas) para enseñar algo mientras
     * [fetchMediaInfo] termina. null si no hay forma rápida para ese enlace: entonces se espera.
     */
    suspend fun previewMediaInfo(url: String): MediaInfo? = null
}
