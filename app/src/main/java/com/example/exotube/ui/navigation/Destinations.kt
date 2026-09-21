package com.example.exotube.ui.navigation

import kotlinx.serialization.Serializable

// Navegación "type-safe": cada pantalla es una clase. Si una pantalla necesita un dato (el id de
// la playlist), es un parámetro de la clase: el compilador impide navegar sin él.

@Serializable
data object LibraryDestination

@Serializable
data object ExploreDestination

@Serializable
data object PlaylistsDestination

@Serializable
data class PlaylistDestination(val playlistId: Long)
