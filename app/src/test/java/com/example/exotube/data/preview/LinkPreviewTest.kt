package com.example.exotube.data.preview

import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.Platform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La vista previa rápida, con respuestas de verdad de TikTok y de X guardadas el día que se
 * escribió. Si un día cambian el formato, estas pruebas lo dirán antes que los usuarios.
 */
class LinkPreviewTest {

    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(checkNotNull(javaClass.getResourceAsStream("/$name")).reader().readText()).jsonObject

    private val tiktokUrl = "https://www.tiktok.com/@scout2015/video/6718335390845095173"

    @Test
    fun `TikTok - título, miniatura y las opciones de siempre`() {
        val media = checkNotNull(tiktokPreview(fixture("tiktok-oembed.json"), tiktokUrl))

        assertEquals(Platform.TIKTOK, media.platform)
        assertTrue(media.title.startsWith("Scramble up ur name"))
        assertTrue(media.thumbnailUrl.orEmpty().startsWith("https://"))
        assertEquals(listOf("Auto", "MP3", "M4A"), media.formats.map { it.label })
        assertEquals(tiktokUrl, media.sourceUrl)
    }

    @Test
    fun `TikTok - si no es un video no hay vista previa`() {
        val photo = Json.parseToJsonElement("""{"type":"photo","title":"x"}""").jsonObject
        assertNull(tiktokPreview(photo, tiktokUrl))
    }

    @Test
    fun `X - las calidades de verdad, de mayor a menor, con su peso estimado`() {
        val media = checkNotNull(tweetPreview(fixture("x-tweet-un-video.json"), "https://x.com/a/status/1575560063510810624"))

        assertEquals(Platform.X, media.platform)
        assertEquals(listOf("720p", "360p", "270p"), media.videoFormats.map { it.label })
        assertEquals("b[height<=720][width<=1280]/b", media.videoFormats.first().formatId)
        // 2.176.000 bits por segundo durante 21 s, en bytes.
        assertEquals(2_176_000L * 21 / 8, media.videoFormats.first().sizeBytes)
        assertEquals(21L, media.durationSeconds)
        assertTrue(media.title.startsWith("Absolutely heartbreaking footage"))
        assertTrue("sin el enlace t.co del final", !media.title.contains("t.co"))
        assertTrue(media.thumbnailUrl.orEmpty().endsWith(".jpg"))
        assertNull(media.playlistIndex)
    }

    @Test
    fun `X - con varios videos se descarga el primero, como hace yt-dlp`() {
        val media = checkNotNull(tweetPreview(fixture("x-tweet-varios-videos.json"), "https://x.com/a/status/1577719286659006464"))

        assertEquals(1, media.playlistIndex)
        assertEquals(4L, media.durationSeconds)
    }

    @Test
    fun `X - un tuit sin video no tiene vista previa`() {
        val textOnly = Json.parseToJsonElement("""{"text":"hola","mediaDetails":[{"type":"photo"}]}""").jsonObject
        assertNull(tweetPreview(textOnly, "https://x.com/a/status/1"))
    }

    @Test
    fun `el número del tuit sale de cualquier forma de enlace`() {
        assertEquals("1575560063510810624", tweetId("https://x.com/usuario/status/1575560063510810624?s=20"))
        assertEquals("123", tweetId("https://twitter.com/i/status/123"))
        assertEquals("456", tweetId("https://mobile.twitter.com/u/statuses/456"))
        assertNull(tweetId("https://x.com/usuario"))
    }

    @Test
    fun `las opciones de sonido no cambian cuando llega yt-dlp`() {
        val media = checkNotNull(tiktokPreview(fixture("tiktok-oembed.json"), tiktokUrl))

        assertEquals(listOf("ba/b", "ba[ext=m4a]/ba/b"), media.formats.filter { it.type == MediaType.AUDIO }.map { it.formatId })
    }
}
