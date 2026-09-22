package com.example.exotube.domain.repository

import com.example.exotube.domain.model.LibraryItem

/**
 * Borra del teléfono una canción o un video descargado.
 *
 * Es aparte de [LibraryRepository] porque aquello solo LEE la biblioteca y esto la modifica:
 * quien solo quiere mirar la lista no tiene por qué poder borrar nada.
 */
interface MediaFileDeleter {

    /**
     * Borra el archivo de [item] y todo lo que lo recordaba (playlists e historial de escucha),
     * para que no queden huecos apuntando a un archivo que ya no existe.
     *
     * Si el archivo ya no estaba, cuenta como éxito: el resultado es el mismo que se pedía.
     *
     * Puede fallar pidiendo permiso cuando el archivo no lo creó ExoTube (ver
     * `data.library.ApprovalRequired`): hay que enseñar el diálogo del sistema y, si el usuario
     * acepta, volver a llamar a esta función.
     */
    suspend fun delete(item: LibraryItem): Result<Unit>
}
