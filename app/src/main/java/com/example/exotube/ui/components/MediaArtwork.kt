package com.example.exotube.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.video.videoFramePercent
import com.example.exotube.R
import com.example.exotube.domain.model.MediaType

/**
 * Carátula de un elemento de la biblioteca. Por debajo siempre hay un degradado verde con un
 * icono; encima, en los videos, Coil dibuja un fotograma. Si el fotograma no se puede leer,
 * simplemente se ve el degradado: nunca queda un hueco vacío.
 */
@Composable
fun MediaArtwork(
    uri: String?,
    type: MediaType,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    iconSize: Dp = 24.dp,
    @DrawableRes placeholderIcon: Int = if (type == MediaType.VIDEO) R.drawable.ic_video else R.drawable.ic_audio,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHighest))),
    ) {
        Icon(
            painter = painterResource(placeholderIcon),
            contentDescription = null, // decorativo: el título ya describe el elemento
            tint = colors.primary,
            modifier = Modifier.size(iconSize),
        )
        if (type == MediaType.VIDEO && uri != null) {
            val context = LocalPlatformContext.current
            // Fotograma al 10 % del video: el primero suele ser negro (fundido de entrada).
            val request = remember(uri) {
                ImageRequest.Builder(context).data(uri).videoFramePercent(0.1).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
