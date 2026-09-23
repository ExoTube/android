package com.example.exotube.data.newpipe

import com.example.exotube.data.ytdlp.YtDlpMapper
import com.example.exotube.domain.model.MediaFormat
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.MediaFormat as NewPipeFormat

/**
 * Reconoce un enlace de YouTube para la hoja de descarga: título, miniatura, duración y las
 * calidades que se pueden bajar.
 *
 * Antes lo hacía yt-dlp, y tardaba lo mismo que al reproducir en línea: arrancar Python y varias
 * consultas a YouTube, de 5 a 10 segundos mirando la hoja vacía. NewPipe hace una sola consulta
 * desde la propia app y responde en uno o dos.
 *
 * La descarga en sí la sigue haciendo yt-dlp, que sabe unir imagen y sonido y convertir a MP3.
 * Para que ambos hablen el mismo idioma, cada calidad se describe con el número de formato de
 * YouTube (el "itag"), que es justo lo que yt-dlp entiende.
 */
class NewPipeMediaInfo(private val client: OkHttpClient) {

    /**
     * La metadata de [url], o null si no es un video de YouTube (TikTok, Instagram…) o si no trae
     * ningún formato. Lanza si YouTube o la red fallan: quien llama prueba entonces con yt-dlp.
     */
    suspend fun fetch(url: String): MediaInfo? {
        if (Platform.fromUrl(url) != Platform.YOUTUBE) return null
        // runInterruptible: si el usuario cierra la hoja, la consulta se corta en el acto.
        return runInterruptible(Dispatchers.IO) {
            NewPipeSetup.ensure(client)
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            val durationSeconds = info.duration.takeIf { it > 0 }
            val formats = downloadFormats(
                videos = (info.videoOnlyStreams + info.videoStreams).mapNotNull { it.toItagVideo() },
                audios = info.audioStreams.mapNotNull { it.toItagAudio() },
                durationSeconds = durationSeconds,
            )
            if (formats.isEmpty()) return@runInterruptible null
            MediaInfo(
                sourceUrl = url,
                platform = Platform.YOUTUBE,
                title = info.name?.takeIf { it.isNotBlank() } ?: url,
                thumbnailUrl = bestThumbnail(info.thumbnails.map { it.url to it.width }),
                durationSeconds = durationSeconds,
                formats = formats,
            )
        }
    }
}

/** Un formato de imagen de YouTube, ya sin las clases de NewPipe (así se prueba sin internet). */
internal data class ItagVideo(
    val itag: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    /** H.264: se reproduce en cualquier teléfono, VP9 y AV1 no siempre. */
    val isH264: Boolean,
    /** Los formatos "todo en uno" (360p) ya traen el sonido; los demás son solo imagen. */
    val hasAudio: Boolean,
    val bitrate: Int,
    val bytes: Long?,
)

/** Un formato de solo sonido. */
internal data class ItagAudio(
    val itag: Int,
    val isM4a: Boolean,
    val bitrate: Int,
    val bytes: Long?,
    /** false en los doblajes: el peso que se enseña debe ser el del idioma original. */
    val isOriginal: Boolean = true,
)

/**
 * Las opciones de la hoja de descarga, con el mismo aspecto que las de yt-dlp
 * (ver `data.ytdlp.YtDlpMapper`): una por calidad de imagen, de mayor a menor, y luego MP3 y M4A.
 *
 * Cada selector empieza por el itag exacto que vio NewPipe y sigue con alternativas por tamaño:
 * yt-dlp pregunta a YouTube por su cuenta al descargar y, muy de vez en cuando, le ofrecen una
 * lista algo distinta. Con la alternativa, la descarga sale igual en la misma calidad.
 */
internal fun downloadFormats(
    videos: List<ItagVideo>,
    audios: List<ItagAudio>,
    durationSeconds: Long?,
): List<MediaFormat> {
    val bestAudio = audios.maxWithOrNull(compareBy({ it.isOriginal }, { it.isM4a }, { it.bitrate }))

    val videoOptions = videos
        .filter { it.itag > 0 && it.quality > 0 }
        .distinctBy { it.itag }
        .groupBy { it.quality }
        .entries
        .sortedByDescending { it.key }
        .map { (quality, candidates) ->
            val best = candidates.maxWith(compareBy({ it.isH264 }, { !it.hasAudio }, { it.fps }, { it.bitrate }))
            best.toOption(quality, bestAudio)
        }

    val hasAudio = bestAudio != null || videos.any { it.hasAudio }
    val audioOptions = if (!hasAudio) emptyList() else listOf(
        MediaFormat("ba/b", MediaType.AUDIO, "MP3", "mp3", durationSeconds?.let { it * YtDlpMapper.MP3_BITRATE_KBPS * 1000 / 8 }),
        MediaFormat("ba[ext=m4a]/ba/b", MediaType.AUDIO, "M4A", "m4a", bestAudio?.takeIf { it.isM4a }?.bytes),
    )
    return videoOptions + audioOptions
}

/** Calidad = lado corto: un Short vertical de 1080x1920 es "1080p", como en yt-dlp. */
private val ItagVideo.quality: Int
    get() = listOf(width, height).filter { it > 0 }.minOrNull() ?: 0

private fun ItagVideo.toOption(quality: Int, bestAudio: ItagAudio?): MediaFormat {
    // El mismo tamaño de imagen que el itag, por si yt-dlp no lo encuentra. Se limita alto Y ancho
    // para que valga igual con videos horizontales que con Shorts verticales.
    val sameSize = "[height<=$height][width<=$width]"
    val selector = if (hasAudio) {
        "$itag/b$sameSize/b"
    } else {
        "$itag+ba[ext=m4a]/$itag+ba/bv*$sameSize+ba/b$sameSize/b"
    }
    val size = when {
        hasAudio -> bytes
        else -> bytes?.let { video -> bestAudio?.bytes?.let { audio -> video + audio } }
    }
    val fpsSuffix = fps.takeIf { it >= 50 }?.toString().orEmpty()
    return MediaFormat(selector, MediaType.VIDEO, "${quality}p$fpsSuffix", "mp4", size)
}

private fun VideoStream.toItagVideo(): ItagVideo? {
    val item = itagItem ?: return null
    return ItagVideo(
        itag = itag,
        width = width,
        height = height,
        fps = fps,
        isH264 = codec.orEmpty().startsWith("avc1") || format == NewPipeFormat.MPEG_4 && codec.isNullOrBlank(),
        hasAudio = !isVideoOnly,
        bitrate = bitrate,
        bytes = item.contentLength.takeIf { it > 0 },
    )
}

private fun AudioStream.toItagAudio(): ItagAudio? {
    val item = itagItem ?: return null
    return ItagAudio(
        itag = itag,
        isM4a = format == NewPipeFormat.M4A,
        bitrate = averageBitrate.takeIf { it > 0 } ?: bitrate,
        bytes = item.contentLength.takeIf { it > 0 },
        isOriginal = audioTrackType == null || audioTrackType == AudioTrackType.ORIGINAL,
    )
}
