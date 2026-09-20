package com.example.exotube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
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
        container.applicationScope.launch { container.mediaExtractor.warmUp() }
        // Los sitios cambian a menudo: revisamos una vez al día si hay yt-dlp nuevo.
        EngineUpdateWorker.schedule(this)
    }

    /** Coil usará este cargador en toda la app: además de imágenes, sabe sacar fotogramas de videos. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
