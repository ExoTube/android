package com.example.exotube.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Descarga los videos de YouTube a trozos de [CHUNK_BYTES], en vez de pedir el archivo entero de
 * una vez.
 *
 * ¿Por qué? YouTube frena a propósito las descargas largas de una sola petición. Medido: pidiendo
 * el archivo de una vez llegaban 126 KB por segundo, menos de lo que necesita un video a 480p,
 * así que el video se paraba cada poco. Pidiéndolo en trozos de 10 MB, 4,3 MB por segundo: unas
 * 35 veces más. Es lo mismo que hace yt-dlp al descargar de YouTube.
 *
 * Solo se trocea lo que viene de los servidores de video de YouTube (googlevideo.com); lo demás
 * pasa tal cual.
 */
@OptIn(UnstableApi::class)
internal class ChunkedDataSource(
    private val upstream: DataSource,
    chunkBytes: Long = CHUNK_BYTES,
) : DataSource {

    private var spec: DataSpec? = null
    private var isChunked = false
    private val reader = ChunkedReader(UpstreamRanges(), chunkBytes)

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        isChunked = dataSpec.uri.host.orEmpty().endsWith(YOUTUBE_VIDEO_HOST)
        if (!isChunked) return upstream.open(dataSpec)
        return reader.open(dataSpec.position, dataSpec.length)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        if (isChunked) reader.read(buffer, offset, length) else upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        if (isChunked) reader.close() else upstream.close()
    }

    /** Traduce "dame del byte X, Y bytes" a una petición de Media3 sobre el mismo enlace. */
    private inner class UpstreamRanges : RangeSource {

        override fun open(position: Long, length: Long): Long {
            val range = checkNotNull(spec).buildUpon().setPosition(position).setLength(length).build()
            return try {
                upstream.open(range)
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                // 416 = "no hay nada a partir de ahí": se pidió justo después del final.
                if (e.responseCode == RANGE_NOT_SATISFIABLE) 0 else throw e
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)

        override fun close() = upstream.close()

        override fun totalLength(): Long? =
            upstream.responseHeaders.entries
                .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
                ?.value?.firstOrNull()
                ?.let(::totalFromContentRange)
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource())
    }

    private companion object {
        const val YOUTUBE_VIDEO_HOST = "googlevideo.com"
        const val RANGE_NOT_SATISFIABLE = 416

        /** El mismo tamaño que usa yt-dlp: por debajo de lo que YouTube empieza a frenar. */
        const val CHUNK_BYTES = 10L * 1024 * 1024
    }
}

/** Lo mínimo que necesita [ChunkedReader] de una conexión: pedir un trozo y leerlo. */
internal interface RangeSource {
    /** Abre desde [position] hasta [length] bytes. Devuelve cuántos llegarán, o C.LENGTH_UNSET. */
    fun open(position: Long, length: Long): Long

    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    fun close()

    /** El tamaño del archivo entero, si el servidor lo dijo al abrir. */
    fun totalLength(): Long?
}

/**
 * La lógica de los trozos, sin nada de Android para poder probarla con un test.
 *
 * Por fuera se comporta como una sola descarga continua; por dentro, cada vez que se acaba un
 * trozo abre el siguiente justo donde se quedó.
 */
internal class ChunkedReader(private val source: RangeSource, private val chunkBytes: Long) {

    /** Siguiente byte que se va a leer, contando desde el principio del archivo. */
    private var position = 0L

    /** Dónde acaba lo que se pidió (sin incluir); UNKNOWN mientras no se sepa. */
    private var end = UNKNOWN

    /** Dónde acaba el trozo abierto ahora (sin incluir). */
    private var chunkEnd = 0L

    private var isOpen = false

    /** Bytes leídos del trozo abierto. Un trozo que no da ni uno significa que no hay más. */
    private var readFromChunk = 0L

    fun open(position: Long, length: Long): Long {
        this.position = position
        end = if (length == C.LENGTH_UNSET.toLong()) UNKNOWN else position + length
        val start = position
        openNextChunk()
        return if (end == UNKNOWN) C.LENGTH_UNSET.toLong() else end - start
    }

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (end != UNKNOWN && position >= end) return C.RESULT_END_OF_INPUT
            if (position >= chunkEnd) {
                closeChunk()
                if (!openNextChunk()) return C.RESULT_END_OF_INPUT
            }
            val wanted = minOf(length.toLong(), chunkEnd - position).toInt()
            val read = source.read(buffer, offset, wanted)
            if (read == C.RESULT_END_OF_INPUT) {
                // El trozo trajo menos de lo previsto. Si se sabe que queda archivo, se pide el
                // resto; si no, eso era el final. Y si el trozo nuevo no trae nada de nada, se
                // para: volver a pedirlo daría lo mismo, una y otra vez.
                if (end == UNKNOWN || position >= end || readFromChunk == 0L) return C.RESULT_END_OF_INPUT
                chunkEnd = position
                continue
            }
            position += read
            readFromChunk += read
            return read
        }
    }

    fun close() {
        closeChunk()
    }

    /** Abre el trozo que empieza en [position]. false si ya no queda nada que pedir. */
    private fun openNextChunk(): Boolean {
        val length = if (end == UNKNOWN) chunkBytes else minOf(chunkBytes, end - position)
        if (length <= 0) return false
        val opened = source.open(position, length)
        isOpen = true
        readFromChunk = 0
        if (end == UNKNOWN) source.totalLength()?.let { end = it }
        val coming = if (opened == C.LENGTH_UNSET.toLong()) length else minOf(opened, length)
        chunkEnd = position + coming
        // Un trozo vacío quiere decir que el archivo ya se acabó.
        if (coming <= 0) {
            if (end == UNKNOWN) end = position
            return false
        }
        return true
    }

    private fun closeChunk() {
        if (isOpen) source.close()
        isOpen = false
    }

    private companion object {
        const val UNKNOWN = -1L
    }
}

/**
 * El tamaño total del archivo según la cabecera Content-Range ("bytes 0-1023/146515" → 146515).
 * null si no viene o si el servidor no lo sabe (entonces pone un asterisco en lugar del número).
 */
internal fun totalFromContentRange(header: String): Long? =
    header.substringAfterLast('/', missingDelimiterValue = "").trim().toLongOrNull()
