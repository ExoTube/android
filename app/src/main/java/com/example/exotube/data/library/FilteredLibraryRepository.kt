package com.example.exotube.data.library

import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.LibraryVisibility
import com.example.exotube.domain.model.visibleWith
import com.example.exotube.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * La biblioteca tal como la quiere ver el usuario: la de [source] sin los audios que esconde el
 * filtro de Ajustes.
 *
 * Es una capa por encima y no un cambio en la consulta a MediaStore: así el filtro se aplica al
 * instante al mover la barra (no hay que volver a leer el disco) y las playlists, que usan la
 * biblioteca completa, no pierden canciones que el usuario metió a propósito.
 */
class FilteredLibraryRepository(
    private val source: LibraryRepository,
    private val visibility: StateFlow<LibraryVisibility>,
) : LibraryRepository {

    override fun observeDownloads(): Flow<List<LibraryItem>> =
        combine(source.observeDownloads(), visibility) { items, current -> items.visibleWith(current) }
}
