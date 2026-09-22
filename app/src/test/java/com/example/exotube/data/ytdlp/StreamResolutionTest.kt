package com.example.exotube.data.ytdlp

import com.example.exotube.domain.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que hay entre "yt-dlp ha impreso esto" y "el reproductor sabe qué pedir": interpretar sus
 * líneas y decidir cuánto tiempo sirve cada dirección. Todo esto es lógica pura.
 */
class StreamResolutionTest {

    private val video = "https://rr3.googlevideo.com/videoplayback?expire=1790000000&itag=137"
    private val audio = "https://rr3.googlevideo.com/videoplayback?expire=1790000000&itag=140"

    // --- Interpretar las líneas que imprime yt-dlp ---

    @Test
    fun `dos direcciones son imagen y sonido por separado`() {
        val source = toStreamSource(listOf(video, audio), audioOnly = false)

        assertEquals(StreamSource.Separate(video, audio), source)
        assertTrue(source!!.hasVideo)
    }

    @Test
    fun `una sola direccion es un archivo con todo junto`() {
        val source = toStreamSource(listOf(video), audioOnly = false)

        assertEquals(StreamSource.Single(video, hasVideo = true), source)
    }

    /** En modo ahorro de datos nunca hay imagen, aunque llegaran dos líneas. */
    @Test
    fun `en modo ahorro de datos no se trae imagen`() {
        val source = toStreamSource(listOf(audio, video), audioOnly = true)

        assertEquals(StreamSource.Single(audio, hasVideo = false), source)
        assertFalse(source!!.hasVideo)
    }

    @Test
    fun `sin direcciones no hay nada que reproducir`() {
        assertNull(toStreamSource(emptyList(), audioOnly = false))
    }

    // --- Caducidad y caché ---

    @Test
    fun `una direccion recien resuelta se reutiliza`() {
        val cache = StreamUrlCache(nowSeconds = { 1_789_000_000 })

        cache.put("abc", StreamSource.Separate(video, audio))

        assertNotNull(cache.get("abc"))
    }

    /**
     * La caducidad no la inventamos: va escrita en el propio enlace ("expire=1790000000").
     * Un segundo después de esa fecha, la dirección ya no sirve y hay que volver a preguntar.
     */
    @Test
    fun `una direccion caducada se olvida`() {
        var ahora = 1_789_000_000L
        val cache = StreamUrlCache(nowSeconds = { ahora })
        cache.put("abc", StreamSource.Single(video, hasVideo = true))

        ahora = 1_790_000_001 // pasada la fecha del enlace

        assertNull(cache.get("abc"))
    }

    /** Con dos direcciones manda la que caduque antes: si falta una, no se puede reproducir. */
    @Test
    fun `con dos direcciones vale la caducidad mas temprana`() {
        var ahora = 1_789_000_000L
        val cache = StreamUrlCache(nowSeconds = { ahora })
        val pronto = "https://rr3.googlevideo.com/videoplayback?expire=1789500000&itag=140"
        cache.put("abc", StreamSource.Separate(video, pronto))

        ahora = 1_789_600_000 // ya caducó el sonido, aunque la imagen siguiera valiendo

        assertNull(cache.get("abc"))
    }

    /** YouTube también escribe la caducidad como parte de la ruta, no solo como parámetro. */
    @Test
    fun `reconoce la caducidad escrita en la ruta`() {
        var ahora = 1_789_000_000L
        val cache = StreamUrlCache(nowSeconds = { ahora })
        cache.put("abc", StreamSource.Single("https://host/expire/1789200000/file.mp4", hasVideo = true))

        assertNotNull(cache.get("abc"))

        ahora = 1_789_300_000
        assertNull(cache.get("abc"))
    }

    /** Si no encontramos la fecha, mejor guardar poco rato que arriesgarse a un enlace muerto. */
    @Test
    fun `sin fecha en el enlace se guarda solo unos minutos`() {
        var ahora = 1_000L
        val cache = StreamUrlCache(nowSeconds = { ahora })
        cache.put("abc", StreamSource.Single("https://host/sin-fecha.mp4", hasVideo = true))

        ahora = 1_000 + 60 // un minuto después: todavía sirve
        assertNotNull(cache.get("abc"))

        ahora = 1_000 + 10 * 60 // diez minutos después: ya no
        assertNull(cache.get("abc"))
    }

    @Test
    fun `lo que no se ha guardado no esta`() {
        assertNull(StreamUrlCache().get("nunca-visto"))
    }
}
