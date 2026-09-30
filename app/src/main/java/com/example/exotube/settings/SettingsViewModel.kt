package com.example.exotube.settings

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exotube.ExoTubeApp
import com.example.exotube.R
import com.example.exotube.data.settings.AppSettings
import com.example.exotube.data.settings.CustomBackground
import com.example.exotube.data.settings.CustomBackgroundStore
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.LibraryVisibility
import com.example.exotube.domain.model.visibleWith
import com.example.exotube.domain.repository.LibraryRepository
import com.example.exotube.ui.theme.AppTheme
import com.example.exotube.ui.theme.wallpaperColorsFrom
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: AppTheme = AppTheme.CLASSIC,
    val visibility: LibraryVisibility = LibraryVisibility(),
    /** La biblioteca SIN filtrar: hace falta para decir cuántos audios esconde cada ajuste. */
    val allItems: List<LibraryItem> = emptyList(),
    /** El fondo propio: null si todavía no se eligió imagen. */
    val background: CustomBackground? = null,
    /** true si lo que se ve ahora es el fondo propio y no un tema de la lista. */
    val usingBackground: Boolean = false,
    /** Cuántas veces se compartió ExoTube (para el candado del fondo propio). */
    val shares: Int = 0,
) {
    /** Cuántos audios se esconderían con [draft], para enseñarlo mientras se mueve la barra. */
    fun hiddenCount(draft: LibraryVisibility): Int = allItems.size - allItems.visibleWith(draft).size

    val backgroundUnlocked: Boolean get() = AppSettings.isBackgroundUnlocked(shares)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val settings: AppSettings,
    allMedia: LibraryRepository,
    private val backgroundStore: CustomBackgroundStore,
) : ViewModel() {

    private fun stateOf(themeId: String?, visibility: LibraryVisibility, items: List<LibraryItem>, background: CustomBackground?, shares: Int) =
        SettingsUiState(
            theme = AppTheme.fromId(themeId),
            visibility = visibility,
            allItems = items,
            background = background,
            usingBackground = themeId == AppSettings.CUSTOM_THEME_ID && background != null && AppSettings.isBackgroundUnlocked(shares),
            shares = shares,
        )

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settings.themeId, settings.libraryVisibility, allMedia.observeDownloads(), settings.customBackground, settings.shares,
            ::stateOf,
        ).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Lo guardado ya se sabe al instante: así la pantalla no parpadea con los valores de fábrica.
            stateOf(settings.themeId.value, settings.libraryVisibility.value, emptyList(), settings.customBackground.value, settings.shares.value),
        )

    /** La imagen del fondo propio en pequeño, para la tarjeta de Ajustes. Se relee solo si cambia. */
    val backgroundPreview: StateFlow<ImageBitmap?> = settings.customBackground
        .map { it?.version }
        .distinctUntilChanged()
        .mapLatest { version -> if (version == null) null else backgroundStore.load()?.asImageBitmap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _savingBackground = MutableStateFlow(false)
    val savingBackground: StateFlow<Boolean> = _savingBackground.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    /** Avisos de una sola vez (un Toast): el id del texto. */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    fun onThemeSelected(theme: AppTheme) = settings.setTheme(theme.id)

    fun onUseBackground() = settings.setTheme(AppSettings.CUSTOM_THEME_ID)

    /** Una imagen de la galería: se copia, se sacan sus colores y se pone de fondo al momento. */
    fun onBackgroundPicked(uri: Uri) {
        if (_savingBackground.value) return
        _savingBackground.value = true
        viewModelScope.launch {
            runCatching { backgroundStore.save(uri) }
                .onSuccess { pixels ->
                    val colors = wallpaperColorsFrom(pixels)
                    settings.setCustomBackground(colors.primary.toArgb(), colors.secondary.toArgb(), colors.tertiary.toArgb())
                    settings.setTheme(AppSettings.CUSTOM_THEME_ID)
                }
                .onFailure { _messages.trySend(R.string.theme_custom_error) }
            _savingBackground.value = false
        }
    }

    fun onBackgroundDimChange(dim: Float) = settings.setBackgroundDim(dim)

    fun onVisibilityChange(visibility: LibraryVisibility) = settings.setLibraryVisibility(visibility)

    fun onRestartTutorial() = settings.resetTours()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                SettingsViewModel(app.container.settings, app.container.allMedia, app.container.customBackgroundStore)
            }
        }
    }
}
