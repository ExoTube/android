package com.example.exotube.data.ytdlp

import com.example.exotube.domain.model.MediaError
import com.yausername.youtubedl_android.YoutubeDLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpErrorMapperTest {

    private fun map(stderr: String) = YtDlpErrorMapper.map(YoutubeDLException(stderr))

    @Test
    fun `video privado de YouTube`() {
        assertEquals(
            MediaError.PrivateContent,
            map("ERROR: [youtube] abc: Private video. Sign in if you've been granted access to this video"),
        )
    }

    @Test
    fun `Instagram que pide login, ignorando las advertencias previas`() {
        val stderr = """
            WARNING: [Instagram] abc: General metadata extraction failed
            ERROR: [Instagram] abc: Requested content is not available, rate-limit reached or login required. Use --cookies
        """.trimIndent()
        assertEquals(MediaError.PrivateContent, map(stderr))
    }

    @Test
    fun `tweet sin video`() {
        assertEquals(MediaError.NoMediaFound, map("ERROR: [twitter] 123: No video could be found in this tweet"))
    }

    @Test
    fun `sin conexion a internet`() {
        assertEquals(
            MediaError.NoConnection,
            map("ERROR: [generic] Unable to download webpage: <urlopen error [Errno 7] No address associated with hostname>"),
        )
    }

    @Test
    fun `error desconocido conserva la causa para depurar`() {
        val error = map("ERROR: [TikTok] 123: Unable to extract universal data for rehydration; please report this issue")
        assertTrue(error is MediaError.Unknown)
        assertTrue(error.cause is YoutubeDLException)
    }

    @Test
    fun `un MediaError pasa sin cambios`() {
        assertEquals(MediaError.NoMediaFound, YtDlpErrorMapper.map(MediaError.NoMediaFound))
    }

    /**
     * Si se rompe el propio yt-dlp, Python escupe un Traceback sin ninguna línea "ERROR:".
     * Ese texto lleva las rutas de sus archivos, y una se llama "cookies.py": buscar pistas ahí
     * hacía que la app dijera "este contenido es privado" cuando el problema era otro.
     */
    @Test
    fun `un fallo del motor no se confunde con contenido privado`() {
        val stderr = """
            Traceback (most recent call last):
              File "/data/user/0/com.example.exotube/no_backup/youtubedl-android/yt-dlp/yt_dlp/cookies.py", line 33, in <module>
              File "<frozen zipimport>", line 538, in _get_data
            zipimport.ZipImportError: bad local file header: '/data/user/0/com.example.exotube/no_backup/youtubedl-android/yt-dlp'
        """.trimIndent()

        val error = map(stderr)

        assertTrue(error is MediaError.Unknown)
        assertTrue(error.isRetryable)
    }

    /** Un corte de red sigue detectándose aunque el texto no traiga una línea "ERROR:". */
    @Test
    fun `sin conexion tambien fuera de la linea ERROR`() {
        assertEquals(
            MediaError.NoConnection,
            map("<urlopen error [Errno 7] No address associated with hostname>"),
        )
    }
}
