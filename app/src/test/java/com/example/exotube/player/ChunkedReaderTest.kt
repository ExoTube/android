package com.example.exotube.player

import androidx.media3.common.C
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Leer a trozos tiene que dar EXACTAMENTE los mismos bytes que leer de una vez: un byte de más o
 * de menos en la costura entre dos trozos y el video se vería roto justo ahí.
 *
 * Se usan trozos de 10 bytes en vez de 10 MB: la lógica es la misma y el test va al instante.
 */
class ChunkedReaderTest {

    private val file = ByteArray(25) { it.toByte() }

    /** Un servidor de mentira que respeta los rangos y apunta cada petición. */
    private class FakeServer(private val file: ByteArray, private val sendsTotal: Boolean = true) : RangeSource {
        val requests = mutableListOf<Pair<Long, Long>>()
        private var cursor = 0
        private var until = 0

        override fun open(position: Long, length: Long): Long {
            requests += position to length
            if (position >= file.size) return 0
            cursor = position.toInt()
            until = minOf(file.size.toLong(), position + length).toInt()
            return (until - cursor).toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (cursor >= until) return C.RESULT_END_OF_INPUT
            val n = minOf(length, until - cursor, 4) // de 4 en 4, como una red de verdad: a trocitos
            System.arraycopy(file, cursor, buffer, offset, n)
            cursor += n
            return n
        }

        override fun close() = Unit

        override fun totalLength(): Long? = if (sendsTotal) file.size.toLong() else null
    }

    private fun ChunkedReader.readAll(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(7)
        while (true) {
            val n = read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) return out.toByteArray()
            out.write(buffer, 0, n)
        }
    }

    @Test
    fun `leer a trozos da el archivo entero sin costuras`() {
        val server = FakeServer(file)
        val reader = ChunkedReader(server, chunkBytes = 10)

        val length = reader.open(0, C.LENGTH_UNSET.toLong())

        assertEquals("el tamaño se sabe desde el primer trozo", 25L, length)
        assertArrayEquals(file, reader.readAll())
    }

    /** Ninguna petición pasa de un trozo: es justo lo que evita que YouTube frene. */
    @Test
    fun `ninguna peticion es mayor que un trozo`() {
        val server = FakeServer(file)
        val reader = ChunkedReader(server, chunkBytes = 10)
        reader.open(0, C.LENGTH_UNSET.toLong())
        reader.readAll()

        assertEquals(listOf(0L to 10L, 10L to 10L, 20L to 5L), server.requests)
    }

    /** Al adelantar el video se empieza a mitad del archivo. */
    @Test
    fun `empezar a mitad del archivo`() {
        val server = FakeServer(file)
        val reader = ChunkedReader(server, chunkBytes = 10)

        assertEquals(18L, reader.open(7, C.LENGTH_UNSET.toLong()))
        assertArrayEquals(file.copyOfRange(7, 25), reader.readAll())
        assertEquals(listOf(7L to 10L, 17L to 8L), server.requests)
    }

    /** Si se pide un rango concreto, no se lee ni un byte de más. */
    @Test
    fun `un rango concreto se respeta`() {
        val reader = ChunkedReader(FakeServer(file), chunkBytes = 10)

        assertEquals(12L, reader.open(3, 12))
        assertArrayEquals(file.copyOfRange(3, 15), reader.readAll())
    }

    /** Un servidor que no dice el tamaño total: se sigue pidiendo hasta que llega menos de un trozo. */
    @Test
    fun `sin tamano total tambien llega al final`() {
        val server = FakeServer(file, sendsTotal = false)
        val reader = ChunkedReader(server, chunkBytes = 10)

        assertEquals(C.LENGTH_UNSET.toLong(), reader.open(0, C.LENGTH_UNSET.toLong()))
        assertArrayEquals(file, reader.readAll())
    }

    /** Un archivo que cabe justo en trozos exactos: el siguiente se pide, llega vacío y se acaba. */
    @Test
    fun `archivo del tamano exacto de los trozos`() {
        val exact = ByteArray(20) { it.toByte() }
        val reader = ChunkedReader(FakeServer(exact, sendsTotal = false), chunkBytes = 10)

        reader.open(0, C.LENGTH_UNSET.toLong())

        assertArrayEquals(exact, reader.readAll())
    }

    @Test
    fun `tamano total en la cabecera Content-Range`() {
        assertEquals(146_515L, totalFromContentRange("bytes 0-1023/146515"))
        assertNull("el servidor no lo sabe", totalFromContentRange("bytes 0-1023/*"))
        assertNull(totalFromContentRange("basura"))
    }
}
