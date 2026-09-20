package com.example.exotube.ui

import androidx.annotation.StringRes
import com.example.exotube.R
import com.example.exotube.domain.model.MediaError

/**
 * Traduce el error de dominio a un texto de strings.xml (la capa de dominio no conoce R).
 * Lo usan el Bottom Sheet y las notificaciones de descarga.
 */
@StringRes
fun MediaError.messageRes(): Int = when (this) {
    MediaError.InvalidLink -> R.string.error_invalid_link
    MediaError.UnsupportedPlatform -> R.string.error_unsupported_platform
    MediaError.PrivateContent -> R.string.error_private_content
    MediaError.NoMediaFound -> R.string.error_no_media_found
    MediaError.NoConnection -> R.string.error_no_connection
    is MediaError.Unknown -> R.string.error_unknown
}
