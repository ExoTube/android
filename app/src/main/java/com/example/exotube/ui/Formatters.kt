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

/**
 * Números grandes en corto: 9800 -> "9,8 mil"; 1240000 -> "1,2 M".
 * Por debajo de diez se deja un decimal; por encima estorba más que informa.
 */
fun formatCompactCount(count: Long): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> shorten(count / 1_000.0, "mil")
    else -> shorten(count / 1_000_000.0, "M")
}

private fun shorten(value: Double, suffix: String): String {
    val pattern = if (value >= 10) "%.0f" else "%.1f"
    return "${pattern.format(Locale.getDefault(), value)} $suffix"
}

/**
 * 12345 -> "0:12.3". Para recortar hace falta ver las décimas: con solo los segundos no
 * se puede afinar dónde empieza un estribillo.
 */
fun formatPreciseTime(millis: Long): String {
    val total = millis.coerceAtLeast(0)
    val minutes = total / 60_000
    val seconds = (total % 60_000) / 1_000
    val tenths = (total % 1_000) / 100
    return "%d:%02d.%d".format(Locale.ROOT, minutes, seconds, tenths)
}
