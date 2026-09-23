package com.example.exotube.ui.navigation

import kotlinx.serialization.Serializable

// Navegación "type-safe": cada pantalla es una clase. Si una pantalla necesita un dato (el id de
// la playlist), es un parámetro de la clase: el compilador impide navegar sin él.

@Serializable
data object LibraryDestination

@Serializable
data object ExploreDestination

@Serializable
data object AlbumsDestination

/** El id de un álbum es su propio nombre; vacío para el grupo de las canciones sin etiqueta. */
@Serializable
data class AlbumDestination(val albumId: String)

@Serializable
data object PlaylistsDestination

@Serializable
data class PlaylistDestination(val playlistId: Long)

/**
 * El canal de YouTube de un autor. [channelUrl] puede faltar (algunos videos llegan sin él):
 * entonces se averigua a partir de [videoUrl]. [name] se enseña arriba mientras carga.
 */
@Serializable
data class ChannelDestination(val channelUrl: String?, val videoUrl: String?, val name: String)

/** Ajustes, con sus dos apartados: el tema de colores y el filtro de la biblioteca. */
@Serializable
data object SettingsDestination

@Serializable
data object ThemeSettingsDestination

@Serializable
data object LibraryFilterDestination
