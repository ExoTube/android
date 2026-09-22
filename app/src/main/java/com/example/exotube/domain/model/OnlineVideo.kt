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
    /**
     * Enlace al canal que lo publicó, para poder abrirlo. null si la búsqueda no lo trajo: en
     * ese caso se averigua a partir del propio video al tocar el canal.
     */
    val channelUrl: String? = null,
)

/** Un canal de YouTube: lo que se ve arriba de su página. */
data class OnlineChannel(
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    /** null si el canal oculta cuántos suscriptores tiene. */
    val subscriberCount: Long?,
    val isVerified: Boolean,
)

/**
 * Una página de videos: resultados de una búsqueda o los videos de un canal. [hasMore] dice si
 * merece la pena pedir la siguiente: así la lista sabe si tiene que seguir cargando al final.
 */
data class VideoPage(val videos: List<OnlineVideo>, val hasMore: Boolean)

/**
 * Direcciones con las que reproducir algo que sigue en internet.
 *
 * No son enlaces para compartir: son las direcciones internas del servidor, van firmadas y
 * **caducan** en unas horas. Por eso se piden justo antes de reproducir.
 *
 * YouTube ya casi no ofrece archivos que traigan imagen y sonido juntos (y los que quedan son de
 * 360p), así que lo normal es recibir dos direcciones separadas y que el reproductor las junte.
 * A cambio, se puede ver en 1080p.
 */
sealed interface StreamSource {

    /** false cuando solo se trajo el sonido (modo ahorro de datos). */
    val hasVideo: Boolean

    /** Todas las direcciones que hay que descargar para reproducir esto. */
    val urls: List<String>

    /** Un solo archivo con todo lo que se va a reproducir. */
    data class Single(val url: String, override val hasVideo: Boolean) : StreamSource {
        override val urls: List<String> get() = listOf(url)
    }

    /** Imagen y sonido por separado; el reproductor los reproduce a la vez. */
    data class Separate(val videoUrl: String, val audioUrl: String) : StreamSource {
        override val hasVideo: Boolean get() = true
        override val urls: List<String> get() = listOf(videoUrl, audioUrl)
    }
}
