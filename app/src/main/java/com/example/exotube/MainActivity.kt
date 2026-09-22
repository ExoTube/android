package com.example.exotube

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.exotube.album.AlbumsViewModel
import com.example.exotube.player.PlayerViewModel
import com.example.exotube.playlist.PlaylistsViewModel
import com.example.exotube.ui.AppShell
import com.example.exotube.ui.theme.ExoTubeTheme
import kotlinx.coroutines.launch

/** Única Activity de la app principal: las pantallas son destinos de navegación dentro de ella. */
class MainActivity : ComponentActivity() {

    // Viven a nivel de Activity porque los usan varias pantallas a la vez.
    private val playerViewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory }
    private val playlistsViewModel: PlaylistsViewModel by viewModels { PlaylistsViewModel.Factory }
    private val albumsViewModel: AlbumsViewModel by viewModels { AlbumsViewModel.Factory }

    /** true mientras la app se ve como una ventanita flotante encima de otras apps. */
    private var isInPictureInPicture by mutableStateOf(false)

    private val supportsPictureInPicture: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app siempre es oscura: iconos claros en las barras del sistema, aunque el teléfono
        // esté en modo claro (si no, serían negros sobre negro e invisibles).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        watchPlayerForPictureInPicture()
        setContent {
            ExoTubeTheme {
                AppShell(
                    playerViewModel = playerViewModel,
                    playlistsViewModel = playlistsViewModel,
                    albumsViewModel = albumsViewModel,
                    isInPictureInPicture = isInPictureInPicture,
                    onEnterPictureInPicture = ::enterPictureInPicture.takeIf { supportsPictureInPicture },
                )
            }
        }
    }

    /**
     * Al salir de la app con un video en marcha, este es el aviso que da Android con la
     * navegación de tres botones. Con gestos no siempre llega, y para eso está
     * [watchPlayerForPictureInPicture].
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayingVideo()) enterPictureInPicture()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPictureInPicture = isInPictureInPictureMode
    }

    private fun enterPictureInPicture() {
        if (!supportsPictureInPicture) return
        runCatching { enterPictureInPictureMode(pictureInPictureParams(autoEnter = false)) }
    }

    /**
     * Android 12 en adelante entra en la ventana flotante al deslizar hacia el inicio, pero solo
     * si se le avisa POR ADELANTADO de que la app quiere eso. Aquí le decimos "sí" mientras haya
     * un video sonando y "no" en cuanto deje de haberlo, para que una canción no abra una
     * ventanita negra.
     */
    private fun watchPlayerForPictureInPicture() {
        if (!supportsPictureInPicture || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        lifecycleScope.launch {
            playerViewModel.player.collect { player ->
                player ?: return@collect
                updateAutoEnter()
                // onEvents agrupa todos los cambios del reproductor: empezar, pausar, cambiar de
                // pista. Cualquiera de ellos puede alterar si conviene la ventana flotante.
                player.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = updateAutoEnter()
                })
            }
        }
    }

    private fun updateAutoEnter() {
        runCatching { setPictureInPictureParams(pictureInPictureParams(autoEnter = isPlayingVideo())) }
    }

    private fun pictureInPictureParams(autoEnter: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(autoEnter)
            }
            .build()

    private fun isPlayingVideo(): Boolean {
        val player = playerViewModel.player.value ?: return false
        return player.isPlaying &&
            player.currentMediaItem?.mediaMetadata?.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO
    }
}
