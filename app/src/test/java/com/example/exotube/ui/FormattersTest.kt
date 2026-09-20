package com.example.exotube.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `duracion de una cancion`() {
        assertEquals("4:52", formatDuration(292))
        assertEquals("1:02:05", formatDuration(3725))
        assertEquals("0:00", formatDuration(-5))
    }

    @Test
    fun `duracion total de una playlist se redondea al minuto`() {
        assertEquals("5 min", formatTotalDuration(292_000)) // 4:52
        assertEquals("1 min", formatTotalDuration(10_000)) // nunca "0 min" si hay música
        assertEquals("1 h 5 min", formatTotalDuration(3_900_000))
        assertEquals("0 min", formatTotalDuration(0))
    }
}
