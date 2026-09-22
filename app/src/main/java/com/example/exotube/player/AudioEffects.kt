package com.example.exotube.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Una banda del ecualizador: los graves, los medios, los agudos… */
data class EqualizerBand(
    val index: Int,
    /** Frecuencia central en hercios: 60 Hz son graves; 14 000 Hz, agudos. */
    val centerFrequencyHz: Int,
    /** Cuánto se sube o baja esta banda, en milibelios (100 = 1 decibelio). */
    val levelMb: Int,
)

data class AudioEffectsState(
    /** false si el teléfono no ofrece ecualizador: hay modelos que no lo traen. */
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    /** Hasta dónde se puede subir o bajar cada banda, en milibelios. */
    val minLevelMb: Int = 0,
    val maxLevelMb: Int = 0,
    /** Ajustes ya hechos que trae el propio teléfono ("Rock", "Pop"…). */
    val presetNames: List<String> = emptyList(),
    /** Volumen extra por encima del máximo, en milibelios. 0 = desactivado. */
    val extraLoudnessMb: Int = 0,
)

/**
 * Ecualizador y realce de volumen.
 *
 * Los efectos de audio de Android no se aplican a "la app", sino a una **sesión de audio**: un
 * número que identifica el flujo de sonido que sale del reproductor. Por eso hay que enganchar
 * los efectos a la sesión concreta de nuestro ExoPlayer ([attachTo]).
 *
 * Vive en el [com.example.exotube.di.AppContainer] porque lo usan dos sitios a la vez:
 * [PlaybackService], que es quien tiene la sesión de audio, y la pantalla del ecualizador.
 *
 * Los ajustes se guardan en el teléfono: al volver a abrir la app siguen puestos.
 */
class AudioEffects(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AudioEffectsState())
    val state: StateFlow<AudioEffectsState> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null

    /**
     * Engancha los efectos al sonido de [audioSessionId] y les aplica lo que el usuario dejó
     * puesto la última vez. Si el teléfono no los soporta, no pasa nada: la música suena igual
     * y la pantalla del ecualizador avisa de que no está disponible.
     */
    fun attachTo(audioSessionId: Int) {
        release()
        try {
            // Prioridad 0 = la normal; si otra app pide el efecto con más prioridad, gana ella.
            equalizer = Equalizer(0, audioSessionId)
            loudness = LoudnessEnhancer(audioSessionId)
        } catch (e: RuntimeException) {
            // Hay teléfonos (y emuladores) sin estos efectos: no es un error que deba romper nada.
            Log.w(TAG, "Este dispositivo no ofrece ecualizador", e)
            release()
            _state.value = AudioEffectsState(isAvailable = false)
            return
        }
        restoreSavedSettings()
        publishState()
    }

    fun release() {
        equalizer?.release()
        equalizer = null
        loudness?.release()
        loudness = null
    }

    fun setEnabled(enabled: Boolean) {
        val equalizer = this.equalizer ?: return
        runCatching {
            equalizer.enabled = enabled
            // El volumen extra solo tiene sentido con el ecualizador encendido.
            loudness?.enabled = enabled && savedExtraLoudness() > 0
        }.onFailure { Log.w(TAG, "No se pudo cambiar el estado del ecualizador", it) }
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        publishState()
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val equalizer = this.equalizer ?: return
        val level = levelMb.coerceIn(equalizer.bandLevelRange[0].toInt(), equalizer.bandLevelRange[1].toInt())
        runCatching { equalizer.setBandLevel(bandIndex.toShort(), level.toShort()) }
            .onFailure { Log.w(TAG, "No se pudo ajustar la banda $bandIndex", it) }
        preferences.edit().putInt(keyForBand(bandIndex), level).apply()
        publishState()
    }

    /** Aplica uno de los ajustes que trae el teléfono y guarda el resultado banda por banda. */
    fun applyPreset(presetIndex: Int) {
        val equalizer = this.equalizer ?: return
        runCatching { equalizer.usePreset(presetIndex.toShort()) }
            .onFailure { Log.w(TAG, "No se pudo aplicar el ajuste $presetIndex", it) }
        saveCurrentBands()
        publishState()
    }

    /** Deja todas las bandas planas: el sonido original, sin retocar. */
    fun reset() {
        val equalizer = this.equalizer ?: return
        runCatching {
            repeat(equalizer.numberOfBands.toInt()) { band ->
                equalizer.setBandLevel(band.toShort(), 0)
            }
        }
        setExtraLoudness(0)
        saveCurrentBands()
        publishState()
    }

    /**
     * Sube el volumen por encima del máximo del teléfono. Pasado cierto punto el sonido se
     * distorsiona, así que está limitado a [MAX_EXTRA_LOUDNESS_MB].
     */
    fun setExtraLoudness(gainMb: Int) {
        val gain = gainMb.coerceIn(0, MAX_EXTRA_LOUDNESS_MB)
        runCatching {
            loudness?.setTargetGain(gain)
            loudness?.enabled = gain > 0 && (equalizer?.enabled == true)
        }.onFailure { Log.w(TAG, "No se pudo ajustar el volumen extra", it) }
        preferences.edit().putInt(KEY_LOUDNESS, gain).apply()
        publishState()
    }

    private fun restoreSavedSettings() {
        val equalizer = this.equalizer ?: return
        runCatching {
            repeat(equalizer.numberOfBands.toInt()) { band ->
                val saved = preferences.getInt(keyForBand(band), 0)
                if (saved != 0) equalizer.setBandLevel(band.toShort(), saved.toShort())
            }
            equalizer.enabled = preferences.getBoolean(KEY_ENABLED, false)
            val gain = savedExtraLoudness()
            loudness?.setTargetGain(gain)
            loudness?.enabled = gain > 0 && equalizer.enabled
        }.onFailure { Log.w(TAG, "No se pudieron restaurar los ajustes guardados", it) }
    }

    private fun saveCurrentBands() {
        val equalizer = this.equalizer ?: return
        val editor = preferences.edit()
        runCatching {
            repeat(equalizer.numberOfBands.toInt()) { band ->
                editor.putInt(keyForBand(band), equalizer.getBandLevel(band.toShort()).toInt())
            }
        }
        editor.apply()
    }

    private fun savedExtraLoudness(): Int = preferences.getInt(KEY_LOUDNESS, 0)

    private fun publishState() {
        val equalizer = this.equalizer
        if (equalizer == null) {
            _state.value = AudioEffectsState(isAvailable = false)
            return
        }
        _state.value = runCatching {
            val range = equalizer.bandLevelRange
            AudioEffectsState(
                isAvailable = true,
                isEnabled = equalizer.enabled,
                bands = (0 until equalizer.numberOfBands).map { band ->
                    EqualizerBand(
                        index = band,
                        // getCenterFreq devuelve milihercios; los pasamos a hercios.
                        centerFrequencyHz = equalizer.getCenterFreq(band.toShort()) / 1_000,
                        levelMb = equalizer.getBandLevel(band.toShort()).toInt(),
                    )
                },
                minLevelMb = range[0].toInt(),
                maxLevelMb = range[1].toInt(),
                presetNames = (0 until equalizer.numberOfPresets).map {
                    equalizer.getPresetName(it.toShort())
                },
                extraLoudnessMb = savedExtraLoudness(),
            )
        }.getOrElse { AudioEffectsState(isAvailable = false) }
    }

    private companion object {
        const val TAG = "AudioEffects"
        const val PREFERENCES = "audio_effects"
        const val KEY_ENABLED = "enabled"
        const val KEY_LOUDNESS = "loudness_mb"

        /** +12 dB; más que esto distorsiona en casi cualquier altavoz de móvil. */
        const val MAX_EXTRA_LOUDNESS_MB = 1_200

        fun keyForBand(index: Int) = "band_$index"
    }
}
