package com.example.exotube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.example.exotube.data.library.EmbeddedArtworkFetcher
import com.example.exotube.data.update.UpdateCheckWorker
import com.example.exotube.data.ytdlp.EngineUpdateWorker
import com.example.exotube.di.AppContainer
import kotlinx.coroutines.launch

/** Se crea antes que cualquier Activity; guarda las dependencias compartidas por toda la app. */
class ExoTubeApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Descomprime yt-dlp en segundo plano para que el primer "Compartir" no espere.
        container.applicationScope.launch { container.ytDlpEngine.warmUp() }
        // Los sitios cambian a menudo: revisamos una vez al día si hay yt-dlp nuevo.
        EngineUpdateWorker.schedule(this)
        // Y una vez al día también, si hay una versión nueva de la propia app.
        UpdateCheckWorker.schedule(this)
    }

    /**
     * Coil usará este cargador en toda la app. Además de imágenes normales sabe:
     *  - sacar la portada que va dentro de un MP3 (EmbeddedArtworkFetcher), y
     *  - sacar un fotograma de un video (VideoFrameDecoder).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(EmbeddedArtworkFetcher.Factory(this@ExoTubeApp))
                add(VideoFrameDecoder.Factory())
            }
            .build()
}
