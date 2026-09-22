package com.example.exotube.data.ytdlp

import android.util.Log
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.VideoComment
import com.example.exotube.domain.repository.CommentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/**
 * Lee los comentarios de un video de YouTube con el mismo yt-dlp que ya usa la app.
 *
 * Solo se leen. Para escribir un comentario haría falta iniciar sesión con una cuenta de Google,
 * que es precisamente lo que ExoTube no pide a nadie.
 *
 * Traer comentarios es de lo más lento que hace yt-dlp: son peticiones aparte, una por cada
 * tanda. Por eso se piden pocos, ordenados por los más votados (que son los que la gente
 * quiere leer) y se guardan mientras dure la sesión.
 */
class YtDlpComments(private val engine: YtDlpEngine) : CommentsRepository {

    /**
     * Los comentarios ya traídos en esta sesión. Volver a abrir el mismo video es instantáneo en
     * vez de costar otros diez segundos.
     */
    private val cache = LinkedHashMap<String, List<VideoComment>>()

    override suspend fun comments(video: OnlineVideo): Result<List<VideoComment>> {
        cache[video.id]?.let { return Result.success(it) }

        return try {
            val request = engine.newRequest(video.url)
                .addOption("--dump-single-json")
                // Sin descargar nada del video: solo se quiere su ficha con los comentarios.
                .addOption("--skip-download")
                .addOption("--write-comments")
                // Los más votados primero, y un tope: pedir "todos" en un video popular serían
                // decenas de miles y minutos de espera.
                .addOption("--extractor-args", EXTRACTOR_ARGS)
                .addOption("--no-warnings")

            val json = engine.run(request).out
            val comments = withContext(Dispatchers.Default) {
                YtDlpJson.decodeFromString<YtDlpCommentsDto>(json).toComments()
            }
            remember(video.id, comments)
            Result.success(comments)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron traer los comentarios de ${video.id}", e)
            Result.failure(YtDlpErrorMapper.map(e))
        }
    }

    /** Caché pequeña y con memoria corta: unos pocos videos bastan y no se come la RAM. */
    private fun remember(videoId: String, comments: List<VideoComment>) {
        cache[videoId] = comments
        if (cache.size > CACHED_VIDEOS) cache.remove(cache.keys.first())
    }

    private companion object {
        const val TAG = "YtDlpComments"
        const val CACHED_VIDEOS = 5

        /**
         * `max_comments` va en cuatro partes: total, por hilo principal, respuestas totales y
         * respuestas por hilo. Se piden los 60 mejores comentarios y ninguna respuesta: las
         * respuestas multiplican las peticiones y casi nadie las lee.
         */
        const val EXTRACTOR_ARGS = "youtube:comment_sort=top;max_comments=60,60,0,0"
    }
}

// --- Lo que devuelve yt-dlp ---

@Serializable
internal data class YtDlpCommentsDto(
    val comments: List<YtDlpCommentDto?> = emptyList(),
)

@Serializable
internal data class YtDlpCommentDto(
    val id: String? = null,
    val text: String? = null,
    val author: String? = null,
    @SerialName("author_thumbnail") val authorThumbnail: String? = null,
    @SerialName("like_count") val likeCount: Long? = null,
    @SerialName("_time_text") val timeText: String? = null,
    /** yt-dlp lo marca cuando quien comenta es el dueño del canal. */
    @SerialName("author_is_uploader") val authorIsUploader: Boolean? = null,
    /**
     * "root" en un comentario principal, o el id del padre si es una respuesta. Sirve para
     * quedarse solo con los principales y contar cuántas respuestas cuelgan de cada uno.
     */
    val parent: String? = null,
)

/**
 * Convierte la respuesta de yt-dlp en comentarios que la pantalla pueda pintar.
 *
 * Solo salen los comentarios principales: las respuestas se cuentan y se enseña el número, pero
 * no se mezclan en la lista, porque intercaladas se pierde por completo el hilo de la
 * conversación.
 *
 * Función pura, para poder comprobarla con un test sin red.
 */
internal fun YtDlpCommentsDto.toComments(): List<VideoComment> {
    val all = comments.filterNotNull()
    val repliesByParent = all.filter { it.parent != null && it.parent != ROOT }
        .groupingBy { it.parent }
        .eachCount()

    return all
        .filter { it.parent == null || it.parent == ROOT }
        .mapNotNull { dto -> dto.toComment(repliesByParent[dto.id] ?: 0) }
}

private fun YtDlpCommentDto.toComment(replyCount: Int): VideoComment? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    val text = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return VideoComment(
        id = id,
        // Un comentario sin autor no es normal, pero tampoco es motivo para esconderlo.
        author = author?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN_AUTHOR,
        authorAvatarUrl = authorThumbnail?.takeIf { it.isNotBlank() },
        text = text,
        likeCount = likeCount?.takeIf { it > 0 },
        publishedText = timeText?.takeIf { it.isNotBlank() },
        isFromCreator = authorIsUploader == true,
        replyCount = replyCount,
    )
}

private const val ROOT = "root"
private const val UNKNOWN_AUTHOR = "Alguien"
