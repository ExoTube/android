package com.example.exotube.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Puente entre la UI y [PlaybackService]. Se conecta con un MediaController, que implementa la
 * misma interfaz [Player] que ExoPlayer: la UI no sabe (ni le importa) que el reproductor real
 * vive en un servicio.
 */
class PlayerViewModel(application: Application) : ViewModel() {

    /** null mientras se conecta con el servicio (unos milisegundos). */
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    /** Modo de bucle actual. Lo decide el servicio; aquí solo se refleja. */
    private val _repeatPlan = MutableStateFlow(RepeatPlan.OFF)
    val repeatPlan: StateFlow<RepeatPlan> = _repeatPlan.asStateFlow()

    // Un fallo de reproducción pasa una vez: si fuera estado, al girar la pantalla se volvería
    // a avisar de un error viejo.
    private val _errors = Channel<PlaybackException>(Channel.CONFLATED)
    val errors: Flow<PlaybackException> = _errors.receiveAsFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _errors.trySend(error)
        }
    }

    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)),
    )
        .setListener(object : MediaController.Listener {
            // El servicio publica aquí cuántas repeticiones le quedan, también cuando las agota
            // él solo y apaga el bucle. Por eso el botón siempre muestra la verdad.
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                _repeatPlan.value = RepeatSession.planOf(extras)
            }
        })
        .buildAsync()

    init {
        // La conexión es asíncrona (ListenableFuture): avisamos en el hilo principal cuando está lista.
        controllerFuture.addListener(
            {
                val controller = runCatching { controllerFuture.get() }.getOrNull()
                _player.value = controller
                controller?.addListener(playerListener)
                // El estado que ya tuviera el servicio (la música puede llevar rato sonando).
                controller?.sessionExtras?.let { _repeatPlan.value = RepeatSession.planOf(it) }
            },
            ContextCompat.getMainExecutor(application),
        )
    }

    /**
     * Reproduce [items] como cola, empezando por [startIndex].
     * Con [shuffle], empieza por una canción al azar y sigue en orden aleatorio.
     */
    fun playQueue(items: List<LibraryItem>, startIndex: Int = 0, shuffle: Boolean = false) {
        val player = _player.value ?: return
        if (items.isEmpty()) return
        player.shuffleModeEnabled = shuffle
        val first = if (shuffle) items.indices.random() else startIndex
        player.setMediaItems(items.map { it.toMediaItem() }, first, C.TIME_UNSET)
        player.prepare()
        player.play()
    }

    /**
     * Reproduce un video que sigue en internet, con la dirección que acaba de resolver yt-dlp.
     * Va solo en la cola: cada video en línea hay que resolverlo por separado, así que no tiene
     * sentido preparar una lista entera por adelantado.
     */
    fun playOnline(video: OnlineVideo, stream: StreamSource) {
        val player = _player.value ?: return
        player.shuffleModeEnabled = false
        player.setMediaItem(video.toMediaItem(stream))
        player.prepare()
        player.play()
    }

    /** Pasa al siguiente modo de bucle: sin bucle → 1 vez → 2 veces → siempre → sin bucle. */
    fun cycleRepeatPlan() {
        val controller = _player.value as? MediaController ?: return
        val plan = RepeatPlan.after(_repeatPlan.value)
        // Respuesta inmediata al dedo; el servicio confirmará (o corregirá) por los extras.
        _repeatPlan.value = plan
        controller.sendCustomCommand(
            SessionCommand(RepeatSession.COMMAND_SET_PLAN, Bundle.EMPTY),
            RepeatSession.bundleOfRepeats(plan.repeats),
        )
    }

    override fun onCleared() {
        _player.value?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture) // libera la conexión, no el servicio
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlayerViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!)
            }
        }
    }
}

/**
 * La dirección que se le pide reproducir al servicio. Cuando YouTube sirve la imagen y el sonido
 * por separado, las dos viajan empaquetadas en una sola ([MergedStreamUri]).
 */
private fun StreamSource.playbackUri(): Uri = when (this) {
    is StreamSource.Single -> url.toUri()
    is StreamSource.Separate -> MergedStreamUri.encode(videoUrl, audioUrl).toUri()
}

/**
 * El mediaId es la Uri del archivo: viaja siempre hasta el servicio (que la convierte en la Uri a
 * reproducir) y la UI la usa para saber qué elemento de la lista está sonando.
 */
private fun LibraryItem.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(uri)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            // La carátula sale del propio archivo (ver MediaFileBitmapLoader).
            .setArtworkUri(uri.toUri())
            .setMediaType(if (type == MediaType.VIDEO) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
            .build(),
    )
    .build()

/**
 * Aquí el mediaId (el enlace público de YouTube) y la dirección que se reproduce son DISTINTOS:
 * la segunda caduca. La dirección viaja en requestMetadata porque es lo único que sobrevive al
 * paso de la app al servicio; ver PlaybackService.onAddMediaItems.
 *
 * Cuando YouTube sirve la imagen y el sonido por separado, las dos direcciones van empaquetadas
 * en una sola Uri ([MergedStreamUri]), que el servicio vuelve a separar.
 */
private fun OnlineVideo.toMediaItem(stream: StreamSource): MediaItem {
    val playbackUri = stream.playbackUri()
    return MediaItem.Builder()
        .setMediaId(url)
        .setUri(playbackUri)
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder().setMediaUri(playbackUri).build(),
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(channel)
                .setArtworkUri(thumbnailUrl?.toUri()) // la miniatura ya es una imagen de internet
                .setMediaType(
                    if (stream.hasVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC,
                )
                .build(),
        )
        .build()
}
