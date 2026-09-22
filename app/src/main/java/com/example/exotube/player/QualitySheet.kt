package com.example.exotube.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.example.exotube.R
import com.example.exotube.domain.model.VideoQuality

/**
 * La lista de calidades para el video en línea que suena.
 *
 * "Automática" va la primera y es la que tiene cada video al empezar. Debajo de ella se dice en
 * qué calidad se está viendo de verdad, porque "automática" a secas no le dice nada a nadie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(
    player: Player,
    selected: VideoQuality,
    onSelect: (VideoQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    val playingHeight = rememberVideoHeight(player)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.quality_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = stringResource(R.string.quality_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                VideoQuality.entries.forEach { quality ->
                    QualityRow(
                        label = quality.label(),
                        hint = quality.hint(playingHeight.takeIf { selected == VideoQuality.AUTO }),
                        isSelected = quality == selected,
                        onClick = {
                            onSelect(quality)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityRow(label: String, hint: String?, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Toda la fila se puede tocar, no solo el circulito: es lo que espera el dedo.
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            hint?.let {
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
private fun VideoQuality.label(): String = when (this) {
    VideoQuality.AUTO -> stringResource(R.string.quality_auto)
    else -> stringResource(R.string.quality_height, checkNotNull(maxHeight))
}

/**
 * La explicación bajo cada opción; solo la llevan las que no se explican solas.
 * [playingHeight] es la altura real del video, si se está viendo en automático.
 */
@Composable
private fun VideoQuality.hint(playingHeight: Int?): String? = when (this) {
    VideoQuality.AUTO -> if (playingHeight != null && playingHeight > 0) {
        stringResource(R.string.quality_auto_now, playingHeight)
    } else {
        stringResource(R.string.quality_auto_hint)
    }
    VideoQuality.P1080 -> stringResource(R.string.quality_hint_1080)
    VideoQuality.P144 -> stringResource(R.string.quality_hint_144)
    else -> null
}

/**
 * La calidad que se está viendo ahora ("720" de "720p"), y se actualiza si cambia. 0 si aún no
 * se sabe. Es el lado CORTO de la imagen: un video vertical de 720p mide 720 de ancho y 1280 de
 * alto, y nadie diría que es "1280p".
 */
@Composable
private fun rememberVideoHeight(player: Player): Int {
    var height by remember(player) { mutableIntStateOf(player.videoSize.shortSide()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                height = videoSize.shortSide()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return height
}

private fun VideoSize.shortSide(): Int = minOf(width, height)
