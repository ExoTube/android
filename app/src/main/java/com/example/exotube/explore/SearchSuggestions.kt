package com.example.exotube.explore

import com.example.exotube.library.normalizeForSearch

/** Una línea del menú de predicciones del buscador. */
data class SearchSuggestion(
    val text: String,
    /** true si es una búsqueda que ya hizo el usuario (lleva el reloj y se puede borrar). */
    val isRecent: Boolean,
)

/**
 * Qué enseñar debajo del buscador mientras se escribe, como en YouTube.
 *
 *  - Sin nada escrito: las últimas búsquedas, para repetir una con un toque.
 *  - Escribiendo: primero las búsquedas recientes que empiezan igual (suelen ser lo que se busca)
 *    y después las predicciones de YouTube, sin repetir ninguna.
 *
 * No distingue mayúsculas ni tildes: "cancion" encuentra la reciente "Canción".
 */
internal fun buildSuggestions(query: String, recent: List<String>, predictions: List<String>): List<SearchSuggestion> {
    val typed = query.normalizeForSearch()
    if (typed.isEmpty()) return recent.take(MAX_RECENT_SHOWN).map { SearchSuggestion(it, isRecent = true) }

    val recentMatches = recent
        .filter { it.normalizeForSearch().startsWith(typed) && it.normalizeForSearch() != typed }
        .take(MAX_RECENT_WHILE_TYPING)
    val seen = recentMatches.map { it.normalizeForSearch() }.toMutableSet()
    val fresh = predictions.filter { prediction -> seen.add(prediction.normalizeForSearch()) }

    return (recentMatches.map { SearchSuggestion(it, isRecent = true) } + fresh.map { SearchSuggestion(it, isRecent = false) })
        .take(MAX_SUGGESTIONS)
}

/**
 * Las predicciones que se pidieron para [askedFor] y todavía valen para [current]: al escribir
 * una letra más, las que ya no empiezan por lo escrito sobran. Así el menú se va estrechando al
 * momento, sin esperar a que YouTube conteste la letra nueva. Si se borró lo escrito o se cambió
 * por otra cosa, no vale ninguna.
 */
internal fun predictionsStillValid(current: String, askedFor: String, predictions: List<String>): List<String> {
    val now = current.normalizeForSearch()
    val then = askedFor.normalizeForSearch()
    if (now.isEmpty() || !now.startsWith(then)) return emptyList()
    return predictions.filter { it.normalizeForSearch().startsWith(now) }
}

private const val MAX_RECENT_SHOWN = 6
private const val MAX_RECENT_WHILE_TYPING = 2
private const val MAX_SUGGESTIONS = 8
