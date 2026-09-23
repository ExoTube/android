package com.example.exotube.explore

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.exotube.ui.tour.TourSpot
import com.example.exotube.ui.tour.tourSpot
import com.example.exotube.R
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.ui.components.LoadMoreFooter
import com.example.exotube.ui.components.LoadMoreWhenNearEnd
import com.example.exotube.ui.components.OnlineVideoRow
import com.example.exotube.ui.components.shareToSelf
import com.example.exotube.ui.components.SearchField
import com.example.exotube.ui.messageRes
import com.example.exotube.ui.theme.ExoTubeTheme

/**
 * Conecta el ViewModel con la pantalla y se ocupa de las dos cosas que la pantalla no puede hacer
 * sola: mandar el video al reproductor y abrir la hoja de descarga.
 */
@Composable
fun ExploreRoute(
    onPlayOnline: (OnlineVideo, StreamSource, audioOnly: Boolean) -> Unit,
    onOpenChannel: (OnlineVideo) -> Unit,
    onGoToLibrary: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ExploreViewModel = viewModel(factory = ExploreViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Los eventos son de un solo uso: se consumen aquí y no vuelven a ocurrir al girar la pantalla.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExploreEvent.Play -> onPlayOnline(event.video, event.stream, event.audioOnly)
                is ExploreEvent.ResolveFailed ->
                    Toast.makeText(context, event.error.messageRes(), Toast.LENGTH_LONG).show()
            }
        }
    }

    ExploreScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::onSearch,
        onTopicSelected = viewModel::onTopicSelected,
        onAudioOnlyChange = viewModel::onAudioOnlyChange,
        onVideoSelected = viewModel::onVideoSelected,
        onDownload = { context.startActivity(shareToSelf(it)) },
        onRetry = viewModel::onRetry,
        onLoadMore = { viewModel.onLoadMore() },
        onRetryMore = { viewModel.onLoadMore(userAsked = true) },
        onOpenChannel = onOpenChannel,
        onGoToLibrary = onGoToLibrary,
        contentPadding = contentPadding,
    )
}

/** Pantalla "tonta": solo dibuja el estado que recibe. */
@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onTopicSelected: (ExploreTopic) -> Unit,
    onAudioOnlyChange: (Boolean) -> Unit,
    onVideoSelected: (OnlineVideo) -> Unit,
    onDownload: (OnlineVideo) -> Unit,
    onRetry: () -> Unit,
    onGoToLibrary: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Se llega al final de la lista: hay que traer más resultados. */
    onLoadMore: () -> Unit = {},
    onRetryMore: () -> Unit = {},
    onOpenChannel: (OnlineVideo) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val ready = state.results as? ExploreResults.Ready
    LoadMoreWhenNearEnd(listState, itemCount = ready?.videos?.size ?: 0) { if (ready != null) onLoadMore() }

    LazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier.fillMaxWidth()) {
        item { ExploreHeader() }
        item {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                hint = stringResource(R.string.explore_search_hint),
                onSearch = onSearch,
                modifier = Modifier.tourSpot(TourSpot.EXPLORE_SEARCH),
            )
        }
        item {
            TopicRow(
                selected = state.topic.takeIf { state.isTopicSelected },
                audioOnly = state.audioOnly,
                onTopicSelected = onTopicSelected,
                onAudioOnlyChange = onAudioOnlyChange,
            )
        }

        when (val results = state.results) {
            ExploreResults.Loading -> item {
                Box(Modifier.fillParentMaxHeight(0.5f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ExploreResults.Error -> item {
                ExploreError(
                    error = results.error,
                    onRetry = onRetry,
                    onGoToLibrary = onGoToLibrary,
                    modifier = Modifier.fillParentMaxHeight(0.6f),
                )
            }
            // "Para ti": un bloque por motivo, cada uno con su encabezado explicando de donde
            // sale. Van seguidos en la misma lista, no en carruseles horizontales: aqui lo que
            // se quiere es descubrir, y una lista vertical se lee entera sin tener que arrastrar.
            is ExploreResults.ForYou -> results.blocks.forEachIndexed { blockIndex, block ->
                item(key = "motivo-${block.becauseOf}") { BecauseYouListened(block.becauseOf) }
                items(block.videos, key = { "${block.becauseOf}-${it.id}" }) { video ->
                    OnlineVideoRow(
                        video = video,
                        isResolving = video.id == state.resolvingId,
                        isTourAnchor = blockIndex == 0 && video == block.videos.first(),
                        onClick = { onVideoSelected(video) },
                        onDownload = { onDownload(video) },
                        onOpenChannel = { onOpenChannel(video) },
                    )
                }
            }

            ExploreResults.NothingListenedYet -> item {
                NothingListenedYet(Modifier.fillParentMaxHeight(0.6f))
            }

            is ExploreResults.Ready -> if (results.videos.isEmpty()) {
                item { EmptyResults(Modifier.fillParentMaxHeight(0.6f)) }
            } else {
                items(results.videos, key = { it.id }) { video ->
                    OnlineVideoRow(
                        video = video,
                        isResolving = video.id == state.resolvingId,
                        isTourAnchor = video == results.videos.first(),
                        onClick = { onVideoSelected(video) },
                        onDownload = { onDownload(video) },
                        onOpenChannel = { onOpenChannel(video) },
                    )
                }
                item(key = "mas-resultados") {
                    LoadMoreFooter(results.isLoadingMore, results.loadMoreFailed, onRetry = onRetryMore)
                }
            }
        }
    }
}

@Composable
private fun ExploreHeader() {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
        Text(
            text = buildAnnotatedString {
                append("Exo")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Tube") }
            },
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.explore_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Atajos de temas y, al final, el interruptor de ahorro de datos. */
@Composable
private fun TopicRow(
    selected: ExploreTopic?,
    audioOnly: Boolean,
    onTopicSelected: (ExploreTopic) -> Unit,
    onAudioOnlyChange: (Boolean) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        item {
            FilterChip(
                selected = audioOnly,
                onClick = { onAudioOnlyChange(!audioOnly) },
                label = { Text(stringResource(R.string.explore_audio_only)) },
                modifier = Modifier.tourSpot(TourSpot.EXPLORE_DATA_SAVER),
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_audio), contentDescription = null, Modifier.size(18.dp))
                },
                colors = chipColors(),
            )
        }
        items(ExploreTopic.entries) { topic ->
            FilterChip(
                selected = topic == selected,
                onClick = { onTopicSelected(topic) },
                label = { Text(stringResource(topic.labelRes)) },
                modifier = Modifier.tourSpot(TourSpot.EXPLORE_TOPICS, enabled = topic == ExploreTopic.entries.first()),
                colors = chipColors(),
            )
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)

/**
 * Sin internet no hay nada que explorar, pero la música descargada sigue ahí: en vez de dejar al
 * usuario en un callejón sin salida, le ofrecemos ir a su biblioteca.
 */
@Composable
private fun ExploreError(
    error: MediaError,
    onRetry: () -> Unit,
    onGoToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredMessage(
        icon = R.drawable.ic_explore,
        message = stringResource(error.messageRes()),
        modifier = modifier,
    ) {
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.sheet_retry)) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onGoToLibrary) { Text(stringResource(R.string.explore_go_to_library)) }
    }
}

/** El encabezado de cada bloque de "Para ti": de dónde sale lo que viene debajo. */
@Composable
private fun BecauseYouListened(becauseOf: String) {
    Text(
        text = stringResource(R.string.explore_because_you_listened, becauseOf),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

/**
 * Lo que se ve en "Para ti" antes de haber escuchado nada.
 *
 * Explica de dónde saldrán las sugerencias y, sobre todo, que el historial se queda en el
 * teléfono: una pantalla que dice "te voy a recomendar según lo que escuchas" sin aclarar eso
 * es justo la que hace pensar que la app te está espiando.
 */
@Composable
private fun NothingListenedYet(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_explore),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.explore_for_you_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.explore_for_you_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyResults(modifier: Modifier = Modifier) {
    CenteredMessage(
        icon = R.drawable.ic_search,
        message = stringResource(R.string.explore_empty),
        modifier = modifier,
    )
}

@Composable
private fun CenteredMessage(
    @DrawableRes icon: Int,
    message: String,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        extra()
    }
}

// --- Previews ---

private val previewVideos = listOf(
    OnlineVideo(
        id = "1",
        url = "https://www.youtube.com/watch?v=1",
        title = "Concierto completo en directo desde Lima",
        channel = "Canal de música",
        durationSeconds = 3_725,
        thumbnailUrl = null,
        viewCount = 1_240_000,
    ),
    OnlineVideo(
        id = "2",
        url = "https://www.youtube.com/watch?v=2",
        title = "Canción nueva",
        channel = "Artista",
        durationSeconds = 212,
        thumbnailUrl = null,
        viewCount = 9_800,
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ExploreScreenPreview() {
    ExoTubeTheme {
        ExploreScreen(
            state = ExploreUiState(results = ExploreResults.Ready(previewVideos), resolvingId = "2"),
            onQueryChange = {},
            onSearch = {},
            onTopicSelected = {},
            onAudioOnlyChange = {},
            onVideoSelected = {},
            onDownload = {},
            onRetry = {},
            onGoToLibrary = {},
            contentPadding = PaddingValues(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ExploreOfflinePreview() {
    ExoTubeTheme {
        ExploreScreen(
            state = ExploreUiState(results = ExploreResults.Error(MediaError.NoConnection)),
            onQueryChange = {},
            onSearch = {},
            onTopicSelected = {},
            onAudioOnlyChange = {},
            onVideoSelected = {},
            onDownload = {},
            onRetry = {},
            onGoToLibrary = {},
            contentPadding = PaddingValues(),
        )
    }
}
