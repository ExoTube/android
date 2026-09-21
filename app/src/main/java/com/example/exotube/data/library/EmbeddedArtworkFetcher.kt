package com.example.exotube.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer

/**
 * Le enseña a Coil a sacar la carátula que viaja DENTRO de un MP3 o un M4A.
 *
 * Coil sabe abrir imágenes, no canciones: si le damos la Uri de un MP3 intenta leerlo como si
 * fuera un JPG y falla. Este "fetcher" se pone en medio, abre el archivo con
 * [MediaMetadataRetriever] y le entrega a Coil solo los bytes de la portada.
 *
 * Vale tanto para lo que descarga ExoTube (yt-dlp incrusta la portada al convertir a MP3) como
 * para la música que el usuario ya tenía en el teléfono.
 */
internal class EmbeddedArtworkFetcher(
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // Leer el archivo es trabajo de disco: nunca en el hilo de la interfaz.
        val picture = withContext(Dispatchers.IO) { readEmbeddedPicture() } ?: return null
        return SourceFetchResult(
            // Le pasamos los bytes en bruto, no un Bitmap ya hecho: así Coil los reduce al tamaño
            // real del hueco en pantalla y los guarda en su caché.
            source = ImageSource(Buffer().write(picture), options.fileSystem),
            mimeType = null, // que lo deduzca Coil: unas portadas son JPG y otras PNG
            dataSource = DataSource.DISK,
        )
    }

    /** null si la canción no lleva portada; entonces Coil se rinde y se ve el degradado de fondo. */
    private fun readEmbeddedPicture(): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(options.context, uri.toString().toUri())
            retriever.embeddedPicture
        } catch (_: RuntimeException) {
            // Archivo corrupto, a medio borrar o sin permiso: no hay portada y no es un error grave.
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Coil pregunta a cada fábrica si sabe tratar este dato. Devolver null significa "yo no":
     * entonces sigue con las demás (por ejemplo, VideoFrameDecoder para los videos).
     */
    class Factory(context: Context) : Fetcher.Factory<Uri> {

        private val resolver = context.applicationContext.contentResolver

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Descartamos de entrada lo que no sea un archivo del teléfono (p. ej. las miniaturas
            // de internet de la hoja de descarga), y del resto preguntamos el tipo a MediaStore.
            if (data.scheme != "content") return null
            val isAudio = resolver.getType(data.toString().toUri())?.startsWith("audio/") == true
            return if (isAudio) EmbeddedArtworkFetcher(data, options) else null
        }
    }
}
