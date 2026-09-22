package com.example.exotube.explore

import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.Recommendation
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.repository.OnlineCatalogRepository
import com.example.exotube.domain.repository.RecommendationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Preparar por adelantado los primeros videos de la lista: que se haga, que no se pase de la
 * raya y, sobre todo, que no estorbe a lo que el usuario toca.
 *
 * El reloj es de mentira (StandardTestDispatcher): los segundos que tarda yt-dlp pasan al
 * instante, pero en el orden correcto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorePrefetchTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val videos = List(5) { i ->
        OnlineVideo(
            id = "v$i",
            url = "https://www.youtube.com/watch?v=v$i",
            title = "Video $i",
            channel = "Canal",
            durationSeconds = 200,
            thumbnailUrl = null,
            viewCount = null,
        )
    }

    /** Un catálogo que tarda [resolveMs] en cada enlace y apunta qué se le pidió y qué se canceló. */
    private inner class FakeCatalog(private val resolveMs: Long = 4_000) : OnlineCatalogRepository {
        val started = mutableListOf<String>()
        val finished = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override suspend fun search(query: String) = Result.success(videos)

        override suspend fun resolveStream(
            video: OnlineVideo,
            audioOnly: Boolean,
            maxHeight: Int,
        ): Result<StreamSource> {
            started += video.id
            try {
                delay(resolveMs)
            } catch (e: CancellationException) {
                cancelled += video.id
                throw e
            }
            finished += video.id
            return Result.success(StreamSource.Single("https://googlevideo/${video.id}", hasVideo = true))
        }

        override fun forget(video: OnlineVideo) = Unit
    }

    /** Sin historial: la pestaña abre en "Música", que es una búsqueda normal. */
    private object NoHistory : RecommendationRepository {
        override suspend fun hasEnoughHistory() = false
        override suspend fun forYou() = Result.success(emptyList<Recommendation>())
    }

    private fun TestScope.openExplore(catalog: FakeCatalog): ExploreViewModel {
        val viewModel = ExploreViewModel(catalog, NoHistory, isMeteredConnection = { true })
        runCurrent() // carga la lista inicial
        return viewModel
    }

    @Test
    fun `prepara los tres primeros de uno en uno`() = runTest(dispatcher) {
        val catalog = FakeCatalog()
        openExplore(catalog)

        advanceTimeBy(ExploreViewModel.PREFETCH_DELAY_MS + 1)
        runCurrent()
        // Nunca dos a la vez: el segundo espera a que acabe el primero.
        assertEquals(listOf("v0"), catalog.started)

        advanceUntilIdle()
        assertEquals(listOf("v0", "v1", "v2"), catalog.finished)
    }

    /** Si toca el que se está preparando, se aprovecha ese trabajo: no se empieza otro igual. */
    @Test
    fun `tocar el video que se prepara no lo pide dos veces`() = runTest(dispatcher) {
        val catalog = FakeCatalog()
        val viewModel = openExplore(catalog)
        advanceTimeBy(ExploreViewModel.PREFETCH_DELAY_MS + 1_000) // v0 va por la mitad

        viewModel.onVideoSelected(videos[0])
        advanceUntilIdle()

        assertEquals(listOf("v0"), catalog.started)
        assertTrue(catalog.cancelled.isEmpty())
        // Y el recorrido se para: lo que viene detrás ya no hace falta.
        assertEquals(listOf("v0"), catalog.finished)
    }

    /** Si toca otro, lo que se preparaba se cancela para que yt-dlp atienda lo que importa. */
    @Test
    fun `tocar otro video cancela lo que se preparaba`() = runTest(dispatcher) {
        val catalog = FakeCatalog()
        val viewModel = openExplore(catalog)
        advanceTimeBy(ExploreViewModel.PREFETCH_DELAY_MS + 1_000)

        viewModel.onVideoSelected(videos[4])
        advanceUntilIdle()

        assertEquals(listOf("v0"), catalog.cancelled)
        assertEquals(listOf("v4"), catalog.finished)
    }

    /** Tocar muy rápido, antes de la pausa inicial: no se llega a preparar nada. */
    @Test
    fun `tocar antes de empezar no prepara nada`() = runTest(dispatcher) {
        val catalog = FakeCatalog()
        val viewModel = openExplore(catalog)

        viewModel.onVideoSelected(videos[1])
        advanceUntilIdle()

        assertEquals(listOf("v1"), catalog.started)
    }

    /** Una búsqueda nueva deja la lista vieja sin preparar: esos videos ya no se van a tocar. */
    @Test
    fun `una busqueda nueva para lo de la lista anterior`() = runTest(dispatcher) {
        val catalog = FakeCatalog()
        val viewModel = openExplore(catalog)
        advanceTimeBy(ExploreViewModel.PREFETCH_DELAY_MS + 1_000)

        viewModel.onQueryChange("soda stereo")
        viewModel.onSearch()
        runCurrent()

        assertEquals(listOf("v0"), catalog.cancelled)
    }
}
