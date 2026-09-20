package com.example.exotube.download

import androidx.work.workDataOf
import com.example.exotube.domain.model.DownloadRequest
import com.example.exotube.domain.model.MediaFormat
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadWorkDataTest {

    @Test
    fun `ida y vuelta conserva todos los datos que necesita el worker`() {
        val request = DownloadRequest(
            url = "https://www.instagram.com/p/abc/",
            title = "Carrusel",
            platform = Platform.INSTAGRAM,
            format = MediaFormat("1+ba[ext=m4a]/1+ba/1", MediaType.VIDEO, "720p", "mp4", sizeBytes = null),
            playlistIndex = 3,
        )

        assertEquals(request, request.toWorkData().toDownloadRequest())
    }

    @Test
    fun `sin playlist se recupera como null`() {
        val request = DownloadRequest(
            url = "https://youtu.be/abc",
            title = "Video",
            platform = Platform.YOUTUBE,
            format = MediaFormat("ba/b", MediaType.AUDIO, "MP3", "mp3", sizeBytes = null),
        )

        assertNull(request.toWorkData().toDownloadRequest()!!.playlistIndex)
    }

    @Test
    fun `datos incompletos devuelven null en vez de fallar`() {
        assertNull(workDataOf("title" to "sin url").toDownloadRequest())
    }
}
