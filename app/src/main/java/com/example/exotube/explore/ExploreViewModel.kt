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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

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
    data class Ready(
        val videos: List<OnlineVideo>,
        /** Si YouTube tiene más resultados: al llegar al final se piden solos. */
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        /** Falló la última tanda: se enseña "Reintentar" y no se vuelve a pedir sola. */
        val loadMoreFailed: Boolean = false,
    ) : ExploreResults
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

    /** Traer la siguiente tanda de resultados. */
    private var moreJob: Job? = null

    /** El texto de la búsqueda que se está viendo, para pedir su siguiente página. */
    private var loadedQuery: String? = null

    /** El recorrido que va preparando los primeros videos de la lista, uno tras otro. */
    private var prefetchJob: Job? = null

    /** El video que se está preparando ahora mismo, si hay alguno. */
    private var prefetching: Prefetch? = null

    /**
     * Un video cuyo enlace se está pidiendo por adelantado. [work] vive fuera del recorrido
     * ([prefetchJob]) a propósito: si el usuario toca justo este video, se para el recorrido
     * pero este trabajo sigue y se aprovecha.
     */
    private class Prefetch(
        val videoId: String,
        val audioOnly: Boolean,
        val work: Deferred<Result<StreamSource>>,
    )

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
        // Lo que se estuviera preparando deja paso a lo que el usuario acaba de pedir, salvo
        // que sea justo este video: entonces se espera a ese trabajo en vez de empezar otro.
        val alreadyStarted = stopPrefetching(except = video.id)
        resolveJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolvingId = video.id)
            val audioOnly = _uiState.value.audioOnly
            val result = if (alreadyStarted != null) {
                awaitOrCancel(alreadyStarted.work)
            } else {
                resolve(video, audioOnly)
            }
            _uiState.value = _uiState.value.copy(resolvingId = null)
            _events.send(
                result.fold(
                    onSuccess = { ExploreEvent.Play(video, it, audioOnly) },
                    onFailure = { ExploreEvent.ResolveFailed(it.asMediaError()) },
                ),
            )
        }
    }

    /**
     * Todo video empieza en calidad automática; si se quiere otra, se cambia ya viéndolo, desde
     * el reproductor. Si el enlace ya se pidió antes (por adelantado), sale de la caché al momento.
     */
    private suspend fun resolve(video: OnlineVideo, audioOnly: Boolean): Result<StreamSource> =
        catalog.resolveStream(
            video = video,
            audioOnly = audioOnly,
            maxHeight = VideoQuality.AUTO.heightFor(isMeteredConnection()),
        )

    /** Si el usuario se va a otro video mientras se espera, este trabajo ya no sirve a nadie. */
    private suspend fun awaitOrCancel(work: Deferred<Result<StreamSource>>): Result<StreamSource> =
        try {
            work.await()
        } catch (e: CancellationException) {
            work.cancel()
            throw e
        }

    /**
     * Pide por adelantado los enlaces de los primeros videos de la lista, para que al tocar uno
     * empiece casi al momento.
     *
     * Casi toda la espera al tocar un video es yt-dlp preguntándole a YouTube por el enlace (unos
     * segundos); el video en sí arranca enseguida. Así que esa pregunta se hace mientras el
     * usuario todavía está leyendo la lista. Lo que se prepara queda en la caché del catálogo.
     *
     * Con límites, para no gastar batería ni datos por nada:
     *  - solo los [PREFETCH_COUNT] primeros, que es lo que se ve sin desplazarse;
     *  - de uno en uno, nunca varios yt-dlp a la vez;
     *  - después de una pausa, para no competir con las miniaturas que se están cargando.
     * Solo se piden enlaces, unos pocos kilobytes; del video no se descarga nada.
     */
    private fun prefetchFirst(results: ExploreResults) {
        stopPrefetching()
        val videos = when (results) {
            is ExploreResults.Ready -> results.videos
            is ExploreResults.ForYou -> results.blocks.flatMap { it.videos }
            else -> return
        }.take(PREFETCH_COUNT)
        if (videos.isEmpty()) return

        prefetchJob = viewModelScope.launch {
            delay(PREFETCH_DELAY_MS)
            for (video in videos) {
                val audioOnly = _uiState.value.audioOnly
                // En viewModelScope y no dentro de este recorrido: ver [Prefetch].
                val work = viewModelScope.async { resolve(video, audioOnly) }
                prefetching = Prefetch(video.id, audioOnly, work)
                work.join()
                prefetching = null
            }
        }
    }

    /**
     * Para el recorrido y cancela lo que se estuviera preparando, salvo que sea el video
     * [except] (con el mismo modo de solo audio): ese trabajo se devuelve para aprovecharlo.
     */
    private fun stopPrefetching(except: String? = null): Prefetch? {
        prefetchJob?.cancel()
        prefetchJob = null
        val current = prefetching ?: return null
        prefetching = null
        if (current.videoId == except && current.audioOnly == _uiState.value.audioOnly) return current
        current.work.cancel()
        return null
    }

    /**
     * La lista llegó al final: se pide la siguiente tanda de resultados y se añade debajo.
     *
     * Si la última tanda falló, no se vuelve a pedir sola cada vez que se mueve la lista (sería
     * un bucle de errores): espera a que el usuario toque "Reintentar" ([userAsked]).
     */
    fun onLoadMore(userAsked: Boolean = false) {
        val ready = _uiState.value.results as? ExploreResults.Ready ?: return
        val query = loadedQuery ?: return
        if (!ready.hasMore || ready.isLoadingMore || searchJob?.isActive == true) return
        if (ready.loadMoreFailed && !userAsked) return

        // Se marca ya, antes de lanzar nada: si llega otro aviso de "final de la lista" en el
        // mismo instante, tiene que ver que ya se está cargando y no pedir la misma página.
        updateReady { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        moreJob = viewModelScope.launch {
            val result = catalog.searchMore(query)
            if (loadedQuery != query) return@launch // mientras tanto se buscó otra cosa
            result.fold(
                onSuccess = { page ->
                    updateReady { current ->
                        val videos = (current.videos + page.videos).distinctBy(OnlineVideo::id)
                        current.copy(
                            videos = videos,
                            // Una tanda sin nada nuevo también es el final: evita pedir sin fin.
                            hasMore = page.hasMore && videos.size > current.videos.size,
                            isLoadingMore = false,
                        )
                    }
                },
                onFailure = { updateReady { it.copy(isLoadingMore = false, loadMoreFailed = true) } },
            )
        }
    }

    private fun updateReady(change: (ExploreResults.Ready) -> ExploreResults.Ready) {
        val ready = _uiState.value.results as? ExploreResults.Ready ?: return
        _uiState.value = _uiState.value.copy(results = change(ready))
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
        moreJob?.cancel()
        loadedQuery = null // "Para ti" no se pagina
        stopPrefetching() // lo de la lista anterior ya no se va a tocar
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(results = ExploreResults.Loading)
            val results = recommendations.forYou().fold(
                onSuccess = { blocks ->
                    if (blocks.isEmpty()) ExploreResults.NothingListenedYet else ExploreResults.ForYou(blocks)
                },
                onFailure = { ExploreResults.Error(it.asMediaError()) },
            )
            _uiState.value = _uiState.value.copy(results = results)
            prefetchFirst(results)
        }
    }

    private fun load(query: String) {
        searchJob?.cancel() // una búsqueda nueva deja obsoleta la anterior
        moreJob?.cancel()
        loadedQuery = query
        stopPrefetching()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(results = ExploreResults.Loading)
            val results = catalog.search(query).fold(
                // distinctBy: YouTube a veces repite un video, y la lista no admite dos iguales.
                onSuccess = { ExploreResults.Ready(it.videos.distinctBy(OnlineVideo::id), hasMore = it.hasMore) },
                onFailure = { ExploreResults.Error(it.asMediaError()) },
            )
            _uiState.value = _uiState.value.copy(results = results)
            prefetchFirst(results)
        }
    }

    companion object {
        /** Cuántos videos de arriba de la lista se preparan por adelantado. */
        const val PREFETCH_COUNT = 3

        /** Espera antes de empezar, para dejar cargar antes las miniaturas de la lista. */
        const val PREFETCH_DELAY_MS = 1_500L

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
