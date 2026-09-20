package com.example.exotube.domain

import java.net.URI

/**
 * Extrae la URL de un texto compartido. Las apps rara vez envían solo el enlace; TikTok,
 * por ejemplo, envía "Mira este video de @usuario https://vm.tiktok.com/ZM.../".
 */
object SharedLinkParser {

    private val urlRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    // Signos que suelen quedar pegados al final del enlace en una frase.
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')

    /** Devuelve la primera URL http(s) válida del texto, o null si no hay ninguna. */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return urlRegex.findAll(text)
            .map { it.value.trimEnd(*trailingPunctuation) }
            .firstOrNull(::hasValidHost)
    }

    private fun hasValidHost(candidate: String): Boolean {
        val host = runCatching { URI(candidate).host }.getOrNull()
        return !host.isNullOrBlank() && '.' in host
    }
}
