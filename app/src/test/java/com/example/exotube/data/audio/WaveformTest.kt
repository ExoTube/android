package com.example.exotube.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El último paso de calcular la onda: convertir las sumas de sonido en alturas de 0 a 1.
 *
 * Es donde se decide si la onda se ve o no. Dividir por el máximo teórico en vez de por el tramo
 * más alto de la canción dejaría las grabaciones suaves como una raya pegada al suelo, que es
 * exactamente la "línea recta" que esto venía a quitar.
 */
class WaveformTest {

    /** La barra más alta de la canción llega arriba del todo; las demás, en proporción. */
    @Test
    fun `la parte mas fuerte llega al maximo`() {
        val niveles = normalize(sums = doubleArrayOf(0.04, 0.16, 0.01), counts = longArrayOf(1, 1, 1))

        // Raices: 0,2  0,4  0,1 -> divididas por 0,4
        assertEquals(0.5f, niveles[0], TOLERANCIA)
        assertEquals(1.0f, niveles[1], TOLERANCIA)
        assertEquals(0.25f, niveles[2], TOLERANCIA)
    }

    /**
     * Lo que esto protege: una canción grabada bajita tiene que verse igual de bien que una
     * masterizada fuerte. Lo que interesa es su FORMA, no compararla con otras.
     */
    @Test
    fun `una cancion bajita se ve igual que una fuerte`() {
        val fuerte = normalize(doubleArrayOf(0.25, 1.0, 0.0625), longArrayOf(1, 1, 1))
        val bajita = normalize(doubleArrayOf(0.0025, 0.01, 0.000625), longArrayOf(1, 1, 1))

        repeat(3) { i -> assertEquals(fuerte[i], bajita[i], TOLERANCIA) }
    }

    /** Se divide entre cuántas muestras entraron: un tramo con más datos no debe salir más alto. */
    @Test
    fun `el nivel no depende de cuantas muestras se leyeron`() {
        val pocas = normalize(doubleArrayOf(0.25, 1.0), longArrayOf(1, 1))
        val muchas = normalize(doubleArrayOf(25.0, 100.0), longArrayOf(100, 100))

        assertEquals(pocas[0], muchas[0], TOLERANCIA)
        assertEquals(pocas[1], muchas[1], TOLERANCIA)
    }

    /** Un tramo del que no llegó nada (final cortado, hueco) vale cero y no rompe el reparto. */
    @Test
    fun `un tramo vacio vale cero`() {
        val niveles = normalize(doubleArrayOf(1.0, 0.0), longArrayOf(1, 0))

        assertEquals(1f, niveles[0], TOLERANCIA)
        assertEquals(0f, niveles[1], TOLERANCIA)
    }

    /** Silencio absoluto: todo a cero, sin dividir por cero ni devolver NaN. */
    @Test
    fun `una cancion en silencio no revienta`() {
        val niveles = normalize(doubleArrayOf(0.0, 0.0), longArrayOf(10, 10))

        assertTrue(niveles.all { it == 0f })
    }

    @Test
    fun `todos los niveles caen entre cero y uno`() {
        val niveles = normalize(doubleArrayOf(0.01, 0.5, 0.25, 0.0), longArrayOf(3, 7, 1, 5))

        assertTrue(niveles.all { it in 0f..1f })
    }

    private companion object {
        const val TOLERANCIA = 0.0001f
    }
}
