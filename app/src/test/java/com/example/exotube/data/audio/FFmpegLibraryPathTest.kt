package com.example.exotube.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El fallo que esto evita se vio en el teléfono: al guardar un recorte, el sistema se negaba a
 * arrancar FFmpeg con "CANNOT LINK EXECUTABLE ... library libc++_shared.so not found". Esa
 * librería no está en el paquete de FFmpeg sino en el de Python, así que la ruta tiene que
 * incluir los dos.
 */
class FFmpegLibraryPathTest {

    private val noBackupDir = "/data/user/0/com.example.exotube/no_backup"

    private fun path() = libraryPath(noBackupDir).split(":")

    @Test
    fun `busca en las dos carpetas y no solo en la de ffmpeg`() {
        val folders = path()

        assertEquals(2, folders.size)
        assertTrue(folders.any { it.endsWith("packages/python/usr/lib") })
        assertTrue(folders.any { it.endsWith("packages/ffmpeg/usr/lib") })
    }

    /** El mismo orden que usa yt-dlp cuando llama a FFmpeg: quien aporta las librerías, primero. */
    @Test
    fun `python va antes que ffmpeg`() {
        val folders = path()

        assertTrue(folders[0].contains("/python/"))
        assertTrue(folders[1].contains("/ffmpeg/"))
    }

    @Test
    fun `las rutas cuelgan de la carpeta de la app`() {
        assertTrue(path().all { it.startsWith(noBackupDir) })
    }
}
