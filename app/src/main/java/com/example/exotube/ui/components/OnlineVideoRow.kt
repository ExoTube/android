package com.example.exotube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.exotube.R
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.ui.formatCompactCount
import com.example.exotube.ui.formatDuration

/**
 * Fila de un video que todavía está en internet: miniatura, título, canal y botón de descarga.
 *
 * Mientras [isResolving] es true se tapa la miniatura con un indicador: resolver la dirección del
 * video tarda unos segundos y hay que avisar de que algo está pasando.
 *
 * Con [onOpenChannel], el nombre del canal sale en verde y al tocarlo se abre el canal. Es un
 * toque aparte del de la fila: tocar el resto sigue reproduciendo el video.
 */
@Composable
fun OnlineVideoRow(
    video: OnlineVideo,
    isResolving: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    /** null donde no tiene sentido (por ejemplo, dentro del propio canal). */
    onOpenChannel: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Thumbnail(video, isResolving, Modifier.width(140.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (onOpenChannel != null && video.channel != null) {
                ChannelLink(video, onOpenChannel)
            } else {
                video.subtitle()?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onDownload) {
            Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = stringResource(R.string.explore_download),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Thumbnail(video: OnlineVideo, isResolving: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            // Debajo siempre hay un degradado verde: si la miniatura no carga, no queda un hueco.
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHighest))),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null, // decorativo: el título ya dice qué es
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        video.durationSeconds?.let { seconds ->
            Text(
                text = formatDuration(seconds),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        if (isResolving) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/**
 * "Canal · 1,2 M de vistas" con el canal tocable. El canal se encoge (con puntos suspensivos) si
 * no cabe, pero las vistas se ven siempre. El relleno vertical agranda la zona del dedo, que con
 * una sola línea de texto pequeño sería difícil de acertar.
 */
@Composable
private fun ChannelLink(video: OnlineVideo, onOpenChannel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = video.channel.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClickLabel = stringResource(R.string.channel_open), onClick = onOpenChannel)
                .padding(vertical = 6.dp),
        )
        video.viewCount?.let {
            Text(
                text = " · " + stringResource(R.string.explore_views, formatCompactCount(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** "Canal · 1,2 M de vistas", saltándose lo que la plataforma no informe. */
@Composable
private fun OnlineVideo.subtitle(): String? {
    val views = viewCount?.let { stringResource(R.string.explore_views, formatCompactCount(it)) }
    return listOfNotNull(channel, views).joinToString(" · ").takeIf { it.isNotEmpty() }
}
