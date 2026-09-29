package com.example.exotube.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Una línea del menú de predicciones, con el mismo aspecto en Explorar y en la Biblioteca.
 *
 * Lo que el usuario ya escribió va normal y lo que falta, en negrita, como en YouTube: la vista va
 * directa a la parte nueva, que es la que decide si es la búsqueda buscada.
 *
 * @param action botón opcional a la derecha (copiar al buscador, o borrar una búsqueda reciente).
 */
@Composable
fun SearchSuggestionRow(
    text: String,
    typed: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    action: SuggestionAction? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 32.dp, end = 12.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = highlightCompletion(text, typed),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        if (action != null) {
            IconButton(onClick = action.onClick) {
                Icon(
                    painter = painterResource(action.icon),
                    contentDescription = action.description,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** El botón pequeño a la derecha de una predicción. */
class SuggestionAction(@DrawableRes val icon: Int, val description: String, val onClick: () -> Unit)

/** Negrita a partir de lo escrito si la predicción empieza por ello; si no, toda normal. */
private fun highlightCompletion(text: String, typed: String) = buildAnnotatedString {
    val start = typed.trim()
    if (start.isNotEmpty() && text.startsWith(start, ignoreCase = true)) {
        append(text.substring(0, start.length))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(start.length)) }
    } else {
        append(text)
    }
}

/**
 * Al empezar a desplazar la lista se esconde el teclado, para ver lo que hay debajo. Solo el
 * teclado: el buscador conserva el foco, así el menú de predicciones sigue a mano.
 */
@Composable
fun HideKeyboardOnScroll(listState: LazyListState) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collect { keyboard?.hide() }
    }
}
