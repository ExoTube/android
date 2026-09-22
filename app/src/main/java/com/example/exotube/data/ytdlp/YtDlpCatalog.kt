package com.example.exotube.data.ytdlp

import android.os.SystemClock
import android.util.Log
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.repository.OnlineCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Buscar videos en YouTube y obtener las direcciones para verlos sin descargarlos, con el mismo
 * yt-dlp que ya usa la app para descargar.
 *
 * Aquí no se descarga NADA al teléfono: solo se piden las direcciones de los archivos que ya
 * están en el servidor y se le pasan al reproductor, como haría cualquier web de video.
 */
class YtDlpCatalog(private val engine: YtDlpEngine) : OnlineCatalogRepository {

    private val cache = StreamUrlCache()

    override suspend fun search(query: String): Result<List<OnlineVideo>> = try {
        // "ytsearchN:texto" es una URL falsa que entiende yt-dlp: busca y devuelve N resultados.
        val request = engine.newRequest("ytsearch$RESULTS_PER_SEARCH:$query")
            .addOption("--dump-single-json")
            // Sin esto, yt-dlp analizaría cada resultado uno a uno: minutos en vez de segundos.
            .addOption("--flat-playlist")
            .addOption("--no-warnings")
        val json = engine.run(request).out
        val videos = withContext(Dispatchers.Default) {
            OnlineVideoMapper.toOnlineVideos(YtDlpJson.decodeFromString<YtDlpSearchDto>(json))
        }
        Result.success(videos)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Falló la búsqueda en línea", e)
        Result.failure(YtDlpErrorMapper.map(e))
    }

    /**
     * Pide las direcciones con las que reproducir [video].
     *
     * Si ya se resolvió hace poco, se devuelve lo guardado y no se arranca yt-dlp: eso convierte
     * los segundos de espera en cero al volver a una canción.
     */
    override suspend fun resolveStream(
        video: OnlineVideo,
        audioOnly: Boolean,
    ): Result<StreamSource> {
        val key = "${video.id}:$audioOnly"
        cache.get(key)?.let {
            Log.d(TAG, "Dirección reutilizada de la caché: ${video.id}")
            return Result.success(it)
        }

        return try {
            val startMs = SystemClock.elapsedRealtime()
            val urls = directUrls(video.url, if (audioOnly) AUDIO_ONLY_FORMAT else VIDEO_FORMAT)
            val source = toStreamSource(urls, audioOnly)
                ?: return Result.failure(MediaError.NoMediaFound)
            cache.put(key, source)
            Log.d(TAG, "Resuelto ${video.id} en ${SystemClock.elapsedRealtime() - startMs} ms")
            Result.success(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo resolver ${video.id}", e)
            Result.failure(YtDlpErrorMapper.map(e))
        }
    }

    /**
     * `-g` imprime la dirección de cada formato elegido, una por línea, sin descargar nada.
     * Con un formato del tipo "imagen+sonido" salen dos líneas; con uno solo, una.
     */
    private suspend fun directUrls(url: String, format: String): List<String> {
        val request = engine.newRequest(url)
            .addOption("-f", format)
            .addOption("-g")
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            // No usamos formatos troceados (HLS): que yt-dlp no pierda tiempo en analizarlos.
            .addOption("--extractor-args", "youtube:skip=hls")
        return engine.run(request).out.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("http") }
            .toList()
    }

    private companion object {
        const val TAG = "YtDlpCatalog"
        const val RESULTS_PER_SEARCH = 20

        /**
         * Una sola petición con tres intentos encadenados por "/": yt-dlp se queda con el primero
         * que exista. Antes hacían falta dos llamadas a yt-dlp, o sea el doble de espera.
         *
         *  1. Imagen H.264 hasta 1080p + sonido AAC por separado. Los códecs que entiende
         *     cualquier teléfono, así que es el que menos sorpresas da.
         *  2. Lo mismo sin exigir códec (puede ser VP9 u Opus; el reproductor también los lee).
         *  3. Un único archivo con imagen y sonido, que es lo que queda cuando no hay nada mejor.
         *     YouTube solo los sirve en 360p, de ahí que sea el último recurso.
         *
         * "protocol^=http" descarta los formatos troceados en directo, que no sabemos leer;
         * el "?" de "height<=?1080" hace que un formato sin altura declarada no se descarte.
         */
        const val VIDEO_FORMAT =
            "bv*[vcodec^=avc1][height<=?1080][protocol^=http]+ba[ext=m4a][protocol^=http]/" +
                "bv*[height<=?1080][protocol^=http]+ba[protocol^=http]/" +
                "b[vcodec!=none][acodec!=none][protocol^=http]"

        /** Modo ahorro de datos: solo el sonido, sin caer nunca a un formato con imagen. */
        const val AUDIO_ONLY_FORMAT = "ba[protocol^=http]"
    }
}

/**
 * Interpreta las direcciones que imprimió yt-dlp. Dos significa que la imagen y el sonido vienen
 * por separado; una, que está todo junto (o que solo se pidió el sonido).
 *
 * Función aparte, y no un método privado, para poder probarla sin arrancar yt-dlp.
 */
internal fun toStreamSource(urls: List<String>, audioOnly: Boolean): StreamSource? = when {
    urls.isEmpty() -> null
    // yt-dlp las imprime en el orden del formato pedido: primero la imagen, luego el sonido.
    urls.size >= 2 && !audioOnly -> StreamSource.Separate(urls[0], urls[1])
    else -> StreamSource.Single(urls[0], hasVideo = !audioOnly)
}
