package com.example.exotube.data.history

import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.repository.DownloadHistoryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Guarda el historial en Supabase con un usuario ANÓNIMO: sin email ni contraseña.
 * Supabase crea un usuario con un id aleatorio y la sesión queda guardada en el teléfono,
 * así que todas las descargas de este dispositivo comparten el mismo dueño.
 */
class SupabaseHistoryRepository(private val supabase: SupabaseClient) : DownloadHistoryRepository {

    private val signInMutex = Mutex()

    override suspend fun record(request: DownloadRequest, fileSizeBytes: Long): Result<Unit> = try {
        ensureAnonymousSession()
        supabase.from(TABLE).insert(request.toHistoryDto(fileSizeBytes))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e) // sin red, Supabase caído, política RLS mal configurada…
    }

    /**
     * Crea el usuario anónimo la primera vez. El Mutex evita que dos descargas que terminan a la
     * vez creen DOS usuarios distintos (cada uno con medio historial).
     */
    private suspend fun ensureAnonymousSession() = signInMutex.withLock {
        supabase.auth.awaitInitialization() // espera a que cargue la sesión guardada
        if (supabase.auth.currentSessionOrNull() == null) {
            supabase.auth.signInAnonymously()
        }
    }

    companion object {
        private const val TABLE = "downloads_history"

        fun create(url: String, key: String): SupabaseHistoryRepository {
            val client = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
                install(Auth) {
                    // Registramos desde un Worker, casi siempre con la app en segundo plano. Con las
                    // callbacks de ciclo de vida activas, Auth espera a que la app vuelva a primer
                    // plano para cargar la sesión, y awaitInitialization() no terminaría nunca.
                    enableLifecycleCallbacks = false
                }
                install(Postgrest)
            }
            return SupabaseHistoryRepository(client)
        }
    }
}

/** Se usa cuando local.properties no tiene claves de Supabase (p. ej. quien clona el repo). */
object DisabledHistoryRepository : DownloadHistoryRepository {
    override suspend fun record(request: DownloadRequest, fileSizeBytes: Long): Result<Unit> =
        Result.failure(IllegalStateException("Supabase no está configurado en local.properties"))
}
