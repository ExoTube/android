package com.example.exotube

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.exotube.data.network.Ipv4FirstDns
import com.example.exotube.data.newpipe.NewPipeChannels
import com.example.exotube.data.newpipe.NewPipeMediaInfo
import com.example.exotube.data.newpipe.NewPipeSearch
import com.example.exotube.data.newpipe.NewPipeStreamResolver
import com.example.exotube.data.preview.LinkPreviewer
import com.example.exotube.data.ytdlp.InfoJsonCache
import com.example.exotube.data.ytdlp.MediaExtractorManager
import com.example.exotube.data.ytdlp.YtDlpCatalog
import com.example.exotube.data.ytdlp.YtDlpEngine
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.model.OnlineChannel
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.model.VideoPage
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Mide, con la red de verdad, cuánto se tarda en tener el enlace de un video con yt-dlp y con
 * NewPipeExtractor, y si los enlaces de NewPipe se pueden descargar.
 *
 * No comprueba nada: es una medición para decidir, no una prueba que pueda "fallar". Los
 * resultados salen en el registro con la etiqueta "Benchmark":
 *
 *     adb logcat -s Benchmark
 *
 * Se lanza a mano (tarda un par de minutos y necesita internet), nunca con el resto de pruebas.
 */
@RunWith(AndroidJUnit4::class)
class StreamSpeedBenchmark {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val client = OkHttpClient.Builder().dns(Ipv4FirstDns).fastFallback(false).build()

    private val ids = listOf("dQw4w9WgXcQ", "9bZkp7q19f0", "kJQP7kiw5Fk", "OX-us7PEfkc", "VoGwvVoaoCw", "UPVfQKUHTSc")

    @Test
    fun medir() = runBlocking {
        val engine = YtDlpEngine(context).apply { initialize() } // la app lo prepara al arrancar
        val newPipe = NewPipeStreamResolver(client)
        val ytDlpTimes = mutableListOf<Long>()
        val newPipeTimes = mutableListOf<Long>()

        ids.forEachIndexed { index, id ->
            val video = OnlineVideo(id, "https://www.youtube.com/watch?v=$id", id, null, null, null, null)
            // Se alterna quién va primero, para que ninguno se lleve siempre la red "caliente".
            val order = if (index % 2 == 0) listOf("newpipe", "ytdlp") else listOf("ytdlp", "newpipe")
            for (engineName in order) {
                if (engineName == "ytdlp") {
                    // Un catálogo nuevo cada vez: sin su caché, como la primera vez que se toca.
                    var result: Result<StreamSource>? = null
                    val ms = measureTimeMillis { result = YtDlpCatalog(engine).resolveStream(video, false, 480) }
                    ytDlpTimes += ms
                    log("$id yt-dlp   ${ms} ms  ${result?.getOrNull()?.describe() ?: "FALLO ${result?.exceptionOrNull()}"}")
                } else {
                    var source: StreamSource? = null
                    var error: Throwable? = null
                    val ms = measureTimeMillis {
                        source = runCatching { newPipe.resolve(video.url, false, 480) }.onFailure { error = it }.getOrNull()
                    }
                    newPipeTimes += ms
                    log("$id NewPipe  ${ms} ms  ${source?.describe() ?: "FALLO $error"}")
                    source?.urls?.forEach { log("      descarga ${probe(it)}  ${clientOf(it)}") }
                }
            }
        }

        log("RESUMEN yt-dlp  : ${ytDlpTimes.summary()}")
        log("RESUMEN NewPipe : ${newPipeTimes.summary()} (el primero incluye preparar la librería)")
    }

    /**
     * Cuánto tarda la hoja de descarga en reconocer un enlace, con yt-dlp y con NewPipe. Y lo
     * importante: que yt-dlp entienda las opciones que arma NewPipe. Para eso se le pide a yt-dlp
     * que elija el formato de cada opción sin descargar (--simulate), y se baja de verdad la más
     * pequeña para ver que el archivo sale.
     */
    @Test
    fun medirEnlaceDeDescarga() = runBlocking<Unit> {
        val engine = YtDlpEngine(context).apply { initialize() }
        val slow = MediaExtractorManager(engine)
        val quick = MediaExtractorManager(engine, NewPipeMediaInfo(client))
        val slowTimes = mutableListOf<Long>()
        val quickTimes = mutableListOf<Long>()

        ids.forEachIndexed { index, id ->
            val url = "https://www.youtube.com/watch?v=$id"
            val order = if (index % 2 == 0) listOf(quick, slow) else listOf(slow, quick)
            for (manager in order) {
                var info: MediaInfo? = null
                val ms = measureTimeMillis { info = manager.fetchMediaInfo(url).getOrNull() }
                val name = if (manager === quick) "NewPipe" else "yt-dlp "
                (if (manager === quick) quickTimes else slowTimes) += ms
                log("$id $name ${ms} ms  ${info?.formats?.joinToString { "${it.label}:${it.sizeBytes?.div(1_000_000)}MB" } ?: "FALLO"}")
            }
        }
        log("RESUMEN hoja yt-dlp  : ${slowTimes.summary()}")
        log("RESUMEN hoja NewPipe : ${quickTimes.summary()} (el primero incluye preparar la librería)")

        // ¿yt-dlp entiende cada opción de NewPipe? Se le pregunta qué formatos bajaría.
        val url = "https://www.youtube.com/watch?v=${ids.first()}"
        val info = quick.fetchMediaInfo(url).getOrThrow()
        for (format in info.formats) {
            val chosen = runCatching {
                engine.run(
                    engine.newRequest(url).addOption("-f", format.formatId).addOption("--no-playlist")
                        .addOption("--simulate").addOption("--print", "%(format_id)s %(height)sp"),
                ).out.trim()
            }.getOrElse { "FALLO ${it.message?.take(200)}" }
            log("  ${format.label.padEnd(8)} ${format.formatId}  ->  yt-dlp elige: $chosen")
        }

        // Y una descarga de verdad, la más pequeña, para ver que el archivo llega entero.
        val smallest = info.videoFormats.last()
        val dir = File(context.cacheDir, "prueba-descarga").apply { deleteRecursively() }
        val ms = measureTimeMillis {
            val file = runCatching {
                quick.download(DownloadRequest(url, info.title, info.platform, smallest), dir) {}
            }.getOrElse { log("  descarga FALLO $it"); null }
            file?.let { log("  descarga ${smallest.label}: ${it.name} ${it.length() / 1_000} KB") }
        }
        log("  la descarga tardó $ms ms")
        dir.deleteRecursively()
    }

    /**
     * Dónde se va el tiempo al reconocer enlaces que no son de YouTube (esos solo los entiende
     * yt-dlp). Se separa lo que cuesta solo arrancar Python ("--version") de lo que cuesta
     * preguntar a cada sitio, y se mide la página tal cual con OkHttp para ver cuánto es red.
     */
    @Test
    fun medirOtrasPlataformas() = runBlocking<Unit> {
        val engine = YtDlpEngine(context).apply { initialize() }
        val manager = MediaExtractorManager(engine)

        val startups = (1..3).map { measureTimeMillis { engine.run(engine.newRequest("").addOption("--version")) } }
        log("ARRANQUE de Python + yt-dlp (--version): ${startups.summary()}")

        val urls = listOf(
            "https://www.tiktok.com/@scout2015/video/6718335390845095173",
            "https://www.tiktok.com/@leenabhushan/video/6748451240264420610",
            "https://x.com/starwars/status/665052190608723968",
            "https://twitter.com/freethenipple/status/643211948184596480",
            "https://www.instagram.com/reel/Chunk8-jurw/",
            "https://www.instagram.com/p/Bm8OoRrlJk1/",
            "https://www.facebook.com/watch/?v=274175099429670",
            "https://www.facebook.com/facebook/videos/10153231379946729/",
        )
        val only = InstrumentationRegistry.getArguments().getString("url")
        for (url in if (only != null) listOf(only) else urls) {
            val pageMs = measureTimeMillis {
                runCatching { client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() } }
            }
            repeat(2) { attempt ->
                var result: Result<MediaInfo>? = null
                val ms = measureTimeMillis { result = manager.fetchMediaInfo(url) }
                val what = result?.getOrNull()?.formats?.joinToString { it.label }
                    ?: "FALLO ${result?.exceptionOrNull()?.let { it::class.simpleName + " " + (it.cause ?: it).message?.takeLast(300) }}"
                log("${url.take(60).padEnd(60)} intento ${attempt + 1}: ${ms} ms (página sola ${pageMs} ms)  $what")
            }
        }
    }

    /**
     * Descargar desde el enlace (yt-dlp vuelve a analizarlo) o desde el análisis que ya hizo la
     * hoja de descarga (--load-info-json). Se baja lo mismo las dos veces, en MP3.
     */
    @Test
    fun medirDescargaConJson() = runBlocking<Unit> {
        val engine = YtDlpEngine(context).apply { initialize() }
        val url = InstrumentationRegistry.getArguments().getString("url")
            ?: "https://www.tiktok.com/@scout2015/video/6718335390845095173"
        val json = File(context.cacheDir, "prueba-info.json")
        val analysisMs = measureTimeMillis {
            json.writeText(engine.run(engine.newRequest(url).addOption("--dump-single-json").addOption("--no-playlist")).out)
        }
        log("análisis (lo que hace la hoja): $analysisMs ms, ${json.length() / 1000} KB")

        repeat(2) { round ->
            for (useJson in listOf(false, true)) {
                val dir = File(context.cacheDir, "prueba-json-$useJson").apply { deleteRecursively(); mkdirs() }
                val request = (if (useJson) engine.newRequest("").addOption("--load-info-json", json.absolutePath) else engine.newRequest(url))
                    .addOption("-f", "ba/b").addOption("-x").addOption("--audio-format", "mp3")
                    .addOption("-o", File(dir, "%(title).60B.%(ext)s").absolutePath).addOption("--no-playlist")
                var outcome = ""
                val ms = measureTimeMillis {
                    outcome = runCatching { engine.run(request); dir.listFiles()?.joinToString { "${it.name.takeLast(20)} ${it.length() / 1000} KB" } }
                        .getOrElse { "FALLO ${it.message?.takeLast(200)}" }.orEmpty()
                }
                log("ronda ${round + 1} ${if (useJson) "con el análisis guardado" else "desde el enlace       "}: $ms ms  $outcome")
                dir.deleteRecursively()
            }
        }
        json.delete()
    }

    /**
     * La hoja de descarga de TikTok y X como la vive el usuario: cuándo aparece la vista previa,
     * cuándo llega el análisis completo, y cuánto tarda luego la descarga (que ya reutiliza el
     * análisis). Se descarga la calidad más alta que ofrece la vista previa.
     */
    @Test
    fun medirVistaPrevia() = runBlocking<Unit> {
        val engine = YtDlpEngine(context).apply { initialize() }
        val manager = MediaExtractorManager(
            engine,
            previewer = LinkPreviewer(client),
            infoCache = InfoJsonCache(File(context.cacheDir, "prueba-analisis")),
        )
        val urls = listOf(
            "https://www.tiktok.com/@scout2015/video/6718335390845095173",
            "https://x.com/StormChasingVideo/status/1575560063510810624",
        )
        for (url in urls) {
            var preview: MediaInfo? = null
            val previewMs = measureTimeMillis { preview = manager.previewMediaInfo(url) }
            log("${url.take(50)} vista previa: $previewMs ms  ${preview?.formats?.joinToString { "${it.label}:${it.sizeBytes?.div(1000)}KB" } ?: "NINGUNA"}")
            var full: MediaInfo? = null
            val fullMs = measureTimeMillis { full = manager.fetchMediaInfo(url).getOrNull() }
            log("${url.take(50)} completo    : $fullMs ms  ${full?.formats?.joinToString { "${it.label}:${it.sizeBytes?.div(1000)}KB" } ?: "FALLO"}")

            val format = preview?.videoFormats?.firstOrNull() ?: continue
            val dir = File(context.cacheDir, "prueba-vista").apply { deleteRecursively() }
            val downloadMs = measureTimeMillis {
                val file = runCatching {
                    manager.download(DownloadRequest(url, preview!!.title, preview!!.platform, format, preview!!.playlistIndex), dir) {}
                }.getOrElse { log("  descarga FALLO ${it.message?.takeLast(200)}"); null }
                file?.let { log("  descarga ${format.label}: ${it.name.takeLast(25)} ${it.length() / 1000} KB") }
            }
            log("  la descarga tardó $downloadMs ms (con el análisis guardado)")
            dir.deleteRecursively()
        }
    }

    /** El mensaje completo de yt-dlp con un enlace, para entender por qué falla. */
    @Test
    fun diagnosticar() = runBlocking<Unit> {
        val engine = YtDlpEngine(context).apply { initialize() }
        val url = InstrumentationRegistry.getArguments().getString("url") ?: return@runBlocking
        log("yt-dlp ${engine.run(engine.newRequest("").addOption("--version")).out.trim()}")
        val ms = measureTimeMillis {
            runCatching {
                // Las mismas opciones que usa la hoja de descarga (MediaExtractorManager).
                engine.run(
                    engine.newRequest(url).addOption("-v").addOption("--dump-single-json").addOption("--no-playlist")
                        .addOption("--flat-playlist").addOption("--playlist-items", "1-10"),
                )
            }.onSuccess { log("OK ${it.out.length} caracteres de JSON"); it.err.chunked(700).forEach(::log) }
                .onFailure { e -> e.message.orEmpty().chunked(700).forEach(::log) }
        }
        log("tardó $ms ms")
    }

    /** Cuánto tarda una búsqueda con cada motor, y abrir un canal con NewPipe. */
    @Test
    fun medirBusquedaYCanal() = runBlocking {
        val engine = YtDlpEngine(context).apply { initialize() }
        val ytDlp = YtDlpCatalog(engine) // sin NewPipe: solo yt-dlp
        val newPipe = NewPipeSearch(client)
        val ytDlpTimes = mutableListOf<Long>()
        val newPipeTimes = mutableListOf<Long>()

        listOf("minecraft", "soda stereo", "bad bunny", "lofi hip hop", "recetas faciles").forEachIndexed { index, query ->
            val order = if (index % 2 == 0) listOf("newpipe", "ytdlp") else listOf("ytdlp", "newpipe")
            for (engineName in order) {
                if (engineName == "ytdlp") {
                    var n = 0
                    val ms = measureTimeMillis { n = ytDlp.search(query).getOrNull()?.videos?.size ?: -1 }
                    ytDlpTimes += ms
                    log("busqueda '$query' yt-dlp   $ms ms  $n videos")
                } else {
                    var n = 0
                    var channel: String? = null
                    val ms = measureTimeMillis {
                        val results = runCatching { newPipe.first(query) }.getOrNull()
                        n = results?.videos?.size ?: -1
                        channel = results?.videos?.firstOrNull()?.channelUrl
                    }
                    newPipeTimes += ms
                    log("busqueda '$query' NewPipe  $ms ms  $n videos  canal del primero: $channel")
                }
            }
        }
        log("RESUMEN busqueda yt-dlp  : ${ytDlpTimes.summary()}")
        log("RESUMEN busqueda NewPipe : ${newPipeTimes.summary()}")

        val channels = NewPipeChannels(client)
        var result: Result<*>? = null
        var ms = measureTimeMillis { result = channels.open(null, "https://www.youtube.com/watch?v=dQw4w9WgXcQ") }
        log("canal desde un video: $ms ms  ${result?.getOrNull()?.let { describe(it) } ?: "FALLO ${result?.exceptionOrNull()}"}")
        ms = measureTimeMillis { result = channels.more("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw") }
        log("canal, pagina siguiente: $ms ms  ${result?.getOrNull()?.let { describe(it) } ?: "FALLO ${result?.exceptionOrNull()}"}")
    }

    private fun describe(value: Any): String = when (value) {
        is Pair<*, *> -> "${(value.first as OnlineChannel).let { "${it.name}, ${it.subscriberCount} subs, verificado=${it.isVerified}, avatar=${it.avatarUrl != null}" }} / ${describe(value.second!!)}"
        is VideoPage -> "${value.videos.size} videos (hay mas: ${value.hasMore}), primero: ${value.videos.firstOrNull()?.title}"
        else -> value.toString()
    }

    /** Pide el primer trozo del archivo como lo haría el reproductor: sin y con el User-Agent del cliente. */
    private fun probe(url: String): String {
        fun get(userAgent: String?): String = runCatching {
            val request = Request.Builder().url(url).header("Range", "bytes=0-99999")
                .apply { if (userAgent != null) header("User-Agent", userAgent) }
                .build()
            client.newCall(request).execute().use { "${it.code}/${it.body.bytes().size}B" }
        }.getOrElse { "error ${it.javaClass.simpleName}" }
        return "normal=${get(null)} con-UA=${get(userAgentFor(url))}"
    }

    private fun userAgentFor(url: String): String? = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
        YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
        else -> null
    }

    private fun clientOf(url: String) = Regex("[?&]c=([A-Z_]+)").find(url)?.groupValues?.get(1) ?: "?"

    private fun StreamSource.describe(): String = when (this) {
        is StreamSource.Separate -> "imagen itag=${itag(videoUrl)} + sonido itag=${itag(audioUrl)}"
        is StreamSource.Single -> "un archivo itag=${itag(url)}"
    }

    private fun itag(url: String) = Regex("[?&]itag=(\\d+)").find(url)?.groupValues?.get(1) ?: "?"

    private fun List<Long>.summary(): String {
        if (isEmpty()) return "sin datos"
        val sorted = sorted()
        return "mediana ${sorted[size / 2]} ms, min ${sorted.first()}, max ${sorted.last()} (${joinToString()})"
    }

    private fun log(text: String) {
        Log.i("Benchmark", text)
    }
}
