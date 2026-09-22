package com.example.exotube.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.example.exotube.data.network.StreamHttpClient
import okhttp3.OkHttpClient

/**
 * Decide cómo se lee cada cosa que se reproduce.
 *
 * Para un archivo del teléfono, o un video en línea que venga en un solo archivo, no hace nada
 * especial: se lo pasa al lector normal de Media3.
 *
 * Lo interesante es cuando la Uri es una de las nuestras ([MergedStreamUri]), con la imagen y el
 * sonido en direcciones distintas: entonces monta DOS lectores y los junta en uno
 * ([MergingMediaSource]), que los reproduce a la vez y sincronizados. Es lo que permite ver en
 * 1080p, porque YouTube solo sirve 360p cuando mete imagen y sonido en el mismo archivo.
 *
 * Lo que viene de internet se descarga con la conexión de [StreamHttpClient], para salir por la
 * misma IP con la que se pidió la dirección: si no, YouTube la rechaza. Los archivos del teléfono
 * (content://) no pasan por aquí; DefaultDataSource los sigue leyendo como siempre.
 */
@OptIn(UnstableApi::class) // toda la capa de MediaSource es API "inestable" de Media3
internal class StreamingMediaSourceFactory(
    context: Context,
    /** La conexión de los videos en línea (ver [StreamHttpClient]). */
    httpClient: OkHttpClient,
) : MediaSource.Factory {

    private val delegate = DefaultMediaSourceFactory(
        // Lo que viene de YouTube, a trozos (ver ChunkedDataSource): si no, YouTube lo frena.
        DefaultDataSource.Factory(context, ChunkedDataSource.Factory(OkHttpDataSource.Factory(httpClient))),
    )

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri ?: return delegate.createMediaSource(mediaItem)
        val (videoUrl, audioUrl) = MergedStreamUri.decode(uri.toString())
            ?: return delegate.createMediaSource(mediaItem)

        // El lector de la imagen conserva el MediaItem original y, con él, el título y la
        // carátula: MergingMediaSource toma del primer hijo los datos que verá la interfaz.
        val video = delegate.createMediaSource(mediaItem.buildUpon().setUri(videoUrl).build())
        val audio = delegate.createMediaSource(MediaItem.fromUri(audioUrl))

        return MergingMediaSource(
            /* adjustPeriodTimeOffsets = */ false,
            // Las dos pistas del mismo video duran casi lo mismo, pero no exactamente: recortar
            // a la más corta evita que el reproductor falle por unos milisegundos de diferencia.
            /* clipDurations = */ true,
            video,
            audio,
        )
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun setDrmSessionManagerProvider(
        provider: DrmSessionManagerProvider,
    ): MediaSource.Factory = apply { delegate.setDrmSessionManagerProvider(provider) }

    override fun setLoadErrorHandlingPolicy(
        policy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = apply { delegate.setLoadErrorHandlingPolicy(policy) }
}
