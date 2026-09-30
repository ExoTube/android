package com.example.exotube.data.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.example.exotube.domain.model.LibraryVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Las preferencias de la pantalla de Ajustes (el tema de colores, el fondo propio y el filtro de la
 * biblioteca) y qué partes del tutorial se han visto ya.
 *
 * Se guardan en SharedPreferences (un archivo pequeño dentro de la app) y además se exponen como
 * StateFlow: así, al mover la barra de duración o elegir un tema, las pantallas que lo usan se
 * enteran solas y se redibujan al momento, sin reiniciar la app.
 */
class AppSettings(context: Context, allTourIds: Set<String>) {

    private val preferences =
        context.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private val _seenTours = MutableStateFlow(
        preferences.getStringSet(KEY_SEEN_TOURS, null)?.toSet()
            ?: initialSeenTours(context.hasBeenUpdated(), allTourIds).also(::saveSeenTours),
    )

    /** Los recorridos del tutorial que ya se vieron (o se saltaron): no vuelven a salir. */
    val seenTours: StateFlow<Set<String>> = _seenTours.asStateFlow()

    fun markTourSeen(id: String) {
        val updated = _seenTours.value + id
        saveSeenTours(updated)
        _seenTours.value = updated
    }

    /** "Ver el tutorial otra vez": cada pantalla vuelve a explicarse al entrar. */
    fun resetTours() {
        saveSeenTours(emptySet())
        _seenTours.value = emptySet()
    }

    private fun saveSeenTours(ids: Set<String>) {
        // Siempre un conjunto nuevo: Android no admite que se modifique el que devolvió.
        preferences.edit { putStringSet(KEY_SEEN_TOURS, ids.toHashSet()) }
    }

    private val _themeId = MutableStateFlow(preferences.getString(KEY_THEME, null))

    /** El id del tema elegido, o null si nunca se eligió (se usa el verde de siempre). */
    val themeId: StateFlow<String?> = _themeId.asStateFlow()

    private val _libraryVisibility = MutableStateFlow(
        LibraryVisibility(
            minAudioSeconds = preferences.getInt(KEY_MIN_AUDIO_SECONDS, LibraryVisibility.DEFAULT_MIN_AUDIO_SECONDS),
            hideVoiceNotes = preferences.getBoolean(KEY_HIDE_VOICE_NOTES, true),
        ),
    )
    val libraryVisibility: StateFlow<LibraryVisibility> = _libraryVisibility.asStateFlow()

    fun setTheme(id: String) {
        preferences.edit { putString(KEY_THEME, id) }
        _themeId.value = id
    }

    fun setLibraryVisibility(visibility: LibraryVisibility) {
        preferences.edit {
            putInt(KEY_MIN_AUDIO_SECONDS, visibility.minAudioSeconds)
            putBoolean(KEY_HIDE_VOICE_NOTES, visibility.hideVoiceNotes)
        }
        _libraryVisibility.value = visibility
    }

    private val _shares = MutableStateFlow(preferences.getInt(KEY_SHARES, 0))

    /** Cuántas veces se ha compartido ExoTube desde Ajustes: con [SHARES_TO_UNLOCK] se desbloquea el fondo propio. */
    val shares: StateFlow<Int> = _shares.asStateFlow()

    fun addShare() {
        val updated = _shares.value + 1
        preferences.edit { putInt(KEY_SHARES, updated) }
        _shares.value = updated
    }

    /**
     * Se abrió el menú de compartir: se apunta cuándo y cuántas veces se llevaba compartido.
     * Se guarda en disco y no en memoria porque, mientras la persona está en WhatsApp, Android
     * puede cerrar ExoTube para liberar memoria.
     */
    fun markShareStarted(now: Long) = preferences.edit {
        putLong(KEY_SHARE_STARTED_AT, now)
        putInt(KEY_SHARES_AT_START, _shares.value)
    }

    /** Se volvió del menú de compartir: si el aviso de Android no llegó, se decide con [countsAsShared]. */
    fun onShareReturned(now: Long) {
        val startedAt = preferences.getLong(KEY_SHARE_STARTED_AT, -1L)
        val sharesAtStart = preferences.getInt(KEY_SHARES_AT_START, -1)
        preferences.edit {
            remove(KEY_SHARE_STARTED_AT)
            remove(KEY_SHARES_AT_START)
        }
        if (countsAsShared(startedAt, sharesAtStart, _shares.value, now)) addShare()
    }

    private val _customBackground = MutableStateFlow(readCustomBackground())

    /** El fondo propio (sus colores y cuánto se oscurece), o null si todavía no se eligió imagen. */
    val customBackground: StateFlow<CustomBackground?> = _customBackground.asStateFlow()

    /** Una imagen nueva: sus colores, y una versión nueva para que se vuelva a cargar. */
    fun setCustomBackground(primary: Int, secondary: Int, tertiary: Int) {
        val updated = CustomBackground(
            primary, secondary, tertiary,
            dim = _customBackground.value?.dim ?: CustomBackground.DEFAULT_DIM,
            version = System.currentTimeMillis(),
        )
        saveCustomBackground(updated)
        _customBackground.value = updated
    }

    fun setBackgroundDim(dim: Float) {
        val updated = _customBackground.value?.copy(dim = dim.coerceIn(CustomBackground.MIN_DIM, CustomBackground.MAX_DIM)) ?: return
        saveCustomBackground(updated)
        _customBackground.value = updated
    }

    private fun readCustomBackground(): CustomBackground? {
        if (!preferences.contains(KEY_BG_VERSION)) return null
        return CustomBackground(
            primary = preferences.getInt(KEY_BG_PRIMARY, 0),
            secondary = preferences.getInt(KEY_BG_SECONDARY, 0),
            tertiary = preferences.getInt(KEY_BG_TERTIARY, 0),
            dim = preferences.getFloat(KEY_BG_DIM, CustomBackground.DEFAULT_DIM),
            version = preferences.getLong(KEY_BG_VERSION, 0L),
        )
    }

    private fun saveCustomBackground(background: CustomBackground) = preferences.edit {
        putInt(KEY_BG_PRIMARY, background.primary)
        putInt(KEY_BG_SECONDARY, background.secondary)
        putInt(KEY_BG_TERTIARY, background.tertiary)
        putFloat(KEY_BG_DIM, background.dim)
        putLong(KEY_BG_VERSION, background.version)
    }

    companion object {
        /** El "tema" del fondo propio: se guarda como un id más, igual que los de la lista. */
        const val CUSTOM_THEME_ID = "fondo"

        /** Cuántas veces hay que compartir ExoTube para desbloquear el fondo propio. */
        const val SHARES_TO_UNLOCK = 2

        fun isBackgroundUnlocked(shares: Int): Boolean = shares >= SHARES_TO_UNLOCK

        /**
         * ¿Se compartió, aunque Android no lo haya avisado?
         *
         * El aviso "eligió WhatsApp" solo llega con el menú de compartir original de Android;
         * los de muchas marcas (Samsung, Xiaomi...) no lo mandan. Para esos, cuenta haber salido
         * a otra app y vuelto al menos [MIN_AWAY_MS] después: cerrar el menú sin elegir nada es
         * cosa de un segundo. Si el aviso sí llegó, las veces ya subieron y no se cuenta dos veces.
         *
         * [startedAt] y [now] son del reloj que no se atrasa ni se adelanta (desde que se encendió
         * el teléfono); si [now] es menor, el teléfono se reinició entre medias y no se cuenta.
         */
        fun countsAsShared(startedAt: Long, sharesAtStart: Int, sharesNow: Int, now: Long): Boolean =
            startedAt >= 0 && sharesAtStart >= 0 && sharesNow == sharesAtStart &&
                now >= startedAt && now - startedAt >= MIN_AWAY_MS

        /** Lo mínimo fuera de ExoTube para dar por hecho que se compartió. */
        const val MIN_AWAY_MS = 4_000L

        private const val KEY_SHARES = "veces_compartido"
        private const val KEY_SHARE_STARTED_AT = "compartir_desde"
        private const val KEY_SHARES_AT_START = "compartir_veces_antes"
        private const val KEY_BG_PRIMARY = "fondo_color_1"
        private const val KEY_BG_SECONDARY = "fondo_color_2"
        private const val KEY_BG_TERTIARY = "fondo_color_3"
        private const val KEY_BG_DIM = "fondo_oscuro"
        private const val KEY_BG_VERSION = "fondo_version"
        private const val KEY_THEME = "tema"
        private const val KEY_MIN_AUDIO_SECONDS = "duracion_minima"
        private const val KEY_HIDE_VOICE_NOTES = "ocultar_notas_de_voz"
        private const val KEY_SEEN_TOURS = "tutorial_visto"
    }
}

/**
 * Qué partes del tutorial se dan por vistas la primera vez que arranca esta versión.
 *
 * Es para quien acaba de descargar la app: si ya venía usando ExoTube (se actualizó desde una
 * versión anterior, que no tenía tutorial), ya sabe manejarla y sería un fastidio que de pronto
 * cada pantalla se pusiera a explicarse. A esa persona se le da todo por visto; si quiere verlo,
 * está en Ajustes.
 */
internal fun initialSeenTours(hasBeenUpdated: Boolean, allTourIds: Set<String>): Set<String> =
    if (hasBeenUpdated) allTourIds else emptySet()

/**
 * ¿Esta app se instaló de cero o viene de una versión anterior?
 *
 * Android guarda las dos fechas; si no coinciden, es que en algún momento se actualizó. Es la
 * única forma de saberlo para quien viene de la 1.1, que no dejaba ninguna pista guardada.
 */
internal fun Context.hasBeenUpdated(): Boolean = try {
    val info = packageManager.getPackageInfo(packageName, 0)
    info.lastUpdateTime > info.firstInstallTime
} catch (_: PackageManager.NameNotFoundException) {
    false // no puede pasar (nos preguntamos por nosotros mismos), pero no vale la pena arriesgar
}
