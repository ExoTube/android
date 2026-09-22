package com.example.exotube.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mantiene el widget al día con lo que suena. Vive dentro del servicio de reproducción, que es
 * quien sabe qué suena aunque la app esté cerrada.
 *
 * Qué manda y cuándo, para no gastar batería:
 *  - Todo (textos, botones, carátula) solo cuando algo cambia: otra canción, pausa, etc.
 *  - La barra y el tiempo, una vez por segundo, y solo mientras suena.
 *  - Nada de nada si no hay ningún widget puesto en la pantalla de inicio.
 *
 * El giro del disco no cuesta nada: lo anima el lanzador por su cuenta (ver el layout).
 */
@OptIn(UnstableApi::class) // MediaSession.bitmapLoader y Util.shouldShowPlayButton
internal class NowPlayingWidgetUpdater(
    context: Context,
    private val session: MediaSession,
) : Player.Listener {

    private val context = context.applicationContext
    private val manager = AppWidgetManager.getInstance(this.context)
    private val component = ComponentName(this.context, NowPlayingWidget::class.java)
    private val scope = MainScope()

    /** De qué canción es la carátula que hay en [artwork], para no volver a prepararla. */
    private var artworkKey: Any? = null
    private var artwork: WidgetArtwork? = null
    private var tickJob: Job? = null

    fun start() {
        session.player.addListener(this)
        active = this
        refresh()
    }

    /** El servicio se apaga: el widget vuelve a "Nada sonando", con botones que abren la app. */
    fun stop() {
        session.player.removeListener(this)
        scope.cancel()
        if (active === this) active = null
        manager.updateEach(widgetIds()) { layout -> WidgetViews.idle(context, layout) }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(*REDRAW_EVENTS)) refresh()
    }

    /** Vuelve a pintar el widget entero con lo que suena ahora. */
    fun refresh() {
        val ids = widgetIds()
        if (ids.isEmpty()) {
            stopTicking()
            return
        }
        val player = session.player
        if (player.currentMediaItem == null) {
            stopTicking()
            manager.updateEach(ids) { layout -> WidgetViews.idle(context, layout) }
            return
        }
        prepareArtwork(player.mediaMetadata, player.currentMediaItem?.mediaId)
        val snapshot = snapshot(player)
        manager.updateEach(ids) { layout -> WidgetViews.nowPlaying(context, layout, snapshot, artwork) }
        if (player.isPlaying) startTicking() else stopTicking()
    }

    /**
     * Prepara la carátula de lo que suena, si es otra canción.
     *
     * Se carga con el mismo cargador que usa la notificación (que sabe sacar la portada de dentro
     * de un mp3 o bajar la miniatura de YouTube), y se recorta fuera del hilo principal. Mientras
     * tanto el widget se pinta sin carátula, en vez de dejar la de la canción anterior.
     */
    private fun prepareArtwork(metadata: MediaMetadata, mediaId: String?) {
        val key = metadata.artworkUri ?: metadata.artworkData?.contentHashCode() ?: mediaId
        if (key == artworkKey) return
        artworkKey = key
        artwork = null

        val future = session.bitmapLoader.loadBitmapFromMetadata(metadata) ?: return
        future.addListener(
            {
                val bitmap = runCatching { future.get() }
                    .onFailure { Log.w(TAG, "Sin carátula para el widget", it) }
                    .getOrNull() ?: return@addListener
                scope.launch {
                    val prepared = withContext(Dispatchers.Default) {
                        runCatching { WidgetArtwork(WidgetArt.roundCover(bitmap), WidgetArt.blurredBackdrop(bitmap)) }
                            .getOrNull()
                    }
                    // Si mientras tanto cambió la canción, esta carátula ya no pinta nada.
                    if (prepared != null && artworkKey == key) {
                        artwork = prepared
                        refresh()
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val ids = widgetIds()
                if (ids.isEmpty()) break
                val snapshot = snapshot(session.player)
                manager.partiallyUpdateEach(ids) { layout -> WidgetViews.progress(context, layout, snapshot) }
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun widgetIds(): IntArray = manager.getAppWidgetIds(component)

    private fun snapshot(player: Player) = WidgetSnapshot(
        title = player.mediaMetadata.title?.toString().orEmpty(),
        artist = player.mediaMetadata.artist?.toString(),
        isSpinning = player.isPlaying,
        wantsToPlay = !Util.shouldShowPlayButton(player),
        positionMs = player.currentPosition,
        durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 },
    )

    companion object {
        private const val TAG = "NowPlayingWidget"
        private const val TICK_MS = 1_000L

        private val REDRAW_EVENTS = intArrayOf(
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_MEDIA_METADATA_CHANGED,
            Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_POSITION_DISCONTINUITY,
        )

        /**
         * El que está en marcha, si el servicio de reproducción está vivo. Lo consulta el widget
         * cuando Android le pide redibujarse (por ejemplo, al añadir uno nuevo con música sonando).
         * Todo ocurre en el hilo principal, así que no hacen falta más precauciones.
         */
        var active: NowPlayingWidgetUpdater? = null
            private set
    }
}
