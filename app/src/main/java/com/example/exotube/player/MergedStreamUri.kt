package com.example.exotube.player

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Empaqueta en UNA sola dirección las dos que tiene un video en línea (imagen y sonido).
 *
 * ¿Por qué hace falta esto? La app y [PlaybackService] son dos mundos separados para Android, y
 * de todo lo que lleva un MediaItem solo unos pocos campos sobreviven al viaje entre ellos. Uno
 * es la Uri; los objetos que inventemos nosotros, no. Así que las dos direcciones tienen que
 * caber dentro de una, y al otro lado se vuelven a separar.
 *
 * El resultado tiene esta forma (con las direcciones escapadas):
 *
 *     exotube://merge?v=https%3A%2F%2F...&a=https%3A%2F%2F...
 *
 * Trabaja con texto y no con android.net.Uri para que se pueda probar con un test normal, sin
 * emulador. Quien la use la convierte con `toUri()`, que es una línea.
 */
internal object MergedStreamUri {

    private const val PREFIX = "exotube://merge?"
    private const val PARAM_VIDEO = "v"
    private const val PARAM_AUDIO = "a"
    private const val CHARSET = "UTF-8"

    fun encode(videoUrl: String, audioUrl: String): String =
        PREFIX + "$PARAM_VIDEO=${escape(videoUrl)}&$PARAM_AUDIO=${escape(audioUrl)}"

    /** Vuelve a separar las dos direcciones; null si [uri] es una dirección normal. */
    fun decode(uri: String): Pair<String, String>? {
        if (!uri.startsWith(PREFIX)) return null
        val parameters = uri.removePrefix(PREFIX)
            .split('&')
            .mapNotNull { part ->
                val name = part.substringBefore('=', missingDelimiterValue = "")
                val value = part.substringAfter('=', missingDelimiterValue = "")
                if (name.isEmpty() || value.isEmpty()) null else name to unescape(value)
            }
            .toMap()
        val video = parameters[PARAM_VIDEO] ?: return null
        val audio = parameters[PARAM_AUDIO] ?: return null
        return video to audio
    }

    // Las direcciones de YouTube llevan sus propios "?" y "&": sin escaparlas, romperían la nuestra.
    private fun escape(value: String): String = URLEncoder.encode(value, CHARSET)

    private fun unescape(value: String): String = URLDecoder.decode(value, CHARSET)
}
