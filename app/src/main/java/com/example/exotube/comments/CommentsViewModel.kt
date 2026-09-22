package com.example.exotube.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exotube.ExoTubeApp
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.VideoComment
import com.example.exotube.domain.repository.CommentsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CommentsUiState {
    data object Loading : CommentsUiState
    data class Ready(val comments: List<VideoComment>) : CommentsUiState
    data class Failed(val error: MediaError) : CommentsUiState
}

/**
 * Trae los comentarios de un video.
 *
 * Se crea con el video como clave, así que abrir otro empieza de cero y no arrastra los
 * comentarios del anterior.
 */
class CommentsViewModel(
    private val video: OnlineVideo,
    private val repository: CommentsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CommentsUiState>(CommentsUiState.Loading)
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = CommentsUiState.Loading
            _uiState.value = repository.comments(video).fold(
                onSuccess = { CommentsUiState.Ready(it) },
                onFailure = { CommentsUiState.Failed(it as? MediaError ?: MediaError.Unknown(it)) },
            )
        }
    }

    companion object {
        fun factory(video: OnlineVideo): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                CommentsViewModel(video, app.container.comments)
            }
        }
    }
}
