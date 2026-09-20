package com.example.exotube.playlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.exotube.R
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.PlaylistCover
import com.example.exotube.domain.model.PlaylistDetail
import com.example.exotube.ui.components.MediaRow
import com.example.exotube.ui.components.RowAction
import com.example.exotube.ui.formatTotalDuration

/** Conecta el ViewModel (uno por playlist abierta) con la pantalla. */
@Composable
fun PlaylistDetailRoute(
    nowPlayingUri: String?,
    onPlay: (items: List<LibraryItem>, startIndex: Int, shuffle: Boolean) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: PlaylistDetailViewModel = viewModel(factory = PlaylistDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.setCover(it.toString()) }
    }

    when (val current = state) {
        PlaylistDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        // Si se eliminó, volvemos atrás una sola vez (efecto, no durante el dibujo).
        PlaylistDetailUiState.Deleted -> LaunchedEffect(Unit) { onBack() }
        is PlaylistDetailUiState.Ready -> {
            val playlist = current.playlist
            PlaylistDetailScreen(
                playlist = playlist,
                nowPlayingUri = nowPlayingUri,
                onBack = onBack,
                onPlay = { index -> onPlay(playlist.items, index, false) },
                onShuffle = { onPlay(playlist.items, 0, true) },
                onRemove = { viewModel.remove(it.uri) },
                onRename = { showRename = true },
                onChangePhoto = {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemovePhoto = { viewModel.setCover(null) },
                onDelete = { showDelete = true },
                contentPadding = contentPadding,
            )
            if (showRename) {
                PlaylistNameDialog(
                    title = R.string.playlist_rename_title,
                    confirmLabel = R.string.action_save,
                    initialName = playlist.name,
                    onConfirm = { viewModel.rename(it); showRename = false },
                    onDismiss = { showRename = false },
                )
            }
            if (showDelete) {
                DeletePlaylistDialog(
                    playlistName = playlist.name,
                    onConfirm = { viewModel.delete(); showDelete = false },
                    onDismiss = { showDelete = false },
                )
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistDetail,
    nowPlayingUri: String?,
    onBack: () -> Unit,
    onPlay: (index: Int) -> Unit,
    onShuffle: () -> Unit,
    onRemove: (LibraryItem) -> Unit,
    onRename: () -> Unit,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDelete: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(contentPadding = contentPadding, modifier = modifier.fillMaxSize()) {
        item {
            DetailTopBar(
                hasPhoto = playlist.cover is PlaylistCover.Photo,
                onBack = onBack,
                onRename = onRename,
                onChangePhoto = onChangePhoto,
                onRemovePhoto = onRemovePhoto,
                onDelete = onDelete,
            )
        }
        item { DetailHeader(playlist, onPlay = { onPlay(0) }, onShuffle = onShuffle) }
        if (playlist.items.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.playlist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 32.dp),
                )
            }
        } else {
            itemsIndexed(playlist.items, key = { _, item -> item.uri }) { index, item ->
                MediaRow(
                    item = item,
                    isCurrent = item.uri == nowPlayingUri,
                    onClick = { onPlay(index) },
                    actions = listOf(
                        RowAction(R.string.remove_from_playlist, R.drawable.ic_playlist_remove) { onRemove(item) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    hasPhoto: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more_options))
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_rename)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                    onClick = { menuExpanded = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_change_photo)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null) },
                    onClick = { menuExpanded = false; onChangePhoto() },
                )
                if (hasPhoto) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_remove_photo)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null) },
                        onClick = { menuExpanded = false; onRemovePhoto() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun DetailHeader(playlist: PlaylistDetail, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val hasSongs = playlist.items.isNotEmpty()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        PlaylistArtwork(playlist.cover, Modifier.size(180.dp), cornerRadius = 20.dp, iconSize = 72.dp)
        Spacer(Modifier.height(20.dp))
        Text(playlist.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        val songCount = pluralStringResource(R.plurals.playlist_song_count, playlist.items.size, playlist.items.size)
        Text(
            text = if (hasSongs) "$songCount · ${formatTotalDuration(playlist.totalDurationMs)}" else songCount,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPlay, enabled = hasSongs) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.playlist_play))
            }
            OutlinedButton(onClick = onShuffle, enabled = hasSongs) {
                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.playlist_shuffle))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
