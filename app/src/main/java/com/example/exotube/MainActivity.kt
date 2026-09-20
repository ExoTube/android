package com.example.exotube

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.exotube.player.PlayerViewModel
import com.example.exotube.playlist.PlaylistsViewModel
import com.example.exotube.ui.AppShell
import com.example.exotube.ui.theme.ExoTubeTheme

/** Única Activity de la app principal: las pantallas son destinos de navegación dentro de ella. */
class MainActivity : ComponentActivity() {

    // Viven a nivel de Activity porque los usan varias pantallas a la vez.
    private val playerViewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory }
    private val playlistsViewModel: PlaylistsViewModel by viewModels { PlaylistsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app siempre es oscura: iconos claros en las barras del sistema, aunque el teléfono
        // esté en modo claro (si no, serían negros sobre negro e invisibles).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            ExoTubeTheme {
                AppShell(playerViewModel, playlistsViewModel)
            }
        }
    }
}
