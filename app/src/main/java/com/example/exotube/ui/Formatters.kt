package com.example.exotube.ui

import java.util.Locale

/** 212 → "3:32"; 3725 → "1:02:05". */
fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, rest)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, rest)
    }
}

/**
 * Duración total de una playlist, redondeada al minuto: 4:52 → "5 min"; 1:05:00 → "1 h 5 min".
 * Nunca muestra "0 min" si hay algo que reproducir.
 */
fun formatTotalDuration(totalMillis: Long): String {
    if (totalMillis <= 0) return "0 min"
    val totalMinutes = ((totalMillis + 30_000) / 60_000).coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
