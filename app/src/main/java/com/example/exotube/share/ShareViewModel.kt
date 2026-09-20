package com.example.exotube.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exotube.ExoTubeApp
import com.example.exotube.domain.SharedLinkParser
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.MediaFormat
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.model.Platform
import com.example.exotube.domain.repository.DownloadScheduler
import com.example.exotube.domain.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Todo lo que la pantalla puede mostrar. La UI solo dibuja el estado actual. */
sealed interface ShareUiState {
    data object Loading : ShareUiState
    data class Ready(val media: MediaInfo) : ShareUiState
    data class Error(val error: MediaError) : ShareUiState

    /** El usuario eligió un formato: la UI cierra la hoja y termina la Activity. */
    data class DownloadStarted(val media: MediaInfo, val format: MediaFormat) : ShareUiState
}

class ShareViewModel(
    private val mediaRepository: MediaRepository,
    private val downloadScheduler: DownloadScheduler,
) : ViewModel() {

    // Privado y mutable dentro; público y de solo lectura fuera (flujo de datos unidireccional).
    private val _uiState = MutableStateFlow<ShareUiState>(ShareUiState.Loading)
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private var currentUrl: String? = null
    private var loadJob: Job? = null

    /** Entrada desde la Activity con el texto crudo del Intent (puede no ser una URL). */
    fun onSharedText(sharedText: String?) {
        val url = SharedLinkParser.extractUrl(sharedText)
        when {
            url == null -> _uiState.value = ShareUiState.Error(MediaError.InvalidLink)
            // Mismo enlace (p. ej. tras rotar la pantalla): el ViewModel sobrevivió, no recargamos.
            url == currentUrl -> Unit
            Platform.fromUrl(url) == Platform.UNKNOWN ->
                _uiState.value = ShareUiState.Error(MediaError.UnsupportedPlatform)
            else -> {
                currentUrl = url
                loadMediaInfo(url)
            }
        }
    }

    fun onRetry() {
        currentUrl?.let(::loadMediaInfo)
    }

    fun onFormatSelected(format: MediaFormat) {
        val media = (_uiState.value as? ShareUiState.Ready)?.media ?: return
        downloadScheduler.enqueue(
            DownloadRequest(
                url = media.sourceUrl,
                title = media.title,
                platform = media.platform,
                format = format,
                playlistIndex = media.playlistIndex,
            ),
        )
        _uiState.value = ShareUiState.DownloadStarted(media, format)
    }

    private fun loadMediaInfo(url: String) {
        loadJob?.cancel()
        // viewModelScope se cancela solo cuando el ViewModel muere: sin fugas de memoria.
        loadJob = viewModelScope.launch {
            _uiState.value = ShareUiState.Loading
            _uiState.value = mediaRepository.fetchMediaInfo(url).fold(
                onSuccess = { media -> ShareUiState.Ready(media) },
                onFailure = { error ->
                    ShareUiState.Error(error as? MediaError ?: MediaError.Unknown(error))
                },
            )
        }
    }

    companion object {
        /** Le dice a Android cómo construir este ViewModel con sus dependencias. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                ShareViewModel(app.container.mediaRepository, app.container.downloadScheduler)
            }
        }
    }
}
