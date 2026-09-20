package com.example.exotube.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.exotube.R
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.Playlist
import com.example.exotube.domain.model.PlaylistCover
import com.example.exotube.ui.components.MediaArtwork
import com.example.exotube.ui.theme.ExoTubeTheme
import java.io.File

/** Pestaña "Playlists": crear una nueva y abrir las existentes. */
@Composable
fun PlaylistsScreen(
    state: PlaylistsUiState,
    onOpenPlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(contentPadding = contentPadding, modifier = modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.nav_playlists), style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = pluralStringResource(R.plurals.playlist_count, state.playlists.size, state.playlists.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            PlaylistRowLayout(
                artwork = { NewPlaylistBadge(Modifier.size(56.dp)) },
                title = stringResource(R.string.playlist_new),
                subtitle = null,
                onClick = onCreatePlaylist,
            )
        }
        when {
            state.isLoading -> item {
                Box(Modifier.fillParentMaxHeight(0.5f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.playlists.isEmpty() -> item { EmptyPlaylists(Modifier.fillParentMaxHeight(0.5f)) }
            else -> items(state.playlists, key = { it.id }) { playlist ->
                PlaylistRowLayout(
                    artwork = { PlaylistArtwork(playlist.cover, Modifier.size(56.dp)) },
                    title = playlist.name,
                    subtitle = pluralStringResource(R.plurals.playlist_song_count, playlist.itemCount, playlist.itemCount),
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistRowLayout(
    artwork: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        artwork()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyPlaylists(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
    ) {
        PlaylistArtwork(PlaylistCover.Empty, modifier = Modifier.size(96.dp), cornerRadius = 48.dp, iconSize = 44.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.playlists_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.playlists_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Portada de playlist: la foto del usuario, la carátula de una de sus canciones o un icono. */
@Composable
fun PlaylistArtwork(
    cover: PlaylistCover,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    iconSize: Dp = 24.dp,
) {
    when (cover) {
        is PlaylistCover.Photo -> AsyncImage(
            model = File(cover.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        is PlaylistCover.FromSong -> MediaArtwork(
            uri = cover.item.uri,
            type = cover.item.type,
            modifier = modifier,
            cornerRadius = cornerRadius,
            iconSize = iconSize,
            placeholderIcon = R.drawable.ic_queue_music,
        )
        PlaylistCover.Empty -> MediaArtwork(
            uri = null,
            type = MediaType.AUDIO,
            modifier = modifier,
            cornerRadius = cornerRadius,
            iconSize = iconSize,
            placeholderIcon = R.drawable.ic_queue_music,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PlaylistsScreenPreview() {
    ExoTubeTheme {
        PlaylistsScreen(
            state = PlaylistsUiState(
                playlists = listOf(
                    Playlist(1, "Favoritas", 12, PlaylistCover.Empty),
                    Playlist(2, "Para entrenar", 0, PlaylistCover.Empty),
                ),
                isLoading = false,
            ),
            onOpenPlaylist = {},
            onCreatePlaylist = {},
            contentPadding = PaddingValues(),
        )
    }
}
