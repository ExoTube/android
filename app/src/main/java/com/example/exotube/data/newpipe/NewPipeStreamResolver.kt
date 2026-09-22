package com.example.exotube.data.newpipe

import com.example.exotube.domain.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Pide a YouTube los enlaces de un video con NewPipeExtractor, la librería de la app NewPipe.
 *
 * ¿Por qué además de yt-dlp? yt-dlp es un programa de Python: cada vez que se usa, el teléfono
 * arranca un Python entero, y luego yt-dlp consulta a YouTube varias veces para ir sobre seguro.
 * Son varios segundos por video. NewPipeExtractor vive dentro de la app y hace una sola consulta,
 * así que tarda mucho menos. yt-dlp sigue ahí de respaldo por si esto falla algún día.
 */
class NewPipeStreamResolver(private val client: OkHttpClient) {

    /**
     * Los enlaces de [url] con la mejor imagen que no pase de [maxHeight], o solo el sonido con
     * [audioOnly]. null si no es un video de YouTube o si no hay ningún formato que nos sirva.
     *
     * Lanza si YouTube o la red fallan: quien llama decide si prueba con yt-dlp.
     */
    suspend fun resolve(url: String, audioOnly: Boolean, maxHeight: Int): StreamSource? {
        if (!isYouTube(url)) return null
        // runInterruptible: si el usuario toca otro video, se corta la consulta en marcha.
        return runInterruptible(Dispatchers.IO) {
            NewPipeSetup.ensure(client)
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            pickStream(
                videoOnly = info.videoOnlyStreams.mapNotNull { it.toOption() },
                audio = info.audioStreams.mapNotNull { it.toOption() },
                muxed = info.videoStreams.mapNotNull { it.toOption() },
                audioOnly = audioOnly,
                maxHeight = maxHeight,
            )
        }
    }

    private companion object {
        fun isYouTube(url: String) = "youtube.com/" in url || "youtu.be/" in url
    }
}

/**
 * Lo que interesa de cada formato que ofrece YouTube, ya sin las clases de NewPipe. Separarlo
 * permite probar la elección con un test, sin internet.
 */
internal data class StreamOption(
    val url: String,
    /** Alto de la imagen en píxeles; 0 en los formatos de solo sonido. */
    val height: Int,
    /** Imagen H.264 o sonido AAC: lo que cualquier teléfono reproduce sin problemas. */
    val isMostCompatible: Boolean,
    val bitrate: Int,
    /** false en los doblajes y audiodescripciones: sin él se colaría otro idioma. */
    val isOriginalAudio: Boolean = true,
)

/**
 * Elige qué formatos reproducir. Sigue el mismo orden que se le pide a yt-dlp
 * (ver `data.ytdlp.videoFormat`):
 *  1. Imagen H.264 hasta [maxHeight] + el mejor sonido, por separado.
 *  2. Cualquier imagen hasta [maxHeight] (VP9, AV1) + el mejor sonido.
 *  3. Un archivo con todo junto (los de 360p que aún ofrece YouTube).
 * Si no hay nada tan pequeño como [maxHeight], se toma lo más pequeño que haya: mejor ver el
 * video algo más nítido de lo pedido que no verlo.
 */
internal fun pickStream(
    videoOnly: List<StreamOption>,
    audio: List<StreamOption>,
    muxed: List<StreamOption>,
    audioOnly: Boolean,
    maxHeight: Int,
): StreamSource? {
    val bestAudio = audio.maxWithOrNull(
        compareBy<StreamOption>({ it.isOriginalAudio }, { it.isMostCompatible }, { it.bitrate }),
    )
    if (audioOnly) return bestAudio?.let { StreamSource.Single(it.url, hasVideo = false) }

    val video = videoOnly.bestUpTo(maxHeight, onlyCompatible = true)
        ?: videoOnly.bestUpTo(maxHeight, onlyCompatible = false)
        ?: videoOnly.minByOrNull { it.height }
    if (video != null && bestAudio != null) return StreamSource.Separate(video.url, bestAudio.url)

    val together = muxed.bestUpTo(maxHeight, onlyCompatible = false) ?: muxed.minByOrNull { it.height }
    return together?.let { StreamSource.Single(it.url, hasVideo = true) }
}

private fun List<StreamOption>.bestUpTo(maxHeight: Int, onlyCompatible: Boolean): StreamOption? =
    filter { it.height in 1..maxHeight && (!onlyCompatible || it.isMostCompatible) }
        .maxWithOrNull(compareBy({ it.height }, { it.bitrate }))

/**
 * Solo sirven los formatos que son un archivo normal descargable por partes (PROGRESSIVE_HTTP):
 * los troceados (DASH, HLS) necesitarían otro lector y, en YouTube, casi siempre un permiso extra.
 */
private fun Stream.isPlainFile(): Boolean = isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

private fun VideoStream.toOption(): StreamOption? = if (!isPlainFile()) null else StreamOption(
    url = content,
    height = height,
    isMostCompatible = format == MediaFormat.MPEG_4 && codec.orEmpty().startsWith("avc1"),
    bitrate = bitrate,
)

private fun AudioStream.toOption(): StreamOption? = if (!isPlainFile()) null else StreamOption(
    url = content,
    height = 0,
    isMostCompatible = format == MediaFormat.M4A,
    bitrate = averageBitrate.takeIf { it > 0 } ?: bitrate,
    isOriginalAudio = audioTrackType == null || audioTrackType == AudioTrackType.ORIGINAL,
)
