package com.example.exotube.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exotube.ExoTubeApp
import com.example.exotube.data.settings.AppSettings
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.LibraryVisibility
import com.example.exotube.domain.model.visibleWith
import com.example.exotube.domain.repository.LibraryRepository
import com.example.exotube.ui.theme.AppTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val theme: AppTheme = AppTheme.CLASSIC,
    val visibility: LibraryVisibility = LibraryVisibility(),
    /** La biblioteca SIN filtrar: hace falta para decir cuántos audios esconde cada ajuste. */
    val allItems: List<LibraryItem> = emptyList(),
) {
    /** Cuántos audios se esconderían con [draft], para enseñarlo mientras se mueve la barra. */
    fun hiddenCount(draft: LibraryVisibility): Int = allItems.size - allItems.visibleWith(draft).size
}

class SettingsViewModel(
    private val settings: AppSettings,
    allMedia: LibraryRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.themeId, settings.libraryVisibility, allMedia.observeDownloads()) { themeId, visibility, items ->
            SettingsUiState(AppTheme.fromId(themeId), visibility, items)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Lo guardado ya se sabe al instante: así la pantalla no parpadea con los valores de fábrica.
            SettingsUiState(AppTheme.fromId(settings.themeId.value), settings.libraryVisibility.value),
        )

    fun onThemeSelected(theme: AppTheme) = settings.setTheme(theme.id)

    fun onVisibilityChange(visibility: LibraryVisibility) = settings.setLibraryVisibility(visibility)

    fun onRestartTutorial() = settings.resetTours()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                SettingsViewModel(app.container.settings, app.container.allMedia)
            }
        }
    }
}
