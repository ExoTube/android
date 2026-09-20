package com.example.exotube.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exotube.ExoTubeApp
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.repository.LibraryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class LibraryFilter { ALL, DOWNLOADS, AUDIO, VIDEO }

data class LibraryUiState(
    val allItems: List<LibraryItem> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val isLoading: Boolean = true,
) {
    /** Lo que se muestra con el filtro actual. También es la cola de reproducción. */
    val visibleItems: List<LibraryItem> = when (filter) {
        LibraryFilter.ALL -> allItems
        LibraryFilter.DOWNLOADS -> allItems.filter { it.isDownload }
        LibraryFilter.AUDIO -> allItems.filter { it.type == MediaType.AUDIO }
        LibraryFilter.VIDEO -> allItems.filter { it.type == MediaType.VIDEO }
    }

    val downloadsCount: Int = allItems.count { it.isDownload }

    /** Música que ya estaba en el teléfono (solo aparece si se concedió el permiso de audio). */
    val phoneMusicCount: Int = allItems.size - downloadsCount
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(repository: LibraryRepository) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter.ALL)

    /**
     * Al conceder el permiso de audio hay que volver a consultar: MediaStore no avisa de los
     * cambios de permisos, solo de los cambios de archivos. flatMapLatest rehace la consulta.
     */
    private val reloads = MutableStateFlow(0)

    /**
     * combine: cada vez que cambian las descargas O el filtro, se recalcula el estado.
     * stateIn + WhileSubscribed(5 s): deja de consultar MediaStore si la pantalla no está visible
     * (pero sobrevive a una rotación, que tarda menos de 5 s).
     */
    val uiState: StateFlow<LibraryUiState> =
        combine(reloads.flatMapLatest { repository.observeDownloads() }, filter) { items, selected ->
            LibraryUiState(allItems = items, filter = selected, isLoading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onFilterSelected(selected: LibraryFilter) {
        filter.value = selected
    }

    /** Se llama tras conceder (o revocar) el permiso de audio. */
    fun reload() {
        reloads.value++
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                LibraryViewModel(app.container.libraryRepository)
            }
        }
    }
}
