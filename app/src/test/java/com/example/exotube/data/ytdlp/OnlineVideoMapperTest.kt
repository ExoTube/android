package com.example.exotube.data.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El mapeo de los resultados de búsqueda es una función pura: se prueba sin red ni emulador,
 * dándole el JSON tal cual lo devuelve yt-dlp.
 */
class OnlineVideoMapperTest {

    @Test
    fun `convierte un resultado completo`() {
        val entry = YtDlpEntryDto(
            id = "abc123",
            title = "Canción de ejemplo",
            url = "https://www.youtube.com/watch?v=abc123",
            duration = 212.0,
            channel = "Canal",
            viewCount = 1_240_000,
            thumbnails = listOf(YtDlpThumbnailDto("https://img/small.jpg", 320)),
        )

        val video = OnlineVideoMapper.toOnlineVideo(entry)!!

        assertEquals("abc123", video.id)
        assertEquals("https://www.youtube.com/watch?v=abc123", video.url)
        assertEquals("Canción de ejemplo", video.title)
        assertEquals("Canal", video.channel)
        assertEquals(212L, video.durationSeconds)
        assertEquals(1_240_000L, video.viewCount)
    }

    /** Con --flat-playlist a veces el enlace no viene: se arma a partir del id. */
    @Test
    fun `sin enlace lo construye desde el id`() {
        val video = OnlineVideoMapper.toOnlineVideo(YtDlpEntryDto(id = "xyz", title = "Algo"))!!

        assertEquals("https://www.youtube.com/watch?v=xyz", video.url)
    }

    @Test
    fun `usa uploader cuando no hay channel`() {
        val entry = YtDlpEntryDto(id = "a", title = "Algo", uploader = "Cuenta")

        assertEquals("Cuenta", OnlineVideoMapper.toOnlineVideo(entry)?.channel)
    }

    /** Un directo no informa de duración, y a veces llega 0: no queremos enseñar "0:00". */
    @Test
    fun `una duracion de cero se descarta`() {
        val entry = YtDlpEntryDto(id = "a", title = "Directo", duration = 0.0)

        assertNull(OnlineVideoMapper.toOnlineVideo(entry)?.durationSeconds)
    }

    /** Ahorra datos: la miniatura grande no se nota en una fila de lista. */
    @Test
    fun `elige la miniatura mas grande que no pase del limite`() {
        val entry = YtDlpEntryDto(
            id = "a",
            title = "Algo",
            thumbnails = listOf(
                YtDlpThumbnailDto("https://img/120.jpg", 120),
                YtDlpThumbnailDto("https://img/640.jpg", 640),
                YtDlpThumbnailDto("https://img/1920.jpg", 1920),
            ),
        )

        assertEquals("https://img/640.jpg", OnlineVideoMapper.toOnlineVideo(entry)?.thumbnailUrl)
    }

    @Test
    fun `si no hay lista de miniaturas usa la suelta`() {
        val entry = YtDlpEntryDto(id = "a", title = "Algo", thumbnail = "https://img/una.jpg")

        assertEquals("https://img/una.jpg", OnlineVideoMapper.toOnlineVideo(entry)?.thumbnailUrl)
    }

    /** Sin id o sin título no se puede pintar una fila: ese resultado se descarta entero. */
    @Test
    fun `descarta los resultados incompletos en vez de romperse`() {
        val dto = YtDlpSearchDto(
            entries = listOf(
                YtDlpEntryDto(id = "ok", title = "Bueno"),
                YtDlpEntryDto(id = null, title = "Sin id"),
                YtDlpEntryDto(id = "b", title = "   "),
                null, // yt-dlp deja huecos cuando un resultado falla
            ),
        )

        val videos = OnlineVideoMapper.toOnlineVideos(dto)

        assertEquals(1, videos.size)
        assertEquals("ok", videos.first().id)
    }

    /** Lo importante: el JSON real trae cientos de campos que no nos interesan. */
    @Test
    fun `ignora los campos desconocidos del json de yt-dlp`() {
        val json = """
            {
              "_type": "playlist",
              "entries": [
                {
                  "id": "abc",
                  "title": "Un video",
                  "duration": 95.5,
                  "channel": "Canal",
                  "campo_que_no_conocemos": {"a": 1},
                  "thumbnails": [{"url": "https://img/a.jpg", "width": 480, "preference": -1}]
                }
              ]
            }
        """.trimIndent()

        val videos = OnlineVideoMapper.toOnlineVideos(YtDlpJson.decodeFromString<YtDlpSearchDto>(json))

        assertTrue(videos.size == 1)
        assertEquals("Un video", videos.first().title)
        assertEquals(95L, videos.first().durationSeconds)
        assertEquals("https://img/a.jpg", videos.first().thumbnailUrl)
    }
}
