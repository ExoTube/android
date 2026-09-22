package com.example.exotube.data.history

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.exotube.domain.model.ListeningSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Apunta qué se escucha, para que "Para ti" tenga de dónde recomendar.
 *
 * Vive pegado al reproductor y no a la pantalla porque la música sigue sonando con la app
 * cerrada: si lo llevara la pantalla, todo lo que se escucha en segundo plano (que es casi todo)
 * no contaría.
 *
 * **Solo cuenta lo que de verdad se escuchó.** Una canción que se salta a los tres segundos no
 * dice nada sobre los gustos de nadie; contarla ensuciaría las recomendaciones con lo que
 * justamente NO gustó. Por eso hace falta pasar de [MIN_LISTENED_MS].
 *
 * El historial se queda en este teléfono: no se sube a ningún sitio y se borra al desinstalar.
 */
class ListeningRecorder(
    private val history: ListeningDao,
    private val scope: CoroutineScope,
) {

    /** Lo que suena ahora y si ya se apuntó, para no contarlo dos veces al pausar y seguir. */
    private var current: MediaItem? = null
    private var recorded = false

    /** La vigilancia mientras suena la música; se para en cuanto deja de sonar. */
    private var watchJob: Job? = null

    fun attachTo(player: Player) {
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    current = mediaItem
                    recorded = false
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) startWatching(player) else stopWatching()
                }
            },
        )
    }

    /**
     * Mira cada pocos segundos cuánto lleva sonando lo que suena.
     *
     * Hace falta mirarlo a ratos y no esperar a que el reproductor avise, porque durante una
     * reproducción normal NO avisa de nada: los avisos saltan al empezar, al pausar o al cambiar
     * de tema, nunca mientras la canción simplemente avanza. Esperándolos, los 30 segundos no
     * llegaban a comprobarse jamás y el historial se quedaba siempre vacío.
     *
     * Solo corre mientras hay música: en silencio no gasta nada.
     */
    private fun startWatching(player: Player) {
        if (watchJob?.isActive == true) return
        // En el hilo principal porque el reproductor solo se puede tocar desde ahí.
        watchJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                if (!recorded && player.currentPosition >= MIN_LISTENED_MS) {
                    recorded = true
                    record(current ?: player.currentMediaItem)
                }
            }
        }
    }

    private fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    private fun record(item: MediaItem?) {
        val seed = item?.toSeed() ?: return
        scope.launch {
            runCatching {
                history.record(seed.mediaKey, seed.title, seed.artist, seed.videoId, System.currentTimeMillis())
            }.onFailure { Log.w(TAG, "No se pudo apuntar la escucha", it) }
        }
    }

    private companion object {
        const val TAG = "ListeningRecorder"

        /** Medio minuto: lo justo para distinguir "me gusta" de "a ver qué es esto". */
        const val MIN_LISTENED_MS = 30_000L

        /** Cada cuánto se mira. Cinco segundos de más o de menos aquí no cambian nada. */
        const val CHECK_INTERVAL_MS = 5_000L
    }
}

/**
 * Lo que hay que recordar de lo que acaba de sonar.
 *
 * Función aparte para poder comprobarla con un test: sacar el identificador de YouTube de una
 * URL tiene más casos raros de los que parece, y de eso depende que las recomendaciones apunten
 * al video correcto.
 */
internal fun MediaItem.toSeed(): ListeningSeed? {
    val key = mediaId.takeIf { it.isNotBlank() } ?: return null
    val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return null
    return ListeningSeed(
        mediaKey = key,
        title = title,
        artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() },
        videoId = youtubeIdOrNull(key),
    )
}

/**
 * El identificador de un video de YouTube dentro de su enlace, o null si esto no es YouTube.
 *
 * Hay que cubrir las tres formas en que llega un enlace, porque la app los recibe de "Compartir"
 * y de la propia búsqueda:
 *  - youtube.com/watch?v=ID
 *  - youtu.be/ID  (el que genera el botón de compartir)
 *  - youtube.com/shorts/ID
 *
 * Lo que NO es YouTube (un archivo del teléfono, otra plataforma) devuelve null y entonces se
 * recomienda buscando por el artista, que sigue funcionando igual de bien.
 */
internal fun youtubeIdOrNull(url: String): String? {
    if (!url.startsWith("http")) return null // una Uri content:// del teléfono
    val id = WATCH_PARAM.find(url)?.groupValues?.get(1)
        ?: SHORT_LINK.find(url)?.groupValues?.get(1)
        ?: SHORTS.find(url)?.groupValues?.get(1)
    return id?.takeIf { it.length == YOUTUBE_ID_LENGTH }
}

private val WATCH_PARAM = Regex("""[?&]v=([A-Za-z0-9_-]{11})""")
private val SHORT_LINK = Regex("""youtu\.be/([A-Za-z0-9_-]{11})""")
private val SHORTS = Regex("""youtube\.com/shorts/([A-Za-z0-9_-]{11})""")

/** Todos los identificadores de YouTube miden esto; sirve para descartar recortes a medias. */
private const val YOUTUBE_ID_LENGTH = 11
