package com.example.exotube.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Esquema único y oscuro. No usamos "dynamic color" (Android 12+): tomaría los colores del
 * fondo de pantalla del usuario y la app perdería su identidad verde y negra.
 */
private val ExoTubeColors = darkColorScheme(
    primary = ExoGreen,
    onPrimary = ExoInk,
    primaryContainer = ExoGreenDeep,
    onPrimaryContainer = ExoGreenPale,
    inversePrimary = Color(0xFF006D38),
    secondary = Color(0xFF8FD9A8),
    onSecondary = ExoInk,
    secondaryContainer = Color(0xFF1A2E22),
    onSecondaryContainer = Color(0xFFCDEFD9),
    tertiary = Color(0xFF7FE0D0),
    onTertiary = Color(0xFF00201B),
    background = ExoBlack,
    onBackground = TextPrimary,
    surface = ExoBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHighest,
    onSurfaceVariant = TextSecondary,
    surfaceDim = ExoBlack,
    surfaceBright = Color(0xFF26332B),
    surfaceContainerLowest = ExoBlack,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceMid,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    inverseSurface = TextPrimary,
    inverseOnSurface = SurfaceMid,
    outline = Outline,
    outlineVariant = OutlineSoft,
    error = ErrorRed,
    onError = Color(0xFF3B0A0A),
    scrim = ExoBlack,
)

@Composable
fun ExoTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ExoTubeColors,
        typography = Typography,
        content = content,
    )
}
