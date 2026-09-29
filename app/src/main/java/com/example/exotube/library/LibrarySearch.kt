package com.example.exotube.library

import com.example.exotube.domain.model.LibraryItem
import java.text.Normalizer

/**
 * Búsqueda "como la espera una persona": no distingue mayúsculas ni tildes y mira tanto el título
 * como el artista. Así "cancion" encuentra "Canción" y "shakira" encuentra "Shakira".
 *
 * Es Kotlin puro, sin Android: se puede probar con un test normal (ver LibrarySearchTest).
 */
internal fun LibraryItem.matchesQuery(query: String): Boolean {
    val needle = query.normalizeForSearch()
    if (needle.isEmpty()) return true // sin texto escrito, todo vale
    return title.normalizeForSearch().contains(needle) ||
        artist?.normalizeForSearch()?.contains(needle) == true
}

/**
 * Artistas de la biblioteca que encajan con lo escrito, para el menú de predicciones. Primero los
 * que empiezan por ello ("sod" → "Soda Stereo") y luego los que solo lo contienen. Es todo local:
 * sale al instante, sin internet.
 */
internal fun artistSuggestions(items: List<LibraryItem>, query: String): List<String> {
    val typed = query.normalizeForSearch()
    if (typed.length < MIN_TYPED_FOR_ARTISTS) return emptyList()
    return items
        .mapNotNull { it.artist?.trim()?.takeIf(String::isNotEmpty) }
        .distinctBy { it.normalizeForSearch() }
        .filter { artist -> artist.normalizeForSearch().let { it.contains(typed) && it != typed } }
        .sortedBy { !it.normalizeForSearch().startsWith(typed) }
        .take(MAX_ARTIST_SUGGESTIONS)
}

private const val MIN_TYPED_FOR_ARTISTS = 2
private const val MAX_ARTIST_SUGGESTIONS = 4

/**
 * Pasa a minúsculas y quita las tildes: "Canción" → "cancion".
 *
 * Normalizer.Form.NFD separa cada letra acentuada en dos caracteres (la letra y el acento suelto);
 * borrando los acentos sueltos queda la letra pelada.
 */
internal fun String.normalizeForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

/** Los acentos que NFD deja sueltos son "marcas sin espacio" (Mn en la tabla Unicode). */
private val COMBINING_MARKS = Regex("\\p{Mn}+")
