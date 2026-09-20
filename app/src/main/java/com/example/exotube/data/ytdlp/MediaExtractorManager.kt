package com.example.exotube.data.ytdlp

import android.content.Context
import android.util.Log
import com.example.exotube.domain.model.DownloadProgress
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.repository.MediaDownloader
import com.example.exotube.domain.repository.MediaRepository
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Motor basado en yt-dlp. Implementa [MediaRepository] (analizar enlaces) y [MediaDownloader]
 * (descargar), así que el resto de la app lo usa sin saber que existe yt-dlp.
 *
 * yt-dlp es un programa de Python que corre como PROCESO aparte: cada llamada bloquea el hilo
 * durante segundos (o minutos). Por eso todo el trabajo pesado ocurre en Dispatchers.IO.
 */
class MediaExtractorManager(context: Context) : MediaRepository, MediaDownloader {

    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "yt-dlp")
    private val initMutex = Mutex()

    @Volatile
    private var isInitialized = false

    /**
     * Descomprime Python, yt-dlp y FFmpeg (solo la primera vez tarda unos segundos).
     * Es seguro llamarlo muchas veces y desde varias corrutinas a la vez.
     */
    suspend fun initialize() {
        if (isInitialized) return
        // Doble comprobación con Mutex: si dos corrutinas llegan juntas, solo una inicializa.
        initMutex.withLock {
            if (isInitialized) return
            withContext(Dispatchers.IO) {
                YoutubeDL.init(appContext)
                FFmpeg.init(appContext)
            }
            isInitialized = true
        }
    }

    /** Para el arranque de la app: prepara el motor en segundo plano y nunca lanza. */
    suspend fun warmUp() {
        try {
            initialize()
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "No se pudo preparar yt-dlp", e)
        }
    }

    override suspend fun fetchMediaInfo(url: String): Result<MediaInfo> = try {
        initialize()
        // runInterruptible: si la corrutina se cancela (el usuario cierra la hoja), interrumpe
        // el hilo y youtubedl-android mata el proceso de yt-dlp. Sin esto seguiría corriendo.
        val json = runInterruptible(Dispatchers.IO) {
            YoutubeDL.execute(infoRequest(url), null, null).out
        }
        // Parsear JSON grande es trabajo de CPU: Dispatchers.Default.
        val media = withContext(Dispatchers.Default) {
            YtDlpMapper.toMediaInfo(YtDlpJson.decodeFromString<YtDlpInfoDto>(json), url)
        }
        Result.success(media)
    } catch (e: CancellationException) {
        throw e // Nunca "tragarse" la cancelación: rompería la concurrencia estructurada.
    } catch (e: Exception) {
        Log.w(TAG, "Falló la extracción", e)
        Result.failure(YtDlpErrorMapper.map(e))
    }

    override suspend fun download(
        request: DownloadRequest,
        outputDir: File,
        onProgress: (DownloadProgress) -> Unit,
    ): File = try {
        initialize()
        outputDir.mkdirs()
        runInterruptible(Dispatchers.IO) {
            YoutubeDL.execute(downloadRequest(request, outputDir), null) { percent, eta, line ->
                YtDlpMapper.toDownloadProgress(percent, eta, line)?.let(onProgress)
            }
        }
        // La carpeta es exclusiva de esta descarga: el único archivo que queda es el resultado.
        outputDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.length() }
            ?: throw IOException("yt-dlp terminó sin generar ningún archivo")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Falló la descarga", e)
        throw YtDlpErrorMapper.map(e)
    }

    /** Descarga la última versión estable de yt-dlp (los sitios cambian cada pocas semanas). */
    suspend fun updateEngine(): YoutubeDL.UpdateStatus? {
        initialize()
        return withContext(Dispatchers.IO) {
            YoutubeDL.updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
        }
    }

    private fun infoRequest(url: String) = YoutubeDLRequest(url)
        .addOption("--dump-single-json") // imprime la metadata como JSON y no descarga nada
        .addOption("--no-playlist") // watch?v=X&list=Y → solo el video X
        .addOption("--flat-playlist") // en un perfil/canal no analiza cada video
        .addOption("--playlist-items", "1-10") // corta canales enormes; basta para carruseles
        .addOption("--socket-timeout", 20)
        // Guarda en caché datos de YouTube entre ejecuciones: la 2.ª extracción es más rápida.
        .addOption("--cache-dir", cacheDir.absolutePath)

    private fun downloadRequest(request: DownloadRequest, outputDir: File): YoutubeDLRequest {
        val format = request.format
        val ytRequest = YoutubeDLRequest(request.url)
            .addOption("-f", format.formatId)
            .addOption("-o", File(outputDir, "%(title).100B.%(ext)s").absolutePath)
            .addOption("--no-playlist")
            .addOption("--newline") // una línea por actualización de progreso
            .addOption("--no-mtime") // fecha = hoy; si no, la galería lo ordena como un archivo antiguo
            .addOption("--socket-timeout", 20)
            .addOption("--cache-dir", cacheDir.absolutePath)
        request.playlistIndex?.let { ytRequest.addOption("--playlist-items", it) }

        when (format.type) {
            MediaType.VIDEO -> ytRequest
                .addOption("--merge-output-format", "mp4") // video + audio separados → un .mp4
                .addOption("--remux-video", "mp4") // un .webm suelto → .mp4 (sin recodificar)
            MediaType.AUDIO -> ytRequest
                .addOption("-x") // extraer solo el audio (FFmpeg)
                .addOption("--audio-format", format.extension)
                .addOption("--audio-quality", if (format.extension == "mp3") "${YtDlpMapper.MP3_BITRATE_KBPS}K" else "0")
                .addOption("--embed-metadata") // título/artista visibles en apps de música
        }
        return ytRequest
    }

    private companion object {
        const val TAG = "MediaExtractor"
    }
}
