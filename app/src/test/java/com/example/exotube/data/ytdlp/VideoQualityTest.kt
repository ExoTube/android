package com.example.exotube.data.ytdlp

import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Elegir calidad tiene que llegar de verdad hasta yt-dlp. Si la altura elegida no acabara en la
 * petición, el menú cambiaría de opción marcada y el video seguiría igual: justo el tipo de
 * fallo que nadie nota hasta que alguien con datos móviles se queda sin tarifa.
 */
class VideoQualityTest {

    // --- Automática ---

    @Test
    fun `automatica con datos moviles es 480p`() {
        assertEquals(480, VideoQuality.AUTO.heightFor(isMeteredConnection = true))
    }

    @Test
    fun `automatica con wifi es 720p`() {
        assertEquals(720, VideoQuality.AUTO.heightFor(isMeteredConnection = false))
    }

    /** Lo que se elige a mano se respeta, haya la conexión que haya. */
    @Test
    fun `una calidad elegida no depende de la conexion`() {
        VideoQuality.entries.filter { it != VideoQuality.AUTO }.forEach { quality ->
            assertEquals(quality.maxHeight, quality.heightFor(isMeteredConnection = true))
            assertEquals(quality.maxHeight, quality.heightFor(isMeteredConnection = false))
        }
    }

    /** "Automática" va la primera en el menú: es con la que empieza todo video. */
    @Test
    fun `automatica es la primera opcion`() {
        assertEquals(VideoQuality.AUTO, VideoQuality.entries.first())
    }

    /** De mejor a peor, que es como se lee una lista de calidades. */
    @Test
    fun `las calidades van de mayor a menor`() {
        val heights = VideoQuality.entries.mapNotNull { it.maxHeight }
        assertEquals(heights.sortedDescending(), heights)
    }

    // --- Lo que se le pide a yt-dlp ---

    @Test
    fun `el formato lleva la altura elegida`() {
        val format = videoFormat(480)

        assertTrue(format.contains("height<=?480"))
        assertFalse("no puede quedar el tope viejo de 1080p", format.contains("1080"))
    }

    /**
     * Los dos primeros intentos (imagen y sonido por separado) llevan el tope; el tercero, el
     * archivo único de 360p, es el último recurso y se acepta aunque se pidiera menos.
     */
    @Test
    fun `los dos intentos con imagen separada respetan el tope`() {
        val attempts = videoFormat(720).split("/")

        assertEquals(3, attempts.size)
        assertTrue(attempts[0].contains("height<=?720"))
        assertTrue(attempts[1].contains("height<=?720"))
    }

    // --- Olvidar un video en todas sus calidades ---

    private val url = "https://rr3.googlevideo.com/videoplayback?expire=1790000000&itag=137"

    /** Cuando un enlace falla se olvidan todas sus calidades: podrían estar igual de rotas. */
    @Test
    fun `olvidar un video borra todas sus calidades`() {
        val cache = StreamUrlCache(nowSeconds = { 1_789_000_000 })
        cache.put("abc:480p", StreamSource.Single(url, hasVideo = true))
        cache.put("abc:720p", StreamSource.Single(url, hasVideo = true))
        cache.put("abc:audio", StreamSource.Single(url, hasVideo = false))

        cache.removeStartingWith("abc:")

        assertNull(cache.get("abc:480p"))
        assertNull(cache.get("abc:720p"))
        assertNull(cache.get("abc:audio"))
    }

    /** Un video cuyo identificador empieza igual que otro no se lleva por delante al otro. */
    @Test
    fun `olvidar un video no toca los demas`() {
        val cache = StreamUrlCache(nowSeconds = { 1_789_000_000 })
        cache.put("abc:720p", StreamSource.Single(url, hasVideo = true))
        cache.put("abcd:720p", StreamSource.Single(url, hasVideo = true))

        cache.removeStartingWith("abc:")

        assertNotNull(cache.get("abcd:720p"))
    }
}
