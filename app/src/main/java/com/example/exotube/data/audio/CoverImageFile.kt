package com.example.exotube.data.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException

/**
 * Prepara la foto que eligió el usuario para meterla dentro de una canción.
 *
 * No se usa el archivo tal cual a propósito. Una foto del carrete puede pesar cinco megabytes, y
 * la portada viaja DENTRO del MP3: usarla sin tocar engordaría la canción igual que si se le
 * pegara la foto entera. Se reduce a [MAX_SIDE] píxeles de lado, que es más de lo que necesita
 * cualquier pantalla de teléfono para una carátula, y se guarda como JPG, que es el formato que
 * entienden todos los reproductores.
 */
internal class CoverImageFile(context: Context) {

    private val appContext = context.applicationContext

    /** Deja en [target] la imagen de [imageUri] lista para incrustar. */
    fun writeJpeg(imageUri: Uri, target: File) {
        val bitmap = decode(imageUri) ?: throw IOException("No se pudo leer la imagen $imageUri")
        try {
            val scaled = bitmap.scaledDown()
            target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (scaled !== bitmap) scaled.recycle()
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(imageUri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(imageUri)
        } else {
            decodeWithBitmapFactory(imageUri)
        }

    /**
     * Android 9 en adelante. Se prefiere [ImageDecoder] porque endereza sola las fotos hechas con
     * el teléfono de lado, que si no saldrían girada la portada.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(imageUri: Uri): Bitmap {
        val source = ImageDecoder.createSource(appContext.contentResolver, imageUri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Descarta píxeles ya al leer: más rápido y sin cargar la foto entera en memoria.
            decoder.setTargetSampleSize(sampleSizeFor(maxOf(info.size.width, info.size.height)))
            // Un bitmap "de hardware" vive en la GPU y no se puede comprimir; pedimos uno normal.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** Android 8: dos pasadas, una para medir y otra para leer. */
    private fun decodeWithBitmapFactory(imageUri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw IOException("No se pudo abrir la imagen $imageUri")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight))
        }
        return appContext.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    /** Ajuste final: el muestreo solo divide por potencias de dos, así que casi nunca cuadra exacto. */
    private fun Bitmap.scaledDown(): Bitmap {
        val longestSide = maxOf(width, height)
        if (longestSide <= MAX_SIDE) return this
        val factor = MAX_SIDE.toFloat() / longestSide
        return Bitmap.createScaledBitmap(this, (width * factor).toInt(), (height * factor).toInt(), true)
    }

    private companion object {
        /** Lado máximo de la portada, en píxeles. */
        const val MAX_SIDE = 1_000

        const val QUALITY = 90

        /** La potencia de dos más grande que no baja del tamaño que queremos. */
        fun sampleSizeFor(longestSide: Int): Int {
            var sample = 1
            while (longestSide / (sample * 2) >= MAX_SIDE) sample *= 2
            return sample
        }
    }
}
