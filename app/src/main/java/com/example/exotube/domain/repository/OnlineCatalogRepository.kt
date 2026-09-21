package com.example.exotube.domain.repository

import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource

/**
 * Catálogo de videos que todavía están en internet: buscarlos y obtener la dirección para verlos
 * sin descargarlos.
 *
 * Como el resto de repositorios, el ViewModel no sabe qué hay detrás (aquí, yt-dlp).
 */
interface OnlineCatalogRepository {

    /**
     * Busca videos por texto.
     *
     * @return [Result.success] con los resultados (puede venir vacío si no hay ninguno)
     *   o [Result.failure] con un [MediaError].
     */
    suspend fun search(query: String): Result<List<OnlineVideo>>

    /**
     * Pide la dirección directa para reproducir [video] sin descargarlo.
     *
     * Con [audioOnly] trae solo la pista de audio: gasta mucho menos si se está con datos.
     * La dirección caduca, así que hay que pedirla justo antes de reproducir y no guardarla.
     */
    suspend fun resolveStream(video: OnlineVideo, audioOnly: Boolean): Result<StreamSource>
}
