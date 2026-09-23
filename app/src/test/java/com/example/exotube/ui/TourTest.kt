package com.example.exotube.ui

import com.example.exotube.data.settings.initialSeenTours
import com.example.exotube.ui.tour.Tour
import com.example.exotube.ui.tour.TourSpot
import com.example.exotube.ui.tour.stepsToShow
import com.example.exotube.ui.tour.tourToShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cuándo sale cada parte del tutorial y qué pasos se enseñan. */
class TourTest {

    private fun show(
        seen: Set<String> = emptySet(),
        screen: Tour? = Tour.LIBRARY,
        playerOpen: Boolean = false,
        video: Boolean = false,
        mini: Boolean = false,
        busy: Boolean = false,
    ) = tourToShow(seen, screen, playerOpen, video, mini, busy)

    private fun seenAllBut(vararg tours: Tour) = Tour.allIds - tours.map { it.id }.toSet()

    @Test
    fun `al instalar, lo primero es la biblioteca`() {
        assertEquals(Tour.LIBRARY, show())
    }

    @Test
    fun `cada pestaña se explica la primera vez que se entra`() {
        assertEquals(Tour.EXPLORE, show(seen = seenAllBut(Tour.EXPLORE), screen = Tour.EXPLORE))
        assertEquals(Tour.SETTINGS, show(seen = seenAllBut(Tour.SETTINGS), screen = Tour.SETTINGS))
    }

    @Test
    fun `lo ya visto no se repite`() {
        assertNull(show(seen = Tour.allIds))
    }

    @Test
    fun `con el reproductor abierto se explica el reproductor y luego el video`() {
        assertEquals(Tour.PLAYER, show(playerOpen = true, video = true))
        assertEquals(Tour.VIDEO, show(seen = setOf(Tour.PLAYER.id), playerOpen = true, video = true))
        // Una canción no tiene botones de video: esa parte espera al primer video.
        assertNull(show(seen = setOf(Tour.PLAYER.id), playerOpen = true, video = false))
    }

    @Test
    fun `el mini reproductor se explica después de la pantalla, no a la vez`() {
        assertEquals(Tour.LIBRARY, show(mini = true))
        assertEquals(Tour.MINI_PLAYER, show(seen = seenAllBut(Tour.MINI_PLAYER), mini = true))
        assertNull(show(seen = seenAllBut(Tour.MINI_PLAYER), mini = false))
    }

    @Test
    fun `con un diálogo o la pantalla completa encima, el tutorial espera`() {
        assertNull(show(busy = true))
        assertNull(show(playerOpen = true, busy = true))
    }

    @Test
    fun `en una pantalla de detalle no hay tutorial propio`() {
        assertNull(show(seen = seenAllBut(Tour.LIBRARY), screen = null))
    }

    @Test
    fun `se saltan los pasos cuyo botón no está en pantalla, pero no los del centro`() {
        val visible = setOf(TourSpot.TAB_LIBRARY, TourSpot.LIBRARY_SEARCH)
        val steps = stepsToShow(Tour.LIBRARY.steps) { it in visible }

        assertEquals(listOf(null, TourSpot.TAB_LIBRARY, TourSpot.LIBRARY_SEARCH, null, null), steps.map { it.spot })
    }

    @Test
    fun `quien actualiza desde una versión anterior no ve el tutorial y quien instala sí`() {
        assertEquals(Tour.allIds, initialSeenTours(hasBeenUpdated = true, allTourIds = Tour.allIds))
        assertTrue(initialSeenTours(hasBeenUpdated = false, allTourIds = Tour.allIds).isEmpty())
    }

    @Test
    fun `los ids no se repiten, porque es lo que se guarda`() {
        assertEquals(Tour.entries.size, Tour.allIds.size)
    }
}
