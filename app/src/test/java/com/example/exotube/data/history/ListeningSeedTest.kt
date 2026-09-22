package com.example.exotube.data.history

import com.example.exotube.domain.model.ListeningSeed
import com.example.exotube.data.ytdlp.distinctSeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sacar el identificador de YouTube del enlace es de lo que depende que las recomendaciones
 * apunten al video correcto: con él se pide el "Mix" de ese tema, que es la recomendación buena.
 * Sin él se cae al plan B (buscar por el artista), que funciona peor.
 */
class YoutubeIdTest {

    @Test
    fun `el enlace normal de YouTube`() {
        assertEquals("dQw4w9WgXcQ", youtubeIdOrNull("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    /** El que sale al tocar "Compartir" en la app de YouTube, que es como llegan casi todos. */
    @Test
    fun `el enlace corto de compartir`() {
        assertEquals("dQw4w9WgXcQ", youtubeIdOrNull("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", youtubeIdOrNull("https://youtu.be/dQw4w9WgXcQ?si=abc123"))
    }

    @Test
    fun `los shorts tambien cuentan`() {
        assertEquals("dQw4w9WgXcQ", youtubeIdOrNull("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    /** El parámetro puede no ser el primero: al compartir desde una lista vienen otros delante. */
    @Test
    fun `el identificador no tiene que ir el primero`() {
        assertEquals(
            "dQw4w9WgXcQ",
            youtubeIdOrNull("https://www.youtube.com/watch?list=RDabc&v=dQw4w9WgXcQ&index=2"),
        )
    }

    /** Una canción del teléfono no tiene identificador de YouTube, y eso no es un fallo. */
    @Test
    fun `un archivo del telefono no tiene identificador`() {
        assertNull(youtubeIdOrNull("content://media/external/audio/media/42"))
    }

    @Test
    fun `otra plataforma no da identificador de YouTube`() {
        assertNull(youtubeIdOrNull("https://www.tiktok.com/@alguien/video/12345"))
        assertNull(youtubeIdOrNull("https://www.instagram.com/reel/ABC123/"))
    }

    /** Un recorte a medias no vale: pedir el mix de un identificador falso no devuelve nada. */
    @Test
    fun `un identificador incompleto se descarta`() {
        assertNull(youtubeIdOrNull("https://www.youtube.com/watch?v=corto"))
    }
}

/**
 * Sin esto, escuchar cuatro canciones del mismo grupo llenaría "Para ti" con cuatro bloques
 * idénticos titulados "Porque escuchaste Soda Stereo". La gracia es descubrir cosas, no ver la
 * misma lista tres veces.
 */
class DistinctSeedsTest {

    private fun seed(title: String, artist: String?, key: String = title) =
        ListeningSeed(mediaKey = key, title = title, artist = artist, videoId = null)

    @Test
    fun `un artista solo aporta una semilla`() {
        val seeds = listOf(
            seed("Un Misil en Mi Placard", "Soda Stereo"),
            seed("De Música Ligera", "Soda Stereo"),
            seed("Back In Black", "AC/DC"),
        ).distinctSeeds()

        assertEquals(2, seeds.size)
        assertEquals("Un Misil en Mi Placard", seeds[0].title)
        assertEquals("Back In Black", seeds[1].title)
    }

    /** El mismo grupo escrito distinto sigue siendo el mismo grupo. */
    @Test
    fun `las mayusculas no crean artistas nuevos`() {
        val seeds = listOf(seed("A", "Soda Stereo"), seed("B", "SODA STEREO")).distinctSeeds()

        assertEquals(1, seeds.size)
    }

    /** Sin artista, cada canción cuenta por separado: es lo único que las distingue. */
    @Test
    fun `sin artista cada cancion es su propia semilla`() {
        val seeds = listOf(
            seed("Ritmo nocturno", null, key = "content://1"),
            seed("Tarde tranquila", null, key = "content://2"),
        ).distinctSeeds()

        assertEquals(2, seeds.size)
    }

    /** Se conserva el orden: lo más escuchado va primero y debe seguir yendo primero. */
    @Test
    fun `se respeta el orden de llegada`() {
        val seeds = listOf(
            seed("Back In Black", "AC/DC"),
            seed("Un Misil en Mi Placard", "Soda Stereo"),
            seed("Highway to Hell", "AC/DC"),
        ).distinctSeeds()

        assertEquals(listOf("AC/DC", "Soda Stereo"), seeds.map { it.artist })
    }
}
