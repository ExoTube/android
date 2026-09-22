package com.example.exotube.data.ytdlp

import com.example.exotube.domain.model.StreamSource

/**
 * Guarda las direcciones ya resueltas para no volver a preguntarle a YouTube.
 *
 * Resolver un video cuesta unos segundos porque hay que arrancar yt-dlp; pero la dirección que
 * devuelve sirve durante horas. Si el usuario vuelve a la misma canción, con esto suena al
 * instante en vez de esperar otra vez.
 *
 * La fecha de caducidad no la inventamos: viene escrita dentro del propio enlace de YouTube.
 */
internal class StreamUrlCache(
    /** Inyectable para poder probar el paso del tiempo sin esperar de verdad. */
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {

    private data class Entry(val source: StreamSource, val validUntilSeconds: Long)

    private val entries = mutableMapOf<String, Entry>()

    /** null si no está guardado o si ya caducó (en ese caso se olvida). */
    @Synchronized
    fun get(key: String): StreamSource? {
        val entry = entries[key] ?: return null
        if (nowSeconds() >= entry.validUntilSeconds) {
            entries.remove(key)
            return null
        }
        return entry.source
    }

    @Synchronized
    fun put(key: String, source: StreamSource) {
        entries[key] = Entry(source, validUntil(source))
    }

    /** Olvida todo lo guardado cuya clave empiece por [prefix] (un video en todas sus calidades). */
    @Synchronized
    fun removeStartingWith(prefix: String) {
        entries.keys.removeAll { it.startsWith(prefix) }
    }

    /**
     * Cuándo deja de servir: la más temprana de las caducidades de sus direcciones, menos un
     * margen para no empezar a reproducir justo cuando expira.
     */
    private fun validUntil(source: StreamSource): Long {
        val expiries = source.urls.mapNotNull(::expiresAtSeconds)
        if (expiries.isEmpty()) return nowSeconds() + UNKNOWN_EXPIRY_SECONDS
        return expiries.min() - SAFETY_MARGIN_SECONDS
    }

    private companion object {
        /**
         * YouTube escribe la caducidad en el enlace de dos maneras según el formato:
         * "...?expire=1789999999&..." o ".../expire/1789999999/...". Los segundos son desde 1970.
         */
        val EXPIRE = Regex("""(?:[?&]expire=|/expire/)(\d{10,})""")

        /** Si no encontramos la caducidad, guardamos poco rato: mejor corto que equivocado. */
        const val UNKNOWN_EXPIRY_SECONDS = 5L * 60
        const val SAFETY_MARGIN_SECONDS = 60L

        fun expiresAtSeconds(url: String): Long? =
            EXPIRE.find(url)?.groupValues?.get(1)?.toLongOrNull()
    }
}
