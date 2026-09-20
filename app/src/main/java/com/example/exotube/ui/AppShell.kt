package com.example.exotube.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.exotube.R
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.library.LibraryRoute
import com.example.exotube.player.MiniPlayer
import com.example.exotube.player.NowPlayingScreen
import com.example.exotube.player.PlayerViewModel
import com.example.exotube.playlist.AddToPlaylistSheet
import com.example.exotube.playlist.CreatePlaylistDialog
import com.example.exotube.playlist.PlaylistDetailRoute
import com.example.exotube.playlist.PlaylistsScreen
import com.example.exotube.playlist.PlaylistsViewModel
import com.example.exotube.ui.navigation.LibraryDestination
import com.example.exotube.ui.navigation.PlaylistDestination
import com.example.exotube.ui.navigation.PlaylistsDestination

/** Pestañas de la barra inferior. */
private enum class TopLevelTab(val destination: Any, @StringRes val label: Int, @DrawableRes val icon: Int) {
    LIBRARY(LibraryDestination, R.string.nav_library, R.drawable.ic_library_music),
    PLAYLISTS(PlaylistsDestination, R.string.nav_playlists, R.drawable.ic_queue_music),
}

/**
 * Esqueleto de la app: navegación entre pantallas + todo lo que es común a ellas
 * (mini reproductor, pantalla "Reproduciendo" y hoja "Añadir a playlist").
 */
@Composable
fun AppShell(playerViewModel: PlayerViewModel, playlistsViewModel: PlaylistsViewModel) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val player by playerViewModel.player.collectAsStateWithLifecycle()
    val playlistsState by playlistsViewModel.uiState.collectAsStateWithLifecycle()
    val nowPlayingUri = player?.let { rememberCurrentMediaItemState(it).mediaItem?.mediaId }

    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var itemForPlaylist by remember { mutableStateOf<LibraryItem?>(null) }
    // Crear playlist: null = diálogo cerrado; Some(uri?) = abierto (y, si hay uri, se añade al crearla).
    var newPlaylistRequest by remember { mutableStateOf<NewPlaylistRequest?>(null) }

    val isTopLevel = currentDestination == null || TopLevelTab.entries.any { currentDestination.isOn(it) }

    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Sin barra de pestañas (en el detalle), el mini reproductor respeta la barra del sistema.
                Column(if (isTopLevel) Modifier else Modifier.navigationBarsPadding()) {
                    player?.let { MiniPlayer(it, onOpen = { showNowPlaying = true }) }
                    if (isTopLevel) {
                        BottomTabs(currentDestination) { tab ->
                            navController.navigate(tab.destination) {
                                // Patrón estándar de pestañas: no apilar pantallas y recordar su estado.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(navController = navController, startDestination = LibraryDestination) {
                composable<LibraryDestination> {
                    LibraryRoute(
                        nowPlayingUri = nowPlayingUri,
                        onPlay = { items, index -> playerViewModel.playQueue(items, index) },
                        onAddToPlaylist = { itemForPlaylist = it },
                        contentPadding = padding,
                    )
                }
                composable<PlaylistsDestination> {
                    PlaylistsScreen(
                        state = playlistsState,
                        onOpenPlaylist = { navController.navigate(PlaylistDestination(it)) },
                        onCreatePlaylist = { newPlaylistRequest = NewPlaylistRequest(addingMediaUri = null) },
                        contentPadding = padding,
                    )
                }
                composable<PlaylistDestination> {
                    PlaylistDetailRoute(
                        nowPlayingUri = nowPlayingUri,
                        onPlay = playerViewModel::playQueue,
                        onBack = { navController.popBackStack() },
                        contentPadding = padding,
                    )
                }
            }
        }

        // "Reproduciendo" sube desde abajo, por encima de todo.
        AnimatedVisibility(
            visible = showNowPlaying && player != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            player?.let { NowPlayingScreen(it, onCollapse = { showNowPlaying = false }) }
        }
    }

    itemForPlaylist?.let { item ->
        val playlistIds by remember(item.uri) { playlistsViewModel.playlistIdsContaining(item.uri) }
            .collectAsStateWithLifecycle(initialValue = emptySet())
        AddToPlaylistSheet(
            item = item,
            playlists = playlistsState.playlists,
            playlistIdsWithItem = playlistIds,
            onToggle = { playlistId, include -> playlistsViewModel.setMembership(playlistId, item.uri, include) },
            onCreateNew = { newPlaylistRequest = NewPlaylistRequest(addingMediaUri = item.uri) },
            onDismiss = { itemForPlaylist = null },
        )
    }

    newPlaylistRequest?.let { request ->
        CreatePlaylistDialog(
            onConfirm = { name, coverUri ->
                playlistsViewModel.create(name, coverUri?.toString(), request.addingMediaUri)
                newPlaylistRequest = null
            },
            onDismiss = { newPlaylistRequest = null },
        )
    }
}

private data class NewPlaylistRequest(val addingMediaUri: String?)

@Composable
private fun BottomTabs(currentDestination: NavDestination?, onSelect: (TopLevelTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentDestination?.isOn(tab) == true,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

private fun NavDestination.isOn(tab: TopLevelTab): Boolean = hasRoute(tab.destination::class)
