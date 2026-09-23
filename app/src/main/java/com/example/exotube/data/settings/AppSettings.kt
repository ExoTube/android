package com.example.exotube.data.settings

import android.content.Context
import androidx.core.content.edit
import com.example.exotube.domain.model.LibraryVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Las preferencias de la pantalla de Ajustes: el tema de colores y el filtro de la biblioteca.
 *
 * Se guardan en SharedPreferences (un archivo pequeño dentro de la app) y además se exponen como
 * StateFlow: así, al mover la barra de duración o elegir un tema, las pantallas que lo usan se
 * enteran solas y se redibujan al momento, sin reiniciar la app.
 */
class AppSettings(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

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
    }
}
