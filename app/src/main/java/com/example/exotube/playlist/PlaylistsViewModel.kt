package com.example.exotube.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exotube.ExoTubeApp
import com.example.exotube.domain.model.Playlist
import com.example.exotube.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Lista de playlists y acciones que se usan desde varias pantallas (la pestaña Playlists y la
 * hoja "Añadir a playlist" de la Biblioteca). Por eso vive a nivel de Activity y se comparte.
 */
class PlaylistsViewModel(private val repository: PlaylistRepository) : ViewModel() {

    val uiState: StateFlow<PlaylistsUiState> = repository.observePlaylists()
        .map { PlaylistsUiState(playlists = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistsUiState())

    fun playlistIdsContaining(mediaUri: String): Flow<Set<Long>> =
        repository.observePlaylistIdsContaining(mediaUri)

    /**
     * Crea una playlist, con la foto elegida (o sin foto) y, si se indica, le añade una canción
     * (cuando se crea desde "Añadir a playlist").
     */
    fun create(name: String, coverImageUri: String? = null, addingMediaUri: String? = null) {
        viewModelScope.launch {
            val id = repository.create(name, coverImageUri)
            addingMediaUri?.let { repository.add(id, it) }
        }
    }

    fun setMembership(playlistId: Long, mediaUri: String, included: Boolean) {
        viewModelScope.launch {
            if (included) repository.add(playlistId, mediaUri) else repository.remove(playlistId, mediaUri)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                PlaylistsViewModel(app.container.playlistRepository)
            }
        }
    }
}
