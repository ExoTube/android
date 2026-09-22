package com.example.exotube.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.exotube.R
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.VideoComment
import com.example.exotube.ui.formatCompactCount
import com.example.exotube.ui.messageRes
import com.example.exotube.ui.theme.ExoTubeTheme

/**
 * Los comentarios del video, en una hoja que sube desde abajo.
 *
 * Solo se leen: para escribir uno haría falta iniciar sesión con una cuenta de Google, que es
 * justo lo que ExoTube no pide. Se dice en la propia pantalla para que nadie busque el botón de
 * responder pensando que falta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsRoute(
    video: OnlineVideo,
    onDismiss: () -> Unit,
    viewModel: CommentsViewModel = viewModel(
        key = video.id,
        factory = CommentsViewModel.factory(video),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        CommentsContent(state = state, onRetry = viewModel::reload)
    }
}

@Composable
private fun CommentsContent(state: CommentsUiState, onRetry: () -> Unit) {
    when (state) {
        CommentsUiState.Loading -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                // Traer comentarios es lento de verdad: más vale avisar que dejar una rueda muda.
                Text(
                    text = stringResource(R.string.comments_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is CommentsUiState.Failed -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(state.error.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.sheet_retry)) }
            }
        }

        is CommentsUiState.Ready -> if (state.comments.isEmpty()) {
            Centered {
                Text(
                    text = stringResource(R.string.comments_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            CommentList(state.comments)
        }
    }
}

@Composable
private fun CommentList(comments: List<VideoComment>) {
    LazyColumn(modifier = Modifier.navigationBarsPadding()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = pluralStringResource(R.plurals.comments_title, comments.size, comments.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.comments_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(comments, key = { it.id }) { comment -> CommentRow(comment) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CommentRow(comment: VideoComment) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Avatar(comment.authorAvatarUrl)
        Spacer(Modifier.size(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.labelLarge,
                    // Lo del dueño del canal se destaca, como en YouTube.
                    color = if (comment.isFromCreator) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (comment.isFromCreator) FontWeight.Bold else FontWeight.Normal,
                )
                comment.publishedText?.let {
                    Text(
                        text = " · $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)

            val hasFooter = comment.likeCount != null || comment.replyCount > 0
            if (hasFooter) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    comment.likeCount?.let { likes ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_thumb_up),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = formatCompactCount(likes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (comment.replyCount > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.comments_replies,
                                comment.replyCount,
                                comment.replyCount,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(url: String?) {
    val shape = CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(shape),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_audio),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(shape),
            )
        }
    }
}

/** La hoja no ocupa toda la pantalla: lo que va dentro necesita un alto propio para centrarse. */
@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 32.dp),
    ) {
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101813)
@Composable
private fun CommentsPreview() {
    ExoTubeTheme {
        CommentList(
            listOf(
                VideoComment(
                    id = "1",
                    author = "Soda Stereo",
                    authorAvatarUrl = null,
                    text = "Gracias totales.",
                    likeCount = 128_400,
                    publishedText = "hace 3 años",
                    isFromCreator = true,
                    replyCount = 240,
                ),
                VideoComment(
                    id = "2",
                    author = "alguien",
                    authorAvatarUrl = null,
                    text = "Quién sigue escuchando esto en 2026",
                    likeCount = 1_204,
                    publishedText = "hace 2 meses",
                    isFromCreator = false,
                    replyCount = 0,
                ),
            ),
        )
    }
}
