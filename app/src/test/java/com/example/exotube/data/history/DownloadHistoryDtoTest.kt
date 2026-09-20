package com.example.exotube.data.history

import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaFormat
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.Platform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadHistoryDtoTest {

    private val request = DownloadRequest(
        url = "https://vm.tiktok.com/ZMabc/",
        title = "Un título",
        platform = Platform.TIKTOK,
        format = MediaFormat("h264_720", MediaType.VIDEO, "720p", "mp4", sizeBytes = null),
    )

    @Test
    fun `los valores respetan los CHECK del SQL`() {
        val dto = request.copy(title = "x".repeat(800)).toHistoryDto(fileSizeBytes = 12_345)

        assertEquals("tiktok", dto.platform)
        assertEquals("video", dto.mediaType)
        assertEquals("720p", dto.format)
        assertEquals(500, dto.title.length)
        assertEquals(12_345L, dto.fileSizeBytes)
    }

    @Test
    fun `el JSON usa exactamente los nombres de columna de la tabla`() {
        val json: JsonObject = Json.encodeToJsonElement(DownloadHistoryInsertDto.serializer(), request.toHistoryDto(1))
            .jsonObject

        assertEquals(
            setOf("original_url", "platform", "title", "format", "media_type", "file_size_bytes"),
            json.keys,
        )
    }
}
