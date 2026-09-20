package com.example.exotube.playlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.exotube.R
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.Playlist

private const val MAX_NAME_LENGTH = 60

/**
 * Diálogo "Nueva playlist": nombre + foto opcional.
 *
 * La foto se elige con el selector de fotos de Android (Photo Picker): no hace falta pedir
 * permisos de galería, porque el usuario solo comparte la foto que elige.
 */
@Composable
fun CreatePlaylistDialog(
    onConfirm: (name: String, coverImageUri: Uri?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var cover by rememberSaveable { mutableStateOf<Uri?>(null) } // Uri es Parcelable: sobrevive a rotar
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cover = uri // null = el usuario cerró el selector sin elegir
    }
    val isValid = name.isNotBlank()
    val confirm = { if (isValid) onConfirm(name.trim(), cover) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.playlist_new)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CoverPicker(
                    cover = cover,
                    onPick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemove = { cover = null },
                )
                Spacer(Modifier.height(16.dp))
                PlaylistNameField(name, onNameChange = { name = it }, onDone = confirm)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        if (cover == null) R.string.playlist_cover_hint_random else R.string.playlist_cover_hint_photo,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = isValid) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Recuadro para elegir la foto: muestra la elegida o una invitación a añadirla. */
@Composable
private fun CoverPicker(cover: Uri?, onPick: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.size(128.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(colors.surfaceContainerHighest)
                .border(1.dp, colors.outlineVariant, shape)
                .clickable(onClick = onPick),
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = stringResource(R.string.playlist_cover),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_add_photo), null, tint = colors.primary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.playlist_add_photo), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (cover != null) {
            FilledIconButton(
                onClick = onRemove,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.surfaceContainerHigh,
                    contentColor = colors.onSurface,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp),
            ) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.playlist_remove_photo), Modifier.size(18.dp))
            }
        }
    }
}

/** Diálogo para cambiar el nombre de una playlist existente. */
@Composable
fun PlaylistNameDialog(
    @StringRes title: Int,
    @StringRes confirmLabel: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val isValid = name.isNotBlank()
    val confirm = { if (isValid) onConfirm(name.trim()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(title)) },
        text = { PlaylistNameField(name, onNameChange = { name = it }, onDone = confirm) },
        confirmButton = {
            TextButton(onClick = confirm, enabled = isValid) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Campo del nombre: el teclado se abre solo y "Listo" en el teclado confirma. */
@Composable
private fun PlaylistNameField(name: String, onNameChange: (String) -> Unit, onDone: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = name,
        onValueChange = { onNameChange(it.take(MAX_NAME_LENGTH)) },
        placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

@Composable
fun DeletePlaylistDialog(playlistName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.playlist_delete_title, playlistName)) },
        text = { Text(stringResource(R.string.playlist_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Hoja "Añadir a playlist": una casilla por playlist (marcada si la canción ya está) y una
 * fila para crear una nueva. Es "tonta": recibe los datos y avisa de los toques.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    item: LibraryItem,
    playlists: List<Playlist>,
    playlistIdsWithItem: Set<Long>,
    onToggle: (playlistId: Long, include: Boolean) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.add_to_playlist), style = MaterialTheme.typography.titleLarge)
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.playlist_new)) },
                    leadingContent = { NewPlaylistBadge() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onCreateNew),
                )
            }
            items(playlists, key = { it.id }) { playlist ->
                val isIncluded = playlist.id in playlistIdsWithItem
                ListItem(
                    headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.playlist_song_count, playlist.itemCount, playlist.itemCount))
                    },
                    leadingContent = { PlaylistArtwork(playlist.cover, Modifier.size(48.dp)) },
                    trailingContent = {
                        Checkbox(
                            checked = isIncluded,
                            onCheckedChange = { onToggle(playlist.id, it) },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onToggle(playlist.id, !isIncluded) },
                )
            }
        }
    }
}

/** Cuadro verde con "+" para la acción "Nueva playlist". */
@Composable
fun NewPlaylistBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
