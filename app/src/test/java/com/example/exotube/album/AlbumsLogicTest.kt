package com.example.exotube.album

import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El reparto en álbumes es lógica pura: entra la lista de la biblioteca y salen los grupos.
 * Se prueba sin emulador y sin MediaStore.
 */
class AlbumsLogicTest {

    private val sobredosis = song("Sobredosis de TV", "Soda Stereo", "Nada Personal", track = 3)
    private val temblor = song("Cuando Pase el Temblor", "Soda Stereo", "Nada Personal", track = 5)
    private val musicaLigera = song("De Música Ligera", "Soda Stereo", "Canción Animal", track = 8)
    private val sinAlbum = song("Video que descargué", "Un canal", album = null, track = null)

    @Test
    fun `dos canciones del mismo album caen en el mismo grupo`() {
        val albums = buildAlbums(listOf(sobredosis, temblor), AlbumGrouping.BY_ALBUM)

        assertEquals(1, albums.size)
        assertEquals("Nada Personal", albums.first().title)
        assertEquals(2, albums.first().items.size)
    }

    /** Lo que pidió el usuario: la canción nueva se coloca sola en el álbum que ya existía. */
    @Test
    fun `una cancion nueva se suma al album que ya existia`() {
        val antes = buildAlbums(listOf(sobredosis), AlbumGrouping.BY_ALBUM)
        val despues = buildAlbums(listOf(sobredosis, temblor), AlbumGrouping.BY_ALBUM)

        assertEquals(1, antes.first().items.size)
        assertEquals(1, despues.size) // sigue siendo un solo álbum
        assertEquals(2, despues.first().items.size)
    }

    @Test
    fun `dentro del album manda el numero de pista, no el alfabeto`() {
        val albums = buildAlbums(listOf(temblor, sobredosis), AlbumGrouping.BY_ALBUM)

        // Por título, "Cuando..." iría antes que "Sobredosis..."; por pista, al revés.
        assertEquals(listOf("Sobredosis de TV", "Cuando Pase el Temblor"), albums.first().items.map { it.title })
    }

    @Test
    fun `las canciones sin album van a Randoms`() {
        val albums = buildAlbums(listOf(sobredosis, sinAlbum), AlbumGrouping.BY_ALBUM)

        val randoms = albums.single { it.isUntagged }
        assertEquals(listOf(sinAlbum), randoms.items)
        assertEquals(UNTAGGED_ALBUM_ID, randoms.id)
    }

    /** Randoms siempre al final: es un cajón de sastre, no un álbum más. */
    @Test
    fun `Randoms queda de ultimo aunque alfabeticamente fuera antes`() {
        val albums = buildAlbums(listOf(sinAlbum, musicaLigera), AlbumGrouping.BY_ALBUM)

        assertEquals(listOf("Canción Animal", ""), albums.map { it.title })
        assertTrue(albums.last().isUntagged)
    }

    @Test
    fun `los albumes salen en orden alfabetico`() {
        val albums = buildAlbums(listOf(sobredosis, musicaLigera), AlbumGrouping.BY_ALBUM)

        assertEquals(listOf("Canción Animal", "Nada Personal"), albums.map { it.title })
    }

    /**
     * Agrupando por artista, las dos canciones de Soda Stereo caen juntas aunque sean de discos
     * distintos. Y la que no tiene álbum sí tiene artista, así que forma su propio grupo: por eso
     * aquí no aparece Randoms.
     */
    @Test
    fun `agrupando por artista se juntan sus dos discos`() {
        val albums = buildAlbums(listOf(sobredosis, musicaLigera, sinAlbum), AlbumGrouping.BY_ARTIST)

        assertEquals(listOf("Soda Stereo", "Un canal"), albums.map { it.title })
        assertEquals(2, albums.first().items.size)
        assertTrue(albums.none { it.isUntagged })
    }

    /** Agrupando por artista, Randoms es para lo que no trae artista etiquetado. */
    @Test
    fun `sin artista tambien se va a Randoms`() {
        val anonima = song("Pista suelta", artist = null, album = "Algun disco", track = 1)

        val albums = buildAlbums(listOf(sobredosis, anonima), AlbumGrouping.BY_ARTIST)

        assertEquals(listOf(anonima), albums.single { it.isUntagged }.items)
    }

    /** Un video no pertenece a ningún disco: no debe aparecer en esta pantalla. */
    @Test
    fun `los videos no entran en los albumes`() {
        val video = song("Un video", "Canal", album = null, track = null).copy(type = MediaType.VIDEO)

        assertTrue(buildAlbums(listOf(video), AlbumGrouping.BY_ALBUM).isEmpty())
    }

    /** Una recopilación tiene varios artistas: poner solo uno sería mentir. */
    @Test
    fun `el subtitulo solo aparece si todas las canciones son del mismo artista`() {
        val otro = song("Tema invitado", "Otro grupo", "Nada Personal", track = 9)

        val soloSoda = buildAlbums(listOf(sobredosis, temblor), AlbumGrouping.BY_ALBUM).first()
        val recopilacion = buildAlbums(listOf(sobredosis, otro), AlbumGrouping.BY_ALBUM).first()

        assertEquals("Soda Stereo", soloSoda.subtitle)
        assertNull(recopilacion.subtitle)
    }

    /** Una etiqueta que solo tiene espacios es lo mismo que no tenerla. */
    @Test
    fun `una etiqueta en blanco cuenta como sin etiqueta`() {
        val enBlanco = song("Suelta", "Alguien", album = "   ", track = null)

        assertTrue(buildAlbums(listOf(enBlanco), AlbumGrouping.BY_ALBUM).single().isUntagged)
    }

    @Test
    fun `la duracion del album es la suma de sus canciones`() {
        val albums = buildAlbums(listOf(sobredosis, temblor), AlbumGrouping.BY_ALBUM)

        assertEquals(420_000, albums.first().totalDurationMs)
    }

    private fun song(title: String, artist: String?, album: String?, track: Int?) = LibraryItem(
        id = title.hashCode().toLong(),
        uri = "content://media/external/audio/media/${title.hashCode()}",
        title = title,
        artist = artist,
        type = MediaType.AUDIO,
        durationMs = 210_000,
        sizeBytes = 5_000_000,
        dateAddedSeconds = 1,
        album = album,
        trackNumber = track,
    )
}
