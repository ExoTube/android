package com.example.exotube.data.playlist

import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.PlaylistCover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistLogicTest {

    private fun file(id: Long, type: MediaType = MediaType.AUDIO) =
        LibraryItem(id, "content://media/$id", "Canción $id", null, type, 60_000, 1_000, id)

    private fun entry(playlistId: Long, fileId: Long, position: Int) =
        PlaylistItemEntity(playlistId, "content://media/$fileId", position, addedAt = 0)

    @Test
    fun `respeta el orden de la playlist, no el de la biblioteca`() {
        val songs = resolve(
            items = listOf(entry(1, fileId = 3, position = 1), entry(1, fileId = 1, position = 0)),
            files = listOf(file(3), file(1)),
        )
        assertEquals(listOf(1L, 3L), songs.map { it.id })
    }

    @Test
    fun `los archivos borrados del telefono desaparecen de la playlist`() {
        val songs = resolve(
            items = listOf(entry(1, fileId = 1, position = 0), entry(1, fileId = 99, position = 1)),
            files = listOf(file(1)),
        )
        assertEquals(listOf(1L), songs.map { it.id })
    }

    @Test
    fun `cada playlist cuenta solo sus canciones`() {
        val playlists = buildPlaylists(
            playlists = listOf(PlaylistEntity(1, "Favoritas", 0), PlaylistEntity(2, "Vacía", 0)),
            items = listOf(entry(1, fileId = 2, position = 0), entry(1, fileId = 1, position = 1)),
            files = listOf(file(1), file(2)),
        )
        assertEquals(2, playlists.first { it.id == 1L }.itemCount)
        assertEquals(PlaylistCover.Empty, playlists.first { it.id == 2L }.cover)
    }

    @Test
    fun `la foto del usuario tiene prioridad sobre las canciones`() {
        val cover = chooseCover(1, coverPath = "/data/cover.jpg", songs = listOf(file(1, MediaType.VIDEO)))
        assertEquals(PlaylistCover.Photo("/data/cover.jpg"), cover)
    }

    @Test
    fun `sin foto usa una cancion al azar, prefiriendo videos`() {
        val songs = listOf(file(1), file(2, MediaType.VIDEO), file(3), file(4, MediaType.VIDEO))
        val cover = chooseCover(playlistId = 7, coverPath = null, songs = songs)

        assertTrue(cover is PlaylistCover.FromSong)
        assertEquals(MediaType.VIDEO, (cover as PlaylistCover.FromSong).item.type)
    }

    @Test
    fun `el azar es estable, la portada no cambia al redibujar`() {
        val songs = (1L..20L).map { file(it, MediaType.VIDEO) }
        val first = chooseCover(playlistId = 3, coverPath = null, songs = songs)
        repeat(10) { assertEquals(first, chooseCover(playlistId = 3, coverPath = null, songs = songs)) }
    }

    @Test
    fun `playlists distintas pueden elegir portadas distintas`() {
        val songs = (1L..20L).map { file(it, MediaType.VIDEO) }
        val covers = (1L..10L).map { chooseCover(it, null, songs) }.toSet()
        assertTrue("Todas las playlists tendrían la misma portada", covers.size > 1)
    }
}
