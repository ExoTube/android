package com.example.exotube.trim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exotube.ExoTubeApp
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.repository.AudioEditor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class TrimUiState(
    val startMs: Long = 0,
    val endMs: Long = 0,
    /** Nombre del archivo nuevo. Se propone uno y el usuario lo cambia si quiere. */
    val name: String = "",
    val durationMs: Long = 0,
    val isSaving: Boolean = false,
) {
    val fragmentMs: Long = (endMs - startMs).coerceAtLeast(0)
}

/** Cosas de un solo uso: no son estado, van por un canal. */
sealed interface TrimEvent {
    data object Saved : TrimEvent
    data class Failed(val message: String?) : TrimEvent
}

/**
 * Recortar una canción: elegir el trozo, ponerle nombre y guardarlo aparte.
 * El archivo original no se modifica nunca.
 */
class TrimViewModel(
    private val item: LibraryItem,
    private val editor: AudioEditor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TrimUiState(
            startMs = 0,
            // Se propone un fragmento corto desde el principio, no la canción entera: recortar
            // es quedarse con un trozo, así que hay menos que arrastrar.
            endMs = minOf(DEFAULT_FRAGMENT_MS, item.durationMs),
            name = defaultName(item.title),
            durationMs = item.durationMs,
        ),
    )
    val uiState: StateFlow<TrimUiState> = _uiState.asStateFlow()

    private val _events = Channel<TrimEvent>(Channel.BUFFERED)
    val events: Flow<TrimEvent> = _events.receiveAsFlow()

    /** Mover los extremos, sin dejar que se cruzen ni que el fragmento quede vacío. */
    fun onRangeChange(startMs: Long, endMs: Long) {
        val duration = item.durationMs
        val start = startMs.coerceIn(0, (duration - MIN_FRAGMENT_MS).coerceAtLeast(0))
        val end = endMs.coerceIn(start + MIN_FRAGMENT_MS, duration.coerceAtLeast(start + MIN_FRAGMENT_MS))
        _uiState.value = _uiState.value.copy(startMs = start, endMs = end)
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return // no duplicar el archivo si se toca dos veces
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            val result = editor.trim(item, state.startMs, state.endMs, state.name)
            _uiState.value = _uiState.value.copy(isSaving = false)
            _events.send(
                result.fold(
                    onSuccess = { TrimEvent.Saved },
                    onFailure = { TrimEvent.Failed(it.message) },
                ),
            )
        }
    }

    companion object {
        /** Mínimo medio segundo: por debajo no se oye nada reconocible. */
        const val MIN_FRAGMENT_MS = 500L
        private const val DEFAULT_FRAGMENT_MS = 30_000L

        /** "Sobredosis de TV" → "Sobredosis de TV (recorte)". */
        fun defaultName(title: String): String = "$title (recorte)"

        fun factory(item: LibraryItem): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                TrimViewModel(item, app.container.audioEditor)
            }
        }
    }
}
