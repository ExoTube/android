package com.example.exotube.library

import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun item(id: Long, type: MediaType, isDownload: Boolean = true) =
        LibraryItem(id, "content://media/$id", "Elemento $id", null, type, 60_000, 1_000, id, isDownload)

    /** Simula MediaStore: emitir un valor nuevo equivale a "terminó una descarga". */
    private val downloads = MutableStateFlow(listOf(item(1, MediaType.AUDIO), item(2, MediaType.VIDEO)))
    private val repository = object : LibraryRepository {
        override fun observeDownloads() = downloads
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `empieza cargando y luego muestra todas las descargas`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository)
        assertTrue(viewModel.uiState.value.isLoading)

        backgroundScope.launch { viewModel.uiState.collect {} } // WhileSubscribed necesita un observador
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.visibleItems.map { it.id })
    }

    @Test
    fun `el filtro deja solo el tipo elegido`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onFilterSelected(LibraryFilter.VIDEO)
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.uiState.value.visibleItems.map { it.id })
        assertEquals(2, viewModel.uiState.value.allItems.size) // el contador de la cabecera no cambia
    }

    @Test
    fun `el filtro Descargas deja fuera la musica del telefono`() = runTest(dispatcher) {
        downloads.value = listOf(
            item(1, MediaType.AUDIO),
            item(2, MediaType.AUDIO, isDownload = false), // MP3 que ya estaba en el teléfono
            item(3, MediaType.VIDEO),
        )
        val viewModel = LibraryViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // "Audio" muestra los dos MP3, vengan de donde vengan.
        viewModel.onFilterSelected(LibraryFilter.AUDIO)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.visibleItems.map { it.id })

        viewModel.onFilterSelected(LibraryFilter.DOWNLOADS)
        advanceUntilIdle()
        assertEquals(listOf(1L, 3L), viewModel.uiState.value.visibleItems.map { it.id })

        val state = viewModel.uiState.value
        assertEquals(2, state.downloadsCount)
        assertEquals(1, state.phoneMusicCount)
    }

    @Test
    fun `una descarga nueva aparece sola, sin recargar`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        downloads.value = listOf(item(3, MediaType.AUDIO)) + downloads.value
        advanceUntilIdle()

        assertEquals(3L, viewModel.uiState.value.visibleItems.first().id)
    }
}
