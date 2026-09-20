package com.example.exotube.domain.model

/** Metadata de un video listo para descargar. Es un modelo puro: no depende de Android ni de yt-dlp. */
data class MediaInfo(
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val formats: List<MediaFormat>,
    /** Posición del video dentro de un carrusel/lista (1 = primero); null si es un solo video. */
    val playlistIndex: Int? = null,
) {
    val videoFormats: List<MediaFormat> get() = formats.filter { it.type == MediaType.VIDEO }
    val audioFormats: List<MediaFormat> get() = formats.filter { it.type == MediaType.AUDIO }
}

/** Una opción de descarga que el usuario puede elegir en el Bottom Sheet. */
data class MediaFormat(
    /** Selector que recibirá yt-dlp al descargar (p. ej. "bv*[height<=1080]+ba/b"). */
    val formatId: String,
    val type: MediaType,
    /** Texto visible: "1080p", "MP3"… */
    val label: String,
    val extension: String,
    /** Peso estimado en bytes; null si la plataforma no lo informa. */
    val sizeBytes: Long?,
)

enum class MediaType { VIDEO, AUDIO }
