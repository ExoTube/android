package com.example.exotube.library

import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El buscador es lógica pura, sin Android: se puede probar sin emulador. */
class LibrarySearchTest {

    private val cancion = item(title = "Canción de Verano", artist = "Los Ángeles")
    private val video = item(title = "En directo desde Lima", artist = null, type = MediaType.VIDEO)

    @Test
    fun `sin texto escrito aparece todo`() {
        assertTrue(cancion.matchesQuery(""))
        assertTrue(cancion.matchesQuery("   "))
    }

    @Test
    fun `no distingue mayusculas`() {
        assertTrue(cancion.matchesQuery("VERANO"))
        assertTrue(cancion.matchesQuery("verano"))
    }

    /** Lo importante para escribir en el móvil: nadie pone las tildes al buscar. */
    @Test
    fun `no hace falta escribir las tildes`() {
        assertTrue(cancion.matchesQuery("cancion"))
        assertTrue(cancion.matchesQuery("angeles"))
    }

    /** Y al revés: si el archivo viene sin tildes, buscarlas con tilde también debe encontrarlo. */
    @Test
    fun `encuentra un titulo sin tildes buscando con tildes`() {
        assertTrue(item(title = "Cancion sin tilde", artist = null).matchesQuery("canción"))
    }

    @Test
    fun `busca tambien por artista`() {
        assertTrue(cancion.matchesQuery("Los"))
        assertFalse(video.matchesQuery("Los")) // este no tiene artista y no lo lleva en el título
    }

    @Test
    fun `los espacios sobrantes no estorban`() {
        assertTrue(cancion.matchesQuery("  verano  "))
    }

    @Test
    fun `lo que no coincide se queda fuera`() {
        assertFalse(cancion.matchesQuery("reggaeton"))
    }

    /** La búsqueda se aplica junto al filtro: buscar dentro de "Video" no saca canciones. */
    @Test
    fun `el estado combina filtro y busqueda`() {
        val state = LibraryUiState(
            allItems = listOf(cancion, video),
            filter = LibraryFilter.VIDEO,
            query = "lima",
            isLoading = false,
        )

        assertEquals(listOf(video), state.visibleItems)
        assertTrue(state.isSearching)
    }

    private fun item(title: String, artist: String?, type: MediaType = MediaType.AUDIO) = LibraryItem(
        id = 1,
        uri = "content://media/external/audio/media/$title",
        title = title,
        artist = artist,
        type = type,
        durationMs = 180_000,
        sizeBytes = 4_000_000,
        dateAddedSeconds = 1,
    )
}
