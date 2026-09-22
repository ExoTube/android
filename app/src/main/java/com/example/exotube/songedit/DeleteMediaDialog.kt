package com.example.exotube.songedit

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.exotube.R
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.ui.theme.ExoTubeTheme

/**
 * "¿Seguro?" antes de borrar un archivo del teléfono.
 *
 * Aquí no hay papelera: lo borrado se va para siempre. Un toque sin querer en el menú no puede
 * costarle a nadie una canción, así que se pregunta siempre, y se dice claramente que no tiene
 * vuelta atrás. El botón va en rojo por lo mismo.
 */
@Composable
fun DeleteMediaDialog(
    item: LibraryItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.delete_title, item.title)) },
        text = {
            Text(
                stringResource(
                    if (item.type == MediaType.VIDEO) R.string.delete_body_video else R.string.delete_body_song,
                ),
            )
        },
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

@Preview
@Composable
private fun DeleteMediaDialogPreview() {
    ExoTubeTheme {
        DeleteMediaDialog(
            item = LibraryItem(
                id = 1,
                uri = "content://media/external/audio/media/1",
                title = "De Música Ligera",
                artist = "Soda Stereo",
                type = MediaType.AUDIO,
                durationMs = 210_000,
                sizeBytes = 3_400_000,
                dateAddedSeconds = 0,
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
