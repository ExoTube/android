package com.example.exotube.album

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.exotube.R
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.ui.components.MediaArtwork
import com.example.exotube.ui.components.MediaRow
import com.example.exotube.ui.components.RowAction
import com.example.exotube.ui.formatTotalDuration

/**
 * Las canciones de un álbum, en el orden del disco.
 *
 * A diferencia de una playlist, aquí no se añade ni se quita nada: el álbum lo deciden las
 * etiquetas de los archivos. Lo único que se puede hacer es escucharlo y añadir sus canciones
 * a una playlist.
 */
@Composable
fun AlbumDetailScreen(
    album: Album,
    nowPlayingUri: String?,
    isPlaying: Boolean,
    onPlay: (items: List<LibraryItem>, startIndex: Int, shuffle: Boolean) -> Unit,
    onAddToPlaylist: (LibraryItem) -> Unit,
    onTrim: (LibraryItem) -> Unit,
    onChangeCover: (LibraryItem) -> Unit,
    onRename: (LibraryItem) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(contentPadding = contentPadding, modifier = modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                }
            }
        }
        item { AlbumHeader(album, onPlay) }

        if (album.isUntagged) {
            item { UntaggedHint() }
        }

        itemsIndexed(album.items, key = { _, item -> item.uri }) { index, item ->
            MediaRow(
                item = item,
                isCurrent = item.uri == nowPlayingUri,
                onClick = { onPlay(album.items, index, false) },
                isPlaying = isPlaying,
                actions = listOf(
                    RowAction(R.string.add_to_playlist, R.drawable.ic_playlist_add) { onAddToPlaylist(item) },
                    RowAction(R.string.trim_action, R.drawable.ic_cut) { onTrim(item) },
                    RowAction(R.string.cover_action, R.drawable.ic_image) { onChangeCover(item) },
                    RowAction(R.string.rename_action, R.drawable.ic_edit) { onRename(item) },
                ),
            )
        }
    }
}

@Composable
private fun AlbumHeader(
    album: Album,
    onPlay: (items: List<LibraryItem>, startIndex: Int, shuffle: Boolean) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        MediaArtwork(
            uri = album.coverUri,
            type = MediaType.AUDIO,
            cornerRadius = 20.dp,
            iconSize = 72.dp,
            placeholderIcon = if (album.isUntagged) R.drawable.ic_audio else R.drawable.ic_album,
            modifier = Modifier.size(180.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = album.displayTitle(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        val songCount = pluralStringResource(R.plurals.playlist_song_count, album.items.size, album.items.size)
        Text(
            text = listOfNotNull(album.subtitle, songCount, formatTotalDuration(album.totalDurationMs))
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onPlay(album.items, 0, false) }) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.playlist_play))
            }
            OutlinedButton(onClick = { onPlay(album.items, 0, true) }) {
                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.playlist_shuffle))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Explica por qué una canción acabó en "Randoms", que si no parece un fallo de la app. */
@Composable
private fun UntaggedHint() {
    Text(
        text = stringResource(R.string.albums_untagged_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
    )
}
