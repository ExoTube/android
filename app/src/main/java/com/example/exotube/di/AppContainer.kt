package com.example.exotube.di

import android.content.Context
import com.example.exotube.BuildConfig
import com.example.exotube.data.history.DisabledHistoryRepository
import com.example.exotube.data.history.SupabaseHistoryRepository
import com.example.exotube.data.audio.FFmpegAudioEditor
import com.example.exotube.data.audio.FFmpegRunner
import com.example.exotube.data.audio.WaveformReader
import com.example.exotube.data.library.ArtworkCache
import com.example.exotube.data.library.MediaStoreLibraryRepository
import com.example.exotube.data.playlist.ExoTubeDatabase
import com.example.exotube.data.playlist.PlaylistCoverStore
import com.example.exotube.data.playlist.RoomPlaylistRepository
import com.example.exotube.data.update.ApkInstaller
import com.example.exotube.data.update.GitHubUpdateRepository
import com.example.exotube.data.update.UpdateSettings
import com.example.exotube.data.ytdlp.MediaExtractorManager
import com.example.exotube.data.ytdlp.YtDlpCatalog
import com.example.exotube.data.ytdlp.YtDlpEngine
import com.example.exotube.domain.repository.AudioEditor
import com.example.exotube.domain.repository.DownloadHistoryRepository
import com.example.exotube.domain.repository.DownloadScheduler
import com.example.exotube.domain.repository.LibraryRepository
import com.example.exotube.domain.repository.PlaylistRepository
import com.example.exotube.domain.repository.MediaDownloader
import com.example.exotube.domain.repository.MediaRepository
import com.example.exotube.domain.repository.OnlineCatalogRepository
import com.example.exotube.domain.repository.UpdateRepository
import com.example.exotube.download.MediaStoreSaver
import com.example.exotube.player.AudioEffects
import com.example.exotube.download.WorkManagerDownloadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Inyección de dependencias manual: el único lugar donde se eligen las implementaciones.
 * Los ViewModels y Workers reciben interfaces, nunca clases concretas.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Vive lo mismo que la app: para trabajo que no pertenece a ninguna pantalla. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** El motor yt-dlp, compartido por las descargas y por la pestaña Explorar. */
    val ytDlpEngine = YtDlpEngine(appContext)

    private val mediaExtractor = MediaExtractorManager(ytDlpEngine)

    // Para diseñar la UI sin red ni yt-dlp, cámbialo por FakeMediaRepository().
    val mediaRepository: MediaRepository get() = mediaExtractor

    val mediaDownloader: MediaDownloader get() = mediaExtractor

    val onlineCatalog: OnlineCatalogRepository by lazy { YtDlpCatalog(ytDlpEngine) }

    val downloadScheduler: DownloadScheduler by lazy { WorkManagerDownloadScheduler(appContext) }

    val mediaSaver: MediaStoreSaver by lazy { MediaStoreSaver(appContext) }

    /** Compartido por el servicio de reproducción (que lo aplica) y la pantalla del ecualizador. */
    val audioEffects: AudioEffects by lazy { AudioEffects(appContext) }

    val libraryRepository: LibraryRepository by lazy { MediaStoreLibraryRepository(appContext) }

    /** Calcula la forma de onda que dibuja la barra del reproductor. Guarda las últimas. */
    val waveformReader: WaveformReader by lazy { WaveformReader(appContext) }

    /** Para olvidar la carátula guardada cuando se cambia la portada de una canción. */
    val artworkCache: ArtworkCache by lazy { ArtworkCache(appContext) }

    /** Recorta audio con el FFmpeg que ya viaja dentro de la app para las descargas. */
    val audioEditor: AudioEditor by lazy {
        FFmpegAudioEditor(appContext, FFmpegRunner(appContext, ytDlpEngine), mediaSaver)
    }

    // --- Actualizarse a sí misma (Fase 10) ---

    /** Compara con BuildConfig.VERSION_NAME: la versión que de verdad está instalada. */
    val updateRepository: UpdateRepository by lazy { GitHubUpdateRepository(BuildConfig.VERSION_NAME) }

    val updateSettings: UpdateSettings by lazy { UpdateSettings(appContext) }

    val apkInstaller: ApkInstaller by lazy { ApkInstaller(appContext) }

    private val database by lazy { ExoTubeDatabase.create(appContext) }

    val playlistRepository: PlaylistRepository by lazy {
        RoomPlaylistRepository(database.playlistDao(), libraryRepository, PlaylistCoverStore(appContext))
    }

    // Sin claves en local.properties la app funciona igual, solo que sin historial en la nube.
    val historyRepository: DownloadHistoryRepository by lazy {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_KEY.isBlank()) {
            DisabledHistoryRepository
        } else {
            SupabaseHistoryRepository.create(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)
        }
    }
}
