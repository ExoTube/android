package com.example.exotube.data.ytdlp

import com.example.exotube.domain.model.MediaError

/**
 * yt-dlp no devuelve códigos de error: escribe un texto en stderr como
 * "ERROR: [youtube] abc123: Private video. Sign in if you've been granted access…".
 * Aquí lo traducimos a un [MediaError] que la UI sabe explicar.
 */
internal object YtDlpErrorMapper {

    private val networkHints = listOf(
        "urlopen error", "failed to resolve", "name resolution", "no address associated",
        "network is unreachable", "timed out", "connection reset", "connection refused", "getaddrinfo",
    )

    private val noMediaHints = listOf(
        "no video could be found", "there is no video", "no video formats found",
        "no media found", "unsupported url", "requested format is not available",
    )

    private val restrictedHints = listOf(
        "private", "login", "log in", "sign in", "cookies", "confirm your age", "age-restricted",
        "members-only", "unavailable", "not available", "isn't available", "removed",
        "does not exist", "http error 404", "in your country", "restricted",
    )

    fun map(error: Throwable): MediaError {
        if (error is MediaError) return error

        val message = error.message.orEmpty()
        // stderr también trae advertencias ("WARNING: …"): nos quedamos con la línea de error.
        val errorLine = message.lineSequence().lastOrNull { it.startsWith("ERROR") } ?: message
        val text = errorLine.lowercase()

        return when {
            networkHints.any { it in text } -> MediaError.NoConnection
            noMediaHints.any { it in text } -> MediaError.NoMediaFound
            restrictedHints.any { it in text } -> MediaError.PrivateContent
            else -> MediaError.Unknown(error)
        }
    }
}
