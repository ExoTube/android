package com.example.exotube.explore

import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.Recommendation
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.model.VideoPage
import com.example.exotube.domain.repository.InMemorySearchHistory
import com.example.exotube.domain.repository.MAX_RECENT_SEARCHES
import com.example.exotube.domain.repository.OnlineCatalogRepository
import com.example.exotube.domain.repository.RecommendationRepository
import com.example.exotube.domain.repository.SearchSuggestionRepository
import com.example.exotube.domain.repository.rememberQuery
import com.example.exotube.library.artistSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** El menú de predicciones del buscador: qué sale, en qué orden y cuándo se pregunta a YouTube. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchSuggestionsTest {

    // --- Qué se enseña -----------------------------------------------------------------------

    @Test
    fun `sin escribir nada salen las búsquedas recientes`() {
        val list = buildSuggestions("", recent = listOf("soda stereo", "bad bunny"), predictions = listOf("x"))

        assertEquals(listOf(SearchSuggestion("soda stereo", true), SearchSuggestion("bad bunny", true)), list)
    }

    @Test
    fun `escribiendo, primero las recientes que empiezan igual y luego las de YouTube sin repetir`() {
        val list = buildSuggestions(
            query = "Soda",
            recent = listOf("Soda Stereo en vivo", "bad bunny"),
            predictions = listOf("soda stereo", "soda stereo en vivo", "soda stereo zoom"),
        )

        assertEquals(
            listOf(
                SearchSuggestion("Soda Stereo en vivo", isRecent = true),
                SearchSuggestion("soda stereo", isRecent = false),
                SearchSuggestion("soda stereo zoom", isRecent = false),
            ),
            list,
        )
    }

    @Test
    fun `no distingue tildes ni mayúsculas`() {
        val list = buildSuggestions("cancion", recent = listOf("Canción de cuna"), predictions = emptyList())

        assertEquals(listOf(SearchSuggestion("Canción de cuna", isRecent = true)), list)
    }

    @Test
    fun `como mucho ocho líneas`() {
        val list = buildSuggestions("a", recent = emptyList(), predictions = (1..20).map { "a$it" })

        assertEquals(8, list.size)
    }

    @Test
    fun `una búsqueda nueva sube arriba del todo y no se repite`() {
        val recent = rememberQuery(listOf("rock", "Soda Stereo"), "soda stereo")

        assertEquals(listOf("soda stereo", "rock"), recent)
        assertEquals(MAX_RECENT_SEARCHES, (1..30).fold(emptyList<String>()) { acc, i -> rememberQuery(acc, "q$i") }.size)
        assertEquals(listOf("rock"), rememberQuery(listOf("rock"), "   "))
    }

    @Test
    fun `mientras llegan las nuevas, las predicciones anteriores se estrechan con lo escrito`() {
        val old = listOf("soda stereo", "soda stereo zoom", "sodapop")

        assertEquals(listOf("soda stereo", "soda stereo zoom"), predictionsStillValid("soda s", "soda", old))
        assertTrue("se borró: ya no valen", predictionsStillValid("sod", "soda", old).isEmpty())
        assertTrue("otra búsqueda", predictionsStillValid("rock", "soda", old).isEmpty())
    }

    @Test
    fun `una reciente igual a lo escrito no se repite aunque YouTube tarde`() = runTest(dispatcher) {
        val suggestions = SlowSuggestions()
        val viewModel = ExploreViewModel(EmptyCatalog, NoHistory, { false }, suggestions, InMemorySearchHistory(listOf("soda ste")))
        runCurrent()

        viewModel.onQueryChange("soda")
        advanceUntilIdle()
        viewModel.onQueryChange("soda ste")
        runCurrent() // YouTube aún no contestó por "soda ste"

        assertTrue(viewModel.uiState.value.suggestions.none { it.isRecent && it.text == "soda ste" })
    }

    // --- Biblioteca: artistas ----------------------------------------------------------------

    private fun song(artist: String?) = LibraryItem(
        id = artist.hashCode().toLong(), uri = "content://$artist", title = "t", artist = artist,
        type = MediaType.AUDIO, durationMs = 180_000, sizeBytes = 1, dateAddedSeconds = 0,
    )

    @Test
    fun `en la biblioteca se sugieren artistas, primero los que empiezan por lo escrito`() {
        val items = listOf(song("Los Soda"), song("Soda Stereo"), song("Soda Stereo"), song("Shakira"), song(null))

        assertEquals(listOf("Soda Stereo", "Los Soda"), artistSuggestions(items, "soda"))
        assertTrue("con una letra aún no", artistSuggestions(items, "s").isEmpty())
        assertTrue("si ya está escrito entero, no hace falta", artistSuggestions(items, "shakira").isEmpty())
    }

    // --- Cuándo se pregunta a YouTube ---------------------------------------------------------

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Contesta en 300 ms, como YouTube, y apunta qué le preguntaron. */
    private class SlowSuggestions : SearchSuggestionRepository {
        val asked = mutableListOf<String>()
        override suspend fun suggest(query: String): List<String> {
            asked += query
            delay(300)
            return listOf("$query 1", "$query 2")
        }
    }

    private object EmptyCatalog : OnlineCatalogRepository {
        override suspend fun search(query: String) = Result.success(VideoPage(emptyList(), hasMore = false))
        override suspend fun searchMore(query: String) = Result.success(VideoPage(emptyList(), hasMore = false))
        override suspend fun resolveStream(video: OnlineVideo, audioOnly: Boolean, maxHeight: Int) =
            Result.success<StreamSource>(StreamSource.Single(video.url, hasVideo = true))
        override fun forget(video: OnlineVideo) = Unit
    }

    private object NoHistory : RecommendationRepository {
        override suspend fun hasEnoughHistory() = false
        override suspend fun forYou() = Result.success(emptyList<Recommendation>())
    }

    @Test
    fun `quien escribe rápido no provoca una consulta por letra`() = runTest(dispatcher) {
        val suggestions = SlowSuggestions()
        val viewModel = ExploreViewModel(EmptyCatalog, NoHistory, { false }, suggestions, InMemorySearchHistory())
        runCurrent()

        "soda".forEachIndexed { i, _ ->
            viewModel.onQueryChange("soda".substring(0, i + 1))
            advanceTimeBy(50) // teclea más rápido que la espera
        }
        advanceUntilIdle()

        assertEquals(listOf("soda"), suggestions.asked)
        assertEquals(listOf("soda 1", "soda 2"), viewModel.uiState.value.suggestions.map { it.text })
    }

    @Test
    fun `una respuesta vieja no pisa la del texto actual`() = runTest(dispatcher) {
        val suggestions = SlowSuggestions()
        val viewModel = ExploreViewModel(EmptyCatalog, NoHistory, { false }, suggestions, InMemorySearchHistory())
        runCurrent()

        viewModel.onQueryChange("sod")
        advanceTimeBy(200) // ya se preguntó por "sod", pero aún no contestó
        viewModel.onQueryChange("soda")
        advanceUntilIdle()

        assertEquals(listOf("soda 1", "soda 2"), viewModel.uiState.value.suggestions.map { it.text })
    }

    @Test
    fun `al buscar se recuerda la búsqueda, y al borrar el texto sale arriba`() = runTest(dispatcher) {
        val history = InMemorySearchHistory()
        val viewModel = ExploreViewModel(EmptyCatalog, NoHistory, { false }, SlowSuggestions(), history)
        runCurrent()

        viewModel.onSuggestionPicked("soda stereo")
        viewModel.onQueryChange("")
        advanceUntilIdle()

        assertEquals(listOf("soda stereo"), history.recent.value)
        assertEquals(listOf(SearchSuggestion("soda stereo", isRecent = true)), viewModel.uiState.value.suggestions)
    }

    @Test
    fun `sin red no hay predicciones, pero no se rompe nada`() = runTest(dispatcher) {
        val offline = SearchSuggestionRepository { throw java.io.IOException("sin red") }
        val viewModel = ExploreViewModel(EmptyCatalog, NoHistory, { false }, offline, InMemorySearchHistory(listOf("rock")))
        runCurrent()

        viewModel.onQueryChange("ro")
        advanceUntilIdle()

        assertEquals(listOf(SearchSuggestion("rock", isRecent = true)), viewModel.uiState.value.suggestions)
    }
}
