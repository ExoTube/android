package com.example.exotube.songedit

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.exotube.R
import com.example.exotube.ui.theme.ExoTubeTheme

/**
 * Diálogo para cambiar el nombre de una canción.
 *
 * Empieza con el nombre que ya tiene, y no vacío, porque casi siempre se quiere arreglar un
 * título de YouTube (quitarle el "(Official Music Video)") y no escribirlo de cero.
 */
@Composable
fun RenameSongDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(currentTitle) { mutableStateOf(currentTitle) }
    val isValid = title.isNotBlank()
    val confirm = { if (isValid) onConfirm(title.trim()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.rename_title)) },
        text = { TitleField(title, onTitleChange = { title = it }, onDone = confirm) },
        confirmButton = {
            TextButton(onClick = confirm, enabled = isValid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** El teclado se abre solo y "Listo" confirma: dos toques menos. */
@Composable
private fun TitleField(title: String, onTitleChange: (String) -> Unit, onDone: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = title,
        onValueChange = { onTitleChange(it.take(MAX_TITLE_LENGTH)) },
        label = { Text(stringResource(R.string.rename_label)) },
        supportingText = { Text(stringResource(R.string.rename_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .padding(top = 8.dp)
            .focusRequester(focusRequester),
    )
}

/** Un título de canción largo cabe de sobra; más allá solo estorba al leer la lista. */
private const val MAX_TITLE_LENGTH = 100

@Preview
@Composable
private fun RenameSongDialogPreview() {
    ExoTubeTheme {
        RenameSongDialog(
            currentTitle = "Soda Stereo - Un Misil en Mi Placard (Official Audio)",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
