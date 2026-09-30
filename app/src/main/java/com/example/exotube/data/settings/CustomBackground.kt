package com.example.exotube.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * El fondo propio tal como se guarda en Ajustes: los tres colores sacados de la imagen (ARGB),
 * cuánto se oscurece y una versión que cambia con cada imagen nueva (para volver a cargarla).
 * La imagen en sí vive en un archivo: ver [CustomBackgroundStore].
 */
data class CustomBackground(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val dim: Float,
    val version: Long,
) {
    companion object {
        /** Ni tan claro que no se lean los textos, ni tan oscuro que no se vea la imagen. */
        const val DEFAULT_DIM = 0.7f
        const val MIN_DIM = 0.55f
        const val MAX_DIM = 0.9f
    }
}

/**
 * La imagen del fondo propio: una copia dentro de la app, no un enlace a la galería. Así sigue ahí
 * aunque la persona borre la foto original, y no hace falta ningún permiso para leerla.
 */
class CustomBackgroundStore(context: Context) {

    private val appContext = context.applicationContext
    private val file get() = File(appContext.filesDir, "fondo.jpg")

    /**
     * Copia la imagen elegida, reducida al tamaño de una pantalla (una foto de 12 megapíxeles
     * ocuparía 48 MB de memoria solo para dibujarse), y devuelve sus píxeles en miniatura para
     * sacar los colores.
     */
    suspend fun save(uri: Uri): IntArray = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri, MAX_SIDE) ?: throw IOException("No se pudo leer la imagen")
        // Primero a un archivo temporal: si algo falla a mitad, el fondo anterior sigue intacto.
        val temp = File(appContext.filesDir, "fondo.tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!temp.renameTo(file)) throw IOException("No se pudo guardar la imagen")
        val thumbHeight = max(1, (THUMB * bitmap.height.toFloat() / bitmap.width).roundToInt())
        val thumb = Bitmap.createScaledBitmap(bitmap, THUMB, thumbHeight, true)
        IntArray(thumb.width * thumb.height).also { thumb.getPixels(it, 0, thumb.width, 0, 0, thumb.width, thumb.height) }
    }

    /** La imagen guardada, o null si no hay (o no se pudo leer). */
    suspend fun load(): Bitmap? = withContext(Dispatchers.IO) {
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = appContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder respeta la orientación de las fotos de cámara (si no, saldrían de lado).
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val k = maxSide.toFloat() / max(w, h)
                if (k < 1f) decoder.setTargetSize((w * k).roundToInt(), (h * k).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE   // para poder leer sus píxeles
            }
        }
        // Android 8: primero solo las medidas, luego se lee ya reducida a la potencia de 2 más cercana.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    private companion object {
        const val MAX_SIDE = 1600
        const val THUMB = 64
    }
}
