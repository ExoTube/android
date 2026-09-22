package com.example.exotube.data.ytdlp

import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "No tienes historial" y "no pude traerlo" se ven iguales desde dentro —en los dos casos no hay
 * nada que pintar— pero no tienen nada que ver para quien mira la pantalla.
 *
 * Este fallo apareció de verdad al probar en el teléfono: con una canción ya escuchada y sin
 * internet, "Para ti" decía "aún no hay nada que recomendarte" y no ofrecía reintentar. Le estaba
 * diciendo "escucha algo" a alguien que llevaba media hora escuchando.
 */
class RecommendationOutcomeTest {

    private fun block(becauseOf: String, videos: Int = 3) = Recommendation(
        becauseOf = becauseOf,
        videos = List(videos) { i ->
            OnlineVideo(
                id = "$becauseOf-$i",
                url = "https://youtu.be/$i",
                title = "Tema $i",
                channel = becauseOf,
                durationSeconds = 200,
                thumbnailUrl = null,
                viewCount = null,
            )
        },
    )

    @Test
    fun `con recomendaciones se devuelven`() {
        val outcome = outcomeOf(listOf(Result.success(block("Soda Stereo"))))

        assertEquals(1, outcome.getOrNull()?.size)
    }

    /** Lo que se rompió: sin internet hay que decirlo, no fingir que no hay historial. */
    @Test
    fun `si todo fallo se devuelve el fallo, no una lista vacia`() {
        val outcome = outcomeOf(
            listOf(
                Result.failure(MediaError.NoConnection),
                Result.failure(MediaError.NoConnection),
            ),
        )

        assertTrue(outcome.isFailure)
        assertEquals(MediaError.NoConnection, outcome.exceptionOrNull())
    }

    /** Basta con que una semilla funcione: mejor enseñar algo que un error. */
    @Test
    fun `un fallo entre varios no estropea el resto`() {
        val outcome = outcomeOf(
            listOf(
                Result.failure(MediaError.NoConnection),
                Result.success(block("AC/DC")),
            ),
        )

        assertEquals(listOf("AC/DC"), outcome.getOrNull()?.map { it.becauseOf })
    }

    /** Un bloque sin videos no se enseña: un encabezado "Porque escuchaste X" y nada debajo. */
    @Test
    fun `los bloques vacios se descartan`() {
        val outcome = outcomeOf(
            listOf(Result.success(block("Soda Stereo", videos = 0)), Result.success(block("AC/DC"))),
        )

        assertEquals(listOf("AC/DC"), outcome.getOrNull()?.map { it.becauseOf })
    }

    /**
     * Sin fallos pero sin nada que recomendar: las semillas no daban (por ejemplo, una grabación
     * propia sin artista). No hay error que explicar, simplemente no hay sugerencias.
     */
    @Test
    fun `sin fallos y sin resultados no es un error`() {
        val outcome = outcomeOf(listOf(Result.success(null), Result.success(null)))

        assertTrue(outcome.isSuccess)
        assertTrue(outcome.getOrNull().orEmpty().isEmpty())
    }
}
