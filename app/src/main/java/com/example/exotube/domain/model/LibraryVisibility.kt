package com.example.exotube.domain.model

/**
 * Qué audios se esconden de la biblioteca.
 *
 * Con el permiso de audio, la biblioteca ve TODO lo que Android considera música: también las
 * notas de voz de WhatsApp, las grabaciones y los efectos de sonido que traen algunos juegos o
 * apps. Casi todo eso dura unos segundos, así que el filtro principal es por duración.
 *
 * La duración vale para todo el audio, también lo descargado con ExoTube (un recorte de unos
 * segundos, por ejemplo). Los videos no se tocan nunca: el filtro es de audios.
 */
data class LibraryVisibility(
    /** Los audios más cortos que esto no se ven, sean del teléfono o descargas. 0 = se ven todos. */
    val minAudioSeconds: Int = DEFAULT_MIN_AUDIO_SECONDS,
    /** Esconder notas de voz y grabaciones, que pueden durar minutos y no son canciones. */
    val hideVoiceNotes: Boolean = true,
) {
    companion object {
        const val DEFAULT_MIN_AUDIO_SECONDS = 30

        /** El tope de la barra: una canción de verdad casi nunca dura menos de dos minutos. */
        const val MAX_MIN_AUDIO_SECONDS = 120
    }
}

/** Lo que queda al aplicar [visibility]. Los videos no se tocan nunca. */
fun List<LibraryItem>.visibleWith(visibility: LibraryVisibility): List<LibraryItem> = filter { item ->
    when {
        item.type != MediaType.AUDIO -> true
        // Una descarga de ExoTube nunca está en una carpeta de notas de voz, así que esto solo
        // afecta a lo que ya estaba en el teléfono.
        visibility.hideVoiceNotes && item.isVoiceNote -> false
        // MediaStore a veces no sabe la duración (0): ante la duda, se enseña.
        item.durationMs <= 0 -> true
        else -> item.durationMs >= visibility.minAudioSeconds * 1_000L
    }
}

/**
 * Si un archivo, por la carpeta donde está, parece una nota de voz o una grabación.
 *
 * Android no tiene una marca fiable para esto en todas las versiones, pero las apps guardan
 * siempre en las mismas carpetas: "WhatsApp Voice Notes" (y la de WhatsApp Business), y
 * "Recordings" o "Voice Recorder" las grabadoras del sistema. Ojo: "WhatsApp Audio" NO entra,
 * porque ahí acaban las canciones que alguien te pasa por el chat.
 */
fun looksLikeVoiceNote(path: String?): Boolean {
    val folder = path?.lowercase() ?: return false
    return VOICE_NOTE_FOLDERS.any { it in folder }
}

private val VOICE_NOTE_FOLDERS = listOf("voice notes", "recordings/", "voice recorder", "call recording")
