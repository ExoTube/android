package com.example.exotube.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.exotube.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * El tutorial encima de la app: oscurece la pantalla, deja un hueco iluminado sobre el botón del
 * paso actual y pone al lado una tarjeta que explica para qué sirve.
 *
 * Mientras se ve, la app de debajo no recibe toques: así nadie abre algo sin querer a mitad de la
 * explicación. Se sale con "Saltar", con "Entendido" en el último paso o con el botón Atrás; en
 * los tres casos se da por visto y no vuelve a salir (se puede repetir desde Ajustes).
 */
@Composable
fun TourOverlay(tour: Tour, onFinish: () -> Unit) {
    // Los pasos se deciden al empezar, cuando la pantalla ya se ha dibujado y se sabe qué hay.
    var steps by remember(tour) { mutableStateOf<List<TourStep>?>(null) }
    LaunchedEffect(tour) {
        // Un respiro para que la pantalla termine de colocarse (o de cargar, con waitFor).
        delay(START_DELAY_MS)
        tour.waitFor?.let { spot ->
            withTimeoutOrNull(WAIT_FOR_MS) { while (!TourSpots.isVisible(spot)) delay(POLL_MS) }
        }
        steps = stepsToShow(tour.steps, TourSpots::isVisible)
    }
    val shown = steps ?: return
    if (shown.isEmpty()) {
        LaunchedEffect(Unit) { onFinish() }
        return
    }

    var index by remember(tour) { mutableIntStateOf(0) }
    val step = shown[index.coerceAtMost(shown.lastIndex)]
    BackHandler(onBack = onFinish)

    // El botón puede moverse (una lista que se recoloca): se vuelve a preguntar dónde está.
    var target by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(step) {
        while (true) {
            target = step.spot?.let(TourSpots::boundsOf)
            delay(POLL_MS)
        }
    }
    val padding = with(LocalDensity.current) { 6.dp.toPx() }
    val screenEdge = with(LocalDensity.current) { 4.dp.toPx() }
    val screen = LocalWindowInfo.current.containerSize
    // Crecido un poco para no cortar el borde del botón, pero sin salirse de la pantalla: una
    // fila que ocupa todo el ancho perdería el aro por los lados.
    val hole = target?.inflate(padding)?.let {
        Rect(
            left = it.left.coerceAtLeast(screenEdge),
            top = it.top.coerceAtLeast(screenEdge),
            right = it.right.coerceAtMost(screen.width - screenEdge),
            bottom = it.bottom.coerceAtMost(screen.height - screenEdge),
        )
    }
    // El hueco viaja de un botón al siguiente en vez de saltar: se sigue con la vista. El
    // primero aparece ya en su sitio (si no, llegaría volando desde la esquina).
    val moving = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    LaunchedEffect(hole) {
        if (hole == null) return@LaunchedEffect
        if (moving.value == Rect.Zero) moving.snapTo(hole) else moving.animateTo(hole)
    }
    val animatedHole = moving.value
    val ring = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Se come los toques: nada de debajo se activa mientras dura el tutorial.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Offscreen: hace falta para que BlendMode.Clear abra un hueco de verdad en el velo.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(Color.Black.copy(alpha = SCRIM_ALPHA))
            if (hole != null) {
                val radius = CornerRadius(16.dp.toPx())
                drawRoundRect(Color.Transparent, animatedHole.topLeft, animatedHole.size, radius, blendMode = BlendMode.Clear)
                drawRoundRect(ring, animatedHole.topLeft, animatedHole.size, radius, style = Stroke(2.dp.toPx()))
            }
        }

        var cardHeight by remember { mutableIntStateOf(0) }
        val screenHeight = constraints.maxHeight
        val margin = with(LocalDensity.current) { 16.dp.toPx() }
        val cardY: Float = when {
            hole == null -> (screenHeight - cardHeight) / 2f
            // Botón en la mitad de abajo: la tarjeta va encima; si no, debajo.
            hole.center.y > screenHeight / 2f -> hole.top - margin - cardHeight
            else -> hole.bottom + margin
        }.coerceIn(margin, (screenHeight - cardHeight - margin).coerceAtLeast(margin))

        TourCard(
            step = step,
            position = index + 1,
            total = shown.size,
            onNext = { if (index < shown.lastIndex) index++ else onFinish() },
            onSkip = onFinish,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, cardY.roundToInt()) }
                .onSizeChanged { cardHeight = it.height }
                .padding(horizontal = 16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun TourCard(
    step: TourStep,
    position: Int,
    total: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLast = position == total
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        // Los lectores de pantalla leen cada paso nuevo sin tener que buscarlo.
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp)) {
            Text(
                text = stringResource(step.title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = stringResource(step.body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.tour_progress, position, total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!isLast) TextButton(onClick = onSkip) { Text(stringResource(R.string.tour_skip)) }
                Button(onClick = onNext) {
                    Text(stringResource(if (isLast) R.string.tour_done else R.string.tour_next))
                }
            }
        }
    }
}

private const val START_DELAY_MS = 700L
private const val WAIT_FOR_MS = 12_000L
private const val POLL_MS = 200L
private const val SCRIM_ALPHA = 0.78f
