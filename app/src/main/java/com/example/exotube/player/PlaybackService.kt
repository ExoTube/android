package com.example.exotube.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.exotube.MainActivity
import com.example.exotube.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Dueño del reproductor. Vive como servicio para que la música siga sonando con la pantalla
 * apagada o la app cerrada. Media3 se encarga solo de la notificación con controles, la pantalla
 * de bloqueo, los auriculares Bluetooth y el "foco de audio" (pausar si entra una llamada).
 *
 * La UI nunca toca este ExoPlayer directamente: se conecta con un MediaController (PlayerViewModel).
 */
@OptIn(UnstableApi::class) // DefaultMediaNotificationProvider y los BitmapLoader son API "inestable" de Media3
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var artworkLoader: MediaFileBitmapLoader? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pausa si se desconectan los auriculares
            .build()

        // Tocar la notificación abre la app.
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val loader = MediaFileBitmapLoader(this).also { artworkLoader = it }
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            // Carátulas sacadas del propio archivo; CacheBitmapLoader evita recalcular la misma.
            .setBitmapLoader(CacheBitmapLoader(loader))
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_audio)
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Si el usuario cierra la app desde "Recientes" y no está sonando nada, el servicio se detiene. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (mediaSession?.player?.playWhenReady != true) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        artworkLoader?.release()
        artworkLoader = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        /**
         * Los controladores envían los MediaItem; aquí decidimos qué se reproduce de verdad.
         * Solo aceptamos pedidos de nuestra propia app (el servicio es visible para otras apps,
         * p. ej. la del reloj o el coche) y reconstruimos la Uri a partir del mediaId.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            if (controller.packageName != packageName) return Futures.immediateFuture(emptyList())
            return Futures.immediateFuture(mediaItems.map { it.buildUpon().setUri(it.mediaId).build() })
        }
    }
}
