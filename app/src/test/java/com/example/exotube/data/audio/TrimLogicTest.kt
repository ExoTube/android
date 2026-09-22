package com.example.exotube.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Las dos piezas del recorte que se pueden comprobar sin ejecutar nada: los argumentos que se le
 * pasan a FFmpeg y la limpieza del nombre que escribe el usuario.
 */
class TrimLogicTest {

    private val input = File("/tmp/original.mp3")
    private val output = File("/tmp/recorte.mp3")

    private fun arguments(startMs: Long, endMs: Long) = trimArguments(input, output, startMs, endMs)

    @Test
    fun `usa duracion y no instante final`() {
        val args = arguments(startMs = 12_400, endMs = 42_000)

        // -ss es el punto de inicio; -t, lo que dura el trozo (29,6 s), no el instante 42.
        assertEquals("12.400", args[args.indexOf("-ss") + 1])
        assertEquals("29.600", args[args.indexOf("-t") + 1])
        assertFalse("-to" in args)
    }

    /**
     * El orden importa de verdad: "-ss" tiene que ir ANTES de "-i" para que FFmpeg salte directo
     * al segundo pedido. Puesto después, se lee la canción entera hasta llegar ahí.
     */
    @Test
    fun `el salto va antes del archivo de entrada`() {
        val args = arguments(startMs = 60_000, endMs = 70_000)

        assertTrue(args.indexOf("-ss") < args.indexOf("-i"))
    }

    @Test
    fun `copia el audio sin recomprimir y conserva las etiquetas`() {
        val args = arguments(startMs = 0, endMs = 10_000)

        assertEquals("copy", args[args.indexOf("-c") + 1])
        assertEquals("0", args[args.indexOf("-map_metadata") + 1])
    }

    @Test
    fun `el ultimo argumento es el archivo de salida`() {
        val args = arguments(startMs = 0, endMs = 10_000)

        assertEquals(output.absolutePath, args.last())
        assertEquals(input.absolutePath, args[args.indexOf("-i") + 1])
    }

    /** El punto decimal tiene que ser un punto, aunque el teléfono esté en español. */
    @Test
    fun `los segundos van con punto decimal`() {
        val args = arguments(startMs = 1_500, endMs = 3_250)

        assertEquals("1.500", args[args.indexOf("-ss") + 1])
        assertEquals("1.750", args[args.indexOf("-t") + 1])
    }

    // --- Nombre del archivo ---

    @Test
    fun `un nombre normal se respeta`() {
        assertEquals("Mi estribillo", sanitizeFileName("Mi estribillo", fallback = "Canción"))
    }

    /** Estos caracteres no se pueden usar en un nombre de archivo en ningún sistema. */
    @Test
    fun `quita los caracteres prohibidos`() {
        val limpio = sanitizeFileName("""AC/DC: Back "in" Black?*<>|""", fallback = "Canción")

        assertFalse(limpio.any { it in """/\:*?"<>|""" })
        assertTrue(limpio.startsWith("AC DC"))
    }

    @Test
    fun `junta los espacios de sobra y recorta los extremos`() {
        assertEquals("Solo el coro", sanitizeFileName("   Solo    el   coro  ", fallback = "Canción"))
    }

    @Test
    fun `si el usuario lo deja vacio se usa el titulo de la cancion`() {
        assertEquals("Sobredosis de TV", sanitizeFileName("   ", fallback = "Sobredosis de TV"))
        assertEquals("Sobredosis de TV", sanitizeFileName("", fallback = "Sobredosis de TV"))
    }

    /** Si hasta el título original es impracticable, algo tiene que salir. */
    @Test
    fun `nunca devuelve un nombre vacio`() {
        assertEquals("recorte", sanitizeFileName("///", fallback = "***"))
    }

    @Test
    fun `corta los nombres larguisimos`() {
        val largo = "a".repeat(300)

        assertEquals(80, sanitizeFileName(largo, fallback = "Canción").length)
    }

    /**
     * El nombre que se propone solo, con un título de YouTube de los largos. Se comprueba entero
     * porque un recorte guardado con un nombre a medias no se encuentra luego en la biblioteca.
     */
    @Test
    fun `el nombre propuesto para un titulo largo de YouTube sale completo`() {
        val propuesto = "ROSÉ & Bruno Mars - APT. (Official Music Video) (recorte)"

        assertEquals(
            "ROSÉ & Bruno Mars APT. (Official Music Video) (recorte)",
            sanitizeFileName(propuesto, fallback = "x"),
        )
    }

    @Test
    fun `los acentos y la ñ se conservan`() {
        assertEquals("Canción Animal ñ", sanitizeFileName("Canción Animal ñ", fallback = "x"))
    }
}
