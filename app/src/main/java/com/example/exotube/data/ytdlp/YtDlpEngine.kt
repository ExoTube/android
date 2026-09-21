package com.example.exotube.data.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * El motor yt-dlp: arrancarlo y ejecutarlo. Una sola copia para toda la app.
 *
 * yt-dlp es un programa de Python que corre como PROCESO aparte: cada llamada bloquea el hilo
 * durante segundos (o minutos). Por eso todo el trabajo pesado ocurre en Dispatchers.IO.
 *
 * Está separado de quien lo usa (analizar enlaces, descargar, buscar en línea) para que cada
 * uno se ocupe de una sola cosa: aquí no hay ninguna regla de negocio, solo "ejecuta esto".
 */
class YtDlpEngine(context: Context) {

    private val appContext = context.applicationContext

    /** Caché de yt-dlp entre ejecuciones: la segunda consulta al mismo sitio es más rápida. */
    private val cacheDir = File(appContext.cacheDir, "yt-dlp")

    private val initMutex = Mutex()

    @Volatile
    private var isInitialized = false

    /**
     * Descomprime Python, yt-dlp y FFmpeg (solo la primera vez tarda unos segundos).
     * Es seguro llamarlo muchas veces y desde varias corrutinas a la vez.
     */
    suspend fun initialize() {
        if (isInitialized) return
        // Doble comprobación con Mutex: si dos corrutinas llegan juntas, solo una inicializa.
        initMutex.withLock {
            if (isInitialized) return
            withContext(Dispatchers.IO) {
                YoutubeDL.init(appContext)
                FFmpeg.init(appContext)
            }
            isInitialized = true
        }
    }

    /** Para el arranque de la app: prepara el motor en segundo plano y nunca lanza. */
    suspend fun warmUp() {
        try {
            initialize()
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "No se pudo preparar yt-dlp", e)
        }
    }

    /**
     * Ejecuta yt-dlp y espera a que termine.
     *
     * runInterruptible: si la corrutina se cancela (el usuario cierra la pantalla), interrumpe el
     * hilo y youtubedl-android mata el proceso. Sin esto, yt-dlp seguiría corriendo solo.
     */
    suspend fun run(
        request: YoutubeDLRequest,
        onProgress: ((Float, Long, String) -> Unit)? = null,
    ): YoutubeDLResponse {
        initialize()
        return runInterruptible(Dispatchers.IO) {
            YoutubeDL.execute(request, null, onProgress)
        }
    }

    /** Descarga la última versión estable de yt-dlp (los sitios cambian cada pocas semanas). */
    suspend fun update(): YoutubeDL.UpdateStatus? {
        initialize()
        return withContext(Dispatchers.IO) {
            YoutubeDL.updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
        }
    }

    /** Punto de partida de cualquier llamada, con las opciones que siempre queremos. */
    fun newRequest(url: String): YoutubeDLRequest = YoutubeDLRequest(url)
        .addOption("--socket-timeout", SOCKET_TIMEOUT_SECONDS)
        .addOption("--cache-dir", cacheDir.absolutePath)

    private companion object {
        const val TAG = "YtDlpEngine"
        const val SOCKET_TIMEOUT_SECONDS = 20
    }
}
