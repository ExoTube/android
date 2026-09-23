package com.example.exotube.domain

import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.LibraryVisibility
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.looksLikeVoiceNote
import com.example.exotube.domain.model.visibleWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El filtro de la biblioteca: qué audios se esconden y qué no se toca nunca (los videos). */
class LibraryVisibilityTest {

    private fun item(
        title: String,
        seconds: Long,
        type: MediaType = MediaType.AUDIO,
        isDownload: Boolean = false,
        isVoiceNote: Boolean = false,
    ) = LibraryItem(
        id = title.hashCode().toLong(),
        uri = "content://$title",
        title = title,
        artist = null,
        type = type,
        durationMs = seconds * 1_000,
        sizeBytes = 1,
        dateAddedSeconds = 0,
        isDownload = isDownload,
        isVoiceNote = isVoiceNote,
    )

    private val song = item("cancion", 210)
    private val beep = item("efecto", 3)
    private val shortDownload = item("descarga corta", 5, isDownload = true)
    private val shortVideo = item("video corto", 8, type = MediaType.VIDEO, isDownload = true)
    private val voiceNote = item("nota de voz", 95, isVoiceNote = true)
    private val unknownLength = item("sin duracion", 0)

    private val all = listOf(song, beep, shortDownload, shortVideo, voiceNote, unknownLength)

    @Test
    fun `por defecto se esconden los audios cortos y las notas de voz`() {
        assertEquals(listOf(song, shortVideo, unknownLength), all.visibleWith(LibraryVisibility()))
    }

    @Test
    fun `un audio corto descargado con ExoTube también se esconde`() {
        assertFalse(shortDownload in all.visibleWith(LibraryVisibility(minAudioSeconds = 30)))
        assertTrue(shortDownload in all.visibleWith(LibraryVisibility(minAudioSeconds = 0)))
    }

    @Test
    fun `los videos se ven siempre, aunque sean cortos`() {
        assertTrue(shortVideo in all.visibleWith(LibraryVisibility(minAudioSeconds = 120)))
    }

    @Test
    fun `en cero y sin ocultar notas de voz, se ve todo`() {
        assertEquals(all, all.visibleWith(LibraryVisibility(minAudioSeconds = 0, hideVoiceNotes = false)))
    }

    @Test
    fun `el límite incluye el propio valor y un audio de justo 30 segundos se ve`() {
        val exact = item("justo", 30)

        assertEquals(listOf(exact), listOf(exact).visibleWith(LibraryVisibility(minAudioSeconds = 30)))
    }

    @Test
    fun `una nota de voz larga se ve si el usuario lo pide`() {
        val visible = all.visibleWith(LibraryVisibility(minAudioSeconds = 30, hideVoiceNotes = false))

        assertTrue(voiceNote in visible)
    }

    @Test
    fun `las carpetas de notas de voz y grabadoras se reconocen`() {
        assertTrue(looksLikeVoiceNote("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202339/"))
        assertTrue(looksLikeVoiceNote("/storage/emulated/0/WhatsApp Business/Media/WhatsApp Business Voice Notes/PTT.opus"))
        assertTrue(looksLikeVoiceNote("Recordings/"))
        assertTrue(looksLikeVoiceNote("Recordings/Voice Recorder/"))
    }

    @Test
    fun `las canciones que llegan por el chat NO son notas de voz`() {
        assertFalse(looksLikeVoiceNote("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/"))
        assertFalse(looksLikeVoiceNote("Music/"))
        assertFalse(looksLikeVoiceNote(null))
    }
}
