package com.example.exotube.explore

import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.Recommendation
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.model.VideoPage
import com.example.exotube.domain.repository.OnlineCatalogRepository
import com.example.exotube.domain.repository.RecommendationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

/**
 * Al llegar al final de la lista se traen más resultados. Antes la búsqueda se quedaba en 20
 * videos y ahí se acababa todo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorePaginationTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun video(i: Int) = OnlineVideo("v$i", "https://youtu.be/v$i", "Video $i", null, null, null, null)

    /** Tres páginas de 20; opcionalmente, la segunda falla una vez. */
    private class PagedCatalog(
        private val pages: List<List<OnlineVideo>>,
        private var failNext: Boolean = false,
    ) : OnlineCatalogRepository {
        var served = 0
        val searchMoreCalls = mutableListOf<String>()

        override suspend fun search(query: String): Result<VideoPage> {
            served = 1
            return Result.success(VideoPage(pages[0], hasMore = pages.size > 1))
        }

        override suspend fun searchMore(query: String): Result<VideoPage> {
            searchMoreCalls += query
            if (failNext) {
                failNext = false
                return Result.failure(MediaError.NoConnection)
            }
            val page = pages.getOrNull(served) ?: return Result.success(VideoPage(emptyList(), false))
            served++
            return Result.success(VideoPage(page, hasMore = served < pages.size))
        }

        override suspend fun resolveStream(video: OnlineVideo, audioOnly: Boolean, maxHeight: Int) =
            Result.success<StreamSource>(StreamSource.Single(video.url, hasVideo = true))

        override fun forget(video: OnlineVideo) = Unit
    }

    private object NoHistory : RecommendationRepository {
        override suspend fun hasEnoughHistory() = false
        override suspend fun forYou() = Result.success(emptyList<Recommendation>())
    }

    private val threePages = listOf((0 until 20).map(::video), (20 until 40).map(::video), (40 until 50).map(::video))

    private fun TestScope.open(catalog: PagedCatalog): ExploreViewModel {
        val viewModel = ExploreViewModel(catalog, NoHistory, isMeteredConnection = { true })
        advanceUntilIdle()
        return viewModel
    }

    private val ExploreViewModel.ready get() = uiState.value.results as ExploreResults.Ready

    @Test
    fun `al llegar al final se anaden los siguientes`() = runTest(dispatcher) {
        val viewModel = open(PagedCatalog(threePages))
        assertEquals(20, viewModel.ready.videos.size)
        assertTrue(viewModel.ready.hasMore)

        viewModel.onLoadMore()
        advanceUntilIdle()

        assertEquals(40, viewModel.ready.videos.size)
        assertEquals("v20", viewModel.ready.videos[20].id)
    }

    @Test
    fun `al acabarse los resultados deja de pedir`() = runTest(dispatcher) {
        val catalog = PagedCatalog(threePages)
        val viewModel = open(catalog)

        repeat(5) {
            viewModel.onLoadMore()
            advanceUntilIdle()
        }

        assertEquals(50, viewModel.ready.videos.size)
        assertFalse(viewModel.ready.hasMore)
        assertEquals(2, catalog.searchMoreCalls.size)
    }

    /** YouTube a veces repite un video en la página siguiente; la lista no admite dos iguales. */
    @Test
    fun `los repetidos no se duplican`() = runTest(dispatcher) {
        val withRepeat = listOf((0 until 20).map(::video), listOf(video(19), video(20)))
        val viewModel = open(PagedCatalog(withRepeat))

        viewModel.onLoadMore()
        advanceUntilIdle()

        assertEquals(21, viewModel.ready.videos.size)
    }

    /** Si falla, se enseña "Reintentar" y no se vuelve a pedir sola en bucle al mover la lista. */
    @Test
    fun `tras un fallo solo se reintenta si el usuario lo pide`() = runTest(dispatcher) {
        val catalog = PagedCatalog(threePages, failNext = true)
        val viewModel = open(catalog)

        viewModel.onLoadMore()
        advanceUntilIdle()
        assertTrue(viewModel.ready.loadMoreFailed)

        viewModel.onLoadMore() // la lista se mueve: no cuenta
        advanceUntilIdle()
        assertEquals(1, catalog.searchMoreCalls.size)

        viewModel.onLoadMore(userAsked = true) // toca "Reintentar"
        advanceUntilIdle()
        assertEquals(40, viewModel.ready.videos.size)
        assertFalse(viewModel.ready.loadMoreFailed)
    }

    /** Dos avisos seguidos de "llegué al final" no piden dos veces la misma página. */
    @Test
    fun `no se piden dos tandas a la vez`() = runTest(dispatcher) {
        val catalog = PagedCatalog(threePages)
        val viewModel = open(catalog)

        viewModel.onLoadMore()
        viewModel.onLoadMore()
        advanceUntilIdle()

        assertEquals(1, catalog.searchMoreCalls.size)
    }
}
