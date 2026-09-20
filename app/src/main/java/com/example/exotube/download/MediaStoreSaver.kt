package com.example.exotube.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.example.exotube.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Archivo ya visible en la galería / app de música. */
data class SavedFile(val uri: Uri, val mimeType: String, val sizeBytes: Long)

/**
 * Copia el archivo descargado (carpeta temporal privada) a Movies/ExoTube o Music/ExoTube,
 * donde lo ven la galería y los reproductores.
 */
class MediaStoreSaver(context: Context) {

    private val appContext = context.applicationContext

    suspend fun save(file: File, type: MediaType): SavedFile = withContext(Dispatchers.IO) {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
            ?: if (type == MediaType.VIDEO) "video/mp4" else "audio/mpeg"
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(file, type, mimeType)
        } else {
            saveLegacy(file, type, mimeType)
        }
        SavedFile(uri, mimeType, file.length())
    }

    /** Android 10+: sin permisos. MediaStore nos da una Uri y escribimos en ella. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(file: File, type: MediaType, mimeType: String): Uri {
        val resolver = appContext.contentResolver
        val collection = when (type) {
            MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaType.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val details = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, MediaFolders.relativePath(type))
            // "Pendiente": otras apps no lo ven hasta que terminemos de copiarlo.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, details) ?: throw IOException("MediaStore rechazó $file")

        try {
            resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("No se pudo escribir en $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // no dejar un archivo a medias en la galería
            throw e
        }
        return uri
    }

    /** Android 8-9: ruta pública clásica (requiere WRITE_EXTERNAL_STORAGE) + escáner de medios. */
    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(file: File, type: MediaType, mimeType: String): Uri {
        val folder = File(
            Environment.getExternalStoragePublicDirectory(MediaFolders.publicDirectory(type)),
            MediaFolders.APP_FOLDER,
        )
        folder.mkdirs()
        val target = generateSequence(0) { it + 1 }
            .map { n -> File(folder, if (n == 0) file.name else "${file.nameWithoutExtension} ($n).${file.extension}") }
            .first { !it.exists() }
        file.copyTo(target)

        // Convertimos una API de callbacks en una función suspend.
        return suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(appContext, arrayOf(target.absolutePath), arrayOf(mimeType)) { _, uri ->
                if (uri != null) {
                    continuation.resume(uri)
                } else {
                    continuation.resumeWithException(IOException("El escáner no indexó $target"))
                }
            }
        }
    }
}
