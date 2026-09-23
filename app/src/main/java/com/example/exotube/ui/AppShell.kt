package com.example.exotube.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaMetadata
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.exotube.ui.tour.Tour
import com.example.exotube.ui.tour.TourOverlay
import com.example.exotube.ui.tour.TourSpot
import com.example.exotube.ui.tour.tourToShow
import com.example.exotube.ui.tour.tourSpot
import com.example.exotube.ExoTubeApp
import com.example.exotube.R
import com.example.exotube.album.AlbumDetailScreen
import com.example.exotube.album.AlbumsScreen
import com.example.exotube.album.AlbumsViewModel
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.explore.ExploreRoute
import com.example.exotube.library.LibraryRoute
import com.example.exotube.player.EqualizerRoute
import com.example.exotube.player.FloatingVideo
import com.example.exotube.player.FullscreenVideoScreen
import com.example.exotube.player.MiniPlayer
import com.example.exotube.player.NowPlayingScreen
import com.example.exotube.player.PlayerViewModel
import com.example.exotube.playlist.AddToPlaylistSheet
import com.example.exotube.playlist.CreatePlaylistDialog
import com.example.exotube.playlist.PlaylistDetailRoute
import com.example.exotube.playlist.PlaylistsScreen
import com.example.exotube.playlist.PlaylistsViewModel
import com.example.exotube.songedit.RenameSongDialog
import com.example.exotube.channel.ChannelRoute
import com.example.exotube.songedit.DeleteMediaDialog
import com.example.exotube.songedit.SongEditEvent
import com.example.exotube.songedit.SongEditViewModel
import com.example.exotube.trim.TrimRoute
import com.example.exotube.update.UpdateRoute
import com.example.exotube.ui.navigation.AlbumDestination
import com.example.exotube.ui.navigation.AlbumsDestination
import com.example.exotube.ui.navigation.ExploreDestination
import com.example.exotube.ui.navigation.LibraryDestination
import com.example.exotube.ui.navigation.PlaylistDestination
import com.example.exotube.ui.navigation.PlaylistsDestination
import com.example.exotube.ui.navigation.ChannelDestination
import com.example.exotube.ui.navigation.LibraryFilterDestination
import com.example.exotube.ui.navigation.SettingsDestination
import com.example.exotube.ui.navigation.ThemeSettingsDestination
import com.example.exotube.settings.LibraryFilterRoute
import com.example.exotube.settings.SettingsRoute
import com.example.exotube.settings.ThemeSettingsRoute
import com.example.exotube.domain.model.OnlineVideo

/** Pestañas de la barra inferior. */
private enum class TopLevelTab(
    val destination: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val spot: TourSpot,
    /** El tutorial de esta pestaña, que sale la primera vez que se entra. */
    val tour: Tour,
) {
    LIBRARY(LibraryDestination, R.string.nav_library, R.drawable.ic_library_music, TourSpot.TAB_LIBRARY, Tour.LIBRARY),
    EXPLORE(ExploreDestination, R.string.nav_explore, R.drawable.ic_explore, TourSpot.TAB_EXPLORE, Tour.EXPLORE),
    ALBUMS(AlbumsDestination, R.string.nav_albums, R.drawable.ic_album, TourSpot.TAB_ALBUMS, Tour.ALBUMS),
    PLAYLISTS(PlaylistsDestination, R.string.nav_playlists, R.drawable.ic_queue_music, TourSpot.TAB_PLAYLISTS, Tour.PLAYLISTS),
}

/**
 * Esqueleto de la app: navegación entre pantallas + todo lo que es común a ellas
 * (mini reproductor, pantalla "Reproduciendo" y hoja "Añadir a playlist").
 */
@Composable
fun AppShell(
    playerViewModel: PlayerViewModel,
    playlistsViewModel: PlaylistsViewModel,
    albumsViewModel: AlbumsViewModel,
    /** true mientras la app se ve como una ventanita flotante encima de otras apps. */
    isInPictureInPicture: Boolean = false,
    /** null si el teléfono no admite la ventana flotante. */
    onEnterPictureInPicture: (() -> Unit)? = null,
) {
    val player by playerViewModel.player.collectAsStateWithLifecycle()

    // En la ventana flotante no cabe la app: solo el video. Se sale antes de dibujar nada más.
    if (isInPictureInPicture) {
        player?.let { FloatingVideo(it) }
        return
    }

    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val playlistsState by playlistsViewModel.uiState.collectAsStateWithLifecycle()
    val albumsState by albumsViewModel.uiState.collectAsStateWithLifecycle()
    val nowPlayingUri = player?.let { rememberCurrentMediaItemState(it).mediaItem?.mediaId }
    val repeatPlan by playerViewModel.repeatPlan.collectAsStateWithLifecycle()
    // "Muestra el botón de reproducir" equivale a "no está sonando": lo usan el ecualizador de
    // cada fila y la carátula que late, para animarse solo cuando de verdad suena la música.
    val isPlaying = player?.let { !rememberPlayPauseButtonState(it).showPlay } == true

    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var itemToTrim by remember { mutableStateOf<LibraryItem?>(null) }
    var itemToRename by remember { mutableStateOf<LibraryItem?>(null) }
    var itemToDelete by remember { mutableStateOf<LibraryItem?>(null) }
    var itemForPlaylist by remember { mutableStateOf<LibraryItem?>(null) }
    // Crear playlist: null = diálogo cerrado; Some(uri?) = abierto (y, si hay uri, se añade al crearla).
    var newPlaylistRequest by remember { mutableStateOf<NewPlaylistRequest?>(null) }

    val isTopLevel = currentDestination == null || TopLevelTab.entries.any { currentDestination.isOn(it) }

    // El tutorial: qué partes se han visto ya, y si lo que suena ahora es un video.
    val appSettings = (LocalContext.current.applicationContext as ExoTubeApp).container.settings
    val seenTours by appSettings.seenTours.collectAsStateWithLifecycle()
    val isVideoPlaying = player?.let {
        rememberCurrentMediaItemState(it).mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO
    } == true

    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }

    // Si el reproductor falla y no lo pudo arreglar solo, hay que decirlo: si no, el usuario
    // toca un video y no pasa nada.
    val context = LocalContext.current
    LaunchedEffect(playerViewModel) {
        playerViewModel.errors.collect { messageRes ->
            Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
        }
    }
    val online by playerViewModel.online.collectAsStateWithLifecycle()

    // Abrir el canal de un video en línea, desde Explorar o desde "Reproduciendo". El
    // reproductor se baja para que se vea el canal; la música sigue sonando.
    val openChannel: (OnlineVideo) -> Unit = { video ->
        showNowPlaying = false
        navController.navigate(ChannelDestination(video.channelUrl, video.url, video.channel.orEmpty()))
    }

    // Cambiar la portada, el nombre y borrar comparten el permiso que pide Android.
    val songEditViewModel: SongEditViewModel = viewModel(factory = SongEditViewModel.Factory)
    val onChangeCover = rememberChangeCoverAction(
        viewModel = songEditViewModel,
        onDeleted = playerViewModel::removeFromQueue,
    )
    val workingMessage by songEditViewModel.workingMessage.collectAsStateWithLifecycle()

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
                        isPlaying = isPlaying,
                        onPlay = { items, index -> playerViewModel.playQueue(items, index) },
                        onAddToPlaylist = { itemForPlaylist = it },
                        onTrim = { itemToTrim = it },
                        onChangeCover = onChangeCover,
                        onRename = { itemToRename = it },
                        onDelete = { itemToDelete = it },
                        onOpenSettings = { navController.navigate(SettingsDestination) },
                        contentPadding = padding,
                    )
                }
                composable<SettingsDestination> {
                    SettingsRoute(
                        onOpenTheme = { navController.navigate(ThemeSettingsDestination) },
                        onOpenLibraryFilter = { navController.navigate(LibraryFilterDestination) },
                        onBack = { navController.popBackStack() },
                        contentPadding = padding,
                    )
                }
                composable<ThemeSettingsDestination> {
                    ThemeSettingsRoute(onBack = { navController.popBackStack() }, contentPadding = padding)
                }
                composable<LibraryFilterDestination> {
                    LibraryFilterRoute(onBack = { navController.popBackStack() }, contentPadding = padding)
                }
                composable<ExploreDestination> {
                    ExploreRoute(
                        onPlayOnline = { video, stream, audioOnly ->
                            playerViewModel.playOnline(video, stream, audioOnly)
                            showNowPlaying = true // lo que se toca se ve: abrimos el reproductor
                        },
                        onOpenChannel = openChannel,
                        onGoToLibrary = { navController.navigate(LibraryDestination) },
                        contentPadding = padding,
                    )
                }
                composable<AlbumsDestination> {
                    AlbumsScreen(
                        state = albumsState,
                        onGroupingSelected = albumsViewModel::onGroupingSelected,
                        onOpenAlbum = { navController.navigate(AlbumDestination(it.id)) },
                        contentPadding = padding,
                    )
                }
                composable<AlbumDestination> { entry ->
                    // El álbum se busca en el mismo estado que pinta la lista: si su última
                    // canción se borra, el álbum desaparece y volvemos atrás solos.
                    val albumId = entry.toRoute<AlbumDestination>().albumId
                    val album = albumsState.albums.firstOrNull { it.id == albumId }
                    if (album == null) {
                        if (!albumsState.isLoading) LaunchedEffect(albumId) { navController.popBackStack() }
                    } else {
                        AlbumDetailScreen(
                            album = album,
                            nowPlayingUri = nowPlayingUri,
                            isPlaying = isPlaying,
                            onPlay = playerViewModel::playQueue,
                            onAddToPlaylist = { itemForPlaylist = it },
                            onTrim = { itemToTrim = it },
                            onChangeCover = onChangeCover,
                            onRename = { itemToRename = it },
                            onDelete = { itemToDelete = it },
                            onBack = { navController.popBackStack() },
                            contentPadding = padding,
                        )
                    }
                }
                composable<ChannelDestination> { entry ->
                    ChannelRoute(
                        title = entry.toRoute<ChannelDestination>().name,
                        onPlayOnline = { video, stream, audioOnly ->
                            playerViewModel.playOnline(video, stream, audioOnly)
                            showNowPlaying = true
                        },
                        onBack = { navController.popBackStack() },
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
                        isPlaying = isPlaying,
                        onPlay = playerViewModel::playQueue,
                        onBack = { navController.popBackStack() },
                        contentPadding = padding,
                    )
                }
            }
        }

        // "Reproduciendo" sube desde abajo, por encima de todo (salvo del tutorial).
        AnimatedVisibility(
            visible = showNowPlaying && player != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            player?.let {
                NowPlayingScreen(
                    player = it,
                    repeatPlan = repeatPlan,
                    onCycleRepeat = playerViewModel::cycleRepeatPlan,
                    onOpenEqualizer = { showEqualizer = true },
                    onEnterFullscreen = { isFullscreen = true },
                    onEnterPictureInPicture = onEnterPictureInPicture,
                    onCollapse = { showNowPlaying = false },
                    online = online,
                    onChangeQuality = playerViewModel::changeQuality,
                    onOpenChannel = openChannel,
                )
            }
        }

        val screenTour = when {
            currentDestination == null -> Tour.LIBRARY
            currentDestination.hasRoute(SettingsDestination::class) -> Tour.SETTINGS
            else -> TopLevelTab.entries.firstOrNull { currentDestination.isOn(it) }?.tour
        }
        val tour = tourToShow(
            seen = seenTours,
            screenTour = screenTour,
            isPlayerOpen = showNowPlaying && player != null,
            isVideoPlaying = isVideoPlaying,
            hasMiniPlayer = nowPlayingUri != null,
            isBusy = isFullscreen || showEqualizer || itemToTrim != null || itemToRename != null ||
                itemToDelete != null || itemForPlaylist != null || newPlaylistRequest != null || workingMessage != null,
        )
        // key: al cambiar de recorrido, el anterior se descarta entero y el nuevo empieza de cero.
        tour?.let { current ->
            key(current) { TourOverlay(current, onFinish = { appSettings.markTourSeen(current.id) }) }
        }
    }

    // La pantalla completa tapa incluso "Reproduciendo": mientras dura, solo existe el video.
    if (isFullscreen) {
        val current = player
        if (current == null) {
            isFullscreen = false
        } else {
            BackHandler { isFullscreen = false }
            FullscreenVideoScreen(player = current, onExit = { isFullscreen = false })
        }
    }

    // Recortar ocupa la pantalla entera, encima de todo, como "Reproduciendo".
    itemToTrim?.let { item ->
        BackHandler { itemToTrim = null }
        TrimRoute(item = item, onClose = { itemToTrim = null })
    }

    if (showEqualizer) {
        EqualizerRoute(onDismiss = { showEqualizer = false })
    }

    itemToRename?.let { item ->
        RenameSongDialog(
            currentTitle = item.title,
            onConfirm = { newTitle ->
                songEditViewModel.rename(item, newTitle)
                itemToRename = null
            },
            onDismiss = { itemToRename = null },
        )
    }

    itemToDelete?.let { item ->
        DeleteMediaDialog(
            item = item,
            onConfirm = {
                songEditViewModel.delete(item)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null },
        )
    }

    // Reescribir una canción de varios megabytes tarda un par de segundos: hay que decirlo.
    workingMessage?.let { WorkingOverlay(it) }

    // Aviso de versión nueva y, tras actualizar, las novedades. Aparecen solos cuando toca.
    UpdateRoute()

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

/**
 * Devuelve la acción "cambiar la portada de esta canción", con los tres pasos ya resueltos:
 * elegir la foto, aplicarla y, si Android lo exige, pedir permiso para tocar el archivo.
 *
 * No dibuja nada: solo registra los lanzadores del sistema y devuelve la función a llamar.
 */
@Composable
private fun rememberChangeCoverAction(
    viewModel: SongEditViewModel,
    onDeleted: (uri: String) -> Unit,
): (LibraryItem) -> Unit {
    val context = LocalContext.current
    // De qué canción era la portada. El selector de fotos es del sistema y no se lo puede llevar.
    var pendingItem by remember { mutableStateOf<LibraryItem?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val item = pendingItem
        pendingItem = null
        if (uri != null && item != null) viewModel.changeCover(item, uri.toString())
    }

    // El diálogo del sistema para autorizar la escritura en un archivo que no creó ExoTube.
    val grantWrite = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.retryPending() else viewModel.cancelPending()
    }

    // Los avisos son de todas las operaciones, no solo de la portada: el ViewModel es el mismo.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SongEditEvent.Done ->
                    Toast.makeText(context, event.messageRes, Toast.LENGTH_SHORT).show()
                is SongEditEvent.Failed -> Toast.makeText(
                    context,
                    context.getString(event.messageRes, event.cause.orEmpty()),
                    Toast.LENGTH_LONG,
                ).show()
                is SongEditEvent.Deleted -> onDeleted(event.uri)
                is SongEditEvent.NeedsPermission ->
                    grantWrite.launch(IntentSenderRequest.Builder(event.request).build())
            }
        }
    }

    return { item ->
        pendingItem = item
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

/** Tapa la pantalla mientras se reescribe el archivo, para que nadie lo toque a medias. */
@Composable
private fun WorkingOverlay(@StringRes messageRes: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun BottomTabs(currentDestination: NavDestination?, onSelect: (TopLevelTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentDestination?.isOn(tab) == true,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                modifier = Modifier.tourSpot(tab.spot),
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
