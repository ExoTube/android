package com.example.exotube.data.search

import android.content.Context
import androidx.core.content.edit
import com.example.exotube.domain.repository.SearchHistoryRepository
import com.example.exotube.domain.repository.rememberQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Las búsquedas recientes, guardadas en el teléfono. Nunca salen de él: no hay cuenta ni servidor.
 *
 * Se guardan como un solo texto, una búsqueda por línea: SharedPreferences no guarda listas
 * ordenadas (su "conjunto" de textos pierde el orden, y aquí el orden es justo lo que importa).
 */
class SharedPrefsSearchHistory(context: Context) : SearchHistoryRepository {

    private val preferences =
        context.applicationContext.getSharedPreferences("busquedas", Context.MODE_PRIVATE)

    private val _recent = MutableStateFlow(
        preferences.getString(KEY_RECENT, null)?.lines()?.filter { it.isNotBlank() }.orEmpty(),
    )
    override val recent: StateFlow<List<String>> = _recent.asStateFlow()

    override fun remember(query: String) = save(rememberQuery(_recent.value, query))

    override fun forget(query: String) = save(_recent.value.filterNot { it.equals(query, ignoreCase = true) })

    private fun save(list: List<String>) {
        _recent.value = list
        // Una búsqueda no puede llevar saltos de línea (el buscador es de una sola línea).
        preferences.edit { putString(KEY_RECENT, list.joinToString("\n")) }
    }

    private companion object {
        const val KEY_RECENT = "recientes"
    }
}
