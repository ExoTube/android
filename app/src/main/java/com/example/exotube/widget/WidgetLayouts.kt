package com.example.exotube.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.annotation.LayoutRes
import com.example.exotube.R

/**
 * Qué versión del widget cabe en el hueco que le dio el lanzador.
 *
 * Cada teléfono tiene casillas de un tamaño distinto: en uno, 3x3 casillas son 270 dp de lado y
 * en otro apenas 210. La versión normal necesita 264 dp; si no caben, se usa la compacta, que es
 * la misma pero más pequeña. Así el widget nunca sale cortado.
 */
internal enum class WidgetLayout(@LayoutRes val resId: Int) {
    REGULAR(R.layout.widget_now_playing),
    COMPACT(R.layout.widget_now_playing_compact),
    ;

    companion object {
        /** El lado de la tarjeta normal (ver values/widget_dimens.xml). */
        const val REGULAR_SIDE_DP = 264

        /**
         * La versión para un hueco cuyo lado más corto mide [sideDp]. Si no se sabe (el
         * lanzador aún no lo ha dicho), la compacta: mejor pequeña que cortada.
         */
        fun forSide(sideDp: Int?): WidgetLayout =
            if (sideDp != null && sideDp >= REGULAR_SIDE_DP) REGULAR else COMPACT

        fun forWidget(manager: AppWidgetManager, appWidgetId: Int): WidgetLayout =
            forSide(portraitShortSideDp(manager.getAppWidgetOptions(appWidgetId)))
    }
}

/**
 * El lado más corto del hueco con el teléfono en vertical, que es como se usa casi siempre.
 * Android lo da así: en vertical, el ancho es el MÍNIMO y el alto es el MÁXIMO que informa.
 */
internal fun portraitShortSideDp(options: Bundle?): Int? {
    val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
    val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
    if (width <= 0 || height <= 0) return null
    return minOf(width, height)
}
