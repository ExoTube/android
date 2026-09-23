package com.example.exotube.ui.components

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.exotube.R
import com.example.exotube.data.fake.sampleMediaInfo
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.MediaFormat
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.model.MediaType
import com.example.exotube.share.ShareUiState
import com.example.exotube.ui.formatDuration
import com.example.exotube.ui.messageRes
import com.example.exotube.ui.theme.ExoTubeTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Bottom Sheet de descarga. Es "stateless": no conoce al ViewModel, solo recibe el estado y
 * avisa de los clics mediante lambdas (state hoisting). Así es fácil de previsualizar y testear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetDownload(
    uiState: ShareUiState,
    onFormatSelected: (MediaFormat) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val scope = rememberCoroutineScope()
    val hideAndDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, // deslizar hacia abajo o tocar fuera
        sheetState = sheetState,
        modifier = modifier,
    ) {
        when (uiState) {
            ShareUiState.Loading -> LoadingContent()
            is ShareUiState.Ready -> FormatPicker(uiState.media, onFormatSelected, isRefining = uiState.isRefining)
            // Mantiene la lista visible (con la opción marcada) mientras la hoja se oculta.
            is ShareUiState.DownloadStarted ->
                FormatPicker(uiState.media, onFormatSelected = {}, selected = uiState.format)
            is ShareUiState.Error -> ErrorContent(uiState.error, onRetry, onClose = hideAndDismiss)
        }
    }
}

@Composable
private fun FormatPicker(
    media: MediaInfo,
    onFormatSelected: (MediaFormat) -> Unit,
    selected: MediaFormat? = null,
    isRefining: Boolean = false,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item { MediaHeader(media) }
        // Una línea fina que se mueve: ya se puede elegir, pero todavía llegan más calidades.
        item {
            if (isRefining) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.sheet_refining),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
        formatSection(R.string.sheet_section_video, media.videoFormats, selected, onFormatSelected)
        formatSection(R.string.sheet_section_audio, media.audioFormats, selected, onFormatSelected)
    }
}

/** Añade un título de sección y sus formatos a la lista; no añade nada si la lista está vacía. */
private fun LazyListScope.formatSection(
    @StringRes titleRes: Int,
    formats: List<MediaFormat>,
    selected: MediaFormat?,
    onFormatSelected: (MediaFormat) -> Unit,
) {
    if (formats.isEmpty()) return
    item {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
    items(formats, key = { it.formatId + it.extension }) { format ->
        FormatRow(format, isSelected = format == selected, onClick = { onFormatSelected(format) })
    }
}

@Composable
private fun FormatRow(format: MediaFormat, isSelected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val size = format.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }
        ?: stringResource(R.string.sheet_size_unknown)
    @DrawableRes val icon = if (format.type == MediaType.VIDEO) R.drawable.ic_video else R.drawable.ic_audio

    ListItem(
        headlineContent = { Text(format.label) },
        supportingContent = { Text(size) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Text(format.extension.uppercase(Locale.ROOT)) },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun MediaHeader(media: MediaInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = media.thumbnailUrl,
            contentDescription = stringResource(R.string.sheet_thumbnail_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 112.dp, height = 63.dp) // 16:9
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(media.platform.displayName, media.durationSeconds?.let(::formatDuration))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.sheet_loading), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorContent(error: MediaError, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.sheet_close)) }
            if (error.isRetryable) {
                Button(onClick = onRetry) { Text(stringResource(R.string.sheet_retry)) }
            }
        }
    }
}

// --- Previews: el contenido de la hoja, sin el ModalBottomSheet (que no se previsualiza bien) ---

@Preview(showBackground = true)
@Composable
private fun FormatPickerPreview() {
    ExoTubeTheme { FormatPicker(sampleMediaInfo(), onFormatSelected = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ErrorContentPreview() {
    ExoTubeTheme { ErrorContent(MediaError.NoConnection, onRetry = {}, onClose = {}) }
}
