package com.example.exotube.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import com.example.exotube.data.settings.AppSettings
import com.example.exotube.data.settings.CustomBackground
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Los tres acentos de un tema sacados de una imagen: los del fondo personalizado.
 *
 * Con un fondo rosa de flores, los botones y la pestaña activa salen rosas y los detalles, del
 * verde de las hojas. Todo lo demás (capas, textos, bordes) se calcula con [schemeFrom], las
 * mismas reglas que los temas de la lista.
 */
data class WallpaperColors(val primary: Color, val secondary: Color, val tertiary: Color) {

    /**
     * Un negro con un toque del acento. Se ve donde la imagen no llega: las hojas que suben desde
     * abajo, el reproductor a pantalla completa.
     */
    val background: Color get() = Color.hsv(hueOf(primary), 0.45f, 0.08f)

    val colorScheme: ColorScheme get() = schemeFrom(background, primary, secondary, tertiary)

    companion object {
        /** Para imágenes sin color (blanco y negro, grises): el verde de siempre. */
        val Fallback = WallpaperColors(ExoGreen, Color(0xFF8FD9A8), Color(0xFF7FE0D0))
    }
}

/**
 * Busca los colores con más presencia en [pixels] (colores ARGB, por ejemplo una miniatura de
 * 64 × 64 de la imagen).
 *
 * 1. Se descartan los blancos, negros y grises: casi toda imagen tiene y no dicen nada de ella.
 * 2. El resto se reparte por su tono en 24 "cajones" del círculo de colores (15° cada uno),
 *    y cada píxel pesa más cuanto más vivo es.
 * 3. El cajón más pesado da el acento principal; los siguientes, los de apoyo, siempre que sean
 *    de un tono claramente distinto (a más de 45°): si no, serían tres rosas casi iguales.
 * 4. Cada acento se aviva y se aclara lo justo para que brille sobre el fondo oscuro de la app.
 */
fun wallpaperColorsFrom(pixels: IntArray): WallpaperColors {
    val weight = FloatArray(BUCKETS)
    val sumCos = FloatArray(BUCKETS)
    val sumSin = FloatArray(BUCKETS)
    val sumSat = FloatArray(BUCKETS)
    val hsv = FloatArray(3)
    for (argb in pixels) {
        if ((argb ushr 24) < 128) continue                      // transparente
        toHsv(argb, hsv)
        val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
        if (s < MIN_SATURATION || v < MIN_VALUE) continue       // gris, blanco o negro
        val w = s * v
        val i = (h / (360f / BUCKETS)).toInt().coerceIn(0, BUCKETS - 1)
        val rad = Math.toRadians(h.toDouble())
        weight[i] += w
        sumCos[i] += (cos(rad) * w).toFloat()
        sumSin[i] += (sin(rad) * w).toFloat()
        sumSat[i] += s * w
    }
    val total = weight.sum()
    if (total < 1f) return WallpaperColors.Fallback

    // El tono medio de cada cajón (con senos y cosenos: el promedio de 350° y 10° es 0°, no 180°).
    val ranked = (0 until BUCKETS).filter { weight[it] > 0f }.sortedByDescending { weight[it] }
    fun hue(i: Int) = ((Math.toDegrees(atan2(sumSin[i], sumCos[i]).toDouble()) + 360) % 360).toFloat()
    fun sat(i: Int) = sumSat[i] / weight[i]

    val picked = mutableListOf<Int>()
    for (i in ranked) {
        if (picked.size == 3) break
        if (weight[i] < total * MIN_SHARE && picked.isNotEmpty()) break
        if (picked.all { hueDistance(hue(it), hue(i)) >= MIN_HUE_GAP }) picked += i
    }
    val primaryHue = hue(picked[0])
    val primary = accent(primaryHue, sat(picked[0]))
    // Si la imagen es de un solo color, los de apoyo son variaciones del principal.
    val secondary = picked.getOrNull(1)?.let { accent(hue(it), sat(it)) }
        ?: Color.hsv((primaryHue + 30f) % 360f, 0.35f, 0.9f)
    val tertiary = picked.getOrNull(2)?.let { accent(hue(it), sat(it)) }
        ?: Color.hsv(primaryHue, 0.22f, 0.96f)
    return WallpaperColors(primary, secondary, tertiary)
}

/**
 * El mismo tono, con la viveza y la luz justas para destacar sobre negro. Los tonos que reflejan
 * poca luz (el azul puro, el violeta) se aclaran un poco más: si no, se pierden en el fondo oscuro.
 */
private fun accent(hue: Float, saturation: Float): Color {
    var s = saturation.coerceIn(0.5f, 0.85f)
    var color = Color.hsv(hue, s, 0.93f)
    while (color.luminance() < MIN_ACCENT_LUMINANCE && s > 0.2f) {
        s -= 0.05f
        color = Color.hsv(hue, s, 0.93f)
    }
    return color
}

/**
 * Con esta luz como mínimo, el acento destaca sobre el fondo oscuro (contraste de más de 3:1) y el
 * texto negro de sus botones se lee bien (4.5:1). Entre 0.18 y 0.2 no le va bien ni el texto
 * negro ni el blanco: por eso el mínimo está justo por encima.
 */
private const val MIN_ACCENT_LUMINANCE = 0.2f

private fun hueDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return min(d, 360f - d)
}

private fun hueOf(color: Color): Float {
    val hsv = FloatArray(3)
    toHsv(
        ((color.red * 255).toInt() shl 16) or ((color.green * 255).toInt() shl 8) or (color.blue * 255).toInt(),
        hsv,
    )
    return hsv[0]
}

/** RGB a HSV (tono 0–360, saturación y brillo 0–1). Escrito a mano para probarlo sin Android. */
private fun toHsv(argb: Int, out: FloatArray) {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val h = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    out[0] = (h + 360f) % 360f
    out[1] = if (maxC == 0f) 0f else delta / maxC
    out[2] = maxC
}

private const val BUCKETS = 24
private const val MIN_SATURATION = 0.18f
private const val MIN_VALUE = 0.22f
private const val MIN_HUE_GAP = 45f
/** Un color de apoyo tiene que ocupar al menos esta parte: si no, es un detallito, no un color de la imagen. */
private const val MIN_SHARE = 0.04f

// ---------------------------------------------------------------------------------------------
// Qué se ve: un tema de la lista o el fondo propio
// ---------------------------------------------------------------------------------------------

/** El fondo ya cargado para dibujarlo detrás de las pantallas. [dim]: cuánto se oscurece (0–1). */
class Wallpaper(val image: ImageBitmap, val dim: Float)

/** null = sin fondo propio: cada pantalla pinta su color de fondo como siempre. */
val LocalWallpaper = staticCompositionLocalOf<Wallpaper?> { null }

val CustomBackground.colors: WallpaperColors
    get() = WallpaperColors(Color(primary), Color(secondary), Color(tertiary))

/**
 * Lo que pinta la app según Ajustes. El fondo propio solo cuenta si está elegido, si hay imagen y
 * si está desbloqueado; si falta cualquiera de las tres, el tema de siempre.
 */
data class Look(val colorScheme: ColorScheme, val background: CustomBackground?)

fun resolveLook(themeId: String?, background: CustomBackground?, shares: Int): Look =
    if (themeId == AppSettings.CUSTOM_THEME_ID && background != null && AppSettings.isBackgroundUnlocked(shares)) {
        Look(background.colors.colorScheme, background)
    } else {
        Look(AppTheme.fromId(themeId).colorScheme, null)
    }
