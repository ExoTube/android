package com.example.exotube.player

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import com.example.exotube.R
import kotlinx.coroutines.delay

/**
 * El video ocupando toda la pantalla, en horizontal y sin barras del sistema.
 *
 * Sirve igual para los videos descargados y para los de la pestaña Explorar: a este nivel un
 * video es un video, la diferencia de dónde salen los datos se resolvió mucho antes.
 *
 * Los controles se esconden solos a los pocos segundos y vuelven al tocar la pantalla, para que
 * no tapen la imagen. Es lo que hace cualquier reproductor y no hay que explicárselo a nadie.
 */
@Composable
fun FullscreenVideoScreen(player: Player, onExit: () -> Unit, modifier: Modifier = Modifier) {
    LandscapeImmersiveEffect()

    val current = rememberCurrentMediaItemState(player)
    // Si la cola avanza a una canción, aquí no hay nada que mirar: se sale solo.
    val isVideo = current.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO
    LaunchedEffect(isVideo) { if (!isVideo) onExit() }

    var showControls by remember { mutableStateOf(true) }
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(CONTROLS_TIMEOUT_MS)
            showControls = false
        }
    }

    Surface(color = Color.Black, modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Sin indicación ni resalte: es la imagen del video, no un botón.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showControls = !showControls },
        ) {
            // ContentFrame respeta la proporción del video: las bandas negras las pone el fondo.
            ContentFrame(player = player, modifier = Modifier.fillMaxSize())

            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                FullscreenControls(
                    player = player,
                    title = current.mediaMetadata.title?.toString().orEmpty(),
                    onExit = onExit,
                )
            }
        }
    }
}

@Composable
private fun FullscreenControls(player: Player, title: String, onExit: () -> Unit) {
    val playPause = rememberPlayPauseButtonState(player)
    val previous = rememberPreviousButtonState(player)
    val next = rememberNextButtonState(player)

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Velo oscuro para que los botones blancos se vean también sobre imagen clara.
            .background(Color.Black.copy(alpha = 0.35f))
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onExit) {
                Icon(
                    painter = painterResource(R.drawable.ic_fullscreen_exit),
                    contentDescription = stringResource(R.string.player_fullscreen_exit),
                    tint = Color.White,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = previous::onClick, enabled = previous.isEnabled) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = stringResource(R.string.player_previous),
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
                FilledIconButton(
                    onClick = playPause::onClick,
                    enabled = playPause.isEnabled,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.size(64.dp),
                ) {
                    PlayPauseIcon(playPause, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = next::onClick, enabled = next.isEnabled) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = stringResource(R.string.player_next),
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }

        SeekBar(player)
        Spacer(Modifier.size(8.dp))
    }
}

/**
 * Pone la pantalla en horizontal y esconde las barras del sistema mientras se ve el video, y lo
 * deja todo como estaba al salir.
 *
 * La Activity no se recrea al girar (está declarado en el manifest para la ventana flotante), así
 * que la música no se corta ni el video vuelve a empezar.
 *
 * Las barras se esconden con BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE: no desaparecen para siempre,
 * vuelven a asomar si el usuario desliza desde el borde. Esconderlas sin salida sería una trampa.
 */
@Composable
private fun LandscapeImmersiveEffect() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousOrientation = activity.requestedOrientation
        val previousBehavior = controller.systemBarsBehavior

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousBehavior
            activity.requestedOrientation = previousOrientation
        }
    }
}

/** Lo que tarda en esconderse el mando si nadie lo toca. */
private const val CONTROLS_TIMEOUT_MS = 3_500L
