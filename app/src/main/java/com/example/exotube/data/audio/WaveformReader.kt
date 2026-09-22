package com.example.exotube.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import android.util.LruCache
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Saca la forma de onda de una canción: lo alto que suena en cada tramo.
 *
 * Es la onda DE VERDAD, sacada de decodificar el archivo, no un dibujo bonito inventado. Así la
 * barra del reproductor enseña de un vistazo dónde están los bajones y dónde entra el estribillo,
 * que es justo lo que sirve para saber a qué parte saltar.
 *
 * ¿Por qué no un analizador de espectro en vivo, que sería lo obvio? Porque en Android 9 y
 * posteriores la API que lo permite ([android.media.audiofx.Visualizer]) exige el permiso de
 * MICRÓFONO. Pedirle el micrófono a la gente para dibujar unas barritas es un trato pésimo y
 * además da mala espina, con razón. Esta forma no pide ningún permiso, funciona sin internet y
 * encima sigue ahí con la música en pausa.
 */
class WaveformReader(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Decodificar una canción entera cuesta un par de segundos, y es normal volver a la misma
     * canción todo el rato. Con guardar unas pocas basta: cada una son [BUCKETS] números.
     */
    private val cache = LruCache<String, FloatArray>(CACHED_SONGS)

    /** La onda ya calculada, o null si todavía no se ha pedido para esta canción. */
    fun cached(uri: String): FloatArray? = cache.get(uri)

    /**
     * @return [BUCKETS] niveles entre 0 y 1, o null si el archivo no se pudo leer (formato raro,
     *   archivo a medio descargar, sin permiso). Entonces la barra se queda como estaba.
     */
    suspend fun read(uri: String): FloatArray? {
        cache.get(uri)?.let { return it }
        val levels = withContext(Dispatchers.IO) { decode(uri) } ?: return null
        cache.put(uri, levels)
        return levels
    }

    private suspend fun decode(uri: String): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(appContext, uri.toUri(), null)
            val track = extractor.audioTrack() ?: return null
            extractor.selectTrack(track)

            val format = extractor.getTrackFormat(track)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            if (durationUs <= 0) return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            decodeIntoBuckets(extractor, codec, durationUs)
        } catch (e: Exception) {
            // Un archivo corrupto o un formato que este teléfono no decodifica no es un error que
            // valga la pena contarle a nadie: simplemente no hay onda que enseñar.
            Log.i(TAG, "Sin forma de onda para $uri: ${e.message}")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Recorre la canción de principio a fin repartiendo lo que suena en [BUCKETS] tramos.
     *
     * Se decodifica entera y de una pasada, sin ir saltando de tramo en tramo: saltar obliga a
     * vaciar el decodificador cada vez y en un MP3 ni siquiera cae donde le pides, así que lo
     * simple sale más rápido y más exacto.
     */
    private suspend fun decodeIntoBuckets(
        extractor: MediaExtractor,
        codec: MediaCodec,
        durationUs: Long,
    ): FloatArray {
        val sums = DoubleArray(BUCKETS)
        val counts = LongArray(BUCKETS)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        // Red de seguridad: un archivo corrupto puede dejar al decodificador sin dar nunca la
        // señal de fin, y entonces esto giraría para siempre comiendo un núcleo y la batería.
        // Al pasar de tantas vueltas seguidas sin recibir nada, se abandona con lo que haya.
        var idleRounds = 0

        while (!outputDone && idleRounds < MAX_IDLE_ROUNDS) {
            // Si el usuario cambia de canción, esto se cancela y no seguimos gastando batería.
            coroutineContext.ensureActive()

            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> idleRounds++
                else -> if (index >= 0) {
                    idleRounds = 0
                    val bucket = bucketOf(info.presentationTimeUs, durationUs)
                    codec.getOutputBuffer(index)?.let { buffer ->
                        accumulate(buffer, info, sums, counts, bucket)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        return normalize(sums, counts)
    }

    /**
     * Acumula el "volumen" de este trozo de sonido.
     *
     * Se suman los cuadrados de las muestras (lo que se llama RMS) y no los valores tal cual,
     * porque una onda de sonido sube y baja alrededor de cero: sumándola a pelo daría casi cero
     * siempre, por fuerte que sonara.
     */
    private fun accumulate(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        sums: DoubleArray,
        counts: LongArray,
        bucket: Int,
    ) {
        // El búfer que devuelve el decodificador puede ser más grande que lo que acaba de
        // producir: hay que quedarse solo con el trozo válido o se contaría basura como sonido.
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val samples = buffer.slice().order(ByteOrder.nativeOrder()).asShortBuffer()

        var sum = 0.0
        var read = 0
        // Una muestra de cada [SAMPLE_STEP]: para dibujar una barra no hace falta mirarlas todas,
        // y así una canción entera se procesa en un instante en vez de en segundos.
        var i = 0
        while (i < samples.limit()) {
            val value = samples.get(i) / Short.MAX_VALUE.toDouble()
            sum += value * value
            read++
            i += SAMPLE_STEP
        }
        if (read > 0) {
            sums[bucket] += sum
            counts[bucket] += read
        }
    }

    private fun bucketOf(presentationTimeUs: Long, durationUs: Long): Int =
        ((presentationTimeUs.toDouble() / durationUs) * BUCKETS)
            .toInt()
            .coerceIn(0, BUCKETS - 1)

    private fun MediaExtractor.audioTrack(): Int? = (0 until trackCount).firstOrNull { track ->
        getTrackFormat(track).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    }

    private companion object {
        const val TAG = "WaveformReader"

        /** Cuántas barras tiene la onda. Más no se distinguirían en una pantalla de teléfono. */
        const val BUCKETS = 64

        const val CACHED_SONGS = 8
        const val TIMEOUT_US = 10_000L

        /** Vueltas seguidas sin recibir sonido antes de rendirse. Con [TIMEOUT_US], unos 5 s. */
        const val MAX_IDLE_ROUNDS = 500
        const val SAMPLE_STEP = 16
    }
}

/**
 * Convierte las sumas en alturas de 0 a 1.
 *
 * Se divide por el tramo MÁS ALTO de esta canción, no por el máximo teórico: una grabación suave
 * daría si no una onda plana pegada al suelo, y lo que interesa es ver su forma, no compararla
 * con otras canciones.
 *
 * Función aparte para poder comprobarla con un test, sin decodificar nada.
 */
internal fun normalize(sums: DoubleArray, counts: LongArray): FloatArray {
    val levels = FloatArray(sums.size) { i ->
        if (counts[i] == 0L) 0f else sqrt(sums[i] / counts[i]).toFloat()
    }
    val loudest = levels.maxOrNull() ?: 0f
    if (loudest <= 0f) return levels
    return FloatArray(levels.size) { i -> (levels[i] / loudest).coerceIn(0f, 1f) }
}
