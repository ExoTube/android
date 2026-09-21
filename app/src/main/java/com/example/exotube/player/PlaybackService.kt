package com.example.exotube.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
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

    /**
     * Repeticiones que quedan por hacer: 0 = sin bucle, -1 = para siempre.
     * La cuenta vive aquí, y no en la pantalla, porque la pantalla puede cerrarse mientras la
     * música sigue sonando: si la llevara ella, "repetir 2 veces" se volvería infinito.
     */
    private var repeatsLeft = 0

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
        player.addListener(RepeatCountdown())

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

    /** Activa el bucle que pidió la pantalla y publica cuántas repeticiones quedan. */
    private fun applyRepeatPlan(plan: RepeatPlan) {
        repeatsLeft = plan.repeats
        mediaSession?.player?.repeatMode =
            if (plan == RepeatPlan.OFF) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        publishRepeatsLeft()
    }

    /** Los "extras" de la sesión son un Bundle compartido: al cambiarlo, la pantalla se entera. */
    private fun publishRepeatsLeft() {
        mediaSession?.setSessionExtras(RepeatSession.bundleOfRepeats(repeatsLeft))
    }

    /** Descuenta una repetición cada vez que la canción vuelve a empezar por el bucle. */
    private inner class RepeatCountdown : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Solo cuentan las vueltas del bucle: pasar a la siguiente canción o elegir otra, no.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            if (repeatsLeft <= 0) return // 0 = sin bucle; -1 = para siempre, no hay nada que contar
            repeatsLeft--
            if (repeatsLeft == 0) mediaSession?.player?.repeatMode = Player.REPEAT_MODE_OFF
            publishRepeatsLeft()
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        /**
         * Además de los comandos estándar (play, pausa, siguiente…), damos permiso al controlador
         * para usar el nuestro. Si no se declara aquí, Media3 lo rechaza.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(RepeatSession.COMMAND_SET_PLAN, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .setSessionExtras(RepeatSession.bundleOfRepeats(repeatsLeft)) // estado inicial
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != RepeatSession.COMMAND_SET_PLAN) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            applyRepeatPlan(RepeatSession.planOf(args))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * Los controladores envían los MediaItem; aquí decidimos qué se reproduce de verdad.
         * Solo aceptamos pedidos de nuestra propia app (el servicio es visible para otras apps,
         * p. ej. la del reloj o el coche).
         *
         * La Uri no sobrevive al viaje entre la app y el servicio (son procesos distintos para
         * Android), pero requestMetadata sí: de ahí sale la dirección de un video en línea. En un
         * archivo del teléfono no hace falta, porque su mediaId ya ES su Uri.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            if (controller.packageName != packageName) return Futures.immediateFuture(emptyList())
            return Futures.immediateFuture(
                mediaItems.map { item ->
                    item.buildUpon().setUri(item.requestMetadata.mediaUri ?: item.mediaId.toUri()).build()
                },
            )
        }
    }
}
