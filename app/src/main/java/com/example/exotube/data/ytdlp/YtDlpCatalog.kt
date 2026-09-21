package com.example.exotube.data.ytdlp

import android.util.Log
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.repository.OnlineCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Buscar videos en YouTube y obtener la dirección para verlos sin descargarlos, con el mismo
 * yt-dlp que ya usa la app para descargar.
 *
 * Aquí no se descarga NADA al teléfono: solo se pide la dirección del archivo que ya está en el
 * servidor y se la pasamos al reproductor, como haría cualquier web de video.
 */
class YtDlpCatalog(private val engine: YtDlpEngine) : OnlineCatalogRepository {

    override suspend fun search(query: String): Result<List<OnlineVideo>> = try {
        // "ytsearchN:texto" es una URL falsa que entiende yt-dlp: busca y devuelve N resultados.
        val request = engine.newRequest("ytsearch$RESULTS_PER_SEARCH:$query")
            .addOption("--dump-single-json")
            // Sin esto, yt-dlp analizaría cada resultado uno a uno: minutos en vez de segundos.
            .addOption("--flat-playlist")
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
     * Pide la dirección directa del video.
     *
     * Para ver, pedimos un formato que YA lleve imagen y sonido juntos: así es una sola dirección
     * y el reproductor no tiene que mezclar dos flujos. A cambio, YouTube solo ofrece calidades
     * modestas así mezcladas; para ver en alta definición está la descarga.
     *
     * Si ese formato no existe (pasa en algunos videos y en los directos), se intenta al menos el
     * audio: es mejor escuchar la canción que no poder hacer nada.
     */
    override suspend fun resolveStream(
        video: OnlineVideo,
        audioOnly: Boolean,
    ): Result<StreamSource> {
        val attempts = if (audioOnly) {
            listOf(AUDIO_FORMAT to false)
        } else {
            listOf(MUXED_FORMAT to true, AUDIO_FORMAT to false)
        }

        var lastError: Throwable? = null
        for ((format, hasVideo) in attempts) {
            try {
                val url = directUrl(video.url, format)
                if (url != null) return Result.success(StreamSource(url, hasVideo))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo resolver con el formato $format", e)
                lastError = e
            }
        }
        return Result.failure(lastError?.let(YtDlpErrorMapper::map) ?: MediaError.NoMediaFound)
    }

    /** `-g` imprime la dirección del formato elegido, una por línea, sin descargar nada. */
    private suspend fun directUrl(url: String, format: String): String? {
        val request = engine.newRequest(url)
            .addOption("-f", format)
            .addOption("-g")
            .addOption("--no-playlist")
        return engine.run(request).out.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("http") }
    }

    private companion object {
        const val TAG = "YtDlpCatalog"
        const val RESULTS_PER_SEARCH = 20

        /**
         * "b" = el mejor formato; los corchetes son condiciones:
         *  - vcodec/acodec != none: que traiga imagen Y sonido en el mismo archivo;
         *  - protocol^=http: descarga normal, no un directo troceado (HLS), que no sabemos leer.
         */
        const val MUXED_FORMAT = "b[vcodec!=none][acodec!=none][protocol^=http]"

        /** "ba" = el mejor solo-audio. */
        const val AUDIO_FORMAT = "ba[protocol^=http]"
    }
}
