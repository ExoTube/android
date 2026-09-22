package com.example.exotube.album

import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType

/** Cómo se agrupan las canciones en la pantalla Álbumes. */
enum class AlbumGrouping { BY_ALBUM, BY_ARTIST }

/**
 * Un grupo de canciones que se muestra como un álbum.
 *
 * No se guarda en ninguna base de datos: se calcula a partir de las etiquetas de los archivos.
 * Así, en cuanto se descarga una canción nueva aparece sola en su sitio.
 */
data class Album(
    /** Clave estable para navegar. Vacía en el grupo de las canciones sin etiqueta. */
    val id: String,
    /** El nombre del álbum (o del artista, según cómo se agrupe). Vacío si no hay etiqueta. */
    val title: String,
    /** Dato secundario: el artista del álbum, cuando aporta algo. */
    val subtitle: String?,
    val items: List<LibraryItem>,
    /** true en el cajón de sastre: las canciones a las que les falta la etiqueta. */
    val isUntagged: Boolean,
) {
    val totalDurationMs: Long = items.sumOf { it.durationMs }

    /** La carátula del álbum es la de una de sus canciones. */
    val coverUri: String? = items.firstOrNull()?.uri
}

/** Clave del grupo donde caen las canciones sin álbum (o sin artista) etiquetado. */
const val UNTAGGED_ALBUM_ID = ""

/**
 * Reparte las canciones en álbumes.
 *
 * Reglas:
 *  - Solo entra el audio: un video no pertenece a un disco.
 *  - Se agrupa por la etiqueta que diga [grouping]; si el archivo no la trae, va al grupo de las
 *    no etiquetadas (el que la pantalla llama "Randoms").
 *  - Dentro de cada álbum, las canciones van por número de pista si lo traen, y por título si no:
 *    así un disco suena en su orden original y no alfabético.
 *  - Los álbumes salen por orden alfabético, y el grupo de las no etiquetadas siempre al final.
 *
 * Es una función pura: entra una lista y sale otra. Se prueba sin emulador (ver AlbumsLogicTest).
 */
internal fun buildAlbums(items: List<LibraryItem>, grouping: AlbumGrouping): List<Album> {
    val songs = items.filter { it.type == MediaType.AUDIO }

    val groups = songs.groupBy { song ->
        val tag = when (grouping) {
            AlbumGrouping.BY_ALBUM -> song.album
            AlbumGrouping.BY_ARTIST -> song.artist
        }
        tag?.trim()?.takeIf { it.isNotEmpty() } ?: UNTAGGED_ALBUM_ID
    }

    return groups
        .map { (tag, albumSongs) -> toAlbum(tag, albumSongs, grouping) }
        .sortedWith(
            // El cajón de sastre va al final; el resto, alfabético sin distinguir mayúsculas.
            compareBy<Album> { it.isUntagged }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
}

private fun toAlbum(tag: String, songs: List<LibraryItem>, grouping: AlbumGrouping): Album {
    val ordered = songs.sortedWith(
        // Sin número de pista van al final del disco, ordenadas por título.
        compareBy<LibraryItem> { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
    return Album(
        id = tag,
        title = tag,
        subtitle = subtitleFor(ordered, grouping),
        items = ordered,
        isUntagged = tag.isEmpty(),
    )
}

/**
 * Debajo del nombre se pone el dato que NO se usó para agrupar, que es el que añade información:
 * agrupando por álbum interesa el artista; agrupando por artista no hay nada que añadir.
 * Con varios artistas se deja vacío: es una recopilación y poner solo uno sería mentir.
 */
private fun subtitleFor(songs: List<LibraryItem>, grouping: AlbumGrouping): String? {
    if (grouping == AlbumGrouping.BY_ARTIST) return null
    return songs.mapNotNull { it.artist?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
        .singleOrNull()
}
