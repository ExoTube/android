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
    /**
     * Álbum al que pertenece, si el archivo lo trae etiquetado. Los videos nunca lo tienen, y
     * muchas descargas de YouTube tampoco: solo las canciones publicadas como música lo llevan.
     */
    val album: String? = null,
    /** Número de pista dentro del álbum, para poder ordenarlo como en el disco original. */
    val trackNumber: Int? = null,
    /** Nota de voz o grabación (por su carpeta): el filtro de la biblioteca puede esconderla. */
    val isVoiceNote: Boolean = false,
)
