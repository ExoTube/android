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
        maxHeight: Int,
    ): Result<StreamSource> {
        // La calidad forma parte de la clave: el 480p guardado no sirve a quien pide 1080p.
        val key = cacheKeyPrefix(video) + if (audioOnly) "audio" else "${maxHeight}p"
        cache.get(key)?.let {
            Log.d(TAG, "Dirección reutilizada de la caché: ${video.id}")
            return Result.success(it)
        }

        return try {
            val startMs = SystemClock.elapsedRealtime()
            val urls = directUrls(video.url, if (audioOnly) AUDIO_ONLY_FORMAT else videoFormat(maxHeight))
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

    override fun forget(video: OnlineVideo) {
        cache.removeStartingWith(cacheKeyPrefix(video))
    }

    private fun cacheKeyPrefix(video: OnlineVideo) = "${video.id}:"

    /**
     * `-g` imprime la dirección de cada formato elegido, una por línea, sin descargar nada.
     * Con un formato del tipo "imagen+sonido" salen dos líneas; con uno solo, una.
     *
     * **Siempre por IPv4, igual que el reproductor.** YouTube ata cada dirección a la IP que la
     * pidió (va escrita dentro, en `ip=`) y responde "prohibido" (403) si la descarga llega
     * desde otra. Con datos móviles el teléfono suele tener DOS IP a la vez, una IPv4 y otra
     * IPv6: si yt-dlp pedía la dirección por una y el reproductor la descargaba por la otra,
     * NINGÚN video se podía ver, y volver a tocarlo tampoco lo arreglaba. El reproductor hace
     * lo mismo por su lado (ver `player.Ipv4FirstDns`).
     *
     * Si la red no tiene IPv4 en absoluto (muy raro), se vuelve a intentar sin forzarlo.
     */
    private suspend fun directUrls(url: String, format: String): List<String> = try {
        directUrls(url, format, forceIpv4 = true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (YtDlpErrorMapper.map(e) != MediaError.NoConnection) throw e
        Log.i(TAG, "Sin salida por IPv4; se reintenta con la red por defecto")
        directUrls(url, format, forceIpv4 = false)
    }

    private suspend fun directUrls(url: String, format: String, forceIpv4: Boolean): List<String> {
        val request = engine.newRequest(url)
            .addOption("-f", format)
            .addOption("-g")
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            // No usamos formatos troceados (HLS): que yt-dlp no pierda tiempo en analizarlos.
            .addOption("--extractor-args", "youtube:skip=hls")
        if (forceIpv4) request.addOption("--force-ipv4")
        return engine.run(request).out.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("http") }
            .toList()
    }

    private companion object {
        const val TAG = "YtDlpCatalog"
        const val RESULTS_PER_SEARCH = 20

        /** Modo ahorro de datos: solo el sonido, sin caer nunca a un formato con imagen. */
        const val AUDIO_ONLY_FORMAT = "ba[protocol^=http]"
    }
}

/**
 * El formato que se le pide a yt-dlp para ver un video sin pasar de [maxHeight] píxeles de alto.
 *
 * Una sola petición con tres intentos encadenados por "/": yt-dlp se queda con el primero que
 * exista. Antes hacían falta dos llamadas a yt-dlp, o sea el doble de espera.
 *
 *  1. Imagen H.264 hasta [maxHeight] + sonido AAC por separado. Los códecs que entiende
 *     cualquier teléfono, así que es el que menos sorpresas da.
 *  2. Lo mismo sin exigir códec (puede ser VP9 u Opus; el reproductor también los lee).
 *  3. Un único archivo con imagen y sonido, que es lo que queda cuando no hay nada mejor.
 *     YouTube solo los sirve en 360p, de ahí que sea el último recurso.
 *
 * "protocol^=http" descarta los formatos troceados en directo, que no sabemos leer; el "?" de
 * "height<=?720" hace que un formato sin altura declarada no se descarte.
 *
 * Función aparte para poder comprobar con un test que la calidad elegida llega de verdad.
 */
internal fun videoFormat(maxHeight: Int): String =
    "bv*[vcodec^=avc1][height<=?$maxHeight][protocol^=http]+ba[ext=m4a][protocol^=http]/" +
        "bv*[height<=?$maxHeight][protocol^=http]+ba[protocol^=http]/" +
        "b[vcodec!=none][acodec!=none][protocol^=http]"

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
