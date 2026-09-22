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
import com.example.exotube.domain.model.Recommendation
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.domain.model.VideoQuality
import com.example.exotube.domain.repository.OnlineCatalogRepository
import com.example.exotube.domain.repository.RecommendationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Atajos de la pantalla Explorar.
 *
 * Casi todos son, por dentro, una búsqueda en YouTube. [FOR_YOU] es la excepción: en vez de
 * buscar algo fijo, mira lo que el usuario ha escuchado y pide recomendaciones. Por eso su
 * consulta va vacía.
 */
enum class ExploreTopic(val query: String, @StringRes val labelRes: Int) {
    FOR_YOU("", R.string.explore_topic_for_you),
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

    /** "Para ti": bloques con el motivo de cada uno ("Porque escuchaste …"). */
    data class ForYou(val blocks: List<Recommendation>) : ExploreResults

    /** Todavía no hay nada que escuchar de dónde sacar recomendaciones. */
    data object NothingListenedYet : ExploreResults
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
    data class Play(val video: OnlineVideo, val stream: StreamSource, val audioOnly: Boolean) : ExploreEvent
    data class ResolveFailed(val error: MediaError) : ExploreEvent
}

class ExploreViewModel(
    private val catalog: OnlineCatalogRepository,
    private val recommendations: RecommendationRepository,
    /** Si ahora se está con datos móviles; decide la calidad automática. */
    private val isMeteredConnection: () -> Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    // Un canal (y no un StateFlow) porque "reproduce esto" hay que hacerlo UNA vez: si fuera
    // estado, al girar la pantalla se volvería a reproducir.
    private val _events = Channel<ExploreEvent>(Channel.BUFFERED)
    val events: Flow<ExploreEvent> = _events.receiveAsFlow()

    private var searchJob: Job? = null
    private var resolveJob: Job? = null

    init {
        // Se abre en "Para ti" si hay historial; si no, en Música, que es lo útil el primer día.
        viewModelScope.launch {
            val topic = if (recommendations.hasEnoughHistory()) ExploreTopic.FOR_YOU else ExploreTopic.MUSIC
            _uiState.value = _uiState.value.copy(topic = topic)
            loadTopic(topic)
        }
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
        loadTopic(topic)
    }

    fun onAudioOnlyChange(audioOnly: Boolean) {
        _uiState.value = _uiState.value.copy(audioOnly = audioOnly)
    }

    fun onRetry() {
        val state = _uiState.value
        if (state.isTopicSelected) loadTopic(state.topic) else load(state.query.trim())
    }

    /**
     * Prepara un video para reproducirlo: hay que preguntarle a YouTube la dirección real, y eso
     * tarda unos segundos. Mientras, la fila muestra que está trabajando.
     */
    fun onVideoSelected(video: OnlineVideo) {
        resolveJob?.cancel() // si el usuario toca otro video, el anterior ya no interesa
        resolveJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolvingId = video.id)
            val audioOnly = _uiState.value.audioOnly
            // Todo video empieza en calidad automática; si se quiere otra, se cambia ya
            // viéndolo, desde el reproductor.
            val result = catalog.resolveStream(
                video = video,
                audioOnly = audioOnly,
                maxHeight = VideoQuality.AUTO.heightFor(isMeteredConnection()),
            )
            _uiState.value = _uiState.value.copy(resolvingId = null)
            _events.send(
                result.fold(
                    onSuccess = { ExploreEvent.Play(video, it, audioOnly) },
                    onFailure = { ExploreEvent.ResolveFailed(it.asMediaError()) },
                ),
            )
        }
    }

    private fun loadTopic(topic: ExploreTopic) {
        if (topic == ExploreTopic.FOR_YOU) loadForYou() else load(topic.query)
    }

    /**
     * Recomendaciones a partir de lo que el usuario escucha.
     *
     * Tarda más que una búsqueda normal porque consulta varias listas a la vez, así que la
     * pantalla enseña que está trabajando desde el primer momento.
     */
    private fun loadForYou() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(results = ExploreResults.Loading)
            val results = recommendations.forYou().fold(
                onSuccess = { blocks ->
                    if (blocks.isEmpty()) ExploreResults.NothingListenedYet else ExploreResults.ForYou(blocks)
                },
                onFailure = { ExploreResults.Error(it.asMediaError()) },
            )
            _uiState.value = _uiState.value.copy(results = results)
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
                ExploreViewModel(
                    catalog = app.container.onlineCatalog,
                    recommendations = app.container.recommendations,
                    isMeteredConnection = app.container.connection::isMetered,
                )
            }
        }
    }
}

private fun Throwable.asMediaError(): MediaError = this as? MediaError ?: MediaError.Unknown(this)
