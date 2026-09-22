package com.example.exotube.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** La barra del widget: cuánto va lleno según por dónde va la canción. */
class WidgetProgressTest {

    @Test
    fun `al empezar la barra esta vacia`() {
        assertEquals(0, progressOf(positionMs = 0, durationMs = 200_000))
    }

    @Test
    fun `a la mitad va por la mitad`() {
        assertEquals(500, progressOf(positionMs = 100_000, durationMs = 200_000))
    }

    @Test
    fun `al final la barra esta llena`() {
        assertEquals(1_000, progressOf(positionMs = 200_000, durationMs = 200_000))
    }

    /** Un video en línea que aún carga no sabe cuánto dura: la barra espera vacía, no revienta. */
    @Test
    fun `sin duracion la barra esta vacia`() {
        assertEquals(0, progressOf(positionMs = 30_000, durationMs = null))
        assertEquals(0, progressOf(positionMs = 30_000, durationMs = 0))
    }

    /** Justo al cambiar de canción la posición puede pasarse unos milisegundos: nunca más de lleno. */
    @Test
    fun `la barra nunca se sale`() {
        assertEquals(1_000, progressOf(positionMs = 200_500, durationMs = 200_000))
        assertEquals(0, progressOf(positionMs = -40, durationMs = 200_000))
    }

    /** Mil pasos: en una canción de cuatro minutos, cada segundo se nota en la barra. */
    @Test
    fun `un segundo mueve la barra`() {
        val cuatroMinutos = 240_000L
        val antes = progressOf(positionMs = 60_000, durationMs = cuatroMinutos)
        val despues = progressOf(positionMs = 61_000, durationMs = cuatroMinutos)
        assertEquals(true, despues > antes)
    }
}
