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
        val text = message.lowercase()

        // Los fallos de red se reconocen en cualquier parte del texto: esas frases no aparecen
        // por casualidad.
        if (networkHints.any { it in text }) return MediaError.NoConnection

        // Para lo demás solo vale la línea "ERROR:", que es donde yt-dlp explica qué pasa con el
        // enlace. Si no hay ninguna, lo que falló fue el propio motor (un Traceback de Python), y
        // buscar pistas en ese texto engaña: entre las rutas de sus archivos está "cookies.py",
        // que nos haría decirle al usuario que el video es privado.
        val errorLine = message.lineSequence().lastOrNull { it.startsWith("ERROR") }
            ?.lowercase()
            ?: return MediaError.Unknown(error)

        return when {
            noMediaHints.any { it in errorLine } -> MediaError.NoMediaFound
            restrictedHints.any { it in errorLine } -> MediaError.PrivateContent
            else -> MediaError.Unknown(error)
        }
    }
}
