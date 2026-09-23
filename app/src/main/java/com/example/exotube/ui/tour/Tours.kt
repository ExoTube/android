package com.example.exotube.ui.tour

import androidx.annotation.StringRes
import com.example.exotube.R

/**
 * Un paso del tutorial: qué se ilumina ([spot]) y qué se explica. Sin [spot], la tarjeta sale en
 * el centro: para lo que no es un botón de la pantalla (compartir desde otra app, el widget).
 */
data class TourStep(val spot: TourSpot?, @StringRes val title: Int, @StringRes val body: Int)

/**
 * Los recorridos del tutorial, uno por pantalla.
 *
 * No es un único recorrido largo al abrir la app por primera vez, sino uno pequeño la primera vez
 * que se entra en cada sitio: así cada botón se explica cuando se tiene delante, y no hay que
 * recordar diez cosas de golpe. [id] es lo que se guarda para no repetirlo.
 *
 * @param waitFor el recorrido no empieza hasta que se vea este botón. En Explorar, por ejemplo,
 *   los videos tardan un par de segundos en llegar, y sin ellos no hay nada que señalar.
 */
enum class Tour(val id: String, val steps: List<TourStep>, val waitFor: TourSpot? = null) {
    LIBRARY(
        "biblioteca",
        listOf(
            TourStep(null, R.string.tour_welcome_title, R.string.tour_welcome_body),
            TourStep(TourSpot.TAB_LIBRARY, R.string.tour_tab_library_title, R.string.tour_tab_library_body),
            TourStep(TourSpot.TAB_EXPLORE, R.string.tour_tab_explore_title, R.string.tour_tab_explore_body),
            TourStep(TourSpot.TAB_ALBUMS, R.string.tour_tab_albums_title, R.string.tour_tab_albums_body),
            TourStep(TourSpot.TAB_PLAYLISTS, R.string.tour_tab_playlists_title, R.string.tour_tab_playlists_body),
            TourStep(TourSpot.LIBRARY_SEARCH, R.string.tour_library_search_title, R.string.tour_library_search_body),
            TourStep(TourSpot.LIBRARY_FILTERS, R.string.tour_library_filters_title, R.string.tour_library_filters_body),
            TourStep(TourSpot.LIBRARY_PHONE_MUSIC, R.string.tour_library_phone_title, R.string.tour_library_phone_body),
            TourStep(TourSpot.LIBRARY_ROW_MENU, R.string.tour_library_menu_title, R.string.tour_library_menu_body),
            TourStep(TourSpot.LIBRARY_SETTINGS, R.string.tour_library_settings_title, R.string.tour_library_settings_body),
            TourStep(null, R.string.tour_share_title, R.string.tour_share_body),
            TourStep(null, R.string.tour_widget_title, R.string.tour_widget_body),
        ),
    ),
    EXPLORE(
        "explorar",
        listOf(
            TourStep(TourSpot.EXPLORE_SEARCH, R.string.tour_explore_search_title, R.string.tour_explore_search_body),
            TourStep(TourSpot.EXPLORE_DATA_SAVER, R.string.tour_explore_saver_title, R.string.tour_explore_saver_body),
            TourStep(TourSpot.EXPLORE_TOPICS, R.string.tour_explore_topics_title, R.string.tour_explore_topics_body),
            TourStep(TourSpot.EXPLORE_VIDEO, R.string.tour_explore_video_title, R.string.tour_explore_video_body),
            TourStep(TourSpot.EXPLORE_DOWNLOAD, R.string.tour_explore_download_title, R.string.tour_explore_download_body),
            TourStep(TourSpot.EXPLORE_CHANNEL, R.string.tour_explore_channel_title, R.string.tour_explore_channel_body),
        ),
        waitFor = TourSpot.EXPLORE_VIDEO,
    ),
    ALBUMS(
        "albumes",
        listOf(
            TourStep(TourSpot.ALBUMS_GROUPING, R.string.tour_albums_grouping_title, R.string.tour_albums_grouping_body),
            TourStep(TourSpot.ALBUMS_FIRST, R.string.tour_albums_first_title, R.string.tour_albums_first_body),
        ),
    ),
    PLAYLISTS(
        "playlists",
        listOf(
            TourStep(TourSpot.PLAYLISTS_NEW, R.string.tour_playlists_new_title, R.string.tour_playlists_new_body),
            TourStep(TourSpot.PLAYLISTS_FIRST, R.string.tour_playlists_first_title, R.string.tour_playlists_first_body),
        ),
    ),
    MINI_PLAYER(
        "mini",
        listOf(TourStep(TourSpot.MINI_PLAYER, R.string.tour_mini_title, R.string.tour_mini_body)),
        waitFor = TourSpot.MINI_PLAYER,
    ),
    PLAYER(
        "reproductor",
        listOf(
            TourStep(TourSpot.PLAYER_COLLAPSE, R.string.tour_player_collapse_title, R.string.tour_player_collapse_body),
            TourStep(TourSpot.PLAYER_EQUALIZER, R.string.tour_player_equalizer_title, R.string.tour_player_equalizer_body),
            TourStep(TourSpot.PLAYER_SEEK, R.string.tour_player_seek_title, R.string.tour_player_seek_body),
            TourStep(TourSpot.PLAYER_SHUFFLE, R.string.tour_player_shuffle_title, R.string.tour_player_shuffle_body),
            TourStep(TourSpot.PLAYER_CONTROLS, R.string.tour_player_controls_title, R.string.tour_player_controls_body),
            TourStep(TourSpot.PLAYER_REPEAT, R.string.tour_player_repeat_title, R.string.tour_player_repeat_body),
        ),
        waitFor = TourSpot.PLAYER_CONTROLS,
    ),
    VIDEO(
        "video",
        listOf(
            TourStep(TourSpot.VIDEO_QUALITY, R.string.tour_video_quality_title, R.string.tour_video_quality_body),
            TourStep(TourSpot.VIDEO_FLOATING, R.string.tour_video_floating_title, R.string.tour_video_floating_body),
            TourStep(TourSpot.VIDEO_FULLSCREEN, R.string.tour_video_fullscreen_title, R.string.tour_video_fullscreen_body),
            TourStep(TourSpot.VIDEO_CHANNEL, R.string.tour_video_channel_title, R.string.tour_video_channel_body),
        ),
        waitFor = TourSpot.VIDEO_FULLSCREEN,
    ),
    SETTINGS(
        "ajustes",
        listOf(
            TourStep(TourSpot.SETTINGS_THEME, R.string.tour_settings_theme_title, R.string.tour_settings_theme_body),
            TourStep(TourSpot.SETTINGS_FILTER, R.string.tour_settings_filter_title, R.string.tour_settings_filter_body),
            TourStep(TourSpot.SETTINGS_TUTORIAL, R.string.tour_settings_tutorial_title, R.string.tour_settings_tutorial_body),
        ),
    ),
    ;

    companion object {
        val allIds: Set<String> = entries.map { it.id }.toSet()
    }
}

/**
 * Los pasos que de verdad se pueden enseñar ahora: los del centro siempre, y los que señalan un
 * botón solo si ese botón está en pantalla. En una biblioteca vacía no hay "más opciones" de
 * ninguna fila que señalar, y sin permiso de audio no hay aviso; esos pasos se saltan.
 */
internal fun stepsToShow(steps: List<TourStep>, isVisible: (TourSpot) -> Boolean): List<TourStep> =
    steps.filter { step -> step.spot == null || isVisible(step.spot) }

/**
 * Qué recorrido toca enseñar ahora, o ninguno.
 *
 * @param screenTour el de la pantalla que se ve (Biblioteca, Explorar, Ajustes…); null en las
 *   pantallas de detalle, que no tienen recorrido propio.
 * @param isBusy hay algo encima (un diálogo, el recortador, la pantalla completa…): el tutorial
 *   espera a que se cierre, para no tapar lo que el usuario está haciendo.
 */
internal fun tourToShow(
    seen: Set<String>,
    screenTour: Tour?,
    isPlayerOpen: Boolean,
    isVideoPlaying: Boolean,
    hasMiniPlayer: Boolean,
    isBusy: Boolean,
): Tour? {
    fun Tour.pending() = id !in seen
    return when {
        isBusy -> null
        // Con el reproductor abierto, solo se habla del reproductor: es lo que se tiene delante.
        isPlayerOpen -> when {
            Tour.PLAYER.pending() -> Tour.PLAYER
            isVideoPlaying && Tour.VIDEO.pending() -> Tour.VIDEO
            else -> null
        }
        screenTour != null && screenTour.pending() -> screenTour
        // El mini reproductor aparece al poner algo a sonar, en cualquier pestaña.
        hasMiniPlayer && screenTour != null && screenTour != Tour.SETTINGS && Tour.MINI_PLAYER.pending() -> Tour.MINI_PLAYER
        else -> null
    }
}
