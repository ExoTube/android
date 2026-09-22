package com.example.exotube.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.exotube.R
import com.example.exotube.domain.model.OnlineVideo

/**
 * Pide más cuando faltan pocas filas para llegar al final, para que al seguir bajando ya estén
 * ahí y no se note la espera.
 *
 * Se vuelve a mirar también cuando cambia [itemCount]: si una tanda fue corta y la lista sigue
 * viéndose entera, hace falta otra sin esperar a que el usuario mueva el dedo.
 */
@Composable
fun LoadMoreWhenNearEnd(listState: LazyListState, itemCount: Int, onLoadMore: () -> Unit) {
    val isNearEnd by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(isNearEnd, itemCount) {
        if (isNearEnd) onLoadMore()
    }
}

/** Cuántas filas antes del final se piden más. */
private const val LOAD_MORE_AHEAD = 5

/** Debajo de la última fila: la rueda mientras llegan más, o "Reintentar" si fallaron. */
@Composable
fun LoadMoreFooter(isLoading: Boolean, failed: Boolean, onRetry: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.explore_more_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.sheet_retry)) }
            }
            else -> Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Para descargar un video en línea se reutiliza tal cual el flujo de "Compartir": se le manda el
 * enlace a nuestra propia ShareActivity, igual que haría YouTube. Así la hoja de calidades, los
 * permisos y la descarga en segundo plano son EXACTAMENTE el mismo código, sin duplicar nada.
 */
fun shareToSelf(video: OnlineVideo) = Intent(Intent.ACTION_SEND).apply {
    setPackage("com.example.exotube")
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, video.url)
}
