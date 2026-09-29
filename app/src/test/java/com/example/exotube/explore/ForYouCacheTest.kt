package com.example.exotube.explore

import com.example.exotube.data.history.CachedRecommendations
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.Recommendation
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.model.VideoPage
import com.example.exotube.domain.repository.OnlineCatalogRepository
import com.example.exotube.domain.repository.RecommendationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** "Para ti" con memoria: lo de la última vez sale al instante y luego se cambia por lo nuevo. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForYouCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun video(id: String) = OnlineVideo(
        id, "https://youtu.be/$id", "Video $id", "Canal", 200, "https://i.ytimg.com/$id.jpg", 1_000, "https://youtube.com/@c",
    )

    private val yesterday = listOf(Recommendation("Soda Stereo", listOf(video("a"), video("b"))))
    private val today = listOf(Recommendation("Rick Astley", listOf(video("c"))))

    /** Recomendaciones que tardan dos segundos, como con internet, o que fallan (sin red). */
    private class SlowSource(private val result: Result<List<Recommendation>>, private val saved: List<Recommendation>? = null) :
        RecommendationRepository {
        override suspend fun hasEnoughHistory() = true
        override suspend fun forYou(): Result<List<Recommendation>> {
            delay(2_000)
            return result
        }
        override suspend fun lastForYou() = saved
    }

    private object EmptyCatalog : OnlineCatalogRepository {
        override suspend fun search(query: String) = Result.success(VideoPage(emptyList(), hasMore = false))
        override suspend fun searchMore(query: String) = Result.success(VideoPage(emptyList(), hasMore = false))
        override suspend fun resolveStream(video: OnlineVideo, audioOnly: Boolean, maxHeight: Int) =
            Result.failure<StreamSource>(MediaError.NoConnection) // el prefetch no importa aquí
        override fun forget(video: OnlineVideo) = Unit
    }

    @Test
    fun `lo guardado se lee igual que se guardó`() = runBlocking {
        val file = temp.newFile("para_ti.json").apply { delete() }
        val cache = CachedRecommendations(SlowSource(Result.success(yesterday)), file)

        assertNull(cache.lastForYou())
        cache.forYou()

        assertEquals(yesterday, cache.lastForYou())
    }

    @Test
    fun `un fallo no borra lo guardado`() = runBlocking {
        val file = temp.newFile("para_ti.json").apply { delete() }
        CachedRecommendations(SlowSource(Result.success(yesterday)), file).forYou()

        val offline = CachedRecommendations(SlowSource(Result.failure(MediaError.NoConnection)), file)
        offline.forYou()

        assertEquals(yesterday, offline.lastForYou())
    }

    @Test
    fun `un archivo roto se ignora sin romper nada`() = runBlocking {
        val file = temp.newFile("para_ti.json").apply { writeText("esto no es json") }
        // El aviso del registro necesita Android: con Log de mentira basta que no lance.
        val cache = CachedRecommendations(SlowSource(Result.success(today)), file)

        assertEquals(null, runCatching { cache.lastForYou() }.getOrNull())
    }

    @Test
    fun `al abrir Explorar sale lo de ayer al momento y luego lo de hoy`() = runTest(dispatcher) {
        val viewModel = ExploreViewModel(EmptyCatalog, SlowSource(Result.success(today), saved = yesterday), { false })

        advanceTimeBy(100)
        assertEquals(ExploreResults.ForYou(yesterday), viewModel.uiState.value.results)

        advanceUntilIdle()
        assertEquals(ExploreResults.ForYou(today), viewModel.uiState.value.results)
    }

    @Test
    fun `sin internet se queda lo de ayer en vez de un error`() = runTest(dispatcher) {
        val viewModel = ExploreViewModel(EmptyCatalog, SlowSource(Result.failure(MediaError.NoConnection), saved = yesterday), { false })

        advanceUntilIdle()

        assertEquals(ExploreResults.ForYou(yesterday), viewModel.uiState.value.results)
    }

    @Test
    fun `sin nada guardado se espera como antes`() = runTest(dispatcher) {
        val viewModel = ExploreViewModel(EmptyCatalog, SlowSource(Result.success(today)), { false })

        advanceTimeBy(100)
        assertEquals(ExploreResults.Loading, viewModel.uiState.value.results)

        advanceUntilIdle()
        assertEquals(ExploreResults.ForYou(today), viewModel.uiState.value.results)
    }
}
