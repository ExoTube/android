package com.example.exotube.player

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Puente entre la UI y [PlaybackService]. Se conecta con un MediaController, que implementa la
 * misma interfaz [Player] que ExoPlayer: la UI no sabe (ni le importa) que el reproductor real
 * vive en un servicio.
 */
class PlayerViewModel(application: Application) : ViewModel() {

    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)),
    ).buildAsync()

    /** null mientras se conecta con el servicio (unos milisegundos). */
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    init {
        // La conexión es asíncrona (ListenableFuture): avisamos en el hilo principal cuando está lista.
        controllerFuture.addListener(
            { _player.value = runCatching { controllerFuture.get() }.getOrNull() },
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

    override fun onCleared() {
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
