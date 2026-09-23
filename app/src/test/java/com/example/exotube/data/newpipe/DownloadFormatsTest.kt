package com.example.exotube.data.newpipe

import com.example.exotube.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las opciones de la hoja de descarga cuando el enlace lo reconoce NewPipe. Deben verse igual que
 * las de yt-dlp y llevar selectores que yt-dlp entienda al descargar.
 */
class DownloadFormatsTest {

    private fun video(
        itag: Int,
        width: Int,
        height: Int,
        h264: Boolean = true,
        fps: Int = 30,
        hasAudio: Boolean = false,
        bytes: Long? = 1_000L * height,
    ) = ItagVideo(itag, width, height, fps, h264, hasAudio, bitrate = height * 1_000, bytes = bytes)

    private val m4a = ItagAudio(140, isM4a = true, bitrate = 128_000, bytes = 3_000)
    private val opus = ItagAudio(251, isM4a = false, bitrate = 160_000, bytes = 4_000)

    @Test
    fun `una opción por calidad, de mayor a menor, y luego MP3 y M4A`() {
        val formats = downloadFormats(
            videos = listOf(video(134, 640, 360), video(137, 1920, 1080), video(136, 1280, 720)),
            audios = listOf(m4a, opus),
            durationSeconds = 200,
        )

        assertEquals(listOf("1080p", "720p", "360p", "MP3", "M4A"), formats.map { it.label })
        assertEquals(MediaType.VIDEO, formats.first().type)
        assertEquals(MediaType.AUDIO, formats.last().type)
    }

    @Test
    fun `el selector pide ese itag exacto con el mejor sonido, y si no, otro del mismo tamaño`() {
        val option = downloadFormats(listOf(video(136, 1280, 720)), listOf(m4a), 60).first()

        assertEquals("136+ba[ext=m4a]/136+ba/bv*[height<=720][width<=1280]+ba/b[height<=720][width<=1280]/b", option.formatId)
    }

    @Test
    fun `un formato que ya trae sonido no pide otro`() {
        val option = downloadFormats(listOf(video(18, 640, 360, hasAudio = true)), emptyList(), 60).first()

        assertEquals("18/b[height<=360][width<=640]/b", option.formatId)
    }

    @Test
    fun `el peso de un video es imagen más sonido y si falta uno no se inventa`() {
        val known = downloadFormats(listOf(video(136, 1280, 720, bytes = 10_000)), listOf(m4a), 60).first()
        val unknown = downloadFormats(listOf(video(136, 1280, 720, bytes = null)), listOf(m4a), 60).first()

        assertEquals(13_000L, known.sizeBytes)
        assertNull(unknown.sizeBytes)
    }

    @Test
    fun `un Short vertical se nombra por su lado corto, como en yt-dlp`() {
        val option = downloadFormats(listOf(video(137, 1080, 1920)), listOf(m4a), 30).first()

        assertEquals("1080p", option.label)
        assertTrue(option.formatId.contains("[height<=1920][width<=1080]"))
    }

    @Test
    fun `en la misma calidad gana H264 y se marca los 60 fps`() {
        val formats = downloadFormats(
            videos = listOf(video(248, 1920, 1080, h264 = false), video(299, 1920, 1080, fps = 60)),
            audios = listOf(m4a),
            durationSeconds = 60,
        )

        assertEquals("1080p60", formats.first().label)
        assertTrue(formats.first().formatId.startsWith("299+"))
    }

    @Test
    fun `el mismo itag repetido (doblajes) sale una sola vez`() {
        val formats = downloadFormats(listOf(video(136, 1280, 720), video(136, 1280, 720)), listOf(m4a), 60)

        assertEquals(1, formats.count { it.type == MediaType.VIDEO })
    }

    @Test
    fun `el MP3 se calcula por duración y el M4A es el peso del sonido original`() {
        val dubbed = ItagAudio(140, isM4a = true, bitrate = 128_000, bytes = 9_999, isOriginal = false)
        val formats = downloadFormats(listOf(video(136, 1280, 720)), listOf(dubbed, m4a), durationSeconds = 100)

        assertEquals(100L * 192 * 1000 / 8, formats.first { it.label == "MP3" }.sizeBytes)
        assertEquals(3_000L, formats.first { it.label == "M4A" }.sizeBytes)
    }

    @Test
    fun `sin sonido ni video no hay nada que ofrecer`() {
        assertTrue(downloadFormats(emptyList(), emptyList(), 60).isEmpty())
    }
}
