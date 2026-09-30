package com.example.exotube.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.example.exotube.data.settings.AppSettings
import com.example.exotube.data.settings.CustomBackground
import com.example.exotube.ui.theme.AppTheme
import com.example.exotube.ui.theme.WallpaperColors
import com.example.exotube.ui.theme.resolveLook
import com.example.exotube.ui.theme.wallpaperColorsFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * El fondo propio: que de cada imagen salgan colores que la representen y que, sean cuales sean,
 * la app se siga leyendo bien.
 */
class WallpaperColorsTest {

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    /** El tono (0–360) de un color. A mano: en las pruebas no hay Android que lo calcule. */
    private fun hue(c: Color): Float {
        val max = maxOf(c.red, c.green, c.blue)
        val d = max - minOf(c.red, c.green, c.blue)
        if (d == 0f) return 0f
        val h = when (max) {
            c.red -> 60f * (((c.green - c.blue) / d) % 6f)
            c.green -> 60f * ((c.blue - c.red) / d + 2f)
            else -> 60f * ((c.red - c.green) / d + 4f)
        }
        return (h + 360f) % 360f
    }

    /** Una "imagen" de mentira: tantos píxeles de cada color como diga la lista. */
    private fun image(vararg parts: Pair<Long, Int>): IntArray =
        parts.flatMap { (argb, count) -> List(count) { argb.toInt() } }.shuffled(Random(1)).toIntArray()

    // Como el fondo de flores de la referencia: mucho blanco, rosas, hojas verdes y contorno café.
    private val flowers = image(
        0xFFFFFFFF to 2800, 0xFFF4A6C0 to 420, 0xFFE97BA0 to 260, 0xFFFAD4E0 to 180,
        0xFFA8D08D to 200, 0xFF7A4A3A to 150, 0xFFF2C94C to 30,
    )

    @Test
    fun `un fondo de flores rosas da botones rosas y detalles verdes`() {
        val colors = wallpaperColorsFrom(flowers)
        val h1 = hue(colors.primary)
        val h2 = hue(colors.secondary)
        assertTrue("principal rosa, salió $h1", h1 >= 300f || h1 <= 15f)
        assertTrue("apoyo verde, salió $h2", h2 in 70f..160f)
    }

    @Test
    fun `una imagen en blanco y negro se queda con el verde de ExoTube`() {
        val grays = image(0xFFFFFFFF to 1000, 0xFF000000 to 1000, 0xFF808080 to 1000, 0xFF3A3A3A to 500)
        assertEquals(WallpaperColors.Fallback, wallpaperColorsFrom(grays))
        assertEquals(WallpaperColors.Fallback, wallpaperColorsFrom(IntArray(0)))
    }

    @Test
    fun `una imagen de un solo color tambien da tres acentos`() {
        val colors = wallpaperColorsFrom(image(0xFF1565C0 to 3000))
        assertTrue(hue(colors.primary) in 200f..230f)
        assertTrue(colors.secondary != colors.primary && colors.tertiary != colors.primary)
    }

    @Test
    fun `con cualquier imagen los botones y acentos se leen bien`() {
        val rnd = Random(7)
        repeat(300) {
            // Imágenes al azar de 3 a 6 colores cualesquiera.
            val parts = List(rnd.nextInt(3, 7)) { (0xFF000000 or rnd.nextLong(0, 0xFFFFFF)) to rnd.nextInt(50, 800) }
            val scheme = wallpaperColorsFrom(image(*parts.toTypedArray())).colorScheme
            assertTrue("botón", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
            assertTrue("acento sobre fondo", contrast(scheme.primary, scheme.background) >= 3f)
            assertTrue("texto", contrast(scheme.onSurface, scheme.surfaceContainerHighest) >= 7f)
        }
    }

    @Test
    fun `el texto se lee aun encima de la parte mas blanca de la imagen`() {
        // Lo peor que puede tocar detrás de un texto: blanco puro, oscurecido lo que venga por defecto.
        val text = WallpaperColors.Fallback.colorScheme.onSurface
        val whiteDimmed = Color.Black.copy(alpha = CustomBackground.DEFAULT_DIM).compositeOver(Color.White)
        assertTrue(contrast(text, whiteDimmed) >= 4.5f)
        // Y con lo mínimo que deja la barra, al menos lo de un texto grande.
        val whiteLeastDimmed = Color.Black.copy(alpha = CustomBackground.MIN_DIM).compositeOver(Color.White)
        assertTrue(contrast(text, whiteLeastDimmed) >= 3f)
    }

    private val saved = CustomBackground(
        Color(0xFFE97BA0).toArgb(), Color(0xFFA8D08D).toArgb(), Color(0xFFF2C94C).toArgb(), dim = 0.7f, version = 1L,
    )

    @Test
    fun `el fondo propio solo se ve si esta elegido, hay imagen y esta desbloqueado`() {
        assertNotNull(resolveLook(AppSettings.CUSTOM_THEME_ID, saved, shares = 2).background)
        assertNull("falta compartir", resolveLook(AppSettings.CUSTOM_THEME_ID, saved, shares = 1).background)
        assertNull("sin imagen", resolveLook(AppSettings.CUSTOM_THEME_ID, null, shares = 5).background)
        assertNull("otro tema elegido", resolveLook("rock", saved, shares = 5).background)
        assertEquals(AppTheme.ROCK.colorScheme, resolveLook("rock", saved, shares = 5).colorScheme)
        // Si algo falla, el verde de siempre, no una pantalla rara.
        assertEquals(AppTheme.CLASSIC.colorScheme, resolveLook(AppSettings.CUSTOM_THEME_ID, saved, shares = 0).colorScheme)
    }

    @Test
    fun `sin el aviso de Android, volver de WhatsApp tras unos segundos cuenta como compartido`() {
        // Menús de Samsung, Xiaomi...: el aviso no llega y las veces siguen igual.
        assertTrue(AppSettings.countsAsShared(startedAt = 10_000, sharesAtStart = 0, sharesNow = 0, now = 25_000))
        assertTrue(AppSettings.countsAsShared(10_000, 1, 1, 10_000 + AppSettings.MIN_AWAY_MS))
    }

    @Test
    fun `cerrar el menu enseguida no cuenta`() {
        assertEquals(false, AppSettings.countsAsShared(10_000, 0, 0, 11_500))
    }

    @Test
    fun `si el aviso de Android ya lo conto, no se cuenta dos veces`() {
        assertEquals(false, AppSettings.countsAsShared(10_000, 0, sharesNow = 1, now = 30_000))
    }

    @Test
    fun `sin un menu abierto antes, o con el telefono reiniciado entre medias, no cuenta`() {
        assertEquals(false, AppSettings.countsAsShared(-1, -1, 0, 30_000))
        assertEquals(false, AppSettings.countsAsShared(startedAt = 900_000, sharesAtStart = 0, sharesNow = 0, now = 5_000))
    }

    @Test
    fun `se desbloquea al compartir dos veces`() {
        assertEquals(false, AppSettings.isBackgroundUnlocked(0))
        assertEquals(false, AppSettings.isBackgroundUnlocked(1))
        assertEquals(true, AppSettings.isBackgroundUnlocked(2))
        assertEquals(true, AppSettings.isBackgroundUnlocked(9))
    }
}
