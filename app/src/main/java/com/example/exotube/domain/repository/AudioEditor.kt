package com.example.exotube.domain.repository

import com.example.exotube.domain.model.LibraryItem

/**
 * Edición de audio sobre archivos que ya están en el teléfono.
 *
 * Solo funciona con lo descargado: un video en línea no es un archivo, es una dirección que
 * caduca, así que no hay nada que recortar ni que etiquetar.
 */
interface AudioEditor {

    /**
     * Saca el trozo entre [startMs] y [endMs] de [source] y lo guarda como un archivo NUEVO
     * llamado [newName], junto al resto de la música. El original no se toca.
     */
    suspend fun trim(
        source: LibraryItem,
        startMs: Long,
        endMs: Long,
        newName: String,
    ): Result<Unit>

    /**
     * Cambia la carátula de [target] por la imagen [imageUri] (una foto de la galería).
     *
     * Aquí sí se modifica el archivo original: la portada es parte de la canción, y tener dos
     * copias iguales salvo la imagen no le sirve a nadie. El audio no se vuelve a comprimir.
     *
     * Puede fallar con una `RecoverableSecurityException` cuando la canción no la creó ExoTube:
     * Android exige que el usuario autorice tocar los archivos de otras apps.
     */
    suspend fun changeCover(target: LibraryItem, imageUri: String): Result<Unit>

    /**
     * Cambia el nombre de [target] por [newTitle].
     *
     * Cambia las DOS cosas que llamamos "nombre", porque cambiar solo una deja la canción a
     * medias: la etiqueta de dentro del archivo (lo que muestran ExoTube y cualquier otro
     * reproductor) y el nombre del archivo (lo que se ve al conectar el teléfono al ordenador).
     *
     * Igual que [changeCover], puede necesitar que el usuario autorice la escritura.
     */
    suspend fun rename(target: LibraryItem, newTitle: String): Result<Unit>
}
