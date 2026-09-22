package com.example.exotube.domain.model

/**
 * La calidad con la que se ve un video en línea.
 *
 * Cada opción es un TOPE, no una exigencia: "720p" quiere decir "lo mejor que haya hasta 720p".
 * Si un video solo existe en 480p, se ve en 480p con cualquier opción, en vez de fallar.
 */
enum class VideoQuality(
    /** Alto máximo de la imagen, en píxeles; null en [AUTO], que lo decide según la conexión. */
    val maxHeight: Int?,
) {
    AUTO(null),
    P1080(1080),
    P720(720),
    P480(480),
    P360(360),
    P144(144),
    ;

    /**
     * El tope de verdad que se le pide a YouTube.
     *
     * "Automático" no puede cambiar de calidad a mitad del video como hace la web de YouTube
     * (para eso haría falta un formato troceado que yt-dlp no nos da), así que elige una vez, al
     * empezar, mirando la conexión:
     *  - Con datos móviles, 480p. Carga rápido, se ve bien en una pantalla de teléfono y no se
     *    come la tarifa: un video de 1080p gasta unas cuatro veces más.
     *  - Con wifi, 720p. Sigue arrancando rápido y ya se ve nítido.
     *
     * Quien quiera 1080p lo elige a mano; lo que no tiene sentido es hacer esperar a todo el
     * mundo por una calidad que en un teléfono apenas se nota.
     */
    fun heightFor(isMeteredConnection: Boolean): Int =
        maxHeight ?: if (isMeteredConnection) AUTO_ON_MOBILE_DATA else AUTO_ON_WIFI

    companion object {
        const val AUTO_ON_MOBILE_DATA = 480
        const val AUTO_ON_WIFI = 720
    }
}
