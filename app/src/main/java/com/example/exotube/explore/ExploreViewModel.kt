package com.example.exotube.explore

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exotube.ExoTubeApp
import com.example.exotube.R
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.repository.OnlineCatalogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Atajos de la pantalla Explorar: cada uno es, por dentro, una búsqueda en YouTube. */
enum class ExploreTopic(val query: String, @StringRes val labelRes: Int) {
    MUSIC("música", R.string.explore_topic_music),
    LATIN("reggaeton", R.string.explore_topic_latin),
    ROCK("rock", R.string.explore_topic_rock),
    CONCERTS("conciertos", R.string.explore_topic_concerts),
    PODCAST("podcast", R.string.explore_topic_podcast),
    NEWS("noticias", R.string.explore_topic_news),
}

/** Lo que se está mostrando en la zona de resultados. */
sealed interface ExploreResults {
    data object Loading : ExploreResults
    data class Ready(val videos: List<OnlineVideo>) : ExploreResults
    data class Error(val error: MediaError) : ExploreResults
}

data class ExploreUiState(
    /** Texto escrito en el buscador; vacío mientras se navega por atajos. */
    val query: String = "",
    val topic: ExploreTopic = ExploreTopic.MUSIC,
    /** false cuando lo que se ve son resultados de una búsqueda escrita, no de un atajo. */
    val isTopicSelected: Boolean = true,
    /** Modo ahorro: trae solo el sonido, que gasta muchísimos menos datos. */
    val audioOnly: Boolean = false,
    val results: ExploreResults = ExploreResults.Loading,
    /** Id del video cuyo enlace se está resolviendo: la fila muestra un indicador. */
    val resolvingId: String? = null,
)

/** Cosas que pasan una sola vez y no son "estado": van por un canal, no por el StateFlow. */
sealed interface ExploreEvent {
    data class Play(val video: OnlineVideo, val stream: StreamSource) : ExploreEvent
    data class ResolveFailed(val error: MediaError) : ExploreEvent
}

class ExploreViewModel(private val catalog: OnlineCatalogRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    // Un canal (y no un StateFlow) porque "reproduce esto" hay que hacerlo UNA vez: si fuera
    // estado, al girar la pantalla se volvería a reproducir.
    private val _events = Channel<ExploreEvent>(Channel.BUFFERED)
    val events: Flow<ExploreEvent> = _events.receiveAsFlow()

    private var searchJob: Job? = null
    private var resolveJob: Job? = null

    init {
        load(ExploreTopic.MUSIC.query)
    }

    /** Se escribe libremente; no se busca hasta que el usuario lo pide (cada búsqueda cuesta). */
    fun onQueryChange(text: String) {
        _uiState.value = _uiState.value.copy(query = text)
    }

    fun onSearch() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        _uiState.value = _uiState.value.copy(isTopicSelected = false)
        load(query)
    }

    fun onTopicSelected(topic: ExploreTopic) {
        _uiState.value = _uiState.value.copy(topic = topic, query = "", isTopicSelected = true)
        load(topic.query)
    }

    fun onAudioOnlyChange(audioOnly: Boolean) {
        _uiState.value = _uiState.value.copy(audioOnly = audioOnly)
    }

    fun onRetry() {
        val state = _uiState.value
        load(if (state.isTopicSelected) state.topic.query else state.query.trim())
    }

    /**
     * Prepara un video para reproducirlo: hay que preguntarle a YouTube la dirección real, y eso
     * tarda unos segundos. Mientras, la fila muestra que está trabajando.
     */
    fun onVideoSelected(video: OnlineVideo) {
        resolveJob?.cancel() // si el usuario toca otro video, el anterior ya no interesa
        resolveJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolvingId = video.id)
            val result = catalog.resolveStream(video, _uiState.value.audioOnly)
            _uiState.value = _uiState.value.copy(resolvingId = null)
            _events.send(
                result.fold(
                    onSuccess = { ExploreEvent.Play(video, it) },
                    onFailure = { ExploreEvent.ResolveFailed(it.asMediaError()) },
                ),
            )
        }
    }

    private fun load(query: String) {
        searchJob?.cancel() // una búsqueda nueva deja obsoleta la anterior
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(results = ExploreResults.Loading)
            val results = catalog.search(query).fold(
                onSuccess = { ExploreResults.Ready(it) },
                onFailure = { ExploreResults.Error(it.asMediaError()) },
            )
            _uiState.value = _uiState.value.copy(results = results)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                ExploreViewModel(app.container.onlineCatalog)
            }
        }
    }
}

private fun Throwable.asMediaError(): MediaError = this as? MediaError ?: MediaError.Unknown(this)
