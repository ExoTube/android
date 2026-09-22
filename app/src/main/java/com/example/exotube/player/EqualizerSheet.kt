package com.example.exotube.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exotube.ExoTubeApp
import com.example.exotube.R
import com.example.exotube.ui.theme.ExoTubeTheme
import java.util.Locale

/**
 * Conecta el ecualizador con su pantalla.
 *
 * Sin ViewModel a propósito: [AudioEffects] ya vive en el contenedor de dependencias de la app
 * (tiene que vivir ahí, porque los efectos los aplica el servicio de reproducción) y sobrevive
 * de sobra a esta hoja. Un ViewModel aquí solo reenviaría llamadas.
 */
@Composable
fun EqualizerRoute(onDismiss: () -> Unit) {
    val effects = (LocalContext.current.applicationContext as ExoTubeApp).container.audioEffects
    val state by effects.state.collectAsStateWithLifecycle()

    EqualizerSheet(
        state = state,
        onEnabledChange = effects::setEnabled,
        onBandChange = effects::setBandLevel,
        onPresetSelected = effects::applyPreset,
        onLoudnessChange = effects::setExtraLoudness,
        onReset = effects::reset,
        onDismiss = onDismiss,
    )
}

/** Hoja "tonta": solo dibuja el estado que recibe, así se puede previsualizar sin reproductor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    state: AudioEffectsState,
    onEnabledChange: (Boolean) -> Unit,
    onBandChange: (bandIndex: Int, levelMb: Int) -> Unit,
    onPresetSelected: (Int) -> Unit,
    onLoudnessChange: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.equalizer_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (state.isAvailable) {
                    Switch(
                        checked = state.isEnabled,
                        onCheckedChange = onEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            if (!state.isAvailable) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.equalizer_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
                return@Column
            }

            if (state.presetNames.isNotEmpty()) {
                SectionTitle(R.string.equalizer_presets)
                PresetRow(state.presetNames, state.isEnabled, onPresetSelected)
            }

            SectionTitle(R.string.equalizer_bands)
            state.bands.forEach { band ->
                BandSlider(
                    band = band,
                    minLevelMb = state.minLevelMb,
                    maxLevelMb = state.maxLevelMb,
                    enabled = state.isEnabled,
                    onChange = { onBandChange(band.index, it) },
                )
            }

            SectionTitle(R.string.equalizer_loudness)
            Text(
                text = stringResource(R.string.equalizer_loudness_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LevelSlider(
                label = decibels(state.extraLoudnessMb),
                value = state.extraLoudnessMb.toFloat(),
                valueRange = 0f..MAX_LOUDNESS_MB,
                enabled = state.isEnabled,
                onChange = { onLoudnessChange(it.toInt()) },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReset, enabled = state.isEnabled) {
                    Text(stringResource(R.string.equalizer_reset))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.equalizer_close)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun PresetRow(names: List<String>, enabled: Boolean, onSelected: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(names) { index, name ->
            FilterChip(
                selected = false,
                enabled = enabled,
                onClick = { onSelected(index) },
                label = { Text(name) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun BandSlider(
    band: EqualizerBand,
    minLevelMb: Int,
    maxLevelMb: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = frequencyLabel(band.centerFrequencyHz),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        LevelSlider(
            label = decibels(band.levelMb),
            value = band.levelMb.toFloat(),
            valueRange = minLevelMb.toFloat()..maxLevelMb.toFloat(),
            enabled = enabled,
            onChange = { onChange(it.toInt()) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LevelSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}

/** 60 Hz se queda en hercios; 3600 Hz se lee mejor como "3,6 kHz". */
@Composable
private fun frequencyLabel(hz: Int): String = if (hz < 1_000) {
    stringResource(R.string.equalizer_band_hz, hz)
} else {
    val khz = "%.1f".format(Locale.getDefault(), hz / 1_000f).removeSuffix(decimalZero())
    stringResource(R.string.equalizer_band_khz, khz)
}

/** "14,0" sobra un "0": devuelve el separador decimal del idioma seguido de cero para quitarlo. */
private fun decimalZero(): String = "%.1f".format(Locale.getDefault(), 1f).drop(1)

/** Los milibelios son la unidad de Android; la gente entiende decibelios: 300 mB = "+3 dB". */
@Composable
private fun decibels(levelMb: Int): String {
    val db = levelMb / 100f
    val text = if (db == 0f) "0" else "%+.0f".format(Locale.getDefault(), db)
    return stringResource(R.string.equalizer_level_db, text)
}

private const val MAX_LOUDNESS_MB = 1_200f

// --- Preview ---

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun EqualizerSheetPreview() {
    ExoTubeTheme {
        EqualizerSheet(
            state = AudioEffectsState(
                isAvailable = true,
                isEnabled = true,
                bands = listOf(
                    EqualizerBand(0, 60, 600),
                    EqualizerBand(1, 230, 200),
                    EqualizerBand(2, 910, 0),
                    EqualizerBand(3, 3_600, -200),
                    EqualizerBand(4, 14_000, 400),
                ),
                minLevelMb = -1_500,
                maxLevelMb = 1_500,
                presetNames = listOf("Normal", "Clásica", "Dance", "Rock"),
                extraLoudnessMb = 300,
            ),
            onEnabledChange = {},
            onBandChange = { _, _ -> },
            onPresetSelected = {},
            onLoudnessChange = {},
            onReset = {},
            onDismiss = {},
        )
    }
}
