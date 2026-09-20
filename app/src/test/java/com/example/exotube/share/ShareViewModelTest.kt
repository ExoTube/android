package com.example.exotube.share

import com.example.exotube.data.fake.sampleMediaInfo
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.repository.DownloadScheduler
import com.example.exotube.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Repositorio de prueba: devuelve lo que le digamos y cuenta las llamadas. */
    private class StubRepository(private val result: Result<MediaInfo>) : MediaRepository {
        var calls = 0
        override suspend fun fetchMediaInfo(url: String): Result<MediaInfo> {
            calls++
            return result
        }
    }

    /** En vez de WorkManager, solo recuerda qué se le pidió. */
    private class RecordingScheduler : DownloadScheduler {
        val requests = mutableListOf<DownloadRequest>()
        override fun enqueue(request: DownloadRequest) {
            requests += request
        }
    }

    private val scheduler = RecordingScheduler()

    private fun viewModel(repository: MediaRepository = StubRepository(Result.success(sampleMediaInfo()))) =
        ShareViewModel(repository, scheduler)

    // viewModelScope usa Dispatchers.Main, que no existe en tests JVM: lo reemplazamos.
    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `texto sin enlace muestra InvalidLink sin llamar al repositorio`() {
        val repo = StubRepository(Result.success(sampleMediaInfo()))
        val viewModel = viewModel(repo)

        viewModel.onSharedText("hola, mira esto")

        assertEquals(ShareUiState.Error(MediaError.InvalidLink), viewModel.uiState.value)
        assertEquals(0, repo.calls)
    }

    @Test
    fun `plataforma no soportada muestra UnsupportedPlatform`() {
        val viewModel = viewModel()

        viewModel.onSharedText("https://example.com/video")

        assertEquals(ShareUiState.Error(MediaError.UnsupportedPlatform), viewModel.uiState.value)
    }

    @Test
    fun `enlace valido termina en Ready`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onSharedText("Mira https://youtu.be/abc123?si=x")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ShareUiState.Ready)
    }

    @Test
    fun `video privado muestra PrivateContent`() = runTest(dispatcher) {
        val viewModel = viewModel(StubRepository(Result.failure(MediaError.PrivateContent)))

        viewModel.onSharedText("https://www.instagram.com/reel/abc/")
        advanceUntilIdle()

        assertEquals(ShareUiState.Error(MediaError.PrivateContent), viewModel.uiState.value)
    }

    @Test
    fun `el mismo enlace no se procesa dos veces (rotacion)`() = runTest(dispatcher) {
        val repo = StubRepository(Result.success(sampleMediaInfo()))
        val viewModel = viewModel(repo)

        viewModel.onSharedText("https://youtu.be/abc")
        advanceUntilIdle()
        viewModel.onSharedText("https://youtu.be/abc")
        advanceUntilIdle()

        assertEquals(1, repo.calls)
    }

    @Test
    fun `elegir un formato encola la descarga y cambia a DownloadStarted`() = runTest(dispatcher) {
        val media = sampleMediaInfo().copy(playlistIndex = 2)
        val viewModel = viewModel(StubRepository(Result.success(media)))
        viewModel.onSharedText(media.sourceUrl)
        advanceUntilIdle()

        val format = media.formats.first()
        viewModel.onFormatSelected(format)

        val request = scheduler.requests.single()
        assertEquals(media.sourceUrl, request.url)
        assertEquals(format, request.format)
        assertEquals(2, request.playlistIndex)
        assertEquals(ShareUiState.DownloadStarted(media, format), viewModel.uiState.value)
    }

    @Test
    fun `elegir un formato antes de tener la metadata no hace nada`() {
        val viewModel = viewModel()

        viewModel.onFormatSelected(sampleMediaInfo().formats.first())

        assertTrue(scheduler.requests.isEmpty())
    }
}
