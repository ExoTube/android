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
 * Pasa a minúsculas y quita las tildes: "Canción" → "cancion".
 *
 * Normalizer.Form.NFD separa cada letra acentuada en dos caracteres (la letra y el acento suelto);
 * borrando los acentos sueltos queda la letra pelada.
 */
private fun String.normalizeForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

/** Los acentos que NFD deja sueltos son "marcas sin espacio" (Mn en la tabla Unicode). */
private val COMBINING_MARKS = Regex("\\p{Mn}+")
