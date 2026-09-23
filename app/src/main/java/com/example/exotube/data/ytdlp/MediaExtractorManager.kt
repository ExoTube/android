package com.example.exotube.data.ytdlp

import android.util.Log
import com.example.exotube.data.newpipe.NewPipeMediaInfo
import com.example.exotube.data.preview.LinkPreviewer
import com.example.exotube.domain.model.DownloadProgress
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.repository.MediaDownloader
import com.example.exotube.domain.repository.MediaRepository
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Analizar enlaces y descargarlos. Implementa [MediaRepository] (qué formatos hay) y
 * [MediaDownloader] (tráete este), así que el resto de la app lo usa sin saber que existe yt-dlp.
 *
 * Quien arranca y ejecuta yt-dlp es [YtDlpEngine]; aquí solo se decide QUÉ pedirle.
 */
class MediaExtractorManager(
    private val engine: YtDlpEngine,
    /** Reconoce al momento los enlaces de YouTube; sin él (o si falla) se usa yt-dlp. */
    private val quickInfo: NewPipeMediaInfo? = null,
    /** Vista previa al momento de TikTok y X, mientras yt-dlp termina el análisis completo. */
    private val previewer: LinkPreviewer? = null,
    /** Guarda el análisis de yt-dlp para que la descarga no tenga que repetirlo. */
    private val infoCache: InfoJsonCache? = null,
) : MediaRepository, MediaDownloader {

    override suspend fun previewMediaInfo(url: String): MediaInfo? {
        val quick = previewer ?: return null
        return try {
            withTimeoutOrNull(PREVIEW_TIMEOUT_MS) { quick.preview(url) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin vista previa no pasa nada: la hoja espera a yt-dlp, como antes.
            Log.w(TAG, "Sin vista previa rápida", e)
            null
        }
    }

    override suspend fun fetchMediaInfo(url: String): Result<MediaInfo> {
        quickMediaInfo(url)?.let { return Result.success(it) }
        return slowMediaInfo(url)
    }

    /**
     * El camino rápido, solo para YouTube. Cualquier problema (un video raro, un cambio de
     * YouTube, una red lenta) no se enseña: se devuelve null y lo intenta yt-dlp, como siempre.
     */
    private suspend fun quickMediaInfo(url: String): MediaInfo? {
        val quick = quickInfo ?: return null
        return try {
            withTimeoutOrNull(QUICK_TIMEOUT_MS) { quick.fetch(url) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe no reconoció el enlace; se prueba con yt-dlp", e)
            null
        }
    }

    private suspend fun slowMediaInfo(url: String): Result<MediaInfo> = try {
        val json = engine.run(infoRequest(url)).out
        // Parsear JSON grande es trabajo de CPU: Dispatchers.Default.
        val info = withContext(Dispatchers.Default) { YtDlpJson.decodeFromString<YtDlpInfoDto>(json) }
        val media = YtDlpMapper.toMediaInfo(info, url)
        // Solo un video suelto: una lista (carrusel, perfil) llega sin analizar del todo y la
        // descarga tendría que completarla de todos modos.
        if (info.type != "playlist") infoCache?.save(url, json)
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
    ): File {
        val saved = if (request.playlistIndex == null) infoCache?.freshFileFor(request.url) else null
        if (saved != null) {
            try {
                return runDownload(request, outputDir, onProgress, saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Lo normal es que hayan caducado los enlaces del análisis: desde el enlace, otra vez.
                Log.w(TAG, "No sirvió el análisis guardado; se descarga desde el enlace", e)
                infoCache?.forget(request.url)
                outputDir.deleteRecursively()
            }
        }
        return runDownload(request, outputDir, onProgress, savedInfo = null)
    }

    private suspend fun runDownload(
        request: DownloadRequest,
        outputDir: File,
        onProgress: (DownloadProgress) -> Unit,
        savedInfo: File?,
    ): File = try {
        outputDir.mkdirs()
        engine.run(downloadRequest(request, outputDir, savedInfo)) { percent, eta, line ->
            YtDlpMapper.toDownloadProgress(percent, eta, line)?.let(onProgress)
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

    private fun infoRequest(url: String) = engine.newRequest(url)
        .addOption("--dump-single-json") // imprime la metadata como JSON y no descarga nada
        .addOption("--no-playlist") // watch?v=X&list=Y → solo el video X
        .addOption("--flat-playlist") // en un perfil/canal no analiza cada video
        .addOption("--playlist-items", "1-10") // corta canales enormes; basta para carruseles

    private fun downloadRequest(request: DownloadRequest, outputDir: File, savedInfo: File?): YoutubeDLRequest {
        val format = request.format
        // Con el análisis guardado, yt-dlp no mira el enlace: lee el archivo y descarga.
        val ytRequest = (if (savedInfo != null) engine.newRequest("").addOption("--load-info-json", savedInfo.absolutePath) else engine.newRequest(request.url))
            .addOption("-f", format.formatId)
            .addOption("-o", File(outputDir, "%(title).100B.%(ext)s").absolutePath)
            .addOption("--no-playlist")
            .addOption("--newline") // una línea por actualización de progreso
            .addOption("--no-mtime") // fecha = hoy; si no, la galería lo ordena como un archivo antiguo
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
                // La miniatura del video queda dentro del MP3 como portada del disco.
                .addOption("--embed-thumbnail")
                // YouTube sirve las miniaturas en WEBP y muchos reproductores no lo entienden:
                // FFmpeg (que ya va dentro de la app) las convierte a JPG antes de incrustarlas.
                .addOption("--convert-thumbnails", "jpg")
        }
        return ytRequest
    }

    private companion object {
        const val TAG = "MediaExtractor"

        /** Lo normal son 1 o 2 s; si pasa de aquí, algo va mal y es mejor probar con yt-dlp. */
        const val QUICK_TIMEOUT_MS = 8_000L

        /** La vista previa solo sirve si llega bastante antes que yt-dlp (unos 5 s). */
        const val PREVIEW_TIMEOUT_MS = 4_000L
    }
}
