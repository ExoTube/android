package com.example.exotube.data.library

import android.content.Context
import coil3.SingletonImageLoader

/**
 * Tira la carátula guardada en memoria cuando el archivo cambia.
 *
 * Hace falta porque Coil identifica cada imagen por la Uri de la canción, y al cambiar la portada
 * la Uri es la misma de siempre: sin esto, Coil seguiría dando la imagen vieja (la tiene ya
 * descomprimida en memoria) y el usuario pensaría que no funcionó.
 */
class ArtworkCache(context: Context) {

    private val appContext = context.applicationContext

    /** [uri] es la canción cuyo archivo se acaba de reescribir. */
    fun invalidate(uri: String) {
        val loader = SingletonImageLoader.get(appContext)
        // Se vacía la memoria entera y no solo esta entrada: la clave de Coil incluye el tamaño
        // en pantalla, así que la misma canción está guardada varias veces (fila, reproductor,
        // álbum) y no se pueden nombrar todas. Cambiar una portada es algo puntual, y lo único
        // que cuesta es volver a leer del disco las imágenes visibles.
        loader.memoryCache?.clear()
        loader.diskCache?.remove(uri)
    }
}
