package com.example.exotube.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.exotube.ExoTubeApp
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineChannel
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.VideoQuality
import com.example.exotube.domain.repository.ChannelRepository
import com.example.exotube.domain.repository.OnlineCatalogRepository
import com.example.exotube.explore.ExploreEvent
import com.example.exotube.ui.navigation.ChannelDestination
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface ChannelUiState {
    data object Loading : ChannelUiState

    data class Failed(val error: MediaError) : ChannelUiState

    data class Ready(
        val channel: OnlineChannel,
        val videos: List<OnlineVideo>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
        /** El video cuyo enlace se está pidiendo: su fila muestra que está trabajando. */
        val resolvingId: String? = null,
    ) : ChannelUiState
}

/**
 * La página de un canal: quién es y sus videos, del más nuevo al más viejo. Se van cargando más
 * al bajar, igual que los resultados de una búsqueda.
 *
 * Tocar un video lo reproduce igual que en Explorar (mismos eventos), así que quien pinta la
 * pantalla no necesita saber de qué pantalla viene el video.
 */
class ChannelViewModel(
    private val route: ChannelDestination,
    private val channels: ChannelRepository,
    private val catalog: OnlineCatalogRepository,
    private val isMeteredConnection: () -> Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExploreEvent>(Channel.BUFFERED)
    val events: Flow<ExploreEvent> = _events.receiveAsFlow()

    private var resolveJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = ChannelUiState.Loading
            _uiState.value = channels.open(route.channelUrl, route.videoUrl).fold(
                onSuccess = { (channel, page) ->
                    ChannelUiState.Ready(channel, page.videos.distinctBy(OnlineVideo::id), page.hasMore)
                },
                onFailure = { ChannelUiState.Failed(it as? MediaError ?: MediaError.Unknown(it)) },
            )
        }
    }

    /** Ver ExploreViewModel.onLoadMore: misma regla para no reintentar en bucle tras un fallo. */
    fun onLoadMore(userAsked: Boolean = false) {
        val ready = _uiState.value as? ChannelUiState.Ready ?: return
        if (!ready.hasMore || ready.isLoadingMore) return
        if (ready.loadMoreFailed && !userAsked) return

        update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        viewModelScope.launch {
            channels.more(ready.channel.url).fold(
                onSuccess = { page ->
                    update { current ->
                        val videos = (current.videos + page.videos).distinctBy(OnlineVideo::id)
                        current.copy(
                            videos = videos,
                            hasMore = page.hasMore && videos.size > current.videos.size,
                            isLoadingMore = false,
                        )
                    }
                },
                onFailure = { update { it.copy(isLoadingMore = false, loadMoreFailed = true) } },
            )
        }
    }

    /** Como en Explorar: se pide el enlace (en calidad automática) y se manda al reproductor. */
    fun onVideoSelected(video: OnlineVideo) {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            update { it.copy(resolvingId = video.id) }
            val result = catalog.resolveStream(
                video = video,
                audioOnly = false,
                maxHeight = VideoQuality.AUTO.heightFor(isMeteredConnection()),
            )
            update { it.copy(resolvingId = null) }
            _events.send(
                result.fold(
                    onSuccess = { ExploreEvent.Play(video, it, audioOnly = false) },
                    onFailure = { ExploreEvent.ResolveFailed(it as? MediaError ?: MediaError.Unknown(it)) },
                ),
            )
        }
    }

    private fun update(change: (ChannelUiState.Ready) -> ChannelUiState.Ready) {
        val ready = _uiState.value as? ChannelUiState.Ready ?: return
        _uiState.value = change(ready)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                // Navigation guarda los argumentos de la ruta en el SavedStateHandle del ViewModel.
                ChannelViewModel(
                    route = createSavedStateHandle().toRoute<ChannelDestination>(),
                    channels = app.container.channels,
                    catalog = app.container.onlineCatalog,
                    isMeteredConnection = app.container.connection::isMetered,
                )
            }
        }
    }
}
