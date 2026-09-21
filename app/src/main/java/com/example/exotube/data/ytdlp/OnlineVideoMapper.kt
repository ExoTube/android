package com.example.exotube.data.ytdlp

import com.example.exotube.domain.model.OnlineVideo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lo que devuelve `yt-dlp ytsearch…`: una "playlist" cuyos elementos son los resultados.
 * Con --flat-playlist cada elemento trae solo lo justo para pintar una fila (no se analiza
 * video por video, que tardaría muchísimo).
 */
@Serializable
internal data class YtDlpSearchDto(
    val entries: List<YtDlpEntryDto?> = emptyList(),
)

@Serializable
internal data class YtDlpEntryDto(
    val id: String? = null,
    val title: String? = null,
    val url: String? = null,
    val duration: Double? = null,
    /** El canal; en algunas plataformas el campo se llama "uploader". */
    val channel: String? = null,
    val uploader: String? = null,
    @SerialName("view_count") val viewCount: Long? = null,
    val thumbnails: List<YtDlpThumbnailDto> = emptyList(),
    /** Algunas respuestas traen una sola miniatura suelta en vez de la lista. */
    val thumbnail: String? = null,
)

@Serializable
internal data class YtDlpThumbnailDto(
    val url: String? = null,
    val width: Int? = null,
)

/**
 * Traduce los resultados de yt-dlp al modelo de dominio. Es una función pura (entra JSON ya
 * parseado, sale una lista): se puede probar sin red ni emulador.
 */
internal object OnlineVideoMapper {

    fun toOnlineVideos(dto: YtDlpSearchDto): List<OnlineVideo> =
        dto.entries.filterNotNull().mapNotNull(::toOnlineVideo)

    /** null si al resultado le falta lo imprescindible: sin id ni título no se puede mostrar. */
    fun toOnlineVideo(entry: YtDlpEntryDto): OnlineVideo? {
        val id = entry.id?.takeIf { it.isNotBlank() } ?: return null
        val title = entry.title?.takeIf { it.isNotBlank() } ?: return null
        return OnlineVideo(
            id = id,
            url = entry.watchUrl(id),
            title = title,
            channel = (entry.channel ?: entry.uploader)?.takeIf { it.isNotBlank() },
            // Los directos no informan de duración, y a veces llega 0: en ambos casos, null.
            durationSeconds = entry.duration?.toLong()?.takeIf { it > 0 },
            thumbnailUrl = entry.bestThumbnail(),
            viewCount = entry.viewCount?.takeIf { it > 0 },
        )
    }

    /** Con --flat-playlist el campo `url` ya viene completo; si no, se arma desde el id. */
    private fun YtDlpEntryDto.watchUrl(id: String): String =
        url?.takeIf { it.startsWith("http") } ?: "https://www.youtube.com/watch?v=$id"

    /**
     * La miniatura más grande que no pase de [MAX_THUMBNAIL_WIDTH]: en una fila de lista no se
     * nota la diferencia y así no se gastan datos en una imagen de 1280 px.
     */
    private fun YtDlpEntryDto.bestThumbnail(): String? {
        val withWidth = thumbnails.mapNotNull { candidate ->
            candidate.url?.takeIf { it.isNotBlank() }?.let { it to (candidate.width ?: 0) }
        }
        val chosen = withWidth.filter { it.second in 1..MAX_THUMBNAIL_WIDTH }.maxByOrNull { it.second }
            ?: withWidth.firstOrNull() // ninguna dice su ancho: nos quedamos con la primera
        return chosen?.first ?: thumbnail
    }

    private const val MAX_THUMBNAIL_WIDTH = 800
}
