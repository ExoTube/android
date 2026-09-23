package com.example.exotube.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.exotube.BuildConfig
import com.example.exotube.R
import com.example.exotube.domain.model.LibraryVisibility
import com.example.exotube.ui.theme.AppTheme
import com.example.exotube.ui.theme.ExoTubeTheme
import com.example.exotube.ui.theme.ThemeGroup
import com.example.exotube.ui.theme.readableOn
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Ajustes: la lista principal
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsRoute(
    onOpenTheme: () -> Unit,
    onOpenLibraryFilter: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state, onOpenTheme, onOpenLibraryFilter, onBack, contentPadding)
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onOpenTheme: () -> Unit,
    onOpenLibraryFilter: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item { SettingsTopBar(stringResource(R.string.settings_title), onBack) }
        item {
            SettingsRow(
                icon = R.drawable.ic_palette,
                title = stringResource(R.string.settings_theme),
                summary = stringResource(state.theme.label),
                onClick = onOpenTheme,
                trailing = { ThemeDots(state.theme) },
            )
        }
        item {
            val minSeconds = state.visibility.minAudioSeconds
            SettingsRow(
                icon = R.drawable.ic_tune,
                title = stringResource(R.string.settings_library_filter),
                summary = if (minSeconds == 0) {
                    stringResource(R.string.settings_filter_summary_off)
                } else {
                    stringResource(R.string.settings_filter_summary, minSeconds)
                },
                onClick = onOpenLibraryFilter,
            )
        }
        item {
            // Qué APK le toca a este teléfono: sirve para descargar el correcto desde la web.
            Text(
                text = stringResource(
                    R.string.settings_version,
                    BuildConfig.VERSION_NAME,
                    stringResource(phoneKindLabel(Build.SUPPORTED_ABIS.toList())),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Los tres acentos de un tema en bolitas: se reconoce de un vistazo sin leer el nombre. */
@Composable
private fun ThemeDots(theme: AppTheme) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp), modifier = Modifier.padding(end = 8.dp)) {
        listOf(theme.primary, theme.secondary, theme.tertiary).forEach { color ->
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(theme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tema de colores
// ---------------------------------------------------------------------------------------------

@Composable
fun ThemeSettingsRoute(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ThemeSettingsScreen(state.theme, viewModel::onThemeSelected, onBack, contentPadding)
}

@Composable
private fun ThemeSettingsScreen(
    selected: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    // Adaptive: dos columnas en un teléfono, más en una tableta o en horizontal.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Lo que no es una tarjeta ocupa la fila entera (maxLineSpan).
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                SettingsTopBar(stringResource(R.string.theme_title), onBack)
                Text(
                    text = stringResource(R.string.theme_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        ThemeGroup.entries.forEach { group ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(group.label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                )
            }
            items(AppTheme.entries.filter { it.group == group }, key = { it.id }) { theme ->
                ThemeCard(theme, isSelected = theme == selected, onClick = { onThemeSelected(theme) })
            }
        }
    }
}

/**
 * Una miniatura del tema pintada con SUS colores (no con los del tema actual): así se ve cómo
 * quedaría la app antes de elegirlo.
 */
@Composable
private fun ThemeCard(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = theme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainer,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(scheme.background),
            ) {
                // Una "portada" con el degradado de los tres acentos.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary, theme.tertiary))),
                )
                // Una fila de canción de mentira: carátula, título y artista.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_play),
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FakeTextLine(scheme.onSurface, 0.8f)
                        FakeTextLine(scheme.onSurfaceVariant, 0.5f)
                    }
                }
                if (isSelected) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.theme_selected),
                        tint = readableOn(theme.primary),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(theme.primary)
                            .padding(3.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = stringResource(theme.label),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(theme.hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FakeTextLine(color: Color, widthFraction: Float) {
    Box(
        Modifier
            .width(80.dp * widthFraction)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

// ---------------------------------------------------------------------------------------------
// Filtro de la biblioteca
// ---------------------------------------------------------------------------------------------

@Composable
fun LibraryFilterRoute(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryFilterScreen(state, viewModel::onVisibilityChange, onBack, contentPadding)
}

@Composable
private fun LibraryFilterScreen(
    state: SettingsUiState,
    onVisibilityChange: (LibraryVisibility) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val saved = state.visibility
    // Mientras se arrastra, el valor vive solo aquí; se guarda al soltar. Así no se reescribe el
    // archivo de ajustes decenas de veces por segundo, y la biblioteca se recalcula una sola vez.
    var dragging by remember(saved.minAudioSeconds) { mutableFloatStateOf(saved.minAudioSeconds.toFloat()) }
    val draft = saved.copy(minAudioSeconds = dragging.roundToInt())

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item { SettingsTopBar(stringResource(R.string.filter_title), onBack) }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.filter_by_duration),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    // El número grande al lado del título: se lee mientras el dedo tapa la barra.
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = "${draft.minAudioSeconds} s",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    text = if (draft.minAudioSeconds == 0) {
                        stringResource(R.string.filter_by_duration_off)
                    } else {
                        stringResource(R.string.filter_by_duration_body, draft.minAudioSeconds)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Slider(
                    value = dragging,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { onVisibilityChange(draft) },
                    valueRange = 0f..LibraryVisibility.MAX_MIN_AUDIO_SECONDS.toFloat(),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row {
                    Text(
                        stringResource(R.string.filter_min),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.filter_max, LibraryVisibility.MAX_MIN_AUDIO_SECONDS),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant) }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // Toda la fila es el interruptor: más fácil de tocar que el botoncito solo.
                    .toggleable(
                        value = saved.hideVoiceNotes,
                        role = Role.Switch,
                        onValueChange = { onVisibilityChange(draft.copy(hideVoiceNotes = it)) },
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                ) {
                    Text(stringResource(R.string.filter_voice_notes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.filter_voice_notes_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = saved.hideVoiceNotes, onCheckedChange = null)
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                val hidden = state.hiddenCount(draft)
                Text(
                    text = pluralStringResource(R.plurals.filter_hidden_count, hidden, hidden),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.filter_downloads_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Vistas previas
// ---------------------------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SettingsScreenPreview() {
    ExoTubeTheme {
        SettingsScreen(SettingsUiState(theme = AppTheme.SPIDER), {}, {}, {}, PaddingValues())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 1200)
@Composable
private fun ThemeSettingsPreview() {
    ExoTubeTheme {
        ThemeSettingsScreen(AppTheme.CLASSIC, {}, {}, PaddingValues())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LibraryFilterPreview() {
    ExoTubeTheme {
        LibraryFilterScreen(SettingsUiState(), {}, {}, PaddingValues())
    }
}

/**
 * Cómo es el teléfono por dentro, dicho como en la web de descarga. Cuenta el PRIMER procesador
 * de la lista de Android, que es el que de verdad usa el sistema: un teléfono con un procesador
 * de 64 bits pero Android de 32 (pasa en muchos baratos) dice "armeabi-v7a", y el APK de 64 bits
 * no le sirve.
 */
@StringRes
internal fun phoneKindLabel(abis: List<String>): Int = when (abis.firstOrNull()) {
    "arm64-v8a" -> R.string.phone_kind_arm64
    "armeabi-v7a", "armeabi" -> R.string.phone_kind_arm32
    "x86_64", "x86" -> R.string.phone_kind_intel
    else -> R.string.phone_kind_unknown
}
