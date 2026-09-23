package com.example.exotube.data.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** El análisis guardado de yt-dlp: cuándo se reutiliza y cuándo ya no. */
class InfoJsonCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var clock = 1_000_000_000L
    private val cache by lazy { InfoJsonCache(temp.newFolder("analisis"), maxAgeMs = 60_000, now = { clock }) }

    private fun save(url: String) {
        cache.save(url, """{"id":"x"}""")
        // lastModified lo pone el sistema de archivos: se alinea con el reloj de la prueba.
        temp.root.walk().filter { it.isFile }.forEach { it.setLastModified(clock) }
    }

    @Test
    fun `recién guardado se reutiliza`() {
        save("https://www.tiktok.com/@a/video/1")

        assertEquals("""{"id":"x"}""", cache.freshFileFor("https://www.tiktok.com/@a/video/1")?.readText())
    }

    @Test
    fun `cada enlace tiene el suyo`() {
        save("https://www.tiktok.com/@a/video/1")

        assertNull(cache.freshFileFor("https://www.tiktok.com/@a/video/2"))
    }

    @Test
    fun `pasado el tiempo ya no se usa, porque sus enlaces pueden haber caducado`() {
        save("https://x.com/a/status/1")
        clock += 60_001

        assertNull(cache.freshFileFor("https://x.com/a/status/1"))
    }

    @Test
    fun `olvidar uno lo borra`() {
        save("https://x.com/a/status/1")
        assertNotNull(cache.freshFileFor("https://x.com/a/status/1"))

        cache.forget("https://x.com/a/status/1")

        assertNull(cache.freshFileFor("https://x.com/a/status/1"))
    }
}
