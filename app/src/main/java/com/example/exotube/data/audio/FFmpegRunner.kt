package com.example.exotube.data.audio

import android.content.Context
import android.util.Log
import com.example.exotube.data.ytdlp.YtDlpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException

/**
 * Ejecuta el FFmpeg que ya viaja dentro de la app.
 *
 * ExoTube no añade FFmpeg para esto: ya lo llevaba, porque es lo que usa yt-dlp para convertir a
 * MP3 e incrustar carátulas. Aprovecharlo no suma ni un megabyte al APK.
 *
 * Dos detalles de cómo está empaquetado:
 *  - El **ejecutable** viaja como si fuera una librería nativa (`libffmpeg.so`). Es el único sitio
 *    del que Android deja ejecutar un binario propio.
 *  - Las **librerías** que necesita las descomprime youtubedl-android la primera vez, así que hay
 *    que asegurarse de que el motor ya arrancó antes de llamarlo.
 */
class FFmpegRunner(context: Context, private val engine: YtDlpEngine) {

    private val appContext = context.applicationContext

    private val executable: File
        get() = File(appContext.applicationInfo.nativeLibraryDir, EXECUTABLE)

    /**
     * Lanza FFmpeg y espera a que acabe.
     *
     * @return la salida del programa si terminó bien, o el fallo con lo último que escribió, que
     *   es donde FFmpeg explica qué no le gustó.
     */
    suspend fun run(arguments: List<String>): Result<String> {
        // Descomprime las librerías si es la primera vez que se usa el motor.
        engine.initialize()

        val executable = this.executable
        if (!executable.canExecute()) {
            return Result.failure(IOException("No se encontró FFmpeg en ${executable.absolutePath}"))
        }

        return try {
            // runInterruptible: si el usuario cancela, se interrumpe el hilo y matamos el proceso.
            runInterruptible(Dispatchers.IO) { execute(executable, arguments) }
        } catch (e: IOException) {
            Log.w(TAG, "FFmpeg falló", e)
            Result.failure(e)
        }
    }

    private fun execute(executable: File, arguments: List<String>): Result<String> {
        val process = ProcessBuilder(listOf(executable.absolutePath) + arguments)
            .redirectErrorStream(true) // FFmpeg informa por la salida de errores; la queremos junta
            .apply { environment()["LD_LIBRARY_PATH"] = libraryPath(appContext.noBackupFilesDir.absolutePath) }
            .start()
        return try {
            // Hay que leer la salida mientras corre: si no, al llenarse el búfer el proceso se
            // queda bloqueado esperando que alguien lea, y nunca termina.
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Result.success(output)
            } else {
                Result.failure(IOException("FFmpeg terminó con código $exitCode: ${output.takeLast(600)}"))
            }
        } finally {
            process.destroy() // por si salimos por cancelación
        }
    }

    private companion object {
        const val TAG = "FFmpegRunner"
        const val EXECUTABLE = "libffmpeg.so"
    }
}

/**
 * Las carpetas donde FFmpeg tiene que buscar sus librerías.
 *
 * Son DOS, y ahí estuvo el fallo que hacía imposible recortar: con solo la de FFmpeg, el sistema
 * se negaba a arrancarlo con un "CANNOT LINK EXECUTABLE ... library libc++_shared.so not found",
 * porque esa librería no está en el paquete de FFmpeg sino en el de Python. Los dos paquetes se
 * descomprimen juntos y se usan juntos, que es exactamente lo que hace yt-dlp cuando llama a
 * FFmpeg para convertir una descarga a MP3.
 *
 * Trabaja con texto y no con File para poder comprobarla con un test: los tests corren en
 * Windows, donde File inventaría una letra de unidad y barras invertidas que en el teléfono no
 * existen.
 */
internal fun libraryPath(noBackupFilesDir: String): String = PACKAGES_WITH_LIBRARIES
    .joinToString(PATH_SEPARATOR) { name -> "$noBackupFilesDir/$PACKAGES_ROOT/$name/usr/lib" }

/**
 * Los dos puntos, escritos a mano y no con File.pathSeparator, porque esto no es una ruta de
 * Java: es lo que lee el enlazador de Linux, que siempre usa ":" aunque el ordenador donde
 * corren los tests sea Windows.
 */
private const val PATH_SEPARATOR = ":"

/** Donde youtubedl-android descomprime lo que ejecuta. */
private const val PACKAGES_ROOT = "youtubedl-android/packages"

/** Python va primero, como en youtubedl-android: es quien aporta las librerías compartidas. */
private val PACKAGES_WITH_LIBRARIES = listOf("python", "ffmpeg")
