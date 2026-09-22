package com.example.exotube.songedit

import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exotube.ExoTubeApp
import com.example.exotube.R
import com.example.exotube.data.library.ApprovalRequired
import com.example.exotube.data.library.ArtworkCache
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.repository.AudioEditor
import com.example.exotube.domain.repository.MediaFileDeleter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Cosas de un solo uso: no son estado, van por un canal. */
sealed interface SongEditEvent {
    /** Salió bien; [messageRes] dice qué se hizo. */
    data class Done(@StringRes val messageRes: Int) : SongEditEvent

    /**
     * El archivo [uri] ya no existe. Aparte de avisar, hay que sacarlo de la cola del
     * reproductor: si no, al llegarle el turno intentaría abrir un archivo borrado.
     */
    data class Deleted(val uri: String) : SongEditEvent

    data class Failed(@StringRes val messageRes: Int, val cause: String?) : SongEditEvent

    /**
     * Android pide que el usuario autorice modificar este archivo, porque no lo creó ExoTube.
     * [request] es el diálogo del sistema que hay que abrir; si dice que sí, se reintenta.
     */
    data class NeedsPermission(val request: IntentSender) : SongEditEvent
}

/**
 * Los cambios que se hacen sobre un archivo que ya está en el teléfono: la portada y el nombre
 * de una canción, y borrar una canción o un video.
 *
 * Todos comparten lo que tiene miga (avisar de que se está trabajando, y el permiso que Android
 * exige para tocar un archivo de otra app), así que viven en el mismo ViewModel en vez de
 * repetirse en tres.
 *
 * Ninguno tiene pantalla propia: se tocan desde el menú de la canción, se elige la foto o se
 * escribe el nombre, y se aplica. Una pantalla intermedia solo estorbaría.
 */
class SongEditViewModel(
    private val editor: AudioEditor,
    private val deleter: MediaFileDeleter,
    private val artworkCache: ArtworkCache,
) : ViewModel() {

    /** El texto del aviso mientras se trabaja, o null si no hay nada en marcha. */
    private val _workingMessage = MutableStateFlow<Int?>(null)
    val workingMessage: StateFlow<Int?> = _workingMessage.asStateFlow()

    private val _events = Channel<SongEditEvent>(Channel.BUFFERED)
    val events: Flow<SongEditEvent> = _events.receiveAsFlow()

    /** Lo que se estaba haciendo cuando Android pidió permiso, para repetirlo tal cual. */
    private var pending: Edit? = null

    fun changeCover(item: LibraryItem, imageUri: String) = start(Edit.Cover(item, imageUri))

    fun rename(item: LibraryItem, newTitle: String) = start(Edit.Rename(item, newTitle))

    /** Ya confirmado por el usuario: aquí no se pregunta otra vez. */
    fun delete(item: LibraryItem) = start(Edit.Delete(item))

    /** El usuario aceptó el diálogo del sistema: ahora sí se puede escribir. */
    fun retryPending() {
        pending?.let { run(it, isRetry = true) }
    }

    /** El usuario rechazó el diálogo del sistema. */
    fun cancelPending() {
        pending = null
    }

    private fun start(edit: Edit) {
        pending = edit
        run(edit, isRetry = false)
    }

    /**
     * [isRetry]: se repite después de que el usuario diera permiso. Si aun así Android lo pide
     * otra vez, NO se vuelve a enseñar el diálogo: se da por fallido. Si no, un archivo que
     * Android se empeñara en proteger abriría un diálogo detrás de otro sin fin.
     */
    private fun run(edit: Edit, isRetry: Boolean) {
        if (_workingMessage.value != null) return // no dos escrituras a la vez sobre el mismo archivo
        viewModelScope.launch {
            _workingMessage.value = edit.workingRes
            val result = when (edit) {
                is Edit.Cover -> editor.changeCover(edit.item, edit.imageUri)
                is Edit.Rename -> editor.rename(edit.item, edit.newTitle)
                is Edit.Delete -> deleter.delete(edit.item)
            }
            _workingMessage.value = null

            result.fold(
                onSuccess = {
                    // La carátula guardada en memoria es la de antes: hay que olvidarla.
                    if (edit is Edit.Cover) artworkCache.invalidate(edit.item.uri)
                    pending = null
                    if (edit is Edit.Delete) _events.send(SongEditEvent.Deleted(edit.item.uri))
                    _events.send(SongEditEvent.Done(edit.doneRes))
                },
                onFailure = { error ->
                    val permissionRequest = error.permissionRequestOrNull()
                    if (permissionRequest != null && !isRetry) {
                        _events.send(SongEditEvent.NeedsPermission(permissionRequest))
                    } else {
                        pending = null
                        _events.send(SongEditEvent.Failed(edit.failedRes, error.message))
                    }
                },
            )
        }
    }

    /** Las dos operaciones, guardadas enteras para poder repetirlas después del permiso. */
    private sealed interface Edit {
        val item: LibraryItem

        @get:StringRes val workingRes: Int

        @get:StringRes val doneRes: Int

        @get:StringRes val failedRes: Int

        data class Cover(override val item: LibraryItem, val imageUri: String) : Edit {
            override val workingRes = R.string.cover_working
            override val doneRes = R.string.cover_changed
            override val failedRes = R.string.cover_failed
        }

        data class Rename(override val item: LibraryItem, val newTitle: String) : Edit {
            override val workingRes = R.string.rename_working
            override val doneRes = R.string.rename_done
            override val failedRes = R.string.rename_failed
        }

        data class Delete(override val item: LibraryItem) : Edit {
            override val workingRes = R.string.delete_working
            override val doneRes = R.string.delete_done
            override val failedRes = R.string.delete_failed
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                SongEditViewModel(
                    editor = app.container.audioEditor,
                    deleter = app.container.mediaFileDeleter,
                    artworkCache = app.container.artworkCache,
                )
            }
        }
    }
}

/**
 * Distingue "Android quiere que el usuario dé permiso" de un fallo de verdad.
 *
 * En Android 10 y posteriores, escribir en un archivo de otra app no se deniega a secas: lanza
 * una excepción que trae dentro el diálogo que hay que mostrar. Con la música que descarga
 * ExoTube no pasa (los archivos son suyos), pero la biblioteca muestra toda la del teléfono.
 *
 * Al borrar, el diálogo ya viene preparado por quien borra ([ApprovalRequired]).
 */
private fun Throwable.permissionRequestOrNull(): IntentSender? = when {
    this is ApprovalRequired -> request
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> recoverableIntentSender()
    else -> null
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun Throwable.recoverableIntentSender(): IntentSender? =
    (this as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
