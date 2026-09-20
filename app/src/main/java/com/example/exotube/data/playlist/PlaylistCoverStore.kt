package com.example.exotube.data.playlist

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Guarda las fotos de portada de las playlists en el almacenamiento PRIVADO de la app.
 *
 * El selector de fotos solo da permiso temporal para leer la imagen, y el usuario podría
 * borrarla de la galería. Por eso hacemos una copia propia, reducida (una portada no necesita
 * una foto de 12 MP) y en JPEG.
 */
class PlaylistCoverStore(context: Context) {

    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "playlist_covers")

    /** @return la ruta de la copia, o null si la imagen no se pudo leer. */
    suspend fun save(playlistId: Long, sourceUri: String): String? = withContext(Dispatchers.IO) {
        // Coil ya sabe leer una Uri, reducirla y girarla según los datos EXIF de la cámara.
        val request = ImageRequest.Builder(appContext)
            .data(sourceUri)
            .size(MAX_SIZE_PX)
            .allowHardware(false) // los bitmaps "hardware" no se pueden comprimir a archivo
            .build()
        val bitmap = (appContext.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
        if (bitmap == null) {
            Log.w(TAG, "No se pudo leer la imagen elegida")
            return@withContext null
        }
        directory.mkdirs()
        // Nombre único en cada cambio: si reutilizáramos el nombre, Coil mostraría la foto vieja
        // desde su caché.
        val file = File(directory, "cover_${playlistId}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        file.absolutePath
    }

    fun delete(path: String?) {
        path?.let { File(it).delete() }
    }

    private companion object {
        const val TAG = "PlaylistCoverStore"
        const val MAX_SIZE_PX = 1024
        const val JPEG_QUALITY = 85
    }
}
