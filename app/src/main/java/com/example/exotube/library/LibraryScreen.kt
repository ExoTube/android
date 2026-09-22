package com.example.exotube.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.exotube.R
import com.example.exotube.data.library.AudioLibraryPermission
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.ui.components.MediaRow
import com.example.exotube.ui.components.RowAction
import com.example.exotube.ui.components.SearchField
import com.example.exotube.ui.theme.ExoTubeTheme

/** Conecta el ViewModel con la pantalla. Reproducir y "añadir a playlist" los resuelve el padre. */
@Composable
fun LibraryRoute(
    nowPlayingUri: String?,
    isPlaying: Boolean,
    onPlay: (items: List<LibraryItem>, startIndex: Int) -> Unit,
    onAddToPlaylist: (LibraryItem) -> Unit,
    onTrim: (LibraryItem) -> Unit,
    onChangeCover: (LibraryItem) -> Unit,
    onRename: (LibraryItem) -> Unit,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canReadPhoneMusic by remember { mutableStateOf(AudioLibraryPermission.isGranted(context)) }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canReadPhoneMusic = granted
        if (granted) viewModel.reload()
    }
    // El usuario puede cambiar el permiso desde los Ajustes del sistema: al volver, lo comprobamos.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = AudioLibraryPermission.isGranted(context)
        if (granted != canReadPhoneMusic) {
            canReadPhoneMusic = granted
            viewModel.reload()
        }
    }

    LibraryScreen(
        state = state,
        nowPlayingUri = nowPlayingUri,
        isPlaying = isPlaying,
        canReadPhoneMusic = canReadPhoneMusic,
        onFilterSelected = viewModel::onFilterSelected,
        onQueryChange = viewModel::onQueryChange,
        onItemClick = { index -> onPlay(state.visibleItems, index) },
        onAddToPlaylist = onAddToPlaylist,
        onTrim = onTrim,
        onChangeCover = onChangeCover,
        onRename = onRename,
        onAllowPhoneMusic = { requestPermission.launch(AudioLibraryPermission.name) },
        contentPadding = contentPadding,
    )
}

/** Pantalla "tonta": solo dibuja el estado que recibe. Por eso se puede previsualizar sin reproductor. */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    nowPlayingUri: String?,
    isPlaying: Boolean,
    canReadPhoneMusic: Boolean,
    onFilterSelected: (LibraryFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onItemClick: (index: Int) -> Unit,
    onAddToPlaylist: (LibraryItem) -> Unit,
    onTrim: (LibraryItem) -> Unit,
    onChangeCover: (LibraryItem) -> Unit,
    onRename: (LibraryItem) -> Unit,
    onAllowPhoneMusic: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(contentPadding = contentPadding, modifier = modifier.fillMaxSize()) {
        item { LibraryHeader(state) }
        item {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                hint = stringResource(R.string.library_search_hint),
            )
        }
        item { FilterRow(selected = state.filter, onFilterSelected = onFilterSelected) }
        // Solo tiene sentido ofrecerlo mientras se ve audio: los videos nunca salen del teléfono.
        if (!canReadPhoneMusic && state.filter != LibraryFilter.VIDEO) {
            item { PhoneMusicBanner(onAllow = onAllowPhoneMusic) }
        }

        when {
            state.isLoading -> item {
                Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.visibleItems.isEmpty() -> item {
                EmptyLibrary(state = state, modifier = Modifier.fillParentMaxHeight(0.7f))
            }
            else -> itemsIndexed(state.visibleItems, key = { _, item -> item.uri }) { index, item ->
                MediaRow(
                    item = item,
                    isCurrent = item.uri == nowPlayingUri,
                    onClick = { onItemClick(index) },
                    isPlaying = isPlaying,
                    actions = buildList {
                        add(RowAction(R.string.add_to_playlist, R.drawable.ic_playlist_add) { onAddToPlaylist(item) })
                        // Recortar y cambiar la portada son cosas de canciones: un video no
                        // tiene carátula ni se recorta desde aquí.
                        if (item.type == MediaType.AUDIO) {
                            add(RowAction(R.string.trim_action, R.drawable.ic_cut) { onTrim(item) })
                            add(RowAction(R.string.cover_action, R.drawable.ic_image) { onChangeCover(item) })
                            add(RowAction(R.string.rename_action, R.drawable.ic_edit) { onRename(item) })
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(state: LibraryUiState) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
        // Logotipo de texto: "Exo" en blanco y "Tube" en verde.
        Text(
            text = buildAnnotatedString {
                append("Exo")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Tube") }
            },
            style = MaterialTheme.typography.headlineLarge,
        )
        val downloads = pluralStringResource(R.plurals.library_count, state.downloadsCount, state.downloadsCount)
        Text(
            // Con música del teléfono añadimos cuántas canciones son de fuera.
            text = if (state.phoneMusicCount > 0) {
                stringResource(R.string.library_count_mixed, downloads, state.phoneMusicCount)
            } else {
                downloads
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Invitación a dar el permiso de audio para ver también la música que ya está en el teléfono. */
@Composable
private fun PhoneMusicBanner(onAllow: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_audio),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.library_phone_music_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.library_phone_music_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onAllow) { Text(stringResource(R.string.library_phone_music_allow)) }
        }
    }
}

@Composable
private fun FilterRow(selected: LibraryFilter, onFilterSelected: (LibraryFilter) -> Unit) {
    // LazyRow: con cuatro filtros ya no caben todos en pantallas estrechas.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        items(LibraryFilter.entries) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringResource(filter.labelRes)) },
                leadingIcon = filter.iconRes?.let { icon ->
                    { Icon(painterResource(icon), contentDescription = null, Modifier.size(18.dp)) }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** Tres situaciones distintas con la misma pinta: sin descargas, sin resultados de filtro, o de búsqueda. */
@Composable
private fun EmptyLibrary(state: LibraryUiState, modifier: Modifier = Modifier) {
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
                painter = painterResource(
                    if (state.isSearching) R.drawable.ic_search else R.drawable.ic_library_music,
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = when {
                state.isSearching -> stringResource(R.string.library_empty_search, state.query.trim())
                state.allItems.isNotEmpty() -> stringResource(R.string.library_empty_filtered)
                else -> stringResource(R.string.library_empty_title)
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        // Las instrucciones solo ayudan cuando todavía no hay nada descargado.
        if (!state.isSearching && state.allItems.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val LibraryFilter.labelRes: Int
    get() = when (this) {
        LibraryFilter.ALL -> R.string.library_filter_all
        LibraryFilter.DOWNLOADS -> R.string.library_filter_downloads
        LibraryFilter.AUDIO -> R.string.library_filter_audio
        LibraryFilter.VIDEO -> R.string.library_filter_video
    }

private val LibraryFilter.iconRes: Int?
    get() = when (this) {
        LibraryFilter.ALL -> null
        LibraryFilter.DOWNLOADS -> R.drawable.ic_library_music
        LibraryFilter.AUDIO -> R.drawable.ic_audio
        LibraryFilter.VIDEO -> R.drawable.ic_video
    }

// --- Previews: para iterar el diseño (y compararlo con Figma) sin ejecutar la app ---

private val previewItems = listOf(
    LibraryItem(1, "content://preview/1", "Canción de ejemplo", "Artista", MediaType.AUDIO, 212_000, 5_100_000, 3),
    LibraryItem(2, "content://preview/2", "Video musical en directo", null, MediaType.VIDEO, 245_000, 48_300_000, 2),
    LibraryItem(3, "content://preview/3", "Canción del teléfono", "Otro artista", MediaType.AUDIO, 180_000, 4_200_000, 1, isDownload = false),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LibraryScreenPreview() {
    ExoTubeTheme {
        LibraryScreen(
            state = LibraryUiState(allItems = previewItems, isLoading = false),
            nowPlayingUri = "content://preview/2",
            isPlaying = true,
            canReadPhoneMusic = true,
            onFilterSelected = {},
            onQueryChange = {},
            onItemClick = {},
            onAddToPlaylist = {},
            onTrim = {},
            onChangeCover = {},
            onRename = {},
            onAllowPhoneMusic = {},
            contentPadding = PaddingValues(),
        )
    }
}

/** Con el aviso para dar permiso a la música del teléfono. */
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LibraryScreenWithBannerPreview() {
    ExoTubeTheme {
        LibraryScreen(
            state = LibraryUiState(allItems = previewItems.take(2), isLoading = false),
            nowPlayingUri = null,
            isPlaying = false,
            canReadPhoneMusic = false,
            onFilterSelected = {},
            onQueryChange = {},
            onItemClick = {},
            onAddToPlaylist = {},
            onTrim = {},
            onChangeCover = {},
            onRename = {},
            onAllowPhoneMusic = {},
            contentPadding = PaddingValues(),
        )
    }
}

/** Una búsqueda que no encuentra nada. */
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LibrarySearchWithoutResultsPreview() {
    ExoTubeTheme {
        LibraryScreen(
            state = LibraryUiState(allItems = previewItems, query = "reggaeton", isLoading = false),
            nowPlayingUri = null,
            isPlaying = false,
            canReadPhoneMusic = true,
            onFilterSelected = {},
            onQueryChange = {},
            onItemClick = {},
            onAddToPlaylist = {},
            onTrim = {},
            onChangeCover = {},
            onRename = {},
            onAllowPhoneMusic = {},
            contentPadding = PaddingValues(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun EmptyLibraryPreview() {
    ExoTubeTheme {
        LibraryScreen(
            state = LibraryUiState(isLoading = false),
            nowPlayingUri = null,
            isPlaying = false,
            canReadPhoneMusic = true,
            onFilterSelected = {},
            onQueryChange = {},
            onItemClick = {},
            onAddToPlaylist = {},
            onTrim = {},
            onChangeCover = {},
            onRename = {},
            onAllowPhoneMusic = {},
            contentPadding = PaddingValues(),
        )
    }
}
