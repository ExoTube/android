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
    /** Lo que el usuario ha escrito en el buscador. */
    val query: String = "",
    val isLoading: Boolean = true,
) {
    /** Lo que se muestra con el filtro y la búsqueda actuales. También es la cola de reproducción. */
    val visibleItems: List<LibraryItem> = allItems.filter { it.matches(filter) && it.matchesQuery(query) }

    val downloadsCount: Int = allItems.count { it.isDownload }

    /** Música que ya estaba en el teléfono (solo aparece si se concedió el permiso de audio). */
    val phoneMusicCount: Int = allItems.size - downloadsCount

    val isSearching: Boolean = query.isNotBlank()
}

private fun LibraryItem.matches(filter: LibraryFilter): Boolean = when (filter) {
    LibraryFilter.ALL -> true
    LibraryFilter.DOWNLOADS -> isDownload
    LibraryFilter.AUDIO -> type == MediaType.AUDIO
    LibraryFilter.VIDEO -> type == MediaType.VIDEO
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(repository: LibraryRepository) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val query = MutableStateFlow("")

    /**
     * Al conceder el permiso de audio hay que volver a consultar: MediaStore no avisa de los
     * cambios de permisos, solo de los cambios de archivos. flatMapLatest rehace la consulta.
     */
    private val reloads = MutableStateFlow(0)

    /**
     * combine: cada vez que cambian las descargas, el filtro O la búsqueda, se recalcula el estado.
     * El filtrado es en memoria, no en MediaStore: la lista ya está cargada, así que escribir en el
     * buscador no toca el disco y responde al instante.
     *
     * stateIn + WhileSubscribed(5 s): deja de consultar MediaStore si la pantalla no está visible
     * (pero sobrevive a una rotación, que tarda menos de 5 s).
     */
    val uiState: StateFlow<LibraryUiState> =
        combine(
            reloads.flatMapLatest { repository.observeDownloads() },
            filter,
            query,
        ) { items, selected, text ->
            LibraryUiState(allItems = items, filter = selected, query = text, isLoading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onFilterSelected(selected: LibraryFilter) {
        filter.value = selected
    }

    fun onQueryChange(text: String) {
        query.value = text
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
