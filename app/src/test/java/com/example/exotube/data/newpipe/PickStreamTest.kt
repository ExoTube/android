package com.example.exotube.data.newpipe

import com.example.exotube.data.network.youtubeStreamUserAgent
import com.example.exotube.domain.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Qué formatos se reproducen de entre los que ofrece YouTube. Tiene que dar lo mismo que se le
 * pide a yt-dlp, para que cambiar de motor no cambie lo que ve el usuario.
 */
class PickStreamTest {

    private fun video(height: Int, h264: Boolean = true, bitrate: Int = height * 1_000) =
        StreamOption("v$height${if (h264) "avc" else "vp9"}", height, isMostCompatible = h264, bitrate = bitrate)

    private fun audio(name: String, m4a: Boolean = true, bitrate: Int = 128_000, original: Boolean = true) =
        StreamOption(name, 0, isMostCompatible = m4a, bitrate = bitrate, isOriginalAudio = original)

    private val allVideo = listOf(video(144), video(360), video(480), video(720), video(1080), video(1080, h264 = false))
    private val allAudio = listOf(audio("a-m4a"), audio("a-opus", m4a = false, bitrate = 160_000))

    @Test
    fun `la mejor imagen H264 que no pase del tope, con su sonido`() {
        val source = pickStream(allVideo, allAudio, emptyList(), audioOnly = false, maxHeight = 480)

        assertEquals(StreamSource.Separate("v480avc", "a-m4a"), source)
    }

    /** Aunque el Opus tenga más bitrate: el AAC es el que reproduce cualquier teléfono. */
    @Test
    fun `se prefiere el sonido AAC`() {
        val source = pickStream(allVideo, allAudio, emptyList(), audioOnly = false, maxHeight = 720)

        assertEquals("a-m4a", (source as StreamSource.Separate).audioUrl)
    }

    @Test
    fun `sin H264 hasta el tope se usa otro codec`() {
        val onlyVp9 = listOf(video(480, h264 = false), video(1080))

        val source = pickStream(onlyVp9, allAudio, emptyList(), audioOnly = false, maxHeight = 720)

        assertEquals("v480vp9", (source as StreamSource.Separate).videoUrl)
    }

    /** Si todo es más grande que lo pedido, se ve algo más nítido antes que no verlo. */
    @Test
    fun `si nada cabe en el tope se toma lo mas pequeno`() {
        val source = pickStream(listOf(video(720), video(360)), allAudio, emptyList(), audioOnly = false, maxHeight = 144)

        assertEquals("v360avc", (source as StreamSource.Separate).videoUrl)
    }

    /** Un doblaje no puede colarse por tener más calidad: se oye el idioma original. */
    @Test
    fun `se elige el audio original y no un doblaje`() {
        val withDub = listOf(audio("doblaje", bitrate = 256_000, original = false), audio("original", bitrate = 96_000))

        val source = pickStream(allVideo, withDub, emptyList(), audioOnly = true, maxHeight = 480)

        assertEquals(StreamSource.Single("original", hasVideo = false), source)
    }

    @Test
    fun `solo audio no trae imagen`() {
        val source = pickStream(allVideo, allAudio, emptyList(), audioOnly = true, maxHeight = 1080)

        assertEquals(StreamSource.Single("a-m4a", hasVideo = false), source)
    }

    @Test
    fun `sin imagen y sonido por separado se usa el archivo con todo junto`() {
        val muxed = listOf(StreamOption("todo360", 360, isMostCompatible = true, bitrate = 500_000))

        val source = pickStream(emptyList(), emptyList(), muxed, audioOnly = false, maxHeight = 720)

        assertEquals(StreamSource.Single("todo360", hasVideo = true), source)
    }

    @Test
    fun `sin nada que sirva no hay enlace`() {
        assertNull(pickStream(emptyList(), emptyList(), emptyList(), audioOnly = false, maxHeight = 480))
    }

    // --- El User-Agent de cada tipo de enlace ---

    @Test
    fun `los enlaces de la app de Android llevan su User-Agent`() {
        val ua = youtubeStreamUserAgent("https://rr1---sn-x.googlevideo.com/videoplayback?expire=1790000000&c=ANDROID&itag=140")
        assertNotNull(ua)
    }

    @Test
    fun `lo que no es un enlace de YouTube no se toca`() {
        assertNull(youtubeStreamUserAgent("https://api.github.com/repos/ExoTube/exotube.github.io/releases/latest"))
        assertNull(youtubeStreamUserAgent("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
    }
}
