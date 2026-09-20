package com.example.exotube.domain.model

import java.net.URI

/** Plataformas soportadas, detectadas por el dominio de la URL. */
enum class Platform(val displayName: String, private val domains: Set<String>) {
    YOUTUBE("YouTube", setOf("youtube.com", "youtu.be")),
    TIKTOK("TikTok", setOf("tiktok.com")),
    INSTAGRAM("Instagram", setOf("instagram.com", "instagr.am")),
    X("X", setOf("x.com", "twitter.com")),
    FACEBOOK("Facebook", setOf("facebook.com", "fb.watch", "fb.com")),
    UNKNOWN("Web", emptySet());

    companion object {
        /** Acepta subdominios: m.youtube.com, vm.tiktok.com, music.youtube.com… */
        fun fromUrl(url: String): Platform {
            val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return UNKNOWN
            return entries.firstOrNull { platform ->
                platform.domains.any { domain -> host == domain || host.endsWith(".$domain") }
            } ?: UNKNOWN
        }
    }
}
