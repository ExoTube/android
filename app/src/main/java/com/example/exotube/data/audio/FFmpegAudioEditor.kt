package com.example.exotube.data.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.example.exotube.domain.model.LibraryItem
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.repository.AudioEditor
import com.example.exotube.download.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/**
 * Edita audio con FFmpeg **sin volver a comprimir**: copia los datos tal cual, así que el
 * resultado suena exactamente igual que el original y tarda un instante.
 *
 * FFmpeg corre como otro proceso y no entiende las Uri "content://" de Android, así que en las
 * dos operaciones el archivo se trae primero a una carpeta nuestra.
 */
class FFmpegAudioEditor(
    context: Context,
    private val ffmpeg: FFmpegRunner,
    private val saver: MediaStoreSaver,
) : AudioEditor {

    private val appContext = context.applicationContext
    private val coverImages = CoverImageFile(appContext)

    /** El recorte sale como archivo nuevo: el original nunca se toca. */
    override suspend fun trim(
        source: LibraryItem,
        startMs: Long,
        endMs: Long,
        newName: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (endMs <= startMs) {
            return@withContext Result.failure(IllegalArgumentException("El fragmento está vacío"))
        }
        inWorkDir("recortes") { workDir ->
            val extension = extensionOf(source)
            val input = File(workDir, "original.$extension").also { copyFrom(source.uri.toUri(), it) }
            val output = File(workDir, "${sanitizeFileName(newName, source.title)}.$extension")

            ffmpeg.run(trimArguments(input, output, startMs, endMs)).getOrThrow()
            requireGenerated(output)

            saver.save(output, MediaType.AUDIO)
        }
    }

    /** La portada sí se escribe sobre el archivo original: es parte de la canción. */
    override suspend fun changeCover(
        target: LibraryItem,
        imageUri: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        inWorkDir("portadas") { workDir ->
            val extension = extensionOf(target)
            val input = File(workDir, "original.$extension").also { copyFrom(target.uri.toUri(), it) }
            val cover = File(workDir, "portada.jpg").also { coverImages.writeJpeg(imageUri.toUri(), it) }
            val output = File(workDir, "conportada.$extension")

            ffmpeg.run(coverArguments(input, cover, output)).getOrThrow()
            requireGenerated(output)

            writeBack(output, target.uri.toUri())
        }
    }

    /**
     * El nombre nuevo se aplica en un orden concreto para que no pueda quedar a medias.
     *
     * Primero se prepara el archivo etiquetado en la caché (todavía no se ve nada), después se
     * renombra en MediaStore y solo al final se sobrescriben los bytes. Si Android va a pedir
     * permiso, lo pide en el paso del medio, cuando aún no se ha tocado nada: el usuario acepta,
     * se repite la operación entera y ya está. Al revés, un permiso denegado dejaría el archivo
     * con el nombre nuevo y la etiqueta vieja.
     */
    override suspend fun rename(
        target: LibraryItem,
        newTitle: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val title = newTitle.trim()
        if (title.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("El nombre está vacío"))
        }
        inWorkDir("nombres") { workDir ->
            val extension = extensionOf(target)
            val input = File(workDir, "original.$extension").also { copyFrom(target.uri.toUri(), it) }
            val output = File(workDir, "renombrada.$extension")

            ffmpeg.run(renameArguments(input, output, title)).getOrThrow()
            requireGenerated(output)

            renameInLibrary(target.uri.toUri(), title, extension)
            writeBack(output, target.uri.toUri())
        }
    }

    /**
     * El nombre que ve el resto del teléfono: el título en la ficha de MediaStore y el nombre del
     * archivo en disco.
     *
     * El título se escribe aquí además de en la etiqueta para que la biblioteca se actualice al
     * momento, sin esperar a que Android vuelva a leer el archivo.
     */
    private fun renameInLibrary(uri: Uri, title: String, extension: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.TITLE, title)
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${sanitizeFileName(title, title)}.$extension")
        }
        appContext.contentResolver.update(uri, values, null, null)
    }

    /**
     * Carpeta propia por operación: al acabar se borra entera, bien o mal, y no queda basura de
     * varios megabytes en la caché.
     */
    private inline fun inWorkDir(name: String, block: (File) -> Unit): Result<Unit> {
        val workDir = File(appContext.cacheDir, "$name/${System.currentTimeMillis()}")
        return try {
            workDir.mkdirs()
            block(workDir)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e // cancelar no es fallar: la corrutina tiene que poder morir
        } catch (e: Exception) {
            Log.w(TAG, "Falló la edición en $name", e)
            Result.failure(e)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun copyFrom(source: Uri, target: File) {
        appContext.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use(input::copyTo)
        } ?: throw IOException("No se pudo leer $source")
    }

    /**
     * Sustituye el contenido del archivo que ya está en la biblioteca.
     *
     * El modo "wt" trunca antes de escribir. Sin la "t", si el archivo nuevo fuera más corto que
     * el viejo quedaría pegada al final la cola del anterior y la canción sonaría partida.
     */
    private fun writeBack(file: File, destination: Uri) {
        appContext.contentResolver.openOutputStream(destination, "wt")?.use { output ->
            file.inputStream().use { it.copyTo(output) }
        } ?: throw IOException("No se pudo escribir en $destination")
    }

    private fun requireGenerated(output: File) {
        if (!output.exists() || output.length() == 0L) throw IOException("FFmpeg no generó el archivo")
    }

    /** "audio/mpeg" → "mp3". Si el sistema no lo sabe, asumimos MP3, que es lo que descargamos. */
    private fun extensionOf(source: LibraryItem): String {
        val mimeType = appContext.contentResolver.getType(source.uri.toUri())
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp3"
    }

    private companion object {
        const val TAG = "FFmpegAudioEditor"
    }
}

/**
 * Los argumentos de FFmpeg para sacar un trozo.
 *
 * Detalles que importan:
 *  - `-ss` va ANTES de `-i`: así FFmpeg salta directamente al segundo pedido en vez de leerse
 *    la canción entera hasta llegar ahí.
 *  - se usa `-t` (duración) y no `-to` (instante final): con `-ss` delante, `-to` cambia de
 *    significado y es una fuente clásica de recortes que salen mal.
 *  - `-c copy` copia el audio sin recomprimir: sin pérdida de calidad y casi instantáneo.
 *  - `-map_metadata 0` conserva título, artista y carátula del original.
 *
 * Función aparte para poder comprobarla con un test, sin ejecutar nada.
 */
internal fun trimArguments(input: File, output: File, startMs: Long, endMs: Long): List<String> = listOf(
    "-hide_banner",
    "-nostdin", // no hay teclado que preguntar: si algo falla, que falle y no se quede esperando
    "-y", // sobrescribe sin preguntar (el archivo de salida es nuestro, de una carpeta temporal)
    "-ss", asSeconds(startMs),
    "-i", input.absolutePath,
    "-t", asSeconds(endMs - startMs),
    "-c", "copy",
    "-map_metadata", "0",
    "-id3v2_version", "3", // la versión de etiquetas que entiende cualquier reproductor
    output.absolutePath,
)

/**
 * Los argumentos para cambiar la carátula.
 *
 * Cómo funciona una portada dentro de un MP3: no es un campo de texto, es una "pista de video" de
 * un solo fotograma marcada como imagen adjunta. De ahí cada pieza:
 *  - `-map 0:a` toma SOLO el audio del original, con lo que la portada vieja se queda fuera
 *    (si no, la canción acabaría con dos y cada reproductor elegiría una distinta).
 *  - `-map 1:v` añade la foto nueva como esa pista.
 *  - `-disposition:v attached_pic` es lo que la marca como carátula y no como un video que
 *    habría que reproducir.
 *  - los dos `-metadata:s:v` son los nombres que espera la etiqueta estándar de ID3.
 *  - `-c copy` otra vez: el audio se copia intacto, la imagen ya viene en JPG.
 */
internal fun coverArguments(input: File, cover: File, output: File): List<String> = listOf(
    "-hide_banner",
    "-nostdin",
    "-y",
    "-i", input.absolutePath,
    "-i", cover.absolutePath,
    "-map", "0:a",
    "-map", "1:v",
    "-c", "copy",
    "-map_metadata", "0",
    "-id3v2_version", "3",
    "-metadata:s:v", "title=Album cover",
    "-metadata:s:v", "comment=Cover (front)",
    "-disposition:v", "attached_pic",
    output.absolutePath,
)

/**
 * Los argumentos para cambiar el título que va etiquetado dentro del archivo.
 *
 * `-map 0` copia TODO lo que tenía el original (el audio y la portada, si la llevaba), y el
 * `-metadata title=` que viene después pisa solo el título. Sin el `-map 0` se perdería la
 * carátula, porque FFmpeg por su cuenta se queda únicamente con el audio.
 *
 * Función aparte para poder comprobarla con un test, sin ejecutar nada.
 */
internal fun renameArguments(input: File, output: File, newTitle: String): List<String> = listOf(
    "-hide_banner",
    "-nostdin",
    "-y",
    "-i", input.absolutePath,
    "-map", "0",
    "-c", "copy",
    "-map_metadata", "0",
    "-metadata", "title=$newTitle",
    "-id3v2_version", "3",
    output.absolutePath,
)

/** FFmpeg quiere segundos con punto decimal, sin importar el idioma del teléfono. */
private fun asSeconds(millis: Long): String = "%.3f".format(Locale.ROOT, millis / 1_000.0)

/**
 * Convierte lo que escribió el usuario en un nombre de archivo válido.
 *
 * Quita los caracteres que ningún sistema de archivos admite, recorta los espacios de sobra y
 * limita el largo. Si no queda nada utilizable, usa el título de la canción original.
 */
internal fun sanitizeFileName(name: String, fallback: String): String {
    val cleaned = name.replace(FORBIDDEN, " ").replace(SPACES, " ").trim().take(MAX_LENGTH)
    if (cleaned.isNotEmpty()) return cleaned
    val cleanedFallback = fallback.replace(FORBIDDEN, " ").replace(SPACES, " ").trim().take(MAX_LENGTH)
    return cleanedFallback.ifEmpty { "recorte" }
}

/** Caracteres prohibidos en un nombre de archivo, más los de control. */
private val FORBIDDEN = Regex("""[/\\:*?"<>| -]""")
private val SPACES = Regex("""\s+""")
private const val MAX_LENGTH = 80
