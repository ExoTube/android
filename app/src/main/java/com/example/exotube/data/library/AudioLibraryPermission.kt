package com.example.exotube.data.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Permiso para leer la música que el usuario ya tiene en el teléfono (MP3 de otras apps).
 * Sin él, Android solo deja ver los archivos que creó ExoTube.
 *
 * El nombre del permiso cambió en Android 13: antes era el de almacenamiento y ahora existe
 * uno específico para audio, que es mucho más acotado.
 */
object AudioLibraryPermission {

    val name: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
}
