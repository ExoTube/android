package com.example.exotube.data.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.example.exotube.domain.model.LibraryVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Las preferencias de la pantalla de Ajustes (el tema de colores y el filtro de la biblioteca) y
 * qué partes del tutorial se han visto ya.
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

    private companion object {
        const val KEY_THEME = "tema"
        const val KEY_MIN_AUDIO_SECONDS = "duracion_minima"
        const val KEY_HIDE_VOICE_NOTES = "ocultar_notas_de_voz"
        const val KEY_SEEN_TOURS = "tutorial_visto"
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
