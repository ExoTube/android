package com.example.exotube.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Barras de ecualizador: suben y bajan mientras suena la música y se quedan quietas al pausar.
 *
 * Quien la use debe darle un tamaño en [modifier] (por ejemplo `Modifier.size(18.dp, 16.dp)`):
 * las barras se reparten el ancho disponible y crecen hasta el alto disponible.
 */
@Composable
fun PlayingBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    // Lo describimos para los lectores de pantalla: la animación no se "ve" con la voz.
    val described = contentDescription
        ?.let { label -> modifier.semantics { this.contentDescription = label } }
        ?: modifier

    // Dos composables distintos a propósito: al pausar, el animado sale del árbol y Compose deja
    // de recomponer 60 veces por segundo (y el teléfono, de gastar batería).
    if (isPlaying) AnimatedBars(described, color) else StaticBars(described, color)
}

@Composable
private fun AnimatedBars(modifier: Modifier, color: Color) {
    val transition = rememberInfiniteTransition(label = "ecualizador")
    // Cada barra tarda algo distinto: si todas tardaran lo mismo subirían y bajarían a la vez.
    val fractions = BAR_PERIODS_MS.map { periodMs ->
        transition.animateFloat(
            initialValue = MIN_FRACTION,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(periodMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse, // sube y baja, en vez de saltar al principio
            ),
            label = "barra",
        )
    }
    // Leer el valor animado DENTRO del Canvas (y no fuera) hace que cada fotograma solo repita el
    // dibujo: Compose no vuelve a recomponer el árbol entero sesenta veces por segundo.
    Canvas(modifier) { drawBars(fractions.map { it.value }, color) }
}

@Composable
private fun StaticBars(modifier: Modifier, color: Color) {
    Canvas(modifier) { drawBars(PAUSED_FRACTIONS, color) }
}

/** Dibuja una barra redondeada por cada altura de [fractions], repartidas en el ancho. */
private fun DrawScope.drawBars(fractions: List<Float>, color: Color) {
    // Tantos huecos como barras menos uno: barra, hueco, barra, hueco… barra.
    val unit = size.width / (fractions.size * 2 - 1)
    fractions.forEachIndexed { index, fraction ->
        val barHeight = size.height * fraction
        drawRoundRect(
            color = color,
            topLeft = Offset(x = index * unit * 2, y = size.height - barHeight), // crecen desde abajo
            size = Size(unit, barHeight),
            cornerRadius = CornerRadius(unit / 2),
        )
    }
}

/** Ninguna barra baja del todo: así se sigue leyendo como un ecualizador y no como un hueco. */
private const val MIN_FRACTION = 0.22f
private val BAR_PERIODS_MS = listOf(430, 620, 340, 520)
private val PAUSED_FRACTIONS = listOf(0.35f, 0.6f, 0.3f, 0.45f)
