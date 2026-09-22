package com.example.exotube.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

/**
 * Las dos versiones de la carátula que usa el widget.
 *
 * Van pequeñas a propósito: el widget no vive en la app sino en el lanzador, y cada imagen hay
 * que mandársela entera en cada actualización. Una portada a tamaño completo pesaría varios
 * megas y Android podría rechazar la actualización.
 */
internal object WidgetArt {

    /** Lado del círculo del centro del disco, en píxeles. Sobra para 52 dp en cualquier pantalla. */
    private const val COVER_SIZE = 192

    /**
     * Lado del fondo desenfocado. Tan pequeño que, al estirarlo para llenar el widget, se ve
     * borroso por sí solo: es el desenfoque más barato que existe, y queda muy parecido.
     */
    private const val BACKDROP_SIZE = 24

    /** La carátula recortada en círculo, para ponerla en el centro del disco como una etiqueta. */
    fun roundCover(source: Bitmap): Bitmap {
        val square = centerSquare(source, COVER_SIZE)
        val output = createBitmap(COVER_SIZE, COVER_SIZE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = COVER_SIZE / 2f
        Canvas(output).drawCircle(radius, radius, radius, paint)
        return output
    }

    /**
     * La carátula reducida a unos pocos píxeles para el fondo.
     *
     * Se reduce a la mitad una y otra vez en lugar de ir directa al tamaño final: de un salto,
     * solo se tomarían unos pocos píxeles sueltos de la imagen y el color saldría "a manchas";
     * a pasos, cada píxel final es la mezcla de todos los de su zona.
     */
    fun blurredBackdrop(source: Bitmap): Bitmap {
        var current = centerSquare(source, maxOf(BACKDROP_SIZE, minOf(source.width, source.height)))
        while (current.width / 2 >= BACKDROP_SIZE) {
            current = current.scale(current.width / 2, current.height / 2)
        }
        return current.scale(BACKDROP_SIZE, BACKDROP_SIZE)
    }

    /** El cuadrado central de [source], escalado a [size] de lado (una portada de video es 16:9). */
    private fun centerSquare(source: Bitmap, size: Int): Bitmap {
        // Una imagen guardada en la memoria de la tarjeta gráfica no se puede pintar en un
        // lienzo normal: primero hay que traerla a la memoria de siempre.
        val readable = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }
        val side = minOf(readable.width, readable.height)
        val left = (readable.width - side) / 2
        val top = (readable.height - side) / 2
        val matrix = Matrix().apply { setScale(size.toFloat() / side, size.toFloat() / side) }
        return Bitmap.createBitmap(readable, left, top, side, side, matrix, true)
    }
}
