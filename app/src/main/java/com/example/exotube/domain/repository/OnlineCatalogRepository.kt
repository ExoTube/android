package com.example.exotube.domain.repository

import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.model.VideoPage

/**
 * Catálogo de videos que todavía están en internet: buscarlos y obtener la dirección para verlos
 * sin descargarlos.
 *
 * Como el resto de repositorios, el ViewModel no sabe qué hay detrás (aquí, yt-dlp).
 */
interface OnlineCatalogRepository {

    /**
     * Busca videos por texto: la primera página de resultados.
     *
     * @return [Result.success] con los resultados (puede venir vacío si no hay ninguno)
     *   o [Result.failure] con un [MediaError].
     */
    suspend fun search(query: String): Result<VideoPage>

    /**
     * La página siguiente de [query]. Solo tiene sentido justo después de [search] con ese mismo
     * texto; si no, devuelve una página vacía sin más.
     */
    suspend fun searchMore(query: String): Result<VideoPage>

    /**
     * Pide la dirección directa para reproducir [video] sin descargarlo.
     *
     * Con [audioOnly] trae solo la pista de audio: gasta mucho menos si se está con datos.
     * Si no, trae la mejor imagen que no pase de [maxHeight] píxeles de alto.
     * La dirección caduca, así que hay que pedirla justo antes de reproducir y no guardarla.
     */
    suspend fun resolveStream(
        video: OnlineVideo,
        audioOnly: Boolean,
        maxHeight: Int,
    ): Result<StreamSource>

    /**
     * Olvida las direcciones guardadas de [video], para que la próxima vez se pidan de nuevo.
     *
     * Hace falta cuando el reproductor no consigue usar una: si se quedara guardada, volver a
     * tocar el video repetiría el mismo fallo una y otra vez.
     */
    fun forget(video: OnlineVideo)
}
