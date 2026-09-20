package com.example.exotube.domain.model

/** Un archivo descargado que ya está en el teléfono y se puede reproducir. */
data class LibraryItem(
    val id: Long,
    /** Uri "content://" de MediaStore, como texto para no depender de android.net.Uri. */
    val uri: String,
    val title: String,
    /** Artista/canal si el archivo lo trae en sus metadatos. */
    val artist: String?,
    val type: MediaType,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    /** true si lo descargó ExoTube; false si ya estaba en el teléfono (música de otras apps). */
    val isDownload: Boolean = true,
)
