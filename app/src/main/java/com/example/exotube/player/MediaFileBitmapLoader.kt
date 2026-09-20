package com.example.exotube.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Carátulas para la notificación, la pantalla de bloqueo y los dispositivos Bluetooth.
 *
 * Media3 pide la imagen de `artworkUri`. Nuestro artworkUri es el propio archivo
 * (content://media/…), así que sacamos la imagen de dentro:
 *   1. La portada incrustada, si la tiene (lo habitual en MP3/M4A).
 *   2. Si no, un fotograma al 10 % del video (como las miniaturas de la biblioteca).
 * Cualquier otra Uri (http, file…) la carga el cargador estándar de Media3.
 */
@OptIn(UnstableApi::class)
class MediaFileBitmapLoader(context: Context) : BitmapLoader {

    private val appContext = context.applicationContext
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val standardLoader = DataSourceBitmapLoader.Builder(appContext)
        .setExecutorService(executor) // un solo hilo de trabajo para ambos casos
        .setMaximumOutputDimension(MAX_SIZE_PX)
        .build()

    override fun supportsMimeType(mimeType: String): Boolean = standardLoader.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = standardLoader.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        if (uri.authority == MediaStore.AUTHORITY) {
            executor.submit<Bitmap> { extractArtwork(uri) } // leer el archivo tarda: fuera del hilo principal
        } else {
            standardLoader.loadBitmap(uri)
        }

    fun release() {
        executor.shutdown()
    }

    private fun extractArtwork(uri: Uri): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            val embedded = retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) * 1_000
            val artwork = embedded
                ?: retriever.getFrameAtTime(durationUs / 10, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: throw IOException("El archivo no tiene imagen: $uri")
            return artwork.scaledDown()
        } finally {
            retriever.release()
        }
    }

    /**
     * La imagen viaja al sistema por IPC (entre procesos). Un fotograma 1080p ocupa ~8 MB y puede
     * superar el límite de Android; 512 px es de sobra para una notificación.
     */
    private fun Bitmap.scaledDown(): Bitmap {
        val scale = MAX_SIZE_PX.toFloat() / maxOf(width, height)
        if (scale >= 1f) return this
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    }

    private companion object {
        const val MAX_SIZE_PX = 512
    }
}
