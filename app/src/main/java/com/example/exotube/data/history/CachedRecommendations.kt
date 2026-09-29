package com.example.exotube.data.history

import android.util.Log
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.Recommendation
import com.example.exotube.domain.repository.RecommendationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * "Para ti" con memoria: guarda las últimas recomendaciones en el teléfono para enseñarlas al
 * instante la próxima vez, mientras se piden las nuevas por detrás.
 *
 * Sin esto, cada vez que se abría Explorar había que esperar a internet antes de ver nada. Con
 * esto, lo de ayer sale en el acto y en uno o dos segundos se cambia por lo de hoy.
 */
class CachedRecommendations(
    private val source: RecommendationRepository,
    private val file: File,
) : RecommendationRepository by source {

    override suspend fun forYou(): Result<List<Recommendation>> {
        val result = source.forYou()
        result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { save(it) }
        return result
    }

    override suspend fun lastForYou(): List<Recommendation>? = withContext(Dispatchers.IO) {
        try {
            if (!file.isFile) return@withContext null
            json.decodeFromString<List<SavedBlock>>(file.readText()).map { it.toRecommendation() }.ifEmpty { null }
        } catch (e: Exception) {
            // Un archivo roto (una versión antigua, una escritura a medias) no debe impedir nada.
            Log.w(TAG, "No se pudo leer el Para ti guardado", e)
            null
        }
    }

    private suspend fun save(blocks: List<Recommendation>) = withContext(Dispatchers.IO) {
        try {
            // Se escribe en otro archivo y se renombra: así nunca queda uno a medio escribir.
            val temp = File(file.path + ".tmp")
            temp.writeText(json.encodeToString(blocks.map(SavedBlock::from)))
            temp.renameTo(file)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar el Para ti", e)
        }
    }

    @Serializable
    private data class SavedBlock(val becauseOf: String, val videos: List<SavedVideo>) {
        fun toRecommendation() = Recommendation(becauseOf, videos.map { it.toOnlineVideo() })

        companion object {
            fun from(block: Recommendation) = SavedBlock(block.becauseOf, block.videos.map(SavedVideo::from))
        }
    }

    @Serializable
    private data class SavedVideo(
        val id: String,
        val url: String,
        val title: String,
        val channel: String? = null,
        val durationSeconds: Long? = null,
        val thumbnailUrl: String? = null,
        val viewCount: Long? = null,
        val channelUrl: String? = null,
    ) {
        fun toOnlineVideo() = OnlineVideo(id, url, title, channel, durationSeconds, thumbnailUrl, viewCount, channelUrl)

        companion object {
            fun from(video: OnlineVideo) = with(video) {
                SavedVideo(id, url, title, channel, durationSeconds, thumbnailUrl, viewCount, channelUrl)
            }
        }
    }

    private companion object {
        const val TAG = "CachedRecommendations"
        val json = Json { ignoreUnknownKeys = true }
    }
}
