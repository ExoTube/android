package com.example.exotube

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.exotube.data.network.Ipv4FirstDns
import com.example.exotube.data.newpipe.NewPipeChannels
import com.example.exotube.data.newpipe.NewPipeSearch
import com.example.exotube.data.newpipe.NewPipeStreamResolver
import com.example.exotube.data.ytdlp.YtDlpCatalog
import com.example.exotube.data.ytdlp.YtDlpEngine
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
