package com.example.exotube.share

import com.example.exotube.data.fake.sampleMediaInfo
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.repository.DownloadScheduler
import com.example.exotube.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

    /** Vista previa en 1 s y análisis completo en 5 s, como TikTok en un teléfono. */
    private class SlowRepository(
        private val preview: MediaInfo?,
        private val full: Result<MediaInfo>,
    ) : MediaRepository {
        override suspend fun previewMediaInfo(url: String): MediaInfo? {
            delay(1_000)
            return preview
        }

        override suspend fun fetchMediaInfo(url: String): Result<MediaInfo> {
            delay(5_000)
            return full
        }
    }

    private val tiktokPreview = sampleMediaInfo("https://www.tiktok.com/@a/video/1").copy(title = "Vista previa")
    private val tiktokFull = sampleMediaInfo("https://www.tiktok.com/@a/video/1").copy(title = "Completo")

    @Test
    fun `la vista previa sale primero y luego la cambia el análisis completo`() = runTest(dispatcher) {
        val viewModel = viewModel(SlowRepository(tiktokPreview, Result.success(tiktokFull)))
        viewModel.onSharedText(tiktokPreview.sourceUrl)

        advanceTimeBy(1_500)
        assertEquals(ShareUiState.Ready(tiktokPreview, isRefining = true), viewModel.uiState.value)

        advanceUntilIdle()
        assertEquals(ShareUiState.Ready(tiktokFull), viewModel.uiState.value)
    }

    @Test
    fun `se puede descargar desde la vista previa sin esperar`() = runTest(dispatcher) {
        val viewModel = viewModel(SlowRepository(tiktokPreview, Result.success(tiktokFull)))
        viewModel.onSharedText(tiktokPreview.sourceUrl)
        advanceTimeBy(1_500)

        val format = tiktokPreview.formats.first()
        viewModel.onFormatSelected(format)
        advanceUntilIdle()

        assertEquals(format, scheduler.requests.single().format)
        // Al llegar el análisis completo, la hoja no vuelve a abrirse: la descarga ya empezó.
        assertEquals(ShareUiState.DownloadStarted(tiktokPreview, format), viewModel.uiState.value)
    }

    @Test
    fun `si el análisis completo falla, se dice aunque hubiera vista previa`() = runTest(dispatcher) {
        val viewModel = viewModel(SlowRepository(tiktokPreview, Result.failure(MediaError.PrivateContent)))
        viewModel.onSharedText(tiktokPreview.sourceUrl)

        advanceUntilIdle()

        assertEquals(ShareUiState.Error(MediaError.PrivateContent), viewModel.uiState.value)
    }

    @Test
    fun `sin vista previa se espera al análisis completo, como antes`() = runTest(dispatcher) {
        val viewModel = viewModel(SlowRepository(preview = null, full = Result.success(tiktokFull)))
        viewModel.onSharedText(tiktokFull.sourceUrl)

        advanceTimeBy(1_500)
        assertEquals(ShareUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()
        assertEquals(ShareUiState.Ready(tiktokFull), viewModel.uiState.value)
    }

    @Test
    fun `los pesos que estimó la vista previa se quedan si yt-dlp no los sabe`() {
        val preview = tiktokPreview.copy(formats = tiktokPreview.formats.map { it.copy(sizeBytes = 1_000) })
        val full = tiktokFull.copy(formats = tiktokFull.formats.mapIndexed { i, f -> f.copy(sizeBytes = if (i == 0) null else 5L) })

        val merged = full.withSizesFrom(preview)

        assertEquals(1_000L, merged.formats[0].sizeBytes) // yt-dlp no lo sabía: el estimado
        assertEquals(5L, merged.formats[1].sizeBytes) // yt-dlp lo sabía: gana yt-dlp
        assertEquals(full, full.withSizesFrom(null))
    }

    @Test
    fun `elegir un formato antes de tener la metadata no hace nada`() {
        val viewModel = viewModel()

        viewModel.onFormatSelected(sampleMediaInfo().formats.first())

        assertTrue(scheduler.requests.isEmpty())
    }
}
