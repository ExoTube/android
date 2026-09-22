package com.example.exotube.data.library

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.example.exotube.data.history.ListeningDao
import com.example.exotube.data.playlist.PlaylistDao
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.repository.MediaFileDeleter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android necesita que el usuario apruebe borrar este archivo, porque no lo creó ExoTube (por
 * ejemplo, música que ya estaba en el teléfono, o descargas de antes de reinstalar la app).
 *
 * [request] es el diálogo del sistema que hay que abrir. Si el usuario acepta, se vuelve a pedir
 * el borrado: en Android 11 y posteriores el propio sistema ya lo habrá hecho, y el segundo
 * intento encuentra que el archivo ya no está y solo limpia playlists e historial.
 */
class ApprovalRequired(val request: IntentSender) : Exception("Hace falta el permiso del usuario")

/**
 * Borra archivos a través de MediaStore, que es quien lleva la cuenta de la música y los videos
 * del teléfono. Borrar el archivo "a mano" (con File.delete) lo dejaría listado en MediaStore
 * como un fantasma que no se puede reproducir.
 */
class MediaStoreDeleter(
    context: Context,
    private val playlists: PlaylistDao,
    private val history: ListeningDao,
) : MediaFileDeleter {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun delete(item: LibraryItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Primero se mira si sigue ahí. Tras aprobar el diálogo de Android 11+, el sistema
            // ya lo borró; pedirle a MediaStore que borre algo que ya no existe puede responder
            // "sin permiso" y abrir OTRO diálogo, que es justo lo que pasó al probarlo.
            if (exists(item.uri)) deleteFile(item.uri)
            forget(item.uri)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            val approval = approvalFor(item.uri, e)
            if (approval != null) {
                Result.failure(approval)
            } else {
                Log.w(TAG, "Sin permiso para borrar ${item.uri}", e)
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo borrar ${item.uri}", e)
            Result.failure(e)
        }
    }

    private fun exists(uri: String): Boolean =
        resolver.query(uri.toUri(), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.moveToFirst() } == true

    /** Que MediaStore no borre ninguna fila no es un error: el archivo ya no estaba. */
    private fun deleteFile(uri: String) {
        val rows = resolver.delete(uri.toUri(), null, null)
        if (rows == 0) Log.i(TAG, "El archivo ya no estaba: $uri")
    }

    /**
     * Borrado el archivo, se olvida también en las playlists y en el historial. Las playlists ya
     * esconden solas lo que no existe, pero así no se quedan filas huérfanas para siempre, y
     * "Para ti" deja de recomendar a partir de algo que el usuario acaba de tirar.
     */
    private suspend fun forget(uri: String) {
        playlists.removeEverywhere(uri)
        history.forget(uri)
    }

    /**
     * El diálogo del sistema que desbloquea el borrado, o null si Android no ofrece ninguno
     * (antes de Android 10 no hay: sin el permiso de almacenamiento, simplemente no se puede).
     */
    private fun approvalFor(uri: String, error: SecurityException): ApprovalRequired? = when {
        // Android 11+: diálogo propio para borrar, que además hace el borrado él mismo.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            ApprovalRequired(MediaStore.createDeleteRequest(resolver, listOf(uri.toUri())).intentSender)

        // Android 10: el permiso viene dentro de la propia excepción.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && error is RecoverableSecurityException ->
            ApprovalRequired(error.userAction.actionIntent.intentSender)

        else -> null
    }

    private companion object {
        const val TAG = "MediaStoreDeleter"
    }
}
