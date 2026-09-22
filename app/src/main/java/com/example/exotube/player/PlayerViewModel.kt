package com.example.exotube.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import com.example.exotube.ExoTubeApp
import com.example.exotube.R
import com.example.exotube.data.ytdlp.EngineUpdateWorker
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.model.VideoQuality
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lo que hay que recordar del video en línea que está sonando.
 *
 * El reproductor solo guarda la dirección que le dimos, y esa dirección caduca y va atada a una
 * calidad concreta. Para cambiar de calidad, o para pedir una dirección nueva cuando la vieja
 * falla, hace falta saber de qué video se trataba: eso es esto.
 */
data class OnlinePlayback(
    val video: OnlineVideo,
    /** Modo ahorro de datos: solo sonido, así que no hay calidad de imagen que elegir. */
    val audioOnly: Boolean,
    /** Cada video empieza en automático; el usuario la cambia desde el reproductor. */
    val quality: VideoQuality = VideoQuality.AUTO,
    /** true mientras se le pide a YouTube la dirección nueva (otra calidad, o recuperar un fallo). */
    val isLoadingStream: Boolean = false,
)

/**
 * Puente entre la UI y [PlaybackService]. Se conecta con un MediaController, que implementa la
 * misma interfaz [Player] que ExoPlayer: la UI no sabe (ni le importa) que el reproductor real
 * vive en un servicio.
 */
class PlayerViewModel(private val application: Application) : ViewModel() {

    private val container get() = (application as ExoTubeApp).container

    /** null mientras se conecta con el servicio (unos milisegundos). */
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    /** Modo de bucle actual. Lo decide el servicio; aquí solo se refleja. */
    private val _repeatPlan = MutableStateFlow(RepeatPlan.OFF)
    val repeatPlan: StateFlow<RepeatPlan> = _repeatPlan.asStateFlow()

    /** El video en línea que suena, o null si lo que suena es un archivo del teléfono. */
    private val _online = MutableStateFlow<OnlinePlayback?>(null)
    val online: StateFlow<OnlinePlayback?> = _online.asStateFlow()

    /** Pedir una dirección nueva a YouTube: solo una a la vez, la última que se pidió. */
    private var streamJob: Job? = null

    /** Cuándo se intentó recuperar un fallo por última vez; null si aún no hizo falta. */
    private var lastRecoveryAtMs: Long? = null

    // Un fallo de reproducción pasa una vez: si fuera estado, al girar la pantalla se volvería
    // a avisar de un error viejo. Lleva el texto que hay que enseñar.
    private val _errors = Channel<Int>(Channel.CONFLATED)
    val errors: Flow<Int> = _errors.receiveAsFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (!recoverOnlineStream()) _errors.trySend(R.string.player_error)
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
        forgetOnline()
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
    fun playOnline(video: OnlineVideo, stream: StreamSource, audioOnly: Boolean) {
        val player = _player.value ?: return
        forgetOnline()
        _online.value = OnlinePlayback(video, audioOnly)
        player.shuffleModeEnabled = false
        player.setMediaItem(video.toMediaItem(stream))
        player.prepare()
        player.play()
    }

    /**
     * Cambia la calidad del video en línea que suena, sin cortarlo.
     *
     * Mientras YouTube responde, el video sigue viéndose en la calidad de antes; solo cuando la
     * dirección nueva está lista se cambia, en el mismo segundo en que iba. Si falla, se queda
     * como estaba y se avisa: mejor eso que dejar la pantalla en negro.
     */
    fun changeQuality(quality: VideoQuality) {
        val online = _online.value ?: return
        if (online.audioOnly || quality == online.quality) return
        _online.value = online.copy(quality = quality, isLoadingStream = true)
        loadStream(online.video, audioOnly = false, quality) {
            _online.update { it?.copy(quality = online.quality) }
            _errors.trySend(R.string.quality_change_failed)
        }
    }

    /**
     * Intenta arreglar solo un fallo de un video en línea, con una dirección recién pedida.
     *
     * Casi siempre que un video en línea falla es porque su dirección ya no sirve (caducó, o
     * YouTube la rechazó). Antes se guardaba y se reutilizaba al volver a tocar el video, así
     * que el fallo se repetía para siempre. Ahora se olvida y se pide otra, sin que el usuario
     * tenga que hacer nada.
     *
     * Como mucho una vez por minuto: si la dirección nueva también falla, el problema es otro y
     * reintentar sin parar solo gastaría batería. En ese caso se avisa y, por si la culpa es de
     * un yt-dlp desactualizado (YouTube cambia a menudo), se le pide que se actualice ya.
     *
     * @return true si se está recuperando; false si no era un video en línea o ya se intentó.
     */
    private fun recoverOnlineStream(): Boolean {
        val online = _online.value ?: return false
        val player = _player.value ?: return false
        if (player.currentMediaItem?.mediaId != online.video.url) return false

        container.onlineCatalog.forget(online.video)
        val now = SystemClock.elapsedRealtime()
        val last = lastRecoveryAtMs
        if (last != null && now - last < RECOVERY_COOLDOWN_MS) {
            EngineUpdateWorker.runNow(application)
            return false
        }
        lastRecoveryAtMs = now

        _online.value = online.copy(isLoadingStream = true)
        loadStream(online.video, online.audioOnly, online.quality) {
            _errors.trySend(R.string.player_error)
        }
        return true
    }

    /** Pide a YouTube una dirección para [video] y, cuando llega, la pone en el reproductor. */
    private fun loadStream(
        video: OnlineVideo,
        audioOnly: Boolean,
        quality: VideoQuality,
        onFailure: (Throwable) -> Unit,
    ) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            container.onlineCatalog
                .resolveStream(video, audioOnly, quality.heightFor(container.connection.isMetered()))
                .onSuccess { swapStream(video, it) }
                .onFailure(onFailure)
            _online.update { it?.copy(isLoadingStream = false) }
        }
    }

    /**
     * Cambia la dirección de lo que suena por [stream], en el mismo punto y sin pausar si estaba
     * sonando. No hace nada si mientras tanto el usuario ya se fue a otra cosa.
     */
    private fun swapStream(video: OnlineVideo, stream: StreamSource) {
        val player = _player.value ?: return
        if (player.currentMediaItem?.mediaId != video.url) return
        val position = player.currentPosition
        val keepPlaying = player.playWhenReady
        player.setMediaItem(video.toMediaItem(stream), position)
        player.prepare()
        player.playWhenReady = keepPlaying
    }

    private fun forgetOnline() {
        streamJob?.cancel()
        _online.value = null
        lastRecoveryAtMs = null
    }

    /**
     * Saca de la cola todo lo que apunte a [uri], que acaba de borrarse del teléfono. Si era lo
     * que sonaba, el reproductor pasa solo al siguiente; si no, nadie nota nada.
     */
    fun removeFromQueue(uri: String) {
        val player = _player.value ?: return
        // De atrás hacia delante: al quitar uno, los índices de los anteriores no se mueven.
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(index).mediaId == uri) player.removeMediaItem(index)
        }
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
        /** Entre dos intentos de recuperar un video en línea que falla. */
        private const val RECOVERY_COOLDOWN_MS = 60_000L

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
