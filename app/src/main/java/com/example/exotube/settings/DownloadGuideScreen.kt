package com.example.exotube.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.exotube.R
import com.example.exotube.ui.theme.ExoTubeTheme
import com.example.exotube.settings.GuideTimeline as T

/**
 * Ajustes → "Cómo descargar": una animación que enseña, red por red, dónde tocar para mandar un
 * video a ExoTube, y debajo los mismos pasos por escrito. El paso que se está viendo en la
 * animación se resalta en la lista, así se sabe qué texto corresponde a cada toque.
 */
@Composable
fun DownloadGuideScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    var platform by rememberSaveable { mutableStateOf(GuidePlatform.TIKTOK) }
    // Al cambiar de red, la animación empieza de cero: key descarta la vuelta anterior.
    val time = key(platform) { rememberGuideTime() }
    val activeStep = T.stepAt(time)

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                }
                Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            ) {
                items(GuidePlatform.entries) { option ->
                    FilterChip(
                        selected = option == platform,
                        onClick = { platform = option },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                GuidePhone(platform, time, Modifier.width(220.dp).height(440.dp))
            }
        }
        itemsIndexed(platform.steps) { index, text ->
            GuideStepRow(number = index + 1, text = stringResource(text, stringResource(platform.moreLabel)), isActive = index == activeStep)
        }
        item {
            Text(
                text = stringResource(R.string.guide_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

/** El reloj de la animación: de 0 a [T.TOTAL_MS] y vuelta a empezar, sin fin. */
@Composable
private fun rememberGuideTime(): Int {
    val transition = rememberInfiniteTransition(label = "guia")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = T.TOTAL_MS.toFloat(),
        animationSpec = infiniteRepeatable(tween(T.TOTAL_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "tiempo",
    )
    return time.toInt()
}

@Composable
private fun GuideStepRow(number: Int, text: String, isActive: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isActive) colors.primary else colors.surfaceContainerHighest),
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) colors.onPrimary else colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// El teléfono dibujado
// ---------------------------------------------------------------------------------------------

private val ScreenBlack = Color(0xFF0B0B0B)
private val SheetGrey = Color(0xFF1F1F1F)
private val Placeholder = Color(0xFF3A3A3A)

/**
 * Un teléfono con un video y, según el momento [time], el panel de compartir de la red, el de
 * Android, la hoja de ExoTube y la descarga. Todo se coloca en fracciones del tamaño de la
 * pantalla, las mismas que usa el guion ([GuideTimeline]) para mover el dedo.
 */
@Composable
private fun GuidePhone(platform: GuidePlatform, time: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(ScreenBlack)
            .border(3.dp, Color(0xFF444444), RoundedCornerShape(28.dp)),
    ) {
        FakeVideo(platform)
        ShareButtons(platform)

        // Cada hoja sube desde abajo y se queda hasta que la siguiente termina de taparla.
        Sheet(time, from = T.PANEL_IN, until = T.SYSTEM_IN + 300, heightFraction = 0.28f) {
            PlatformPanel(platform)
        }
        Sheet(time, from = T.SYSTEM_IN, until = T.EXOTUBE_IN + 300, heightFraction = 0.44f) {
            SystemShareSheet()
        }
        Sheet(time, from = T.EXOTUBE_IN, until = T.TOTAL_MS, heightFraction = 0.48f) {
            ExoTubeSheet(time)
        }
        Finger(time, platform)
    }
}

/** Un video cualquiera: un degradado con el color de la red y un par de líneas de texto. */
@Composable
private fun FakeVideo(platform: GuidePlatform) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(platform.accent.copy(alpha = 0.45f), ScreenBlack))),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = if (platform.shareOnRail) 40.dp else 70.dp),
        ) {
            Bar(Color.White.copy(alpha = 0.8f), 90.dp)
            Bar(Color.White.copy(alpha = 0.5f), 130.dp)
        }
    }
}

/** Los botones de la red (me gusta, comentar, compartir), en columna o en fila según la red. */
@Composable
private fun BoxWithConstraintsScope.ShareButtons(platform: GuidePlatform) {
    val share = platform.shareAt
    val shareIcon = if (platform == GuidePlatform.INSTAGRAM) R.drawable.ic_send else R.drawable.ic_share
    val spots: List<Pair<Offset, Int>> = if (platform.shareOnRail) {
        listOf(
            Offset(share.x, share.y - 0.24f) to R.drawable.ic_favorite,
            Offset(share.x, share.y - 0.12f) to R.drawable.ic_comment,
            share to shareIcon,
        )
    } else {
        listOf(
            Offset(0.14f, share.y) to R.drawable.ic_comment,
            Offset(0.4f, share.y) to R.drawable.ic_favorite,
            share to shareIcon,
        )
    }
    spots.forEach { (at, icon) ->
        val isShare = at == share
        PhoneIcon(at, icon, size = 30.dp, tint = if (isShare) platform.accent else Color.White)
    }
}

@Composable
private fun BoxWithConstraintsScope.PhoneIcon(at: Offset, @DrawableRes icon: Int, size: Dp, tint: Color) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.centeredAt(this, at, size).size(size).padding(3.dp),
    )
}

/**
 * Una hoja que sube desde abajo entre [from] y [until]. Tarda 300 ms en subir y, en la última
 * vuelta, se desvanece antes de que la animación empiece otra vez.
 */
@Composable
private fun BoxWithConstraintsScope.Sheet(
    time: Int,
    from: Int,
    until: Int,
    heightFraction: Float,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    if (time < from || time > until) return
    val progress = ((time - from) / 300f).coerceIn(0f, 1f)
    val eased = 1 - (1 - progress) * (1 - progress)
    val height = maxHeight * heightFraction
    val fadeOut = if (until == T.TOTAL_MS) (1f - (time - (T.TOTAL_MS - 400)) / 400f).coerceIn(0f, 1f) else 1f
    // La hoja ocupa toda la pantalla para que sus hijos usen las mismas fracciones que el dedo.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .offset(y = height * (1 - eased))
            .alpha(fadeOut),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(SheetGrey),
        )
        content()
    }
}

/** El panel de compartir de la red: una fila de apps que termina en "Más" / "Compartir en…". */
@Composable
private fun BoxWithConstraintsScope.PlatformPanel(platform: GuidePlatform) {
    Bar(Color.White.copy(alpha = 0.6f), 70.dp, Modifier.centeredAt(this, Offset(0.5f, 0.77f), 70.dp, 6.dp))
    listOf(0.14f, 0.32f, 0.5f, 0.68f).forEach { x ->
        Dot(Modifier.centeredAt(this, Offset(x, T.MORE_BUTTON.y), 30.dp), Placeholder)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .centeredAt(this, T.MORE_BUTTON, 30.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(0xFF555555)),
    ) {
        Text("•••", color = Color.White, fontSize = 9.sp)
    }
    Label(stringResource(platform.moreLabel), Offset(T.MORE_BUTTON.x - 0.05f, T.MORE_BUTTON.y + 0.06f))
}

/** La hoja de compartir de Android: una cuadrícula de apps, con ExoTube en la segunda casilla. */
@Composable
private fun BoxWithConstraintsScope.SystemShareSheet() {
    Label(stringResource(R.string.guide_share_with), Offset(0.08f, 0.6f), size = 11)
    val columns = listOf(0.14f, 0.39f, 0.64f, 0.89f)
    val rows = listOf(T.EXOTUBE_ICON.y, T.EXOTUBE_ICON.y + 0.14f)
    rows.forEach { y ->
        columns.forEach { x ->
            val at = Offset(x, y)
            if (at == T.EXOTUBE_ICON) {
                Box(
                    Modifier
                        .centeredAt(this, at, 36.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black),
                ) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.fillMaxSize())
                }
                Label("ExoTube", Offset(x - 0.1f, y + 0.055f))
            } else {
                Dot(Modifier.centeredAt(this, at, 32.dp), Placeholder)
            }
        }
    }
}

/** La hoja de ExoTube: las calidades y, al elegir una, la barra de descarga. */
@Composable
private fun BoxWithConstraintsScope.ExoTubeSheet(time: Int) {
    val green = Color(0xFF2EE67A)
    // Cabecera: miniatura y título del video.
    Box(
        Modifier
            .centeredAt(this, Offset(0.2f, 0.6f), 44.dp, 28.dp)
            .size(44.dp, 28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Placeholder),
    )
    Bar(Color.White.copy(alpha = 0.7f), 100.dp, Modifier.centeredAt(this, Offset(0.62f, 0.59f), 100.dp, 6.dp))

    if (time < T.DOWNLOAD_START) {
        listOf("720p" to T.QUALITY_ROW.y, "360p" to T.QUALITY_ROW.y + 0.08f, "MP3" to T.QUALITY_ROW.y + 0.16f)
            .forEachIndexed { index, (label, y) ->
                val chosen = index == 0 && time >= T.TAP_QUALITY
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .centeredAt(this, Offset(0.5f, y), maxWidth - 24.dp, 26.dp)
                        .size(maxWidth - 24.dp, 26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (chosen) green.copy(alpha = 0.3f) else Color.Transparent)
                        .padding(start = 10.dp),
                ) {
                    Text(label, color = Color.White, fontSize = 11.sp)
                }
            }
    } else {
        val progress = T.downloadProgress(time)
        val done = progress >= 1f
        Text(
            text = stringResource(if (done) R.string.guide_saved else R.string.guide_downloading),
            color = if (done) green else Color.White,
            fontSize = 11.sp,
            modifier = Modifier.centeredAt(this, Offset(0.5f, 0.72f), maxWidth - 24.dp, 16.dp),
        )
        Box(
            Modifier
                .centeredAt(this, Offset(0.5f, 0.79f), maxWidth - 40.dp, 6.dp)
                .size(maxWidth - 40.dp, 6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Placeholder),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(green),
            )
        }
        if (done) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier
                    .centeredAt(this, Offset(0.5f, 0.88f), 30.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(green)
                    .padding(5.dp),
            )
        }
    }
}

/**
 * El dedo: un círculo claro que viaja de botón en botón y, al tocar, deja una onda que se
 * expande. Aparece al empezar y se va en cuanto se elige la calidad.
 */
@Composable
private fun BoxWithConstraintsScope.Finger(time: Int, platform: GuidePlatform) {
    val visible = when {
        time < 300 -> time / 300f
        // Tras elegir la calidad el dedo se retira: lo que importa ahora es la barra de descarga.
        time > T.TAP_QUALITY + 250 -> (1f - (time - T.TAP_QUALITY - 250) / 150f).coerceIn(0f, 1f)
        else -> 1f
    }
    if (visible <= 0f) return
    val at = T.fingerAt(time, platform.shareAt)
    val tap = T.taps.firstOrNull { time - it in 0 until 450 }
    Canvas(Modifier.fillMaxSize().alpha(visible)) {
        val center = Offset(size.width * at.x, size.height * at.y)
        if (tap != null) {
            val t = (time - tap) / 450f
            drawCircle(
                color = Color.White.copy(alpha = 0.7f * (1 - t)),
                radius = (12 + 26 * t).dp.toPx(),
                center = center,
                style = Stroke(3.dp.toPx()),
            )
        }
        // Al pulsar el dedo "baja": se encoge un poco.
        val pressed = tap != null && time - tap < 150
        drawCircle(Color.White.copy(alpha = 0.85f), radius = (if (pressed) 10 else 12).dp.toPx(), center = center)
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = (if (pressed) 10 else 12).dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun Bar(color: Color, width: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(width)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

@Composable
private fun Dot(modifier: Modifier, color: Color) {
    Box(modifier.size(30.dp).clip(CircleShape).background(color))
}

@Composable
private fun BoxWithConstraintsScope.Label(text: String, at: Offset, size: Int = 9) {
    Text(
        text = text,
        color = Color.White,
        fontSize = size.sp,
        maxLines = 1,
        modifier = Modifier.offset(x = maxWidth * at.x, y = maxHeight * at.y),
    )
}

/** Coloca un elemento de [width] × [height] con su centro en [at] (fracciones de la pantalla). */
private fun Modifier.centeredAt(scope: BoxWithConstraintsScope, at: Offset, width: Dp, height: Dp = width): Modifier =
    offset(x = scope.maxWidth * at.x - width / 2, y = scope.maxHeight * at.y - height / 2)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun DownloadGuidePreview() {
    ExoTubeTheme { DownloadGuideScreen(onBack = {}, contentPadding = PaddingValues()) }
}
