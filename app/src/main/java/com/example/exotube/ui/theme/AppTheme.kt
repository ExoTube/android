package com.example.exotube.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.exotube.R

/** Los apartados en que se agrupan los temas en Ajustes. */
enum class ThemeGroup(@StringRes val label: Int) {
    CLASSIC(R.string.theme_group_classic),
    CARTOONS(R.string.theme_group_cartoons),
    MUSIC(R.string.theme_group_music),
    SOCIAL(R.string.theme_group_social),
}

/**
 * Los temas de colores que se pueden elegir en Ajustes.
 *
 * Cada tema son solo cuatro colores: el fondo y tres de acento. Todo lo demás (las capas de las
 * tarjetas, los textos, los bordes) se calcula a partir de ellos en [colorScheme], así que añadir
 * un tema nuevo es añadir una línea aquí.
 *
 * Son colores "inspirados en", no dibujos ni logotipos: los personajes y las marcas tienen dueño,
 * y una app pública no puede llevar sus imágenes. Por eso los nombres tampoco son los originales.
 *
 * Todos son oscuros a propósito: la app se ve de noche, en el cine o con el brillo bajo, y los
 * videos lucen más sobre fondo oscuro.
 *
 * @param id lo que se guarda en Ajustes. No se cambia nunca, o se perdería la elección.
 * @param primary el acento principal: botones, pestaña activa, la barra de progreso.
 * @param secondary y [tertiary]: acentos de apoyo (chips, detalles) y el degradado de la muestra.
 */
enum class AppTheme(
    val id: String,
    @StringRes val label: Int,
    @StringRes val hint: Int,
    val group: ThemeGroup,
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
) {
    CLASSIC("clasico", R.string.theme_classic, R.string.theme_classic_hint, ThemeGroup.CLASSIC,
        background = ExoBlack, primary = ExoGreen, secondary = Color(0xFF8FD9A8), tertiary = Color(0xFF7FE0D0)),
    MIDNIGHT("medianoche", R.string.theme_midnight, R.string.theme_midnight_hint, ThemeGroup.CLASSIC,
        background = Color(0xFF05070F), primary = Color(0xFF6EA8FF), secondary = Color(0xFFA7C4FF), tertiary = Color(0xFFC6A6FF)),

    SPIDER("aracnido", R.string.theme_spider, R.string.theme_spider_hint, ThemeGroup.CARTOONS,
        background = Color(0xFF070A1C), primary = Color(0xFFD32F2F), secondary = Color(0xFF3D7BFF), tertiary = Color(0xFFF2F2F2)),
    UNDERSEA("bajo_el_mar", R.string.theme_undersea, R.string.theme_undersea_hint, ThemeGroup.CARTOONS,
        background = Color(0xFF021A2E), primary = Color(0xFFFFE14D), secondary = Color(0xFF4FC3F7), tertiary = Color(0xFFF7A8B8)),
    CANDY("caramelo", R.string.theme_candy, R.string.theme_candy_hint, ThemeGroup.CARTOONS,
        background = Color(0xFF14081A), primary = Color(0xFFFF7AC6), secondary = Color(0xFF7FE7FF), tertiary = Color(0xFFFFD86B)),

    ROCK("rock", R.string.theme_rock, R.string.theme_rock_hint, ThemeGroup.MUSIC,
        background = Color(0xFF070505), primary = Color(0xFFD4AF37), secondary = Color(0xFFE0283F), tertiary = Color(0xFFB8B8B8)),
    HORROR("terror", R.string.theme_horror, R.string.theme_horror_hint, ThemeGroup.MUSIC,
        background = Color(0xFF0C0406), primary = Color(0xFFE3262B), secondary = Color(0xFF9B5DE5), tertiary = Color(0xFFE9E1CF)),
    NEON("neon", R.string.theme_neon, R.string.theme_neon_hint, ThemeGroup.MUSIC,
        background = Color(0xFF0A0420), primary = Color(0xFFFF2E97), secondary = Color(0xFF00E5FF), tertiary = Color(0xFFFFB86B)),

    SELFIE("selfie", R.string.theme_selfie, R.string.theme_selfie_hint, ThemeGroup.SOCIAL,
        background = Color(0xFF0D0710), primary = Color(0xFFD62A62), secondary = Color(0xFFF77737), tertiary = Color(0xFF9B4BD6)),
    GAMER("gamer", R.string.theme_gamer, R.string.theme_gamer_hint, ThemeGroup.SOCIAL,
        background = Color(0xFF16171B), primary = Color(0xFF7983F5), secondary = Color(0xFF57F287), tertiary = Color(0xFFEB459E)),
    WALL("muro", R.string.theme_wall, R.string.theme_wall_hint, ThemeGroup.SOCIAL,
        background = Color(0xFF0F1318), primary = Color(0xFF3B8EF5), secondary = Color(0xFF8AB8F8), tertiary = Color(0xFFF5C33B)),
    CHAT("chat", R.string.theme_chat, R.string.theme_chat_hint, ThemeGroup.SOCIAL,
        background = Color(0xFF0B141A), primary = Color(0xFF00C08B), secondary = Color(0xFF53BDEB), tertiary = Color(0xFFE9EDEF)),
    ;

    /**
     * El esquema completo de Material 3 para este tema. El clásico conserva el suyo, ajustado a
     * mano; los demás se calculan con las mismas reglas.
     */
    val colorScheme: ColorScheme by lazy { if (this == CLASSIC) ClassicColors else generatedScheme() }

    private fun generatedScheme(): ColorScheme {
        // Las capas (tarjetas, hojas, barra inferior) son el fondo con un poco del acento encima:
        // cuanto más alta la capa, más color. Así se distinguen sin romper el tono del tema.
        fun layer(amount: Float) = primary.copy(alpha = amount).compositeOver(background)
        val text = lerp(Color(0xFFEDEDED), primary, 0.06f)
        val textSoft = lerp(Color(0xFFA9A9A9), primary, 0.22f)
        return darkColorScheme(
            primary = primary,
            onPrimary = readableOn(primary),
            primaryContainer = layer(0.24f),
            onPrimaryContainer = lerp(primary, Color.White, 0.55f),
            inversePrimary = lerp(primary, Color.Black, 0.4f),
            secondary = secondary,
            onSecondary = readableOn(secondary),
            secondaryContainer = secondary.copy(alpha = 0.18f).compositeOver(background),
            onSecondaryContainer = lerp(secondary, Color.White, 0.65f),
            tertiary = tertiary,
            onTertiary = readableOn(tertiary),
            background = background,
            onBackground = text,
            surface = background,
            onSurface = text,
            surfaceVariant = layer(0.14f),
            onSurfaceVariant = textSoft,
            surfaceDim = background,
            surfaceBright = layer(0.2f),
            surfaceContainerLowest = background,
            surfaceContainerLow = layer(0.05f),
            surfaceContainer = layer(0.08f),
            surfaceContainerHigh = layer(0.11f),
            surfaceContainerHighest = layer(0.14f),
            inverseSurface = text,
            inverseOnSurface = layer(0.08f),
            outline = lerp(Color(0xFF4A4A4A), primary, 0.25f),
            outlineVariant = lerp(Color(0xFF2A2A2A), primary, 0.15f),
            error = ErrorRed,
            onError = Color(0xFF3B0A0A),
            scrim = Color.Black,
        )
    }

    companion object {
        /** El tema guardado en Ajustes, o el clásico si no hay ninguno (o si ya no existe). */
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/**
 * Texto negro o blanco, el que mejor se lea encima de [color]. Un botón amarillo necesita letras
 * oscuras y uno azul marino, claras.
 *
 * Se decide con el "contraste" de las normas de accesibilidad (WCAG): compara cuánta luz refleja
 * cada color (su luminancia). Se elige el que más contraste da, no "a ojo".
 */
internal fun readableOn(color: Color): Color {
    val light = color.luminance()
    val withWhite = (1f + 0.05f) / (light + 0.05f)
    val withDark = (light + 0.05f) / (DarkText.luminance() + 0.05f)
    return if (withDark >= withWhite) DarkText else Color.White
}

private val DarkText = Color(0xFF111111)
