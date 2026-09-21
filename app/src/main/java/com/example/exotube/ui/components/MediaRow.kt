package com.example.exotube.ui.components

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.exotube.R
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.ui.formatDuration

/** Una opción del menú de una fila ("Añadir a playlist", "Quitar de la playlist"…). */
data class RowAction(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit,
)

/**
 * Fila de canción/video, compartida por la Biblioteca y el detalle de playlist.
 * Si recibe [actions], muestra el botón de opciones con un menú desplegable.
 */
@Composable
fun MediaRow(
    item: LibraryItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    actions: List<RowAction> = emptyList(),
) {
    val context = LocalContext.current
    val subtitle = listOf(
        item.artist ?: stringResource(if (item.type == MediaType.VIDEO) R.string.type_video else R.string.type_audio),
        formatDuration(item.durationMs / 1000),
        Formatter.formatShortFileSize(context, item.sizeBytes),
    ).joinToString(" · ")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = if (actions.isEmpty()) 20.dp else 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        MediaArtwork(item.uri, item.type, Modifier.size(56.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // La fila que está sonando lleva el ecualizador: se mueve si suena, quieto si está en pausa.
        if (isCurrent) {
            PlayingBars(
                isPlaying = isPlaying,
                contentDescription = stringResource(R.string.player_now_playing),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(width = 18.dp, height = 16.dp),
            )
        }
        if (actions.isNotEmpty()) RowMenu(actions)
    }
}

@Composable
private fun RowMenu(actions: List<RowAction>) {
    var expanded by remember { mutableStateOf(false) }
    // El Box ancla el menú desplegable al botón.
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
