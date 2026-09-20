package com.example.exotube.data.ytdlp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DTO (Data Transfer Object): copia fiel de la parte del JSON de yt-dlp que nos interesa.
 * Vive solo en la capa de datos; el resto de la app usa los modelos de dominio.
 */
@Serializable
internal data class YtDlpInfoDto(
    /** "playlist" para perfiles, canales, carruseles de Instagram o tweets con varios videos. */
    @SerialName("_type") val type: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val duration: Double? = null,
    val formats: List<YtDlpFormatDto> = emptyList(),
    val entries: List<YtDlpInfoDto?> = emptyList(),
)

@Serializable
internal data class YtDlpFormatDto(
    @SerialName("format_id") val formatId: String = "",
    val ext: String? = null,
    /** Códec de video; "none" si el formato es solo audio. */
    val vcodec: String? = null,
    /** Códec de audio; "none" si el formato es solo video. */
    val acodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    /** Bitrate total en kbps. */
    val tbr: Double? = null,
    val filesize: Double? = null,
    @SerialName("filesize_approx") val filesizeApprox: Double? = null,
    /** "https", "m3u8_native" (HLS), "http_dash_segments"… */
    val protocol: String? = null,
)

/** El JSON de yt-dlp trae cientos de campos que ignoramos y a veces nulls donde esperamos listas. */
internal val YtDlpJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
