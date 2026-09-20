package com.example.exotube.domain.repository

import com.example.exotube.domain.model.LibraryItem
import kotlinx.coroutines.flow.Flow

/** Descargas guardadas en el teléfono. */
interface LibraryRepository {

    /**
     * Emite la lista actual (más recientes primero) y una nueva cada vez que cambia,
     * por ejemplo cuando termina una descarga. Así la pantalla se actualiza sola.
     */
    fun observeDownloads(): Flow<List<LibraryItem>>
}
