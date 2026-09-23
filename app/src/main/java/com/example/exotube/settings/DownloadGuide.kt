package com.example.exotube.settings

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.exotube.R

/**
 * La guía "Cómo descargar": para cada red, dónde está su botón de Compartir y cómo se llega desde
 * ahí a ExoTube. Aquí solo están los datos; la animación que los dibuja está en
 * DownloadGuideScreen.kt.
 *
 * Los dibujos no copian las apps (sus diseños y logotipos tienen dueño): son un teléfono genérico
 * con los botones en el mismo sitio, que es lo que hace falta para encontrarlos.
 *
 * @param shareAt dónde está el botón de Compartir, en fracciones de la pantalla (0,0 arriba a la
 *   izquierda; 1,1 abajo a la derecha).
 * @param shareOnRail true si los botones van en columna a la derecha del video (TikTok, reels de
 *   Instagram); false si van en una fila debajo (X, Facebook).
 */
enum class GuidePlatform(
    val label: String,
    val accent: Color,
    val shareAt: Offset,
    val shareOnRail: Boolean,
    @StringRes val moreLabel: Int,
    /** Los cuatro pasos escritos, en el mismo orden que la animación. */
    val steps: List<Int>,
) {
    TIKTOK(
        label = "TikTok",
        accent = Color(0xFFFE2C55),
        shareAt = Offset(0.88f, 0.66f),
        shareOnRail = true,
        moreLabel = R.string.guide_more_tiktok,
        steps = listOf(R.string.guide_tiktok_1, R.string.guide_tiktok_2, R.string.guide_step_pick_exotube, R.string.guide_step_quality),
    ),
    INSTAGRAM(
        label = "Instagram",
        accent = Color(0xFFD62A62),
        shareAt = Offset(0.88f, 0.72f),
        shareOnRail = true,
        moreLabel = R.string.guide_more_instagram,
        steps = listOf(R.string.guide_instagram_1, R.string.guide_instagram_2, R.string.guide_step_pick_exotube, R.string.guide_step_quality),
    ),
    X(
        label = "X",
        accent = Color(0xFF1D9BF0),
        shareAt = Offset(0.86f, 0.9f),
        shareOnRail = false,
        moreLabel = R.string.guide_more_x,
        steps = listOf(R.string.guide_x_1, R.string.guide_x_2, R.string.guide_step_pick_exotube, R.string.guide_step_quality),
    ),
    FACEBOOK(
        label = "Facebook",
        accent = Color(0xFF3B8EF5),
        shareAt = Offset(0.8f, 0.9f),
        shareOnRail = false,
        moreLabel = R.string.guide_more_facebook,
        steps = listOf(R.string.guide_facebook_1, R.string.guide_facebook_2, R.string.guide_step_pick_exotube, R.string.guide_step_quality),
    ),
}

/**
 * El guion de la animación, en milisegundos desde que empieza cada vuelta. Está separado del
 * dibujo para que se pueda comprobar con pruebas que las escenas no se pisan.
 */
internal object GuideTimeline {
    const val TOTAL_MS = 9_000

    const val TAP_SHARE = 1_500
    const val PANEL_IN = 1_800
    const val TAP_MORE = 3_200
    const val SYSTEM_IN = 3_600
    const val TAP_EXOTUBE = 5_000
    const val EXOTUBE_IN = 5_400
    const val TAP_QUALITY = 6_200
    const val DOWNLOAD_START = 6_400
    const val DOWNLOAD_END = 7_600

    /** Qué paso del texto corresponde a este momento: se resalta mientras se ve. */
    fun stepAt(timeMs: Int): Int = when {
        timeMs < PANEL_IN -> 0
        timeMs < SYSTEM_IN -> 1
        timeMs < EXOTUBE_IN -> 2
        else -> 3
    }

    /** Cuánto se ha llenado la barra de descarga (0 a 1). */
    fun downloadProgress(timeMs: Int): Float =
        ((timeMs - DOWNLOAD_START).toFloat() / (DOWNLOAD_END - DOWNLOAD_START)).coerceIn(0f, 1f)

    /**
     * Dónde está el dedo, en fracciones de la pantalla del teléfono dibujado. Va de un botón al
     * siguiente justo antes de cada toque, con un poco de calma en medio para que se pueda seguir.
     */
    fun fingerAt(timeMs: Int, shareAt: Offset): Offset {
        val stops = listOf(
            0 to REST,
            600 to REST,
            1_350 to shareAt,
            2_300 to shareAt,
            3_050 to MORE_BUTTON,
            3_900 to MORE_BUTTON,
            4_850 to EXOTUBE_ICON,
            5_600 to EXOTUBE_ICON,
            6_050 to QUALITY_ROW,
            TOTAL_MS to QUALITY_ROW,
        )
        val next = stops.indexOfFirst { it.first >= timeMs }.coerceAtLeast(1)
        val (startTime, from) = stops[next - 1]
        val (endTime, to) = stops[next]
        val t = if (endTime == startTime) 1f else ((timeMs - startTime).toFloat() / (endTime - startTime)).coerceIn(0f, 1f)
        val eased = t * t * (3 - 2 * t) // arranca y frena suave, como una mano de verdad
        return Offset(from.x + (to.x - from.x) * eased, from.y + (to.y - from.y) * eased)
    }

    /** Los toques, para dibujar el círculo que se expande al pulsar. */
    val taps = listOf(TAP_SHARE, TAP_MORE, TAP_EXOTUBE, TAP_QUALITY)

    private val REST = Offset(0.5f, 0.45f)
    val MORE_BUTTON = Offset(0.86f, 0.87f)
    val EXOTUBE_ICON = Offset(0.39f, 0.72f)
    val QUALITY_ROW = Offset(0.5f, 0.72f)
}
