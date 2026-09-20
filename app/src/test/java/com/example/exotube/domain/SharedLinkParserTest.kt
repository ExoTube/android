package com.example.exotube.domain

import com.example.exotube.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLinkParserTest {

    @Test
    fun `extrae la URL de un texto compartido por TikTok`() {
        val text = "Mira este video de @usuario en TikTok! https://vm.tiktok.com/ZMabc123/ #fyp"
        assertEquals("https://vm.tiktok.com/ZMabc123/", SharedLinkParser.extractUrl(text))
    }

    @Test
    fun `quita la puntuacion pegada al final`() {
        assertEquals("https://youtu.be/abc", SharedLinkParser.extractUrl("Míralo (https://youtu.be/abc)."))
    }

    @Test
    fun `devuelve null si no hay enlace`() {
        assertNull(SharedLinkParser.extractUrl("hola, esto no es un enlace"))
        assertNull(SharedLinkParser.extractUrl(""))
        assertNull(SharedLinkParser.extractUrl(null))
        assertNull(SharedLinkParser.extractUrl("https://localhost"))
    }

    @Test
    fun `detecta la plataforma incluyendo subdominios`() {
        assertEquals(Platform.YOUTUBE, Platform.fromUrl("https://m.youtube.com/watch?v=abc"))
        assertEquals(Platform.YOUTUBE, Platform.fromUrl("https://youtu.be/abc?si=xyz"))
        assertEquals(Platform.TIKTOK, Platform.fromUrl("https://vm.tiktok.com/ZMabc/"))
        assertEquals(Platform.INSTAGRAM, Platform.fromUrl("https://www.instagram.com/reel/abc/"))
        assertEquals(Platform.X, Platform.fromUrl("https://twitter.com/u/status/1"))
        assertEquals(Platform.FACEBOOK, Platform.fromUrl("https://fb.watch/abc/"))
        assertEquals(Platform.UNKNOWN, Platform.fromUrl("https://example.com/video"))
        // Un dominio que solo "termina igual" no debe confundirse con YouTube.
        assertEquals(Platform.UNKNOWN, Platform.fromUrl("https://notyoutube.com/watch"))
    }
}
