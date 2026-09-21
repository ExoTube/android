package com.example.exotube.domain.model

/**
 * Un video que todavía está en internet: aún no se ha descargado nada.
 * Es lo que se ve en la pestaña Explorar.
 */
data class OnlineVideo(
    /** Identificador en la plataforma; sirve de clave estable en las listas. */
    val id: String,
    /** Enlace público, el mismo que se compartiría desde la app de origen. */
    val url: String,
    val title: String,
    /** Canal o cuenta que lo publicó. */
    val channel: String?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    val viewCount: Long?,
)

/**
 * Dirección directa del archivo que se está reproduciendo en línea.
 *
 * No es un enlace para compartir: es la dirección interna del servidor, va firmada y **caduca**
 * en unas horas. Por eso no se guarda en ningún sitio; se pide justo antes de reproducir.
 */
data class StreamSource(
    val url: String,
    /** false cuando solo se trajo el audio (modo ahorro de datos). */
    val hasVideo: Boolean,
)
