package com.example.exotube.domain.model

/** Todo lo necesario para descargar un formato elegido por el usuario. */
data class DownloadRequest(
    val url: String,
    val title: String,
    val platform: Platform,
    val format: MediaFormat,
    /** Posición dentro de un carrusel/lista (1 = primero); null si el enlace es un solo video. */
    val playlistIndex: Int? = null,
)

/** Etapas por las que pasa una descarga, para mostrarlas en la notificación. */
sealed interface DownloadProgress {
    data object Preparing : DownloadProgress

    /** [etaSeconds] es null si yt-dlp aún no puede estimarlo. */
    data class Downloading(val percent: Float, val etaSeconds: Long?) : DownloadProgress

    /** Uniendo video + audio o convirtiendo a MP3 (sin porcentaje). */
    data object Processing : DownloadProgress
}
