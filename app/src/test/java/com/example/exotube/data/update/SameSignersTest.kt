package com.example.exotube.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La comparación de firmas que decide si una actualización descargada se puede instalar. */
class SameSignersTest {

    private val ours = setOf("e8153b5f7e6d78a0a596a95a11e81c6cd6bb77ec628c1c09652bade7755e4c62")

    @Test
    fun `la misma llave se acepta`() {
        assertTrue(sameSigners(ours, ours))
    }

    @Test
    fun `otra llave se rechaza, aunque el APK diga llamarse ExoTube`() {
        assertFalse(sameSigners(setOf("0000"), ours))
    }

    @Test
    fun `una llave de más tampoco vale`() {
        assertFalse(sameSigners(ours + "0000", ours))
    }

    @Test
    fun `si no se pudo leer la firma, no se instala`() {
        assertFalse(sameSigners(emptySet(), emptySet()))
        assertFalse(sameSigners(emptySet(), ours))
    }
}
