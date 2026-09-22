package com.example.exotube.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El fallo que esto evita se vio en pantalla: la app mostraba álbumes llamados "Music",
 * "Download" y "ExoTube". No eran álbumes, eran carpetas: Android rellena la columna del álbum
 * con el nombre de la carpeta cuando el archivo no lo trae etiquetado.
 */
class AlbumTagsTest {

    @Test
    fun `un album de verdad se conserva`() {
        assertEquals("Nada Personal", albumTagOrNull("Nada Personal", folderName = "ExoTube"))
    }

    @Test
    fun `si el album coincide con la carpeta no es un album`() {
        assertNull(albumTagOrNull("ExoTube", folderName = "ExoTube"))
        assertNull(albumTagOrNull("Music", folderName = "Music"))
        assertNull(albumTagOrNull("Download", folderName = "Download"))
    }

    /** Android no es constante con las mayúsculas entre la carpeta y la etiqueta. */
    @Test
    fun `la comparacion no distingue mayusculas ni espacios`() {
        assertNull(albumTagOrNull("exotube", folderName = "ExoTube"))
        assertNull(albumTagOrNull("  ExoTube  ", folderName = "ExoTube"))
    }

    @Test
    fun `sin etiqueta no hay album`() {
        assertNull(albumTagOrNull(null, folderName = "ExoTube"))
        assertNull(albumTagOrNull("   ", folderName = "ExoTube"))
        assertNull(albumTagOrNull(UNKNOWN_TAG, folderName = "ExoTube"))
    }

    @Test
    fun `sin saber la carpeta se confia en la etiqueta`() {
        assertEquals("Canción Animal", albumTagOrNull("Canción Animal", folderName = null))
    }

    // --- Sacar el nombre de la carpeta, que Android da de dos formas ---

    @Test
    fun `Android 10 en adelante da la carpeta suelta`() {
        assertEquals("ExoTube", folderNameFrom("Music/ExoTube/", isRelativePath = true))
        assertEquals("Music", folderNameFrom("Music/", isRelativePath = true))
    }

    @Test
    fun `Android 8 y 9 dan la ruta completa del archivo`() {
        assertEquals(
            "ExoTube",
            folderNameFrom("/storage/emulated/0/Music/ExoTube/tema.mp3", isRelativePath = false),
        )
    }

    @Test
    fun `una ruta vacia no da carpeta`() {
        assertNull(folderNameFrom(null, isRelativePath = true))
        assertNull(folderNameFrom("", isRelativePath = true))
        assertNull(folderNameFrom("   ", isRelativePath = false))
    }
}
