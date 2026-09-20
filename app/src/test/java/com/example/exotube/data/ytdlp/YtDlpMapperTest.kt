package com.example.exotube.data.ytdlp

import com.example.exotube.domain.model.DownloadProgress
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.MediaInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtDlpMapperTest {

    private val url = "https://youtu.be/abc"

    private fun video(
        id: String,
        width: Int,
        height: Int,
        vcodec: String? = "avc1.640028",
        acodec: String? = "none",
        size: Double? = 1_000.0,
        fps: Double? = 30.0,
        tbr: Double? = 1_000.0,
        protocol: String = "https",
    ) = YtDlpFormatDto(id, "mp4", vcodec, acodec, width, height, fps, tbr, size, null, protocol)

    private fun audio(id: String, ext: String = "m4a", size: Double? = 100.0, tbr: Double = 128.0) =
        YtDlpFormatDto(id, ext, vcodec = "none", acodec = "mp4a.40.2", tbr = tbr, filesize = size, protocol = "https")

    private fun map(vararg formats: YtDlpFormatDto, duration: Double = 100.0): MediaInfo =
        YtDlpMapper.toMediaInfo(YtDlpInfoDto(title = "Título", duration = duration, formats = formats.toList()), url)

    @Test
    fun `una opcion por calidad, de mayor a menor, prefiriendo H264`() {
        val media = map(
            video("248", 1920, 1080, vcodec = "vp9", tbr = 3_000.0),
            video("137", 1920, 1080),
            video("136", 1280, 720),
            audio("251", ext = "webm", tbr = 160.0),
            audio("140"),
        )

        assertEquals(listOf("1080p", "720p"), media.videoFormats.map { it.label })
        // Video DASH sin audio: se pide junto al mejor audio y se suman los pesos.
        assertEquals("137+ba[ext=m4a]/137+ba/137", media.videoFormats[0].formatId)
        assertEquals(1_100L, media.videoFormats[0].sizeBytes)
    }

    @Test
    fun `video vertical usa el lado corto y un formato con audio no se combina`() {
        val media = map(video("h264_720", 720, 1280, acodec = "aac"))

        val option = media.videoFormats.single()
        assertEquals("720p", option.label)
        assertEquals("h264_720", option.formatId)
        assertEquals(1_000L, option.sizeBytes)
    }

    @Test
    fun `los 60 fps aparecen en la etiqueta`() {
        assertEquals("1080p60", map(video("299", 1920, 1080, fps = 59.94)).videoFormats.single().label)
    }

    @Test
    fun `prefiere descarga directa sobre HLS`() {
        val media = map(
            video("hls-2176", 1280, 720, acodec = null, tbr = 5_000.0, protocol = "m3u8_native"),
            video("http-2176", 1280, 720, acodec = null),
        )
        assertEquals("http-2176", media.videoFormats.single().formatId)
    }

    @Test
    fun `ofrece MP3 con peso estimado y M4A con peso real`() {
        val media = map(video("137", 1920, 1080), audio("140", size = 1_600_000.0), duration = 100.0)

        val (mp3, m4a) = media.audioFormats
        assertEquals("MP3", mp3.label)
        assertEquals(100L * 192 * 1000 / 8, mp3.sizeBytes) // 100 s a 192 kbps
        assertEquals("M4A", m4a.label)
        assertEquals(1_600_000L, m4a.sizeBytes)
    }

    @Test
    fun `peso desconocido si falta el del video o el del audio`() {
        val media = map(video("137", 1920, 1080, size = null), audio("140"))
        assertNull(media.videoFormats.single().sizeBytes)
    }

    @Test
    fun `un GIF sin audio no ofrece opciones de audio`() {
        val media = map(video("gif", 480, 270, acodec = "none"))
        assertEquals(0, media.audioFormats.size)
        assertEquals("gif", media.videoFormats.single().formatId)
    }

    @Test
    fun `un carrusel usa la primera entrada con formatos`() {
        val info = YtDlpInfoDto(
            type = "playlist",
            title = "Carrusel",
            entries = listOf(null, YtDlpInfoDto(title = "Foto"), YtDlpInfoDto(title = "Clip", formats = listOf(video("1", 720, 1280, acodec = "aac")))),
        )
        val media = YtDlpMapper.toMediaInfo(info, url)
        assertEquals("Clip", media.title)
        assertEquals(3, media.playlistIndex) // yt-dlp cuenta desde 1
    }

    @Test
    fun `un video suelto no tiene posicion de playlist`() {
        assertNull(map(video("137", 1920, 1080)).playlistIndex)
    }

    @Test
    fun `interpreta las lineas de progreso de yt-dlp`() {
        assertEquals(
            DownloadProgress.Downloading(45.2f, 83),
            YtDlpMapper.toDownloadProgress(45.2f, 83, "[download]  45.2% of 10.00MiB at 1.00MiB/s ETA 01:23"),
        )
        assertEquals(
            DownloadProgress.Downloading(0.5f, null),
            YtDlpMapper.toDownloadProgress(0.5f, -1, "[download]   0.5% of ~10.00MiB at Unknown ETA Unknown"),
        )
        assertEquals(
            DownloadProgress.Processing,
            YtDlpMapper.toDownloadProgress(100f, 0, "[Merger] Merging formats into \"video.mp4\""),
        )
        // Antes de empezar la descarga (percent = -1) o líneas informativas: sin cambios.
        assertNull(YtDlpMapper.toDownloadProgress(-1f, -1, "[download] Destination: video.f137.mp4"))
        assertNull(YtDlpMapper.toDownloadProgress(100f, 0, "Deleting original file video.f137.mp4"))
    }

    @Test(expected = MediaError.NoMediaFound::class)
    fun `un perfil sin videos lanza NoMediaFound`() {
        YtDlpMapper.toMediaInfo(YtDlpInfoDto(type = "playlist", entries = listOf(YtDlpInfoDto(title = "Post"))), url)
    }

    @Test
    fun `parsea JSON de yt-dlp con campos desconocidos, nulls y decimales`() {
        val json = """
            {
              "id": "abc", "title": "Mi video", "duration": 212.5, "view_count": 10,
              "thumbnail": "https://i.ytimg.com/vi/abc/hq.jpg", "entries": null,
              "formats": [
                {"format_id": "sb0", "ext": "mhtml", "vcodec": "none", "acodec": "none", "protocol": "mhtml"},
                {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2",
                 "tbr": 129.5, "filesize": 3430000, "protocol": "https"},
                {"format_id": "137", "ext": "mp4", "vcodec": "avc1.640028", "acodec": "none",
                 "width": 1920, "height": 1080, "fps": 29.97, "filesize": null,
                 "filesize_approx": 45000000, "protocol": "https", "http_headers": {"User-Agent": "x"}}
              ]
            }
        """.trimIndent()

        val media = YtDlpMapper.toMediaInfo(YtDlpJson.decodeFromString<YtDlpInfoDto>(json), url)

        assertEquals("Mi video", media.title)
        assertEquals(212L, media.durationSeconds)
        assertEquals(listOf("1080p"), media.videoFormats.map { it.label }) // el storyboard se ignora
        assertEquals(48_430_000L, media.videoFormats.single().sizeBytes)
    }
}
