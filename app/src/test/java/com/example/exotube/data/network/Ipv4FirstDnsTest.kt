package com.example.exotube.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

/**
 * El orden en que el reproductor prueba las IP. De él depende que YouTube acepte la descarga:
 * la dirección del video va atada a la IPv4 con la que la pidió yt-dlp.
 *
 * Las direcciones se crean a partir de sus bytes, así que el test no toca la red.
 */
class Ipv4FirstDnsTest {

    private val v4a = InetAddress.getByAddress(byteArrayOf(142.toByte(), 250.toByte(), 1, 1))
    private val v4b = InetAddress.getByAddress(byteArrayOf(142.toByte(), 250.toByte(), 1, 2))
    private val v6a = InetAddress.getByAddress(ByteArray(16) { if (it == 0) 0x26 else it.toByte() })
    private val v6b = InetAddress.getByAddress(ByteArray(16) { if (it == 0) 0x2a else it.toByte() })

    /** Lo que suele contestar el DNS con datos móviles: las IPv6 primero. Hay que darle la vuelta. */
    @Test
    fun `la ipv4 pasa delante de la ipv6`() {
        assertEquals(listOf(v4a, v6a), preferIpv4(listOf(v6a, v4a)))
    }

    /** Las IPv6 no se tiran: si la IPv4 no conectara, son el plan B. */
    @Test
    fun `las ipv6 se quedan detras`() {
        assertEquals(4, preferIpv4(listOf(v6a, v4a, v6b, v4b)).size)
    }

    /** Dentro de cada tipo se respeta el orden del DNS, que ya elige el servidor más cercano. */
    @Test
    fun `dentro de cada tipo se conserva el orden`() {
        assertEquals(listOf(v4a, v4b, v6b, v6a), preferIpv4(listOf(v6b, v4a, v6a, v4b)))
    }

    @Test
    fun `solo ipv6 se deja como esta`() {
        assertEquals(listOf(v6b, v6a), preferIpv4(listOf(v6b, v6a)))
    }
}
