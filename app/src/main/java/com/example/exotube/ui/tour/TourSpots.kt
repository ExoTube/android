package com.example.exotube.ui.tour

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Cada botón que el tutorial puede señalar. El nombre dice dónde está.
 *
 * Son "puntos de anclaje": el botón se marca con [tourSpot] y el tutorial, que se dibuja encima de
 * todo, pregunta aquí dónde quedó para iluminar justo ese sitio.
 */
enum class TourSpot {
    TAB_LIBRARY, TAB_EXPLORE, TAB_ALBUMS, TAB_PLAYLISTS,
    LIBRARY_SEARCH, LIBRARY_FILTERS, LIBRARY_PHONE_MUSIC, LIBRARY_ROW_MENU, LIBRARY_SETTINGS,
    EXPLORE_SEARCH, EXPLORE_DATA_SAVER, EXPLORE_TOPICS, EXPLORE_VIDEO, EXPLORE_DOWNLOAD, EXPLORE_CHANNEL,
    ALBUMS_GROUPING, ALBUMS_FIRST,
    PLAYLISTS_NEW, PLAYLISTS_FIRST,
    MINI_PLAYER,
    PLAYER_COLLAPSE, PLAYER_EQUALIZER, PLAYER_SEEK, PLAYER_SHUFFLE, PLAYER_CONTROLS, PLAYER_REPEAT,
    VIDEO_QUALITY, VIDEO_FLOATING, VIDEO_FULLSCREEN, VIDEO_CHANNEL,
    SETTINGS_THEME, SETTINGS_FILTER, SETTINGS_TUTORIAL,
}

/**
 * Dónde está ahora cada botón marcado.
 *
 * Se guardan las "coordenadas" de Compose y no un rectángulo fijo: así, si la lista se desplaza o
 * el botón desaparece (se cambió de pantalla), al preguntar se obtiene su sitio de ese momento, o
 * nada. Es un objeto único porque la app tiene una sola pantalla principal.
 */
object TourSpots {

    private val spots = mutableStateMapOf<TourSpot, LayoutCoordinates>()

    internal fun record(spot: TourSpot, coordinates: LayoutCoordinates) {
        spots[spot] = coordinates
    }

    /** El rectángulo del botón en la pantalla, o null si ahora mismo no se ve. */
    fun boundsOf(spot: TourSpot): Rect? =
        spots[spot]?.takeIf { it.isAttached }?.boundsInRoot()?.takeIf { it.width > 0f && it.height > 0f }

    fun isVisible(spot: TourSpot): Boolean = boundsOf(spot) != null
}

/** Marca este elemento para que el tutorial lo pueda señalar. */
fun Modifier.tourSpot(spot: TourSpot): Modifier = onGloballyPositioned { TourSpots.record(spot, it) }

/** [tourSpot] solo si [enabled]: para marcar únicamente la primera fila de una lista. */
fun Modifier.tourSpot(spot: TourSpot, enabled: Boolean): Modifier = if (enabled) tourSpot(spot) else this
