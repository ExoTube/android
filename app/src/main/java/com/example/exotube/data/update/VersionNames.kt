package com.example.exotube.data.update

/**
 * Compara dos versiones escritas como "1.2" o "v1.10".
 *
 * Hace falta comparar por NÚMERO y no por texto, que es el error clásico: como texto, "1.10" es
 * menor que "1.9" (porque el "1" va antes del "9"), y la app se quedaría sin avisar de una
 * actualización hasta la versión 2. Por partes, 1.10 es mayor, que es lo correcto.
 *
 * Reglas:
 *  - se ignora la "v" que lleva la etiqueta de la publicación;
 *  - las partes que falten cuentan como cero, así que "1.2" y "1.2.0" son la misma;
 *  - si algo no se entiende como número, se responde que NO hay novedad. Es lo prudente:
 *    antes molestar de menos que mandar a alguien a instalar algo raro.
 */
internal fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = versionParts(remote) ?: return false
    val localParts = versionParts(local) ?: return false

    for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
        val remotePart = remoteParts.getOrElse(i) { 0 }
        val localPart = localParts.getOrElse(i) { 0 }
        if (remotePart != localPart) return remotePart > localPart
    }
    return false // exactamente la misma versión
}

/** "v1.2.3" → [1, 2, 3]. null si no es una versión de números. */
private fun versionParts(version: String): List<Int>? {
    val cleaned = version.trim().removePrefix("v").removePrefix("V")
    if (cleaned.isEmpty()) return null
    return cleaned.split('.').map { part -> part.toIntOrNull() ?: return null }
}
