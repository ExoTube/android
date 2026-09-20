package com.example.exotube.domain.model

/** Qué imagen representa a una playlist. */
sealed interface PlaylistCover {
    /** Foto elegida por el usuario (copia guardada en el almacenamiento privado de la app). */
    data class Photo(val path: String) : PlaylistCover

    /** Sin foto propia: la carátula de una de sus canciones, elegida al azar. */
    data class FromSong(val item: LibraryItem) : PlaylistCover

    /** Playlist vacía y sin foto. */
    data object Empty : PlaylistCover
}

/** Resumen para la lista de playlists. */
data class Playlist(
    val id: Long,
    val name: String,
    /** Canciones que siguen existiendo en el teléfono. */
    val itemCount: Int,
    val cover: PlaylistCover,
)

/** Una playlist con sus canciones, en orden. */
data class PlaylistDetail(
    val id: Long,
    val name: String,
    val items: List<LibraryItem>,
    val cover: PlaylistCover,
) {
    val totalDurationMs: Long get() = items.sumOf { it.durationMs }
}
