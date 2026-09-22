package com.example.exotube.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.example.exotube.ExoTubeApp
import com.example.exotube.ui.theme.ExoTubeTheme
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * La barra del reproductor, dibujada como la forma de onda de la canción en vez de como una línea
 * recta.
 *
 * Lo que se ve es el sonido de verdad: los tramos altos son donde la canción pega fuerte y los
 * bajos donde baja. Eso convierte la barra en un mapa de la canción, y saltar al estribillo deja
 * de ser adivinar.
 *
 * Mientras la onda se calcula (un instante la primera vez) se dibuja plana, así que la barra
 * siempre está ahí y nunca da un salto raro al aparecer.
 */
@Composable
internal fun WaveformSeekBar(player: Player, mediaUri: String?) {
    val levels = rememberWaveform(mediaUri)
    val progress = rememberProgressStateWithTickInterval(player, TICK_MS)

    // Mientras el usuario arrastra mandamos SU posición, no la del reproductor: si no, la barra
    // pelearía con el dedo cada vez que el reproductor informa de dónde va.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val durationMs = progress.durationMs.coerceAtLeast(1L)
    val playedFraction = dragFraction
        ?: (progress.currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    WaveformCanvas(
        levels = levels,
        playedFraction = playedFraction,
        onScrub = { fraction -> dragFraction = fraction },
        onScrubFinished = { fraction ->
            player.seekTo((fraction * durationMs).toLong())
            dragFraction = null
        },
    )

    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        TimeLabel((playedFraction * durationMs).toLong())
        Spacer(Modifier.weight(1f))
        TimeLabel(durationMs)
    }
}

/** Solo el dibujo y el gesto: sin reproductor, para poder previsualizarlo y razonar sobre él. */
@Composable
private fun WaveformCanvas(
    levels: FloatArray?,
    playedFraction: Float,
    onScrub: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val played = colors.primary
    val pending = colors.outlineVariant
    // La onda aparece suavemente en cuanto está lista, en vez de sustituir de golpe la barra plana.
    val reveal by animateFloatAsState(if (levels == null) 0f else 1f, label = "onda")

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            // Tocar un punto salta ahí directamente; arrastrar recorre la canción.
            .pointerInput(Unit) {
                detectTapGestures { offset -> onScrubFinished((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                var current = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        current = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrub(current)
                    },
                    onDragEnd = { onScrubFinished(current) },
                    onDragCancel = { onScrubFinished(current) },
                ) { change, _ ->
                    current = (change.position.x / size.width).coerceIn(0f, 1f)
                    onScrub(current)
                }
            },
    ) {
        val bars = levels?.size ?: FLAT_BARS
        val slot = size.width / bars
        // Sin onda, las barras van pegadas y a la altura mínima: eso es exactamente una barra
        // normal. Al llegar la onda se separan y crecen, así que la línea recta se convierte en
        // la onda a la vista en vez de ser reemplazada de golpe.
        val fill = FLAT_FILL + (BAR_FILL - FLAT_FILL) * reveal
        val barWidth = (slot * fill).coerceAtLeast(1f)
        val playedBars = (playedFraction * bars).roundToInt()

        repeat(bars) { i ->
            val level = levels?.getOrNull(i) ?: 0f
            val height = (MIN_HEIGHT + level * (1f - MIN_HEIGHT) * reveal) * size.height
            val left = i * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = if (i < playedBars) played else pending,
                topLeft = Offset(left, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(min(barWidth, height) / 2f),
            )
        }
    }
}

/**
 * Calcula la onda de esta canción, fuera del hilo de la interfaz.
 *
 * Solo se intenta con archivos del teléfono: un video en línea no es un archivo sino una
 * dirección que caduca, y decodificarlo entero para dibujar una barra sería gastar los datos del
 * usuario dos veces.
 */
@Composable
private fun rememberWaveform(mediaUri: String?): FloatArray? {
    val context = LocalContext.current
    val reader = remember(context) { (context.applicationContext as ExoTubeApp).container.waveformReader }

    // La clave es la Uri: al cambiar de canción se cancela el cálculo de la anterior y empieza el
    // de la nueva, sin dejar trabajo colgado.
    return produceState<FloatArray?>(initialValue = mediaUri?.let(reader::cached), mediaUri) {
        value = mediaUri?.let(reader::cached)
        if (mediaUri != null && value == null && mediaUri.startsWith("content://")) {
            value = reader.read(mediaUri)
        }
    }.value
}

/**
 * Cada cuánto se refresca la posición. Con barras discretas no hace falta ir más fino: la barra
 * solo cambia cuando el avance cruza de una barra a la siguiente.
 */
private const val TICK_MS = 250L

/** Barras de la línea plana mientras no hay onda: las justas para que parezca una barra normal. */
private const val FLAT_BARS = 64

/** Lo que ocupa la barra dentro de su hueco cuando ya hay onda; el resto es el aire entre barras. */
private const val BAR_FILL = 0.55f

/** Mientras no hay onda las barras llenan su hueco entero, o sea: se ven como una barra normal. */
private const val FLAT_FILL = 1f

/**
 * Altura mínima de una barra, como fracción del alto total. Con los 48 dp de la barra sale algo
 * menos de 4 dp, que es justo el grosor de una barra de reproducción normal: así la versión
 * "todavía sin onda" no parece un error, parece la barra de siempre. Y un silencio dentro de la
 * canción sigue viéndose en vez de desaparecer.
 */
private const val MIN_HEIGHT = 0.08f

// --- Preview ---

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun WaveformSeekBarPreview() {
    // Una onda de mentira solo para ver el dibujo: sube, baja y vuelve a subir.
    val levels = FloatArray(64) { i ->
        val x = i / 63f
        (0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin(x * 6.5f)) * (0.4f + 0.6f * x))
    }
    ExoTubeTheme {
        WaveformCanvas(levels = levels, playedFraction = 0.38f, onScrub = {}, onScrubFinished = {})
    }
}
