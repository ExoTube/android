package com.example.exotube.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Empaquetar las dos direcciones y volver a separarlas es la pieza de la que depende que se vea
 * imagen y no solo se oiga el sonido. Si se rompe, el video no se reproduce.
 */
class MergedStreamUriTest {

    /** Direcciones con la pinta de las de verdad: llevan sus propios "?" y "&". */
    private val video = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1790000000&itag=137&mime=video%2Fmp4"
    private val audio = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1790000000&itag=140&mime=audio%2Fmp4"

    @Test
    fun `lo que se empaqueta se vuelve a separar igual`() {
        val (decodedVideo, decodedAudio) = MergedStreamUri.decode(MergedStreamUri.encode(video, audio))!!

        assertEquals(video, decodedVideo)
        assertEquals(audio, decodedAudio)
    }

    /** El punto delicado: si no se escaparan, el "&itag=137" partiría nuestra propia dirección. */
    @Test
    fun `los interrogantes y ampersands de dentro no rompen el paquete`() {
        val encoded = MergedStreamUri.encode(video, audio)

        // Solo el "?" y el "&" que ponemos nosotros; los de las direcciones van escapados.
        assertEquals(1, encoded.count { it == '?' })
        assertEquals(1, encoded.count { it == '&' })
    }

    @Test
    fun `una direccion normal no es un paquete`() {
        assertNull(MergedStreamUri.decode(video))
        assertNull(MergedStreamUri.decode("content://media/external/audio/media/42"))
    }

    @Test
    fun `un paquete incompleto se rechaza en vez de reproducir a medias`() {
        assertNull(MergedStreamUri.decode("exotube://merge?v=$video"))
        assertNull(MergedStreamUri.decode("exotube://merge?"))
    }

    /** Los títulos con acentos o espacios llegan en las direcciones de algunas plataformas. */
    @Test
    fun `sobreviven los caracteres raros`() {
        val raro = "https://ejemplo.com/v?title=Canci%C3%B3n de verano&x=a+b"

        val (decoded, _) = MergedStreamUri.decode(MergedStreamUri.encode(raro, audio))!!

        assertEquals(raro, decoded)
    }
}
