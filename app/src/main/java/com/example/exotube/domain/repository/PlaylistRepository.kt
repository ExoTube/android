package com.example.exotube.domain.repository

import com.example.exotube.domain.model.Playlist
import com.example.exotube.domain.model.PlaylistDetail
import kotlinx.coroutines.flow.Flow

/** Playlists del usuario. Las canciones se identifican por la Uri del archivo. */
interface PlaylistRepository {

    fun observePlaylists(): Flow<List<Playlist>>

    /** Emite null si la playlist se eliminó. */
    fun observePlaylist(id: Long): Flow<PlaylistDetail?>

    /** Ids de las playlists que contienen esa canción (para marcar casillas). */
    fun observePlaylistIdsContaining(mediaUri: String): Flow<Set<Long>>

    /**
     * @param coverImageUri foto elegida por el usuario (Uri del selector de fotos), o null.
     * @return el id de la playlist creada.
     */
    suspend fun create(name: String, coverImageUri: String? = null): Long

    suspend fun rename(id: Long, name: String)

    /** Cambia la foto; con null la quita y vuelve a usarse la carátula de una canción. */
    suspend fun setCover(id: Long, coverImageUri: String?)

    suspend fun delete(id: Long)

    /** Añade al final. Si ya estaba, no hace nada. */
    suspend fun add(playlistId: Long, mediaUri: String)

    suspend fun remove(playlistId: Long, mediaUri: String)
}
