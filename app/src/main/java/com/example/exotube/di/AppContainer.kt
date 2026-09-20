package com.example.exotube.di

import android.content.Context
import com.example.exotube.BuildConfig
import com.example.exotube.data.history.DisabledHistoryRepository
import com.example.exotube.data.history.SupabaseHistoryRepository
import com.example.exotube.data.library.MediaStoreLibraryRepository
import com.example.exotube.data.playlist.ExoTubeDatabase
import com.example.exotube.data.playlist.PlaylistCoverStore
import com.example.exotube.data.playlist.RoomPlaylistRepository
import com.example.exotube.data.ytdlp.MediaExtractorManager
import com.example.exotube.domain.repository.DownloadHistoryRepository
import com.example.exotube.domain.repository.DownloadScheduler
import com.example.exotube.domain.repository.LibraryRepository
import com.example.exotube.domain.repository.PlaylistRepository
import com.example.exotube.domain.repository.MediaDownloader
import com.example.exotube.domain.repository.MediaRepository
import com.example.exotube.download.MediaStoreSaver
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

    val mediaExtractor = MediaExtractorManager(appContext)

    // Para diseñar la UI sin red ni yt-dlp, cámbialo por FakeMediaRepository().
    val mediaRepository: MediaRepository get() = mediaExtractor

    val mediaDownloader: MediaDownloader get() = mediaExtractor

    val downloadScheduler: DownloadScheduler by lazy { WorkManagerDownloadScheduler(appContext) }

    val mediaSaver: MediaStoreSaver by lazy { MediaStoreSaver(appContext) }

    val libraryRepository: LibraryRepository by lazy { MediaStoreLibraryRepository(appContext) }

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
