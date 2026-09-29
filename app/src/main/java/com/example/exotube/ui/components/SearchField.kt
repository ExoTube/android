package com.example.exotube.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.exotube.R

/**
 * Buscador redondeado, compartido por la Biblioteca y Explorar.
 *
 * La diferencia entre los dos es [onSearch]:
 *  - sin él (Biblioteca), filtra mientras se escribe: la lista ya está en el teléfono;
 *  - con él (Explorar), hay que pulsar la lupa o la tecla "Buscar", porque cada búsqueda sale a
 *    internet y sería absurdo lanzar una por cada letra.
 *
 * En los dos, al pulsar "Buscar" el teclado se cierra: ya se ha dicho lo que se quería y hay que
 * dejar sitio para ver los resultados. [onFocusChange] avisa de si se está escribiendo, para que
 * la pantalla enseñe el menú de predicciones solo mientras tanto.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    // Quitar el foco al buscador es lo que cierra el teclado.
    val focusManager = LocalFocusManager.current
    val search: () -> Unit = {
        onSearch?.invoke()
        focusManager.clearFocus()
    }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(hint) },
        leadingIcon = {
            val icon = painterResource(R.drawable.ic_search)
            if (onSearch == null) {
                Icon(icon, contentDescription = null)
            } else {
                IconButton(onClick = search) {
                    Icon(icon, contentDescription = stringResource(R.string.search_action))
                }
            }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { search() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            // Sin la raya de debajo: con el fondo redondeado queda más limpio.
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .onFocusChanged { onFocusChange(it.isFocused) },
    )
}
