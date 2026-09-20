package com.example.exotube.data.playlist

import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.Playlist
import com.example.exotube.domain.model.PlaylistCover
import com.example.exotube.domain.model.PlaylistDetail
import com.example.exotube.domain.repository.LibraryRepository
import com.example.exotube.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/**
 * Une dos fuentes: Room (qué canciones hay en cada playlist) y la biblioteca (qué archivos
 * existen de verdad). Si borras un archivo desde la galería, desaparece de sus playlists
 * automáticamente, porque solo mostramos las Uri que la biblioteca sigue encontrando.
 */
class RoomPlaylistRepository(
    private val dao: PlaylistDao,
    private val library: LibraryRepository,
    private val coverStore: PlaylistCoverStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        combine(dao.observePlaylists(), dao.observeAllItems(), library.observeDownloads(), ::buildPlaylists)

    override fun observePlaylist(id: Long): Flow<PlaylistDetail?> =
        combine(dao.observePlaylist(id), dao.observeItems(id), library.observeDownloads()) { playlist, items, files ->
            playlist?.let {
                val songs = resolve(items, files)
                PlaylistDetail(it.id, it.name, songs, chooseCover(it.id, it.coverPath, songs))
            }
        }

    override fun observePlaylistIdsContaining(mediaUri: String): Flow<Set<Long>> =
        dao.observePlaylistIdsContaining(mediaUri).map { it.toSet() }

    override suspend fun create(name: String, coverImageUri: String?): Long {
        val id = dao.insert(PlaylistEntity(name = name.trim(), createdAt = clock()))
        // Si la foto falla, la playlist se crea igual (con la carátula de una canción).
        coverImageUri?.let { setCover(id, it) }
        return id
    }

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    override suspend fun setCover(id: Long, coverImageUri: String?) {
        val newPath = coverImageUri?.let { coverStore.save(id, it) ?: return } // no se pudo leer: no tocar nada
        val oldPath = dao.coverPath(id)
        dao.setCoverPath(id, newPath)
        coverStore.delete(oldPath) // borrar la foto anterior para no acumular archivos
    }

    override suspend fun delete(id: Long) {
        val coverPath = dao.coverPath(id)
        dao.delete(id)
        coverStore.delete(coverPath)
    }

    override suspend fun add(playlistId: Long, mediaUri: String) = dao.addItem(playlistId, mediaUri, clock())

    override suspend fun remove(playlistId: Long, mediaUri: String) = dao.removeItem(playlistId, mediaUri)
}

// --- Lógica pura (sin Room ni Android): por eso se prueba con tests unitarios normales ---

internal fun buildPlaylists(
    playlists: List<PlaylistEntity>,
    items: List<PlaylistItemEntity>,
    files: List<LibraryItem>,
): List<Playlist> {
    val itemsByPlaylist = items.groupBy { it.playlistId }
    return playlists.map { playlist ->
        val songs = resolve(itemsByPlaylist[playlist.id].orEmpty(), files)
        Playlist(playlist.id, playlist.name, songs.size, chooseCover(playlist.id, playlist.coverPath, songs))
    }
}

/** Convierte Uris guardadas en canciones reales, en orden, descartando archivos que ya no existen. */
internal fun resolve(items: List<PlaylistItemEntity>, files: List<LibraryItem>): List<LibraryItem> {
    val filesByUri = files.associateBy { it.uri }
    return items.sortedBy { it.position }.mapNotNull { filesByUri[it.mediaUri] }
}

/**
 * Portada de una playlist:
 *  1. La foto del usuario, si eligió una.
 *  2. Si no, una canción al azar, prefiriendo videos (tienen imagen real; los audios solo un icono).
 *
 * El azar usa el id de la playlist como semilla: siempre sale la misma canción para las mismas
 * canciones, así la portada no "salta" cada vez que se redibuja la pantalla.
 */
internal fun chooseCover(playlistId: Long, coverPath: String?, songs: List<LibraryItem>): PlaylistCover {
    if (coverPath != null) return PlaylistCover.Photo(coverPath)
    if (songs.isEmpty()) return PlaylistCover.Empty
    val candidates = songs.filter { it.type == MediaType.VIDEO }.ifEmpty { songs }
    return PlaylistCover.FromSong(candidates.random(Random(playlistId)))
}
