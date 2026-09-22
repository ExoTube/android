package com.example.exotube.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Una portada dentro de un MP3 no es un campo de texto: es una "pista de video" de un solo
 * fotograma marcada como imagen adjunta. Estos tests fijan las piezas que hacen que salga así y
 * no como un video de verdad ni como una segunda portada junto a la vieja.
 */
class CoverLogicTest {

    private val input = File("/tmp/original.mp3")
    private val cover = File("/tmp/portada.jpg")
    private val output = File("/tmp/conportada.mp3")

    private val args = coverArguments(input, cover, output)

    /** Dos entradas: la canción primero y la foto después, porque de eso depende el -map. */
    @Test
    fun `la cancion es la primera entrada y la foto la segunda`() {
        val inputs = args.withIndex().filter { it.value == "-i" }.map { args[it.index + 1] }

        assertEquals(listOf(input.absolutePath, cover.absolutePath), inputs)
    }

    /**
     * Lo que evita que la canción acabe con dos portadas: del original se toma SOLO el audio, así
     * que la imagen antigua se queda fuera.
     */
    @Test
    fun `del original solo se toma el audio`() {
        val maps = args.withIndex().filter { it.value == "-map" }.map { args[it.index + 1] }

        assertEquals(listOf("0:a", "1:v"), maps)
    }

    /** Sin esto, algunos reproductores intentarían "reproducir" la foto como un video. */
    @Test
    fun `la imagen se marca como caratula`() {
        assertEquals("attached_pic", args[args.indexOf("-disposition:v") + 1])
    }

    /** Los nombres que espera la etiqueta estándar de ID3; no son decorativos. */
    @Test
    fun `la caratula lleva los nombres estandar`() {
        val metadata = args.withIndex().filter { it.value == "-metadata:s:v" }.map { args[it.index + 1] }

        assertTrue("title=Album cover" in metadata)
        assertTrue("comment=Cover (front)" in metadata)
    }

    @Test
    fun `el audio no se vuelve a comprimir`() {
        assertEquals("copy", args[args.indexOf("-c") + 1])
    }

    @Test
    fun `conserva el titulo y el artista del original`() {
        assertEquals("0", args[args.indexOf("-map_metadata") + 1])
    }

    @Test
    fun `el ultimo argumento es el archivo de salida`() {
        assertEquals(output.absolutePath, args.last())
    }
}

/**
 * Cambiar el nombre toca la etiqueta de dentro del archivo. Lo que estos tests fijan es que al
 * hacerlo no se pierda la portada, que es el descuido clásico: FFmpeg, si no se le dice nada, se
 * queda solo con el audio.
 */
class RenameLogicTest {

    private val input = File("/tmp/original.mp3")
    private val output = File("/tmp/renombrada.mp3")

    private val args = renameArguments(input, output, "Un Misil en Mi Placard")

    @Test
    fun `copia todo lo que tenia el original, no solo el audio`() {
        assertEquals("0", args[args.indexOf("-map") + 1])
    }

    @Test
    fun `escribe el titulo nuevo`() {
        assertEquals("title=Un Misil en Mi Placard", args[args.indexOf("-metadata") + 1])
    }

    @Test
    fun `no vuelve a comprimir el audio`() {
        assertEquals("copy", args[args.indexOf("-c") + 1])
    }

    /** El título va DESPUÉS de copiar las etiquetas viejas, para pisarlas y no al contrario. */
    @Test
    fun `el titulo nuevo pisa el viejo`() {
        assertTrue(args.indexOf("-map_metadata") < args.indexOf("-metadata"))
    }

    @Test
    fun `el ultimo argumento es el archivo de salida`() {
        assertEquals(output.absolutePath, args.last())
    }
}
