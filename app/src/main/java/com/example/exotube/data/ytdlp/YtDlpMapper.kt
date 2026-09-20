package com.example.exotube.data.ytdlp

import com.example.exotube.domain.model.DownloadProgress
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.MediaFormat
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.Platform
import kotlin.math.roundToInt

/**
 * Convierte la lista cruda de yt-dlp (a veces +50 formatos) en unas pocas opciones claras:
 * una por calidad de video (1080p, 720p…) más MP3 y M4A.
 */
internal object YtDlpMapper {

    /** Bitrate al que se convertirá a MP3 (Fase 4); sirve para estimar el peso. */
    const val MP3_BITRATE_KBPS = 192

    private const val NONE = "none"

    /** @throws MediaError.NoMediaFound si no hay nada descargable. */
    fun toMediaInfo(info: YtDlpInfoDto, sourceUrl: String): MediaInfo {
        // Perfiles, carruseles y tweets múltiples llegan como "playlist": usamos la 1.ª entrada con formatos.
        val video: YtDlpInfoDto
        val playlistIndex: Int?
        if (info.type == "playlist") {
            val index = info.entries.indexOfFirst { !it?.formats.isNullOrEmpty() }
            if (index < 0) throw MediaError.NoMediaFound
            video = info.entries[index]!!
            playlistIndex = index + 1 // yt-dlp cuenta desde 1
        } else {
            video = info
            playlistIndex = null
        }
        val durationSeconds = video.duration?.toLong()?.takeIf { it > 0 }
        val bestAudio = bestAudio(video.formats)
        val formats = videoOptions(video.formats, bestAudio) + audioOptions(video.formats, bestAudio, durationSeconds)
        if (formats.isEmpty()) throw MediaError.NoMediaFound

        return MediaInfo(
            sourceUrl = sourceUrl,
            platform = Platform.fromUrl(sourceUrl),
            title = video.title?.takeIf { it.isNotBlank() } ?: info.title ?: sourceUrl,
            thumbnailUrl = video.thumbnail ?: info.thumbnail,
            durationSeconds = durationSeconds,
            formats = formats,
            playlistIndex = playlistIndex,
        )
    }

    // ---------- Progreso de descarga ----------

    /** Etapas posteriores a la descarga: yt-dlp no informa porcentaje en ellas. */
    private val postProcessorTags = listOf("[Merger]", "[ExtractAudio]", "[VideoRemuxer]", "[Metadata]", "[Fixup")

    /**
     * Interpreta una línea de salida de yt-dlp. youtubedl-android ya extrae [percent] y [etaSeconds]
     * (-1 si aún no los conoce). Devuelve null para líneas que no cambian el progreso.
     */
    fun toDownloadProgress(percent: Float, etaSeconds: Long, line: String): DownloadProgress? = when {
        postProcessorTags.any { line.startsWith(it) } -> DownloadProgress.Processing
        line.startsWith("[download]") && percent >= 0 ->
            DownloadProgress.Downloading(percent, etaSeconds.takeIf { it >= 0 })
        else -> null
    }

    // ---------- Video ----------

    private fun videoOptions(formats: List<YtDlpFormatDto>, bestAudio: YtDlpFormatDto?): List<MediaFormat> {
        val videos = formats.filter { it.isVideo }
        val options = videos
            .filter { it.quality != null }
            .groupBy { it.quality!! }
            .entries
            .sortedByDescending { it.key }
            .map { (quality, candidates) -> candidates.maxWith(videoPreference).toVideoOption(quality, bestAudio) }

        if (options.isNotEmpty() || videos.isEmpty()) return options
        // Hay video pero yt-dlp no informa la resolución (pasa con algunos enlaces de Facebook).
        return listOf(MediaFormat("bv*+ba/b", MediaType.VIDEO, "Auto", "mp4", null))
    }

    /** Entre varios formatos de la misma calidad, ¿cuál es mejor? (el "mayor" gana). */
    private val videoPreference = compareBy<YtDlpFormatDto>(
        { it.codecScore },
        { it.protocolScore },
        { it.fps ?: 0.0 },
        { it.tbr ?: 0.0 },
    )

    private fun YtDlpFormatDto.toVideoOption(quality: Int, bestAudio: YtDlpFormatDto?): MediaFormat {
        // YouTube, Instagram y Facebook separan video y audio (DASH): hay que pedir ambos y unirlos.
        val needsAudio = acodec == NONE && bestAudio != null
        val selector = if (needsAudio) "$formatId+ba[ext=m4a]/$formatId+ba/$formatId" else formatId
        val size = if (needsAudio) sizeBytes?.let { video -> bestAudio.sizeBytes?.let { audio -> video + audio } } else sizeBytes
        val fpsSuffix = fps?.takeIf { it >= 50 }?.roundToInt()?.toString().orEmpty()

        return MediaFormat(selector, MediaType.VIDEO, "${quality}p$fpsSuffix", "mp4", size)
    }

    // ---------- Audio ----------

    private fun audioOptions(
        formats: List<YtDlpFormatDto>,
        bestAudio: YtDlpFormatDto?,
        durationSeconds: Long?,
    ): List<MediaFormat> {
        val hasAudio = bestAudio != null || formats.any { it.isVideo && it.acodec != NONE }
        if (!hasAudio) return emptyList() // p. ej. un GIF de X

        val mp3Size = durationSeconds?.let { it * MP3_BITRATE_KBPS * 1000 / 8 }
        val m4aSize = bestAudio?.takeIf { it.ext == "m4a" }?.sizeBytes
        return listOf(
            MediaFormat("ba/b", MediaType.AUDIO, "MP3", "mp3", mp3Size),
            MediaFormat("ba[ext=m4a]/ba/b", MediaType.AUDIO, "M4A", "m4a", m4aSize),
        )
    }

    private fun bestAudio(formats: List<YtDlpFormatDto>): YtDlpFormatDto? =
        formats
            .filter { it.vcodec == NONE && it.acodec != NONE }
            .maxWithOrNull(compareBy({ it.ext == "m4a" }, { it.protocolScore }, { it.tbr ?: 0.0 }))

    // ---------- Propiedades derivadas ----------

    /** Tiene video si declara un códec de video o, al menos, dimensiones. */
    private val YtDlpFormatDto.isVideo: Boolean
        get() = vcodec != NONE && (vcodec != null || quality != null)

    /** Calidad = lado corto: un TikTok vertical de 720x1280 es "720p", no "1280p". */
    private val YtDlpFormatDto.quality: Int?
        get() = listOfNotNull(width, height).filter { it > 0 }.minOrNull()

    private val YtDlpFormatDto.sizeBytes: Long?
        get() = (filesize ?: filesizeApprox)?.toLong()?.takeIf { it > 0 }

    /** H.264 se reproduce en cualquier teléfono; VP9/AV1/HEVC no siempre. */
    private val YtDlpFormatDto.codecScore: Int
        get() = when {
            vcodec == null -> 2 // desconocido: casi siempre H.264 en MP4 (X, Facebook)
            vcodec.startsWith("avc") || vcodec.startsWith("h264") -> 3
            else -> 1
        }

    /** Una descarga directa es más rápida y fiable que HLS (cientos de fragmentos .ts). */
    private val YtDlpFormatDto.protocolScore: Int
        get() = if (protocol?.startsWith("m3u8") == true) 0 else 1
}
