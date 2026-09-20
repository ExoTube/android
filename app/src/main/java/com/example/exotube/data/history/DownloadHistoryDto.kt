package com.example.exotube.data.history

import com.example.exotube.domain.model.DownloadRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fila que se envía a la tabla `downloads_history`. Los nombres de @SerialName deben coincidir
 * con las columnas del SQL. No incluye `id`, `user_id` ni `created_at`: los pone Postgres.
 */
@Serializable
internal data class DownloadHistoryInsertDto(
    @SerialName("original_url") val originalUrl: String,
    val platform: String,
    val title: String,
    val format: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("file_size_bytes") val fileSizeBytes: Long,
)

/** Respeta los CHECK del SQL (longitud del título) para que Postgres no rechace la fila. */
internal fun DownloadRequest.toHistoryDto(fileSizeBytes: Long) = DownloadHistoryInsertDto(
    originalUrl = url,
    platform = platform.name.lowercase(), // Platform.YOUTUBE → "youtube"
    title = title.take(MAX_TITLE_LENGTH),
    format = format.label,
    mediaType = format.type.name.lowercase(), // MediaType.VIDEO → "video"
    fileSizeBytes = fileSizeBytes,
)

private const val MAX_TITLE_LENGTH = 500
