package com.example.exotube.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.exotube.ui.tour.TourSpot
import com.example.exotube.ui.tour.tourSpot
import com.example.exotube.R
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.ui.components.MediaArtwork
import com.example.exotube.ui.theme.ExoTubeTheme

/** Pantalla "tonta": solo dibuja el estado que recibe. */
@Composable
fun AlbumsScreen(
    state: AlbumsUiState,
    onGroupingSelected: (AlbumGrouping) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        // Las cabeceras ocupan las dos columnas; las carátulas, una cada una.
        item(span = { GridItemSpan(maxLineSpan) }) { AlbumsHeader(state) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.tourSpot(TourSpot.ALBUMS_GROUPING)) {
                GroupingRow(selected = state.grouping, onGroupingSelected = onGroupingSelected)
            }
        }

        when {
            state.isLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { EmptyAlbums() }
            else -> items(state.albums, key = { it.id.ifEmpty { UNTAGGED_KEY } }) { album ->
                AlbumCard(
                    album,
                    onClick = { onOpenAlbum(album) },
                    modifier = Modifier.tourSpot(TourSpot.ALBUMS_FIRST, enabled = album == state.albums.first()),
                )
            }
        }
    }
}

@Composable
private fun AlbumsHeader(state: AlbumsUiState) {
    Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        Text(stringResource(R.string.nav_albums), style = MaterialTheme.typography.headlineLarge)
        if (!state.isLoading) {
            val albums = pluralStringResource(R.plurals.albums_count, state.albums.size, state.albums.size)
            val songs = pluralStringResource(R.plurals.playlist_song_count, state.songCount, state.songCount)
            Text(
                text = "$albums · $songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupingRow(selected: AlbumGrouping, onGroupingSelected: (AlbumGrouping) -> Unit) {
    // Row y no LazyRow: son dos opciones fijas, siempre caben.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AlbumGrouping.entries.forEach { grouping ->
            FilterChip(
                selected = grouping == selected,
                onClick = { onGroupingSelected(grouping) },
                label = { Text(stringResource(grouping.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        MediaArtwork(
            uri = album.coverUri,
            type = MediaType.AUDIO,
            cornerRadius = 16.dp,
            iconSize = 44.dp,
            placeholderIcon = if (album.isUntagged) R.drawable.ic_audio else R.drawable.ic_album,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.displayTitle(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.subtitle
                ?: pluralStringResource(R.plurals.playlist_song_count, album.items.size, album.items.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyAlbums() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_album),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.albums_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.albums_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** El grupo sin etiqueta no tiene nombre propio: se lo pone la pantalla. */
@Composable
fun Album.displayTitle(): String =
    if (isUntagged) stringResource(R.string.albums_untagged) else title

private val AlbumGrouping.labelRes: Int
    get() = when (this) {
        AlbumGrouping.BY_ALBUM -> R.string.albums_group_by_album
        AlbumGrouping.BY_ARTIST -> R.string.albums_group_by_artist
    }

/** LazyVerticalGrid necesita una clave no vacía; el grupo sin etiqueta usa esta. */
private const val UNTAGGED_KEY = "__randoms__"

// --- Preview ---

private fun song(title: String, artist: String?, album: String?, track: Int?) = LibraryItem(
    id = title.hashCode().toLong(),
    uri = "content://preview/$title",
    title = title,
    artist = artist,
    type = MediaType.AUDIO,
    durationMs = 210_000,
    sizeBytes = 5_000_000,
    dateAddedSeconds = 1,
    album = album,
    trackNumber = track,
)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AlbumsScreenPreview() {
    val items = listOf(
        song("Sobredosis de TV", "Soda Stereo", "Nada Personal", 3),
        song("Cuando Pase el Temblor", "Soda Stereo", "Nada Personal", 5),
        song("De Música Ligera", "Soda Stereo", "Canción Animal", 8),
        song("Video que descargué", "Canal cualquiera", null, null),
    )
    ExoTubeTheme {
        AlbumsScreen(
            state = AlbumsUiState(
                albums = buildAlbums(items, AlbumGrouping.BY_ALBUM),
                isLoading = false,
            ),
            onGroupingSelected = {},
            onOpenAlbum = {},
            contentPadding = PaddingValues(),
        )
    }
}
