package com.example.exotube.download

import androidx.work.Data
import androidx.work.workDataOf
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaFormat
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.Platform

// WorkManager guarda los parámetros en su base de datos, así que solo acepta tipos simples
// (String, Int…). Estas dos funciones convierten nuestro modelo en ese formato y de vuelta.

private const val KEY_URL = "url"
private const val KEY_TITLE = "title"
private const val KEY_PLATFORM = "platform"
private const val KEY_FORMAT_ID = "format_id"
private const val KEY_FORMAT_TYPE = "format_type"
private const val KEY_FORMAT_LABEL = "format_label"
private const val KEY_FORMAT_EXTENSION = "format_extension"
private const val KEY_PLAYLIST_INDEX = "playlist_index"

internal fun DownloadRequest.toWorkData(): Data = workDataOf(
    KEY_URL to url,
    KEY_TITLE to title,
    KEY_PLATFORM to platform.name,
    KEY_FORMAT_ID to format.formatId,
    KEY_FORMAT_TYPE to format.type.name,
    KEY_FORMAT_LABEL to format.label,
    KEY_FORMAT_EXTENSION to format.extension,
    KEY_PLAYLIST_INDEX to (playlistIndex ?: 0),
)

/** null si faltan datos (no debería pasar, pero nunca confíes en datos persistidos). */
internal fun Data.toDownloadRequest(): DownloadRequest? {
    val url = getString(KEY_URL) ?: return null
    val formatId = getString(KEY_FORMAT_ID) ?: return null
    val type = MediaType.entries.firstOrNull { it.name == getString(KEY_FORMAT_TYPE) } ?: return null
    val extension = getString(KEY_FORMAT_EXTENSION) ?: return null

    return DownloadRequest(
        url = url,
        title = getString(KEY_TITLE) ?: url,
        platform = Platform.entries.firstOrNull { it.name == getString(KEY_PLATFORM) } ?: Platform.UNKNOWN,
        format = MediaFormat(
            formatId = formatId,
            type = type,
            label = getString(KEY_FORMAT_LABEL) ?: extension.uppercase(),
            extension = extension,
            sizeBytes = null,
        ),
        playlistIndex = getInt(KEY_PLAYLIST_INDEX, 0).takeIf { it > 0 },
    )
}
