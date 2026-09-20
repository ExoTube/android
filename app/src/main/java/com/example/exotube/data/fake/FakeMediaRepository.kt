package com.example.exotube.data.fake

import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.MediaFormat
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.Platform
import com.example.exotube.domain.repository.MediaRepository
import kotlinx.coroutines.delay

/**
 * Datos falsos para diseñar la UI sin red ni yt-dlp: actívalo en
 * [com.example.exotube.di.AppContainer] cuando trabajes con el diseño de Figma.
 *
 * Truco de prueba: si la URL contiene "private", simula un video privado.
 */
class FakeMediaRepository : MediaRepository {

    override suspend fun fetchMediaInfo(url: String): Result<MediaInfo> {
        delay(1_200) // simula la latencia de red
        if ("private" in url) return Result.failure(MediaError.PrivateContent)
        return Result.success(sampleMediaInfo(url))
    }
}

/** Datos de muestra, también usados por los @Preview de Compose. */
fun sampleMediaInfo(url: String = "https://youtu.be/dQw4w9WgXcQ") = MediaInfo(
    sourceUrl = url,
    platform = Platform.fromUrl(url),
    title = "Video de ejemplo con un título lo bastante largo para ocupar dos líneas",
    thumbnailUrl = null,
    durationSeconds = 212,
    formats = listOf(
        MediaFormat("bv*[height<=1080]+ba/b", MediaType.VIDEO, "1080p", "mp4", 48_300_000),
        MediaFormat("bv*[height<=720]+ba/b", MediaType.VIDEO, "720p", "mp4", 24_100_000),
        MediaFormat("bv*[height<=480]+ba/b", MediaType.VIDEO, "480p", "mp4", 12_600_000),
        MediaFormat("bv*[height<=360]+ba/b", MediaType.VIDEO, "360p", "mp4", null),
        MediaFormat("ba/b", MediaType.AUDIO, "MP3", "mp3", 3_400_000),
        MediaFormat("ba[ext=m4a]/ba", MediaType.AUDIO, "M4A", "m4a", 3_100_000),
    ),
)
