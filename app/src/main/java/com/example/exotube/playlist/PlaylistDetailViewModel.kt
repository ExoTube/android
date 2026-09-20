package com.example.exotube.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.exotube.ExoTubeApp
import com.example.exotube.domain.model.PlaylistDetail
import com.example.exotube.domain.repository.PlaylistRepository
import com.example.exotube.ui.navigation.PlaylistDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    data class Ready(val playlist: PlaylistDetail) : PlaylistDetailUiState

    /** La playlist ya no existe (el usuario la eliminó): la pantalla vuelve atrás. */
    data object Deleted : PlaylistDetailUiState
}

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val repository: PlaylistRepository,
) : ViewModel() {

    val uiState: StateFlow<PlaylistDetailUiState> = repository.observePlaylist(playlistId)
        .map { detail -> detail?.let { PlaylistDetailUiState.Ready(it) } ?: PlaylistDetailUiState.Deleted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState.Loading)

    fun rename(name: String) {
        viewModelScope.launch { repository.rename(playlistId, name) }
    }

    /** null = quitar la foto (vuelve la carátula de una canción al azar). */
    fun setCover(coverImageUri: String?) {
        viewModelScope.launch { repository.setCover(playlistId, coverImageUri) }
    }

    fun delete() {
        viewModelScope.launch { repository.delete(playlistId) }
    }

    fun remove(mediaUri: String) {
        viewModelScope.launch { repository.remove(playlistId, mediaUri) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                // Navigation guarda los argumentos de la ruta en el SavedStateHandle del ViewModel.
                val route = createSavedStateHandle().toRoute<PlaylistDestination>()
                PlaylistDetailViewModel(route.playlistId, app.container.playlistRepository)
            }
        }
    }
}
