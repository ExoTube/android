package com.example.exotube.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import com.example.exotube.ui.tour.TourSpot
import com.example.exotube.ui.tour.tourSpot
import com.example.exotube.R
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.VideoQuality
import com.example.exotube.ui.components.MediaArtwork
import com.example.exotube.ui.components.PlayingBars
import com.example.exotube.ui.formatDuration

// Los remember…State de Media3 escuchan al Player y recomponen solo lo necesario:
// no hace falta copiar el estado del reproductor a un ViewModel.

/**
 * Barra inferior con lo que está sonando. No se muestra si la cola está vacía.
 * No aplica los márgenes de la barra del sistema: los gestiona quien la coloca (AppShell).
 */
@Composable
fun MiniPlayer(player: Player, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val current = rememberCurrentMediaItemState(player)
    if (current.mediaItem == null) return // cola vacía: no hay nada que enseñar
    val playPause = rememberPlayPauseButtonState(player)
    val next = rememberNextButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, 1_000L)

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth()
            .tourSpot(TourSpot.MINI_PLAYER),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
            ) {
                // La carátula sale de artworkUri, no del mediaId: en un video en línea el
                // mediaId es el enlace de YouTube, y la imagen es su miniatura.
                MediaArtwork(
                    uri = current.mediaMetadata.artworkUri?.toString(),
                    type = current.mediaMetadata.toMediaType(),
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 8.dp,
                    iconSize = 20.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = current.mediaMetadata.title?.toString().orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    current.mediaMetadata.artist?.let {
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = playPause::onClick, enabled = playPause.isEnabled) {
                    PlayPauseIcon(playPause, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = next::onClick, enabled = next.isEnabled) {
                    Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.player_next))
                }
            }
            LinearProgressIndicator(
                progress = { fractionOf(progress.currentPositionMs, progress.durationMs) },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}

/** Pantalla completa "Reproduciendo". Si el archivo es un video, lo muestra en lugar de la carátula. */
@Composable
fun NowPlayingScreen(
    player: Player,
    repeatPlan: RepeatPlan,
    onCycleRepeat: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onEnterFullscreen: () -> Unit,
    /** null si el teléfono no admite la ventana flotante. */
    onEnterPictureInPicture: (() -> Unit)?,
    onCollapse: () -> Unit,
    /** El video en línea que suena, si lo es; null con los archivos del teléfono. */
    online: OnlinePlayback?,
    onChangeQuality: (VideoQuality) -> Unit,
    modifier: Modifier = Modifier,
    /** Abrir el canal del video en línea que suena; null si no aplica. */
    onOpenChannel: ((OnlineVideo) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme

    // Esta pantalla se dibuja ENCIMA del Scaffold, fuera de cualquier Surface: sin este Surface,
    // Compose usaría el color de contenido por defecto (negro) y los textos serían invisibles.
    Surface(color = colors.background, modifier = modifier.fillMaxSize()) {
        NowPlayingContent(
            player = player,
            repeatPlan = repeatPlan,
            onCycleRepeat = onCycleRepeat,
            onOpenEqualizer = onOpenEqualizer,
            onEnterFullscreen = onEnterFullscreen,
            onEnterPictureInPicture = onEnterPictureInPicture,
            onCollapse = onCollapse,
            online = online,
            onChangeQuality = onChangeQuality,
            onOpenChannel = onOpenChannel,
        )
    }
}

/**
 * Solo el video, a pantalla completa y sobre negro: es lo que se ve en la ventana flotante.
 *
 * En la ventana flotante no cabe nada más: Android la dibuja de unos pocos centímetros y los
 * botones vienen puestos por el sistema, no por la app.
 */
@Composable
fun FloatingVideo(player: Player, modifier: Modifier = Modifier) {
    Surface(color = Color.Black, modifier = modifier.fillMaxSize()) {
        ContentFrame(player = player, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun NowPlayingContent(
    player: Player,
    repeatPlan: RepeatPlan,
    onCycleRepeat: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onEnterPictureInPicture: (() -> Unit)?,
    onCollapse: () -> Unit,
    online: OnlinePlayback?,
    onChangeQuality: (VideoQuality) -> Unit,
    onOpenChannel: ((OnlineVideo) -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val current = rememberCurrentMediaItemState(player)
    val metadata = current.mediaMetadata
    val playPause = rememberPlayPauseButtonState(player)
    // showPlay es "toca mostrar el botón de reproducir", o sea: ahora mismo NO está sonando.
    val isPlaying = !playPause.showPlay
    val isVideo = metadata.toMediaType() == MediaType.VIDEO
    // Solo se elige calidad con un video en línea que traiga imagen: un archivo del teléfono
    // tiene la que tiene, y en modo ahorro de datos no hay imagen que elegir.
    val onlineNow = online?.takeIf { it.video.url == current.mediaItem?.mediaId }
    val canChooseQuality = isVideo && onlineNow != null && !onlineNow.audioOnly
    var showQuality by rememberSaveable { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            // Degradado verde muy sutil que se funde con el negro.
            .background(Brush.verticalGradient(0f to colors.primaryContainer, 0.55f to colors.background))
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onCollapse, modifier = Modifier.tourSpot(TourSpot.PLAYER_COLLAPSE)) {
                Icon(painterResource(R.drawable.ic_expand_more), stringResource(R.string.player_collapse))
            }
            Text(
                text = stringResource(R.string.player_now_playing),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // La ventana flotante solo tiene sentido con imagen; para una canción basta con que
            // el sonido siga, que ya ocurre al salir de la app.
            if (canChooseQuality) {
                IconButton(onClick = { showQuality = true }, modifier = Modifier.tourSpot(TourSpot.VIDEO_QUALITY)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_hd),
                        contentDescription = stringResource(R.string.quality_open),
                    )
                }
            }
            if (isVideo && onEnterPictureInPicture != null) {
                IconButton(onClick = onEnterPictureInPicture, modifier = Modifier.tourSpot(TourSpot.VIDEO_FLOATING)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_picture_in_picture),
                        contentDescription = stringResource(R.string.player_picture_in_picture),
                    )
                }
            }
            IconButton(onClick = onOpenEqualizer, modifier = Modifier.tourSpot(TourSpot.PLAYER_EQUALIZER)) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = stringResource(R.string.equalizer_open),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        if (isVideo) {
            VideoFrame(
                player = player,
                isLoadingStream = onlineNow?.isLoadingStream == true,
                onEnterFullscreen = onEnterFullscreen,
            )
        } else {
            BreathingArtwork(
                uri = metadata.artworkUri?.toString(),
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }
        Spacer(Modifier.weight(1f))

        Text(
            text = metadata.title?.toString().orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        metadata.artist?.let {
            // Con un video en línea, el canal se puede tocar: lleva a su página, con sus videos.
            val openChannel = onlineNow?.let { playing -> onOpenChannel?.let { { it(playing.video) } } }
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (openChannel != null) colors.primary else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (openChannel != null) {
                            Modifier
                                .tourSpot(TourSpot.VIDEO_CHANNEL)
                                .clickable(onClickLabel = stringResource(R.string.channel_open), onClick = openChannel)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        Spacer(Modifier.height(16.dp))
        // La onda solo tiene sentido con la musica: en un video lo que importa es la imagen, y
        // ademas de un video en linea no hay archivo que leer.
        Box(Modifier.tourSpot(TourSpot.PLAYER_SEEK)) {
            if (isVideo) {
                SeekBar(player)
            } else {
                WaveformSeekBar(player, current.mediaItem?.mediaId)
            }
        }
        Spacer(Modifier.height(8.dp))
        PlaybackControls(player, playPause, repeatPlan, onCycleRepeat)
        Spacer(Modifier.height(32.dp))
    }

    if (showQuality && canChooseQuality && onlineNow != null) {
        QualitySheet(
            player = player,
            selected = onlineNow.quality,
            onSelect = onChangeQuality,
            onDismiss = { showQuality = false },
        )
    }
}

/**
 * El video dentro del reproductor, con el botón de pantalla completa en una esquina.
 *
 * ContentFrame dibuja la imagen y respeta su relación de aspecto; el botón va encima, donde lo
 * espera cualquiera que haya visto un video en un teléfono.
 *
 * Con [isLoadingStream] se está pidiendo a YouTube otra dirección (otra calidad, o recuperar un
 * fallo): una rueda en el centro dice que algo está pasando y que no hace falta tocar nada.
 */
@Composable
private fun VideoFrame(player: Player, isLoadingStream: Boolean, onEnterFullscreen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        ContentFrame(player = player, modifier = Modifier.fillMaxSize())
        if (isLoadingStream) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        IconButton(
            onClick = onEnterFullscreen,
            modifier = Modifier.align(Alignment.BottomEnd).tourSpot(TourSpot.VIDEO_FULLSCREEN),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_fullscreen),
                contentDescription = stringResource(R.string.player_fullscreen),
                tint = Color.White,
            )
        }
    }
}

/**
 * La carátula "respira" mientras suena la música y se encoge al pausar, con un ecualizador
 * en la esquina. Es la señal de que algo está sonando sin tener que mirar los botones.
 */
@Composable
private fun BreathingArtwork(uri: String?, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(PAUSED_SCALE) }
    // LaunchedEffect se cancela y se relanza cada vez que cambia isPlaying: al pausar, el latido
    // infinito se corta solo. No hay ningún temporizador que parar a mano.
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            scale.animateTo(1f, tween(durationMillis = 500, easing = FastOutSlowInEasing))
            scale.animateTo(
                targetValue = BEAT_SCALE,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1_400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        } else {
            scale.animateTo(PAUSED_SCALE, tween(durationMillis = 400, easing = FastOutSlowInEasing))
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        MediaArtwork(
            uri = uri,
            type = MediaType.AUDIO,
            cornerRadius = 24.dp,
            iconSize = 96.dp,
            // graphicsLayer solo reescala al dibujar: no vuelve a medir ni a colocar nada.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
        // Pastilla oscura para que las barras se vean también sobre carátulas claras.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            PlayingBars(isPlaying = isPlaying, modifier = Modifier.size(width = 24.dp, height = 20.dp))
        }
    }
}

@Composable
internal fun SeekBar(player: Player) {
    val progress = rememberProgressStateWithTickInterval(player, 500L)
    // Mientras el usuario arrastra, mostramos SU posición, no la del reproductor.
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }
    val durationMs = progress.durationMs.coerceAtLeast(0L).toFloat()
    val positionMs = (dragPositionMs ?: progress.currentPositionMs.toFloat()).coerceIn(0f, durationMs.coerceAtLeast(1f))

    Slider(
        value = positionMs,
        onValueChange = { dragPositionMs = it },
        onValueChangeFinished = {
            dragPositionMs?.let { player.seekTo(it.toLong()) }
            dragPositionMs = null
        },
        valueRange = 0f..durationMs.coerceAtLeast(1f),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
    Row(Modifier.fillMaxWidth()) {
        TimeLabel(positionMs.toLong())
        Spacer(Modifier.weight(1f))
        TimeLabel(durationMs.toLong())
    }
}

@Composable
internal fun TimeLabel(millis: Long) {
    Text(
        text = formatDuration(millis / 1000),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlaybackControls(
    player: Player,
    playPause: PlayPauseButtonState,
    repeatPlan: RepeatPlan,
    onCycleRepeat: () -> Unit,
) {
    val previous = rememberPreviousButtonState(player)
    val next = rememberNextButtonState(player)

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ShuffleButton(player)
        IconButton(onClick = previous::onClick, enabled = previous.isEnabled, modifier = Modifier.size(52.dp)) {
            Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.player_previous), Modifier.size(34.dp))
        }
        FilledIconButton(
            onClick = playPause::onClick,
            enabled = playPause.isEnabled,
            modifier = Modifier.size(72.dp).tourSpot(TourSpot.PLAYER_CONTROLS),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            PlayPauseIcon(playPause, modifier = Modifier.size(38.dp))
        }
        IconButton(onClick = next::onClick, enabled = next.isEnabled, modifier = Modifier.size(52.dp)) {
            Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.player_next), Modifier.size(34.dp))
        }
        RepeatButton(repeatPlan, onCycleRepeat)
    }
}

/** Orden aleatorio de la cola. Media3 ya lleva la cuenta: aquí solo se pinta encendido o apagado. */
@Composable
private fun ShuffleButton(player: Player) {
    val shuffle = rememberShuffleButtonState(player)
    IconButton(onClick = shuffle::onClick, enabled = shuffle.isEnabled, modifier = Modifier.tourSpot(TourSpot.PLAYER_SHUFFLE)) {
        Icon(
            painter = painterResource(R.drawable.ic_shuffle),
            contentDescription = stringResource(
                if (shuffle.shuffleOn) R.string.player_shuffle_on else R.string.player_shuffle_off,
            ),
            tint = activeTint(shuffle.shuffleOn),
        )
    }
}

/**
 * Un solo botón para los cuatro modos de bucle. El número sobre el icono dice cuántas
 * repeticiones quedan ("2" = esta canción sonará dos veces más); el infinito, que no paran.
 */
@Composable
private fun RepeatButton(plan: RepeatPlan, onClick: () -> Unit) {
    val tint = activeTint(plan != RepeatPlan.OFF)
    IconButton(onClick = onClick, modifier = Modifier.tourSpot(TourSpot.PLAYER_REPEAT)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_repeat),
                contentDescription = stringResource(plan.labelRes),
                tint = tint,
            )
            plan.badge?.let { badge ->
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 11.dp, y = (-5).dp),
                )
            }
        }
    }
}

@Composable
internal fun PlayPauseIcon(
    state: PlayPauseButtonState,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val (icon, label) = if (state.showPlay) {
        R.drawable.ic_play to R.string.player_play
    } else {
        R.drawable.ic_pause to R.string.player_pause
    }
    Icon(painterResource(icon), stringResource(label), modifier, tint)
}

/** Verde de marca cuando la opción está encendida; gris apagado cuando no. */
@Composable
private fun activeTint(isActive: Boolean): Color =
    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

private val RepeatPlan.labelRes: Int
    get() = when (this) {
        RepeatPlan.OFF -> R.string.player_repeat_off
        RepeatPlan.ONCE -> R.string.player_repeat_once
        RepeatPlan.TWICE -> R.string.player_repeat_twice
        RepeatPlan.FOREVER -> R.string.player_repeat_forever
    }

private val RepeatPlan.badge: String?
    get() = when (this) {
        RepeatPlan.OFF -> null
        RepeatPlan.ONCE -> "1"
        RepeatPlan.TWICE -> "2"
        RepeatPlan.FOREVER -> "∞" // símbolo de infinito
    }

private fun MediaMetadata.toMediaType(): MediaType =
    if (mediaType == MediaMetadata.MEDIA_TYPE_VIDEO) MediaType.VIDEO else MediaType.AUDIO

private fun fractionOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/** Al pausar, la carátula se encoge un poco; al sonar, late entre 1 y [BEAT_SCALE]. */
private const val PAUSED_SCALE = 0.94f
private const val BEAT_SCALE = 1.02f
