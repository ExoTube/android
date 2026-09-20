package com.example.exotube.data.library

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.repository.LibraryRepository
import com.example.exotube.download.MediaFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Lee la biblioteca desde MediaStore (la base de datos de medios de Android).
 *
 * Qué se muestra:
 *  - VIDEO: solo lo descargado por ExoTube (Movies/ExoTube). Una app siempre ve sus propios archivos.
 *  - AUDIO: toda la música del teléfono SI el usuario concedió el permiso de audio; si no,
 *    solo la carpeta Music/ExoTube.
 */
class MediaStoreLibraryRepository(context: Context) : LibraryRepository {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override fun observeDownloads(): Flow<List<LibraryItem>> = callbackFlow {
        // ContentObserver es una API de callbacks; callbackFlow la convierte en Flow.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        MediaType.entries.forEach { resolver.registerContentObserver(collection(it), true, observer) }
        trySend(Unit) // primera carga
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
        // Una descarga provoca varios avisos seguidos; conflate descarta los que se acumulan.
        .conflate()
        .map { queryAll() }
        .flowOn(Dispatchers.IO) // las consultas a disco, fuera del hilo principal

    private fun queryAll(): List<LibraryItem> =
        MediaType.entries.flatMap(::query).sortedByDescending { it.dateAddedSeconds }

    private fun query(type: MediaType): List<LibraryItem> {
        val collection = collection(type)
        // En Android 10+ la carpeta está en RELATIVE_PATH ("Music/ExoTube/"); antes, en la ruta completa.
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA
        }
        val artistColumn = if (type == MediaType.VIDEO) MediaStore.Video.Media.ARTIST else MediaStore.Audio.Media.ARTIST
        val durationColumn = if (type == MediaType.VIDEO) MediaStore.Video.Media.DURATION else MediaStore.Audio.Media.DURATION
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            artistColumn,
            durationColumn,
            pathColumn,
        )
        val (selection, arguments) = selectionFor(type, pathColumn)

        val items = mutableListOf<LibraryItem>()
        resolver.query(collection, projection, selection, arguments, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val artistIndex = cursor.getColumnIndexOrThrow(artistColumn)
            val durationIndex = cursor.getColumnIndexOrThrow(durationColumn)
            val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val displayName = cursor.getString(nameIndex).orEmpty()
                items += LibraryItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    title = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() }
                        ?: displayName.substringBeforeLast('.'),
                    artist = cursor.getString(artistIndex)?.takeIf { it.isNotBlank() && it != UNKNOWN_ARTIST },
                    type = type,
                    durationMs = cursor.getLong(durationIndex),
                    sizeBytes = cursor.getLong(sizeIndex),
                    dateAddedSeconds = cursor.getLong(dateIndex),
                    isDownload = cursor.isInAppFolder(pathIndex),
                )
            }
        }
        return items
    }

    /**
     * Qué filas pedimos:
     *  - Video, o audio sin permiso: solo la carpeta de la app.
     *  - Audio con permiso: toda la música. IS_MUSIC deja fuera tonos de llamada, alarmas y
     *    sonidos de notificación, que MediaStore marca con 0.
     *
     * IFNULL: cuando Android no clasificó el archivo, la columna llega vacía (NULL). En SQL,
     * "NULL != 0" no es cierto, así que esos archivos desaparecerían de la biblioteca aunque sean
     * música normal. Ante la duda, preferimos mostrarlos.
     */
    private fun selectionFor(type: MediaType, pathColumn: String): Pair<String, Array<String>> {
        val onlyAppFolder = "$pathColumn LIKE ?" to arrayOf(folderPattern(type))
        if (type == MediaType.VIDEO || !AudioLibraryPermission.isGranted(appContext)) return onlyAppFolder
        return "IFNULL(${MediaStore.Audio.Media.IS_MUSIC}, 1) != 0" to emptyArray()
    }

    private fun folderPattern(type: MediaType): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaFolders.relativePath(type)}%"
        } else {
            "%/${MediaFolders.relativePath(type)}%"
        }

    /** Distingue lo descargado con ExoTube de lo que ya estaba en el teléfono. */
    private fun Cursor.isInAppFolder(pathIndex: Int): Boolean =
        getString(pathIndex)?.contains("${MediaFolders.APP_FOLDER}/") == true

    private fun collection(type: MediaType): Uri = when (type) {
        MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    private companion object {
        const val UNKNOWN_ARTIST = "<unknown>" // valor que pone MediaStore cuando no hay artista
    }
}
