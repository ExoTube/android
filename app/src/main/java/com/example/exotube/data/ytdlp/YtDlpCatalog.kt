package com.example.exotube.data.ytdlp

import android.os.SystemClock
import android.util.Log
import com.example.exotube.data.newpipe.NewPipeSearch
import com.example.exotube.data.newpipe.NewPipeStreamResolver
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.model.VideoPage
import com.example.exotube.domain.repository.OnlineCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Buscar videos en YouTube y obtener las direcciones para verlos sin descargarlos, con el mismo
 * yt-dlp que ya usa la app para descargar.
 *
 * Aquí no se descarga NADA al teléfono: solo se piden las direcciones de los archivos que ya
 * están en el servidor y se le pasan al reproductor, como haría cualquier web de video.
 */
class YtDlpCatalog(
    private val engine: YtDlpEngine,
    /** El camino rápido para los enlaces; null para usar solo yt-dlp. */
    private val quickResolver: NewPipeStreamResolver? = null,
    /** Búsqueda por páginas con NewPipe; null para buscar solo con yt-dlp. */
    private val quickSearch: NewPipeSearch? = null,
) : OnlineCatalogRepository {

    /**
     * Por dónde seguir la última búsqueda. Solo se recuerda una: la que se está mirando.
     * Si la hizo NewPipe se guarda su "siguiente página"; si la hizo yt-dlp, cuántos van.
     */
    @Volatile
    private var searchCursor: SearchCursor? = null

    private sealed interface SearchCursor {
        val query: String

        class ByNewPipe(override val query: String, val next: NewPipeSearch.Next?) : SearchCursor

        class ByYtDlp(override val query: String, val shown: Int) : SearchCursor
    }

    private val cache = StreamUrlCache()

    /**
     * Videos cuyo enlace rápido (NewPipe) acabó fallando al reproducirse. Para esos, la próxima
     * vez se va directo a yt-dlp, que es más lento pero más a prueba de cambios de YouTube.
     */
    private val quickFailed = mutableSetOf<String>()

    /**
     * Primero con NewPipe (más rápido y por páginas); si falla, con yt-dlp como siempre.
     */
    override suspend fun search(query: String): Result<VideoPage> {
        quickSearch?.let { newPipe ->
            try {
                val startMs = SystemClock.elapsedRealtime()
                val results = newPipe.first(query)
                Log.d(TAG, "Búsqueda con NewPipe en ${SystemClock.elapsedRealtime() - startMs} ms")
                searchCursor = SearchCursor.ByNewPipe(query, results.next)
                return Result.success(VideoPage(results.videos, hasMore = results.next != null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "NewPipe no pudo buscar; se prueba con yt-dlp", e)
            }
        }
        return try {
            val startMs = SystemClock.elapsedRealtime()
            val videos = ytDlpSearch(query, RESULTS_PER_SEARCH)
            Log.d(TAG, "Búsqueda con yt-dlp en ${SystemClock.elapsedRealtime() - startMs} ms")
            searchCursor = SearchCursor.ByYtDlp(query, videos.size)
            Result.success(VideoPage(videos, hasMore = videos.size >= RESULTS_PER_SEARCH))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falló la búsqueda en línea", e)
            Result.failure(YtDlpErrorMapper.map(e))
        }
    }

    override suspend fun searchMore(query: String): Result<VideoPage> {
        val cursor = searchCursor?.takeIf { it.query == query } ?: return Result.success(NO_MORE)
        return try {
            when (cursor) {
                is SearchCursor.ByNewPipe -> {
                    val next = cursor.next ?: return Result.success(NO_MORE)
                    val results = checkNotNull(quickSearch).more(next)
                    searchCursor = SearchCursor.ByNewPipe(query, results.next)
                    Result.success(VideoPage(results.videos, hasMore = results.next != null))
                }
                // yt-dlp no sabe "seguir": se repite la búsqueda pidiendo más y se quitan los
                // que ya se veían. Más lento, pero es solo el plan B.
                is SearchCursor.ByYtDlp -> {
                    val wanted = cursor.shown + RESULTS_PER_SEARCH
                    val all = ytDlpSearch(query, wanted)
                    val fresh = all.drop(cursor.shown)
                    searchCursor = SearchCursor.ByYtDlp(query, all.size)
                    Result.success(VideoPage(fresh, hasMore = fresh.isNotEmpty() && all.size >= wanted))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron traer más resultados", e)
            Result.failure(YtDlpErrorMapper.map(e))
        }
    }

    /** "ytsearchN:texto" es una URL falsa que entiende yt-dlp: busca y devuelve N resultados. */
    private suspend fun ytDlpSearch(query: String, count: Int): List<OnlineVideo> {
        val request = engine.newRequest("ytsearch$count:$query")
            .addOption("--dump-single-json")
            // Sin esto, yt-dlp analizaría cada resultado uno a uno: minutos en vez de segundos.
            .addOption("--flat-playlist")
            .addOption("--no-warnings")
        val json = engine.run(request).out
        return withContext(Dispatchers.Default) {
            OnlineVideoMapper.toOnlineVideos(YtDlpJson.decodeFromString<YtDlpSearchDto>(json))
        }
    }

    /**
     * Pide las direcciones con las que reproducir [video].
     *
     * Si ya se resolvió hace poco, se devuelve lo guardado y no se pregunta a nadie: eso convierte
     * los segundos de espera en cero al volver a una canción.
     *
     * Si no, primero se intenta con NewPipe ([quickResolver]) y, si falla, con yt-dlp. Medido en
     * el emulador con seis videos: NewPipe tardó 1,9 s de mediana y yt-dlp 6 s (y a veces 19),
     * con exactamente los mismos formatos.
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

        quickResolve(video, audioOnly, maxHeight)?.let { source ->
            cache.put(key, source)
            return Result.success(source)
        }

        return try {
            val startMs = SystemClock.elapsedRealtime()
            val urls = directUrls(video.url, if (audioOnly) AUDIO_ONLY_FORMAT else videoFormat(maxHeight))
            val source = toStreamSource(urls, audioOnly)
                ?: return Result.failure(MediaError.NoMediaFound)
            cache.put(key, source)
            Log.d(TAG, "Resuelto ${video.id} con yt-dlp en ${SystemClock.elapsedRealtime() - startMs} ms")
            Result.success(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo resolver ${video.id}", e)
            Result.failure(YtDlpErrorMapper.map(e))
        }
    }

    /**
     * El enlace de [video] no se pudo reproducir: se olvida y, si era de NewPipe, la próxima vez
     * se pide a yt-dlp. Así un fallo de NewPipe nunca deja un video sin poder verse.
     */
    override fun forget(video: OnlineVideo) {
        cache.removeStartingWith(cacheKeyPrefix(video))
        synchronized(quickFailed) { quickFailed += video.id }
    }

    /**
     * El camino rápido. Devuelve null (y entonces se usa yt-dlp) si no está disponible, si ese
     * video ya falló por aquí o si NewPipe no lo consigue a tiempo, por la razón que sea.
     */
    private suspend fun quickResolve(video: OnlineVideo, audioOnly: Boolean, maxHeight: Int): StreamSource? {
        val resolver = quickResolver ?: return null
        if (synchronized(quickFailed) { video.id in quickFailed }) return null
        val startMs = SystemClock.elapsedRealtime()
        return try {
            withTimeoutOrNull(QUICK_TIMEOUT_MS) { resolver.resolve(video.url, audioOnly, maxHeight) }
                ?.also { Log.d(TAG, "Resuelto ${video.id} con NewPipe en ${SystemClock.elapsedRealtime() - startMs} ms") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe no pudo con ${video.id}; se prueba con yt-dlp", e)
            null
        }
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
     * lo mismo por su lado (ver `data.network.Ipv4FirstDns`).
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

        /**
         * Lo que se espera a NewPipe antes de pasarle el trabajo a yt-dlp. Suele tardar menos de
         * dos segundos; si tarda mucho más, algo va mal y no tiene sentido esperarlo.
         */
        const val QUICK_TIMEOUT_MS = 8_000L

        const val RESULTS_PER_SEARCH = 20

        val NO_MORE = VideoPage(emptyList(), hasMore = false)

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
