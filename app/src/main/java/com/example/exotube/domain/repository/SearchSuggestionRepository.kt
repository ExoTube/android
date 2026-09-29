package com.example.exotube.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Las predicciones del buscador de Explorar: lo que la gente suele buscar al escribir esto. */
fun interface SearchSuggestionRepository {
    /** Lanza si no hay red; quien llama decide qué enseñar entonces (normalmente, nada). */
    suspend fun suggest(query: String): List<String>
}

/** Las últimas búsquedas del usuario, para ofrecerlas antes de que escriba nada. */
interface SearchHistoryRepository {
    /** De la más reciente a la más antigua. */
    val recent: StateFlow<List<String>>

    fun remember(query: String)

    fun forget(query: String)
}

/** Sin predicciones: para las pruebas y para cuando no hay de dónde sacarlas. */
object NoSuggestions : SearchSuggestionRepository {
    override suspend fun suggest(query: String): List<String> = emptyList()
}

/** Un historial que vive solo mientras dura la app. Sirve para las pruebas. */
class InMemorySearchHistory(initial: List<String> = emptyList()) : SearchHistoryRepository {
    private val _recent = MutableStateFlow(initial)
    override val recent: StateFlow<List<String>> = _recent.asStateFlow()

    override fun remember(query: String) {
        _recent.value = rememberQuery(_recent.value, query)
    }

    override fun forget(query: String) {
        _recent.value = _recent.value.filterNot { it.equals(query, ignoreCase = true) }
    }
}

/**
 * La lista de recientes tras buscar [query]: arriba del todo, sin repetirse (aunque cambien las
 * mayúsculas) y como mucho [MAX_RECENT_SEARCHES].
 */
fun rememberQuery(recent: List<String>, query: String): List<String> {
    val clean = query.trim()
    if (clean.isEmpty()) return recent
    return (listOf(clean) + recent.filterNot { it.equals(clean, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
}

const val MAX_RECENT_SEARCHES = 20
