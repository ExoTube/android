package com.example.exotube.download

import android.os.Environment
import com.example.exotube.domain.model.MediaType

/** Dónde guarda ExoTube sus archivos. Lo comparten quien guarda (MediaStoreSaver) y quien lee (biblioteca). */
internal object MediaFolders {

    const val APP_FOLDER = "ExoTube"

    /** "Movies" o "Music". */
    fun publicDirectory(type: MediaType): String = when (type) {
        MediaType.VIDEO -> Environment.DIRECTORY_MOVIES
        MediaType.AUDIO -> Environment.DIRECTORY_MUSIC
    }

    /** Ruta relativa que usa MediaStore en Android 10+: "Movies/ExoTube/". */
    fun relativePath(type: MediaType): String = "${publicDirectory(type)}/$APP_FOLDER/"
}
