package com.example.exotube.data.ytdlp

import android.util.Log
import com.example.exotube.domain.model.DownloadProgress
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.repository.MediaDownloader
import com.example.exotube.domain.repository.MediaRepository
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Analizar enlaces y descargarlos. Implementa [MediaRepository] (qué formatos hay) y
 * [MediaDownloader] (tráete este), así que el resto de la app lo usa sin saber que existe yt-dlp.
 *
 * Quien arranca y ejecuta yt-dlp es [YtDlpEngine]; aquí solo se decide QUÉ pedirle.
 */
class MediaExtractorManager(private val engine: YtDlpEngine) : MediaRepository, MediaDownloader {

    override suspend fun fetchMediaInfo(url: String): Result<MediaInfo> = try {
        val json = engine.run(infoRequest(url)).out
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
        outputDir.mkdirs()
        engine.run(downloadRequest(request, outputDir)) { percent, eta, line ->
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

    private fun downloadRequest(request: DownloadRequest, outputDir: File): YoutubeDLRequest {
        val format = request.format
        val ytRequest = engine.newRequest(request.url)
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
    }
}
