package com.example.exotube.data.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** De un resultado de búsqueda de NewPipe a una fila de Explorar. */
class SearchHitTest {

    private fun hit(
        url: String? = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        title: String? = "Never Gonna Give You Up",
        channel: String? = "Rick Astley",
        duration: Long = 213,
        views: Long = 1_500_000_000,
        thumbnails: List<Pair<String, Int>> = listOf("chica" to 168, "media" to 336, "grande" to 1280),
        isLive: Boolean = false,
    ) = SearchHit(url, title, channel, duration, views, thumbnails, isLive)

    @Test
    fun `un resultado normal se convierte entero`() {
        val video = hit().toOnlineVideo()!!

        assertEquals("dQw4w9WgXcQ", video.id)
        assertEquals("Never Gonna Give You Up", video.title)
        assertEquals("Rick Astley", video.channel)
        assertEquals(213L, video.durationSeconds)
        assertEquals(1_500_000_000L, video.viewCount)
    }

    /** La mayor que no pase de 800 px: la de 1280 gastaría datos sin que se note en la fila. */
    @Test
    fun `se elige una miniatura mediana`() {
        assertEquals("media", hit().toOnlineVideo()?.thumbnailUrl)
    }

    /** Un directo no se puede reproducir como archivo: mejor no ofrecerlo. */
    @Test
    fun `los directos no salen`() {
        assertNull(hit(isLive = true).toOnlineVideo())
    }

    /** NewPipe pone -1 cuando no sabe algo: eso no puede verse como "-1 vistas". */
    @Test
    fun `lo desconocido queda vacio y no en negativo`() {
        val video = hit(duration = -1, views = -1).toOnlineVideo()!!

        assertNull(video.durationSeconds)
        assertNull(video.viewCount)
    }

    @Test
    fun `los shorts tambien valen`() {
        assertEquals("abcdefghijk", hit(url = "https://www.youtube.com/shorts/abcdefghijk").toOnlineVideo()?.id)
    }

    @Test
    fun `sin titulo o sin enlace de YouTube no se muestra`() {
        assertNull(hit(title = " ").toOnlineVideo())
        assertNull(hit(url = null).toOnlineVideo())
        assertNull(hit(url = "https://www.youtube.com/channel/UC123").toOnlineVideo())
    }
}
