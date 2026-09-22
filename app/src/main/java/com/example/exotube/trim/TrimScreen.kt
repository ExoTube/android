package com.example.exotube.trim

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.exotube.R
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.ui.components.MediaArtwork
import com.example.exotube.ui.formatPreciseTime
import com.example.exotube.ui.theme.ExoTubeTheme

/**
 * Conecta el ViewModel con la pantalla y avisa del resultado.
 *
 * El ViewModel se crea con la canción como clave: al abrir otra canción se crea uno nuevo, sin
 * arrastrar el recorte de la anterior.
 */
@Composable
fun TrimRoute(
    item: LibraryItem,
    onClose: () -> Unit,
    viewModel: TrimViewModel = viewModel(key = item.uri, factory = TrimViewModel.factory(item)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                TrimEvent.Saved -> context.getString(R.string.trim_saved)
                is TrimEvent.Failed -> context.getString(R.string.trim_failed, event.message.orEmpty())
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            if (event is TrimEvent.Saved) onClose()
        }
    }

    TrimScreen(
        item = item,
        state = state,
        onRangeChange = viewModel::onRangeChange,
        onNameChange = viewModel::onNameChange,
        onSave = viewModel::save,
        onClose = onClose,
    )
}

/** Pantalla "tonta": solo dibuja el estado que recibe. */
@Composable
fun TrimScreen(
    item: LibraryItem,
    state: TrimUiState,
    onRangeChange: (startMs: Long, endMs: Long) -> Unit,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Se dibuja encima de todo, fuera del Scaffold: sin Surface los textos saldrían invisibles.
    Surface(color = colors.background, modifier = modifier.fillMaxSize()) {
        Column(
            Modifier
                .systemBarsPadding()
                .imePadding() // que el teclado no tape el campo del nombre
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                }
                Text(
                    text = stringResource(R.string.trim_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            SongSummary(item)

            Spacer(Modifier.height(24.dp))
            FragmentRange(state, onRangeChange)

            Spacer(Modifier.height(24.dp))
            FragmentPreview(item, state)

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.trim_name_label)) },
                supportingText = { Text(stringResource(R.string.trim_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSave,
                enabled = !state.isSaving && state.fragmentMs >= TrimViewModel.MIN_FRAGMENT_MS,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = colors.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(stringResource(R.string.trim_save))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SongSummary(item: LibraryItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MediaArtwork(item.uri, MediaType.AUDIO, Modifier.size(64.dp), cornerRadius = 12.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            item.artist?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FragmentRange(state: TrimUiState, onRangeChange: (Long, Long) -> Unit) {
    val duration = state.durationMs.coerceAtLeast(1)
    RangeSlider(
        value = state.startMs.toFloat()..state.endMs.toFloat(),
        onValueChange = { range -> onRangeChange(range.start.toLong(), range.endInclusive.toLong()) },
        valueRange = 0f..duration.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TimeLabel(R.string.trim_start, state.startMs)
        Text(
            text = stringResource(R.string.trim_fragment, formatPreciseTime(state.fragmentMs)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        TimeLabel(R.string.trim_end, state.endMs)
    }
}

@Composable
private fun TimeLabel(labelRes: Int, millis: Long) {
    Column {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(formatPreciseTime(millis), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Escuchar solo el trozo elegido, antes de guardarlo.
 *
 * Usa un reproductor propio y aparte del de la app, con la "configuración de recorte" de Media3:
 * se le dice desde y hasta dónde, y él se encarga de parar solo. No hay temporizadores.
 */
@Composable
private fun FragmentPreview(item: LibraryItem, state: TrimUiState) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Al mover los extremos, el fragmento de antes ya no es el que se quiere oír.
    LaunchedEffect(state.startMs, state.endMs) {
        if (player.isPlaying) player.stop()
    }

    OutlinedButton(
        onClick = {
            if (isPlaying) {
                player.stop()
            } else {
                player.setMediaItem(item.clippedTo(state))
                player.prepare()
                player.play()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (isPlaying) R.string.trim_stop_preview else R.string.trim_preview))
    }
}

private fun LibraryItem.clippedTo(state: TrimUiState): MediaItem = MediaItem.Builder()
    .setUri(uri)
    .setClippingConfiguration(
        MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(state.startMs)
            .setEndPositionMs(state.endMs)
            .build(),
    )
    .build()

// --- Preview ---

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TrimScreenPreview() {
    val item = LibraryItem(
        id = 1,
        uri = "content://preview/1",
        title = "Sobredosis de TV",
        artist = "Soda Stereo",
        type = MediaType.AUDIO,
        durationMs = 353_000,
        sizeBytes = 8_000_000,
        dateAddedSeconds = 1,
    )
    ExoTubeTheme {
        TrimScreen(
            item = item,
            state = TrimUiState(
                startMs = 12_400,
                endMs = 42_000,
                name = TrimViewModel.defaultName(item.title),
                durationMs = item.durationMs,
            ),
            onRangeChange = { _, _ -> },
            onNameChange = {},
            onSave = {},
            onClose = {},
        )
    }
}
