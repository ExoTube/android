package com.example.exotube.channel

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.exotube.R
import com.example.exotube.domain.model.OnlineChannel
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.StreamSource
import com.example.exotube.explore.ExploreEvent
import com.example.exotube.ui.components.LoadMoreFooter
import com.example.exotube.ui.components.LoadMoreWhenNearEnd
import com.example.exotube.ui.components.OnlineVideoRow
import com.example.exotube.ui.components.shareToSelf
import com.example.exotube.ui.formatCompactCount
import com.example.exotube.ui.messageRes

/** El canal de un autor: su cabecera y todos sus videos, cargándose al bajar. */
@Composable
fun ChannelRoute(
    /** Lo que se enseña arriba mientras el canal carga (el nombre que traía el video). */
    title: String,
    onPlayOnline: (OnlineVideo, StreamSource, audioOnly: Boolean) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ChannelViewModel = viewModel(factory = ChannelViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExploreEvent.Play -> onPlayOnline(event.video, event.stream, event.audioOnly)
                is ExploreEvent.ResolveFailed ->
                    Toast.makeText(context, event.error.messageRes(), Toast.LENGTH_LONG).show()
            }
        }
    }

    ChannelScreen(
        title = title,
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onVideoSelected = viewModel::onVideoSelected,
        onDownload = { context.startActivity(shareToSelf(it)) },
        onLoadMore = { viewModel.onLoadMore() },
        onRetryMore = { viewModel.onLoadMore(userAsked = true) },
        contentPadding = contentPadding,
    )
}

@Composable
private fun ChannelScreen(
    title: String,
    state: ChannelUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onVideoSelected: (OnlineVideo) -> Unit,
    onDownload: (OnlineVideo) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val ready = state as? ChannelUiState.Ready
    LoadMoreWhenNearEnd(listState, itemCount = ready?.videos?.size ?: 0) { if (ready != null) onLoadMore() }

    LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                }
                Text(
                    text = ready?.channel?.name ?: title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when (state) {
            ChannelUiState.Loading -> item {
                Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ChannelUiState.Failed -> item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillParentMaxHeight(0.7f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                ) {
                    Text(
                        text = stringResource(state.error.messageRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.sheet_retry)) }
                }
            }

            is ChannelUiState.Ready -> {
                item { ChannelHeader(state.channel) }
                if (state.videos.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.channel_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                items(state.videos, key = { it.id }) { video ->
                    // Sin enlace al canal: ya se está en él.
                    OnlineVideoRow(
                        video = video,
                        isResolving = video.id == state.resolvingId,
                        onClick = { onVideoSelected(video) },
                        onDownload = { onDownload(video) },
                    )
                }
                item(key = "mas-videos") {
                    LoadMoreFooter(state.isLoadingMore, state.loadMoreFailed, onRetry = onRetryMore)
                }
            }
        }
    }
}

/**
 * Arriba del canal: su portada, su foto, el nombre y cuántos le siguen. Como en YouTube, para
 * que se reconozca a primera vista.
 */
@Composable
private fun ChannelHeader(channel: OnlineChannel) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        channel.bannerUrl?.let { banner ->
            AsyncImage(
                model = banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(BANNER_RATIO)
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainerHighest),
            )
            Spacer(Modifier.height(12.dp))
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                // Si la foto tarda o no hay, queda un círculo verde en vez de un hueco.
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHighest))),
        ) {
            AsyncImage(
                model = channel.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (channel.isVerified) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_verified),
                    contentDescription = stringResource(R.string.channel_verified),
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        channel.subscriberCount?.let {
            Text(
                text = stringResource(R.string.channel_subscribers, formatCompactCount(it)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.channel_videos),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
        )
    }
}

/** Las portadas de canal de YouTube son muy apaisadas. */
private const val BANNER_RATIO = 4f
