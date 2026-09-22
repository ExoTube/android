package com.example.exotube.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exotube.ExoTubeApp
import com.example.exotube.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AlbumsUiState(
    val albums: List<Album> = emptyList(),
    val grouping: AlbumGrouping = AlbumGrouping.BY_ALBUM,
    val isLoading: Boolean = true,
) {
    /** Cuántas canciones hay repartidas, para el subtítulo de la pantalla. */
    val songCount: Int = albums.sumOf { it.items.size }
}

/**
 * Los álbumes salen de la MISMA lista que la Biblioteca: no hay una segunda consulta ni una tabla
 * aparte. Se agrupan en memoria, así que al descargar una canción aparece sola en su álbum.
 */
class AlbumsViewModel(repository: LibraryRepository) : ViewModel() {

    private val grouping = MutableStateFlow(AlbumGrouping.BY_ALBUM)

    val uiState: StateFlow<AlbumsUiState> =
        combine(repository.observeDownloads(), grouping) { items, selected ->
            AlbumsUiState(
                albums = buildAlbums(items, selected),
                grouping = selected,
                isLoading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumsUiState())

    fun onGroupingSelected(selected: AlbumGrouping) {
        grouping.value = selected
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                AlbumsViewModel(app.container.libraryRepository)
            }
        }
    }
}
