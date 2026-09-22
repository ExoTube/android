package com.example.exotube.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import com.example.exotube.MainActivity
import com.example.exotube.R
import com.example.exotube.player.PlaybackService
import com.example.exotube.ui.formatDuration

/**
 * El widget "tocadiscos" de la pantalla de inicio.
 *
 * Android llama a esta clase cuando alguien añade el widget o cuando hay que redibujarlo. Lo que
 * suena, en cambio, lo sabe el servicio de reproducción: por eso, si está en marcha, es él quien
 * lo pinta ([NowPlayingWidgetUpdater]); si no, el widget se queda en "Nada sonando".
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val updater = NowPlayingWidgetUpdater.active
        if (updater != null) {
            updater.refresh()
        } else {
            manager.updateEach(appWidgetIds) { layout -> WidgetViews.idle(context, layout) }
        }
    }

    /**
     * El usuario cambió el tamaño del widget: puede que ahora quepa la otra versión (normal o
     * compacta), así que se vuelve a pintar.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        onUpdate(context, manager, intArrayOf(appWidgetId))
    }

    companion object {
        /**
         * Deja el widget en "Nada sonando". Se llama al arrancar la app: si Android la cerró de
         * golpe, el servicio no pudo avisar y el widget seguiría enseñando la última canción,
         * con el disco girando aunque ya no sonara nada.
         */
        fun resetToIdle(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            manager.updateEach(ids) { layout -> WidgetViews.idle(context, layout) }
        }
    }
}

/** Lo que el widget enseña de lo que suena, ya sacado del reproductor. */
internal data class WidgetSnapshot(
    val title: String,
    val artist: String?,
    /** El disco gira solo cuando de verdad sale sonido (no mientras carga). */
    val isSpinning: Boolean,
    /** El brazo va sobre el disco y el botón muestra "pausa" cuando el usuario quiere que suene. */
    val wantsToPlay: Boolean,
    val positionMs: Long,
    /** null mientras no se sabe cuánto dura (un video en línea que aún está cargando). */
    val durationMs: Long?,
)

/** La carátula ya preparada para el widget (ver [WidgetArt]). */
internal class WidgetArtwork(val cover: Bitmap, val backdrop: Bitmap)

/**
 * Rellena la plantilla del widget.
 *
 * Un widget no se modifica: cada vez se manda una plantilla nueva ([RemoteViews]) con los
 * cambios apuntados, y el lanzador la aplica. Por eso aquí solo hay funciones que construyen.
 */
internal object WidgetViews {

    /** Nada sonando: disco quieto, brazo levantado, y cualquier toque abre la app. */
    fun idle(context: Context, layout: WidgetLayout): RemoteViews = RemoteViews(context.packageName, layout.resId).apply {
        setTextViewText(R.id.widget_title, context.getString(R.string.widget_idle_title))
        setTextViewText(R.id.widget_artist, context.getString(R.string.widget_idle_subtitle))
        showDisc(isSpinning = false, armDown = false)
        val openApp = openApp(context)
        listOf(
            android.R.id.background,
            R.id.widget_previous,
            R.id.widget_play_pause,
            R.id.widget_next,
        ).forEach { setOnClickPendingIntent(it, openApp) }
    }

    fun nowPlaying(
        context: Context,
        layout: WidgetLayout,
        snapshot: WidgetSnapshot,
        artwork: WidgetArtwork?,
    ): RemoteViews =
        RemoteViews(context.packageName, layout.resId).apply {
            setTextViewText(R.id.widget_title, snapshot.title)
            if (snapshot.artist.isNullOrBlank()) {
                setViewVisibility(R.id.widget_artist, View.GONE)
            } else {
                setTextViewText(R.id.widget_artist, snapshot.artist)
            }
            showDisc(isSpinning = snapshot.isSpinning, armDown = snapshot.wantsToPlay)
            showProgress(snapshot)

            artwork?.let {
                setImageViewBitmap(R.id.widget_cover, it.cover)
                // En Android 11 y anteriores el fondo no respeta las esquinas redondeadas del
                // widget y asomaría en punta: ahí se queda el fondo liso.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setImageViewBitmap(R.id.widget_backdrop, it.backdrop)
                }
            }

            setImageViewResource(
                R.id.widget_play_pause,
                if (snapshot.wantsToPlay) R.drawable.ic_pause else R.drawable.ic_play,
            )
            setContentDescription(
                R.id.widget_play_pause,
                context.getString(if (snapshot.wantsToPlay) R.string.widget_pause else R.string.widget_play),
            )

            setOnClickPendingIntent(android.R.id.background, openApp(context))
            setOnClickPendingIntent(
                R.id.widget_play_pause,
                // Solo "reproducir" puede tener que arrancar el servicio en primer plano.
                mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, startsPlayback = !snapshot.wantsToPlay),
            )
            setOnClickPendingIntent(
                R.id.widget_previous,
                mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, startsPlayback = false),
            )
            setOnClickPendingIntent(
                R.id.widget_next,
                mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT, startsPlayback = false),
            )
        }

    /** Solo la barra y los tiempos: lo que cambia cada segundo, sin volver a mandar imágenes. */
    fun progress(context: Context, layout: WidgetLayout, snapshot: WidgetSnapshot): RemoteViews =
        RemoteViews(context.packageName, layout.resId).apply { showProgress(snapshot) }

    private fun RemoteViews.showProgress(snapshot: WidgetSnapshot) {
        setProgressBar(R.id.widget_progress, PROGRESS_MAX, progressOf(snapshot.positionMs, snapshot.durationMs), false)
        setTextViewText(R.id.widget_position, formatDuration(snapshot.positionMs / 1_000))
        setTextViewText(R.id.widget_duration, snapshot.durationMs?.let { formatDuration(it / 1_000) } ?: "--:--")
    }

    /**
     * El disco que gira y el quieto son dos vistas distintas (ver el layout): se enseña una u
     * otra. El brazo baja sobre el disco al darle a reproducir y se aparta al pausar.
     */
    private fun RemoteViews.showDisc(isSpinning: Boolean, armDown: Boolean) {
        setViewVisibility(R.id.widget_disc_spinning, if (isSpinning) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_disc_still, if (isSpinning) View.INVISIBLE else View.VISIBLE)
        setImageViewResource(
            R.id.widget_tonearm,
            if (armDown) R.drawable.widget_tonearm_on else R.drawable.widget_tonearm_off,
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Un botón del widget, convertido en la misma pulsación que mandan unos auriculares o la
     * notificación ("reproducir/pausa", "siguiente"...). El servicio de reproducción ya sabe
     * atenderlas, así que el widget no necesita ningún código propio para controlar la música.
     *
     * Con [startsPlayback], Android tiene que dejar que el servicio pase a primer plano (el de
     * la notificación), porque va a empezar a sonar con la app cerrada.
     */
    private fun mediaButton(context: Context, keyCode: Int, startsPlayback: Boolean): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, PlaybackService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        val requestCode = REQUEST_MEDIA_BUTTON_BASE + keyCode
        return if (startsPlayback) {
            PendingIntent.getForegroundService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
        }
    }

    private const val PROGRESS_MAX = 1_000
    private const val REQUEST_OPEN_APP = 7_000
    private const val REQUEST_MEDIA_BUTTON_BASE = 7_100
}

/**
 * Pinta cada widget con la versión que cabe en su hueco: uno puede estar en 3x3 y otro en 4x4 a
 * la vez, así que no se les puede mandar a todos lo mismo.
 */
internal fun AppWidgetManager.updateEach(ids: IntArray, build: (WidgetLayout) -> RemoteViews) {
    ids.forEach { id -> updateAppWidget(id, build(WidgetLayout.forWidget(this, id))) }
}

/**
 * Como [updateEach], pero solo con lo que cambia. Tiene que usar la misma versión que ya tiene
 * cada widget: Android no deja aplicar cambios de una versión sobre la otra.
 */
internal fun AppWidgetManager.partiallyUpdateEach(ids: IntArray, build: (WidgetLayout) -> RemoteViews) {
    ids.forEach { id -> partiallyUpdateAppWidget(id, build(WidgetLayout.forWidget(this, id))) }
}

/**
 * Cuánto de la barra va lleno, de 0 a 1000. Mil pasos y no cien: en una canción de cuatro
 * minutos, cien pasos harían que la barra avanzara a saltos de más de dos segundos.
 */
internal fun progressOf(positionMs: Long, durationMs: Long?): Int {
    if (durationMs == null || durationMs <= 0) return 0
    return (positionMs.coerceIn(0, durationMs) * 1_000 / durationMs).toInt()
}
