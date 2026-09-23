package com.example.exotube.data.newpipe

import com.example.exotube.data.history.youtubeIdOrNull
import com.example.exotube.domain.model.OnlineVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Busca en YouTube con NewPipeExtractor, por páginas.
 *
 * Con yt-dlp había que decir de antemano cuántos resultados se querían, y pedir más era repetir
 * la búsqueda entera. Aquí YouTube devuelve una página y "por dónde seguir" ([Next]): al llegar
 * al final de la lista se pide la siguiente, como en la app de YouTube.
 */
class NewPipeSearch(private val client: OkHttpClient) {

    /** Por dónde seguir una búsqueda. No se mira por dentro: se guarda y se devuelve. */
    class Next internal constructor(internal val query: SearchQueryHandler, internal val page: Page)

    class Results(val videos: List<OnlineVideo>, val next: Next?)

    suspend fun first(query: String): Results = runInterruptible(Dispatchers.IO) {
        NewPipeSetup.ensure(client)
        val handler = ServiceList.YouTube.searchQHFactory
            .fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val info = SearchInfo.getInfo(ServiceList.YouTube, handler)
        Results(info.relatedItems.toOnlineVideos(), info.nextPage?.let { Next(handler, it) })
    }

    suspend fun more(next: Next): Results = runInterruptible(Dispatchers.IO) {
        NewPipeSetup.ensure(client)
        val page = SearchInfo.getMoreItems(ServiceList.YouTube, next.query, next.page)
        Results(page.items.toOnlineVideos(), page.nextPage?.let { Next(next.query, it) })
    }

    private fun List<InfoItem>.toOnlineVideos(): List<OnlineVideo> = streamHits().mapNotNull { it.toOnlineVideo() }
}

/** Los videos de una lista de NewPipe (que también trae canales y playlists), ya sin sus clases. */
internal fun List<InfoItem>.streamHits(): List<SearchHit> = filterIsInstance<StreamInfoItem>().map { item ->
    SearchHit(
        url = item.url,
        title = item.name,
        channel = item.uploaderName,
        durationSeconds = item.duration,
        viewCount = item.viewCount,
        thumbnails = item.thumbnails.map { it.url to it.width },
        isLive = item.streamType == StreamType.LIVE_STREAM || item.streamType == StreamType.AUDIO_LIVE_STREAM,
        channelUrl = item.uploaderUrl,
    )
}

/** Un resultado de búsqueda, ya sin las clases de NewPipe, para poder probar la conversión. */
internal data class SearchHit(
    val url: String?,
    val title: String?,
    val channel: String?,
    /** En segundos; NewPipe da -1 o 0 cuando no lo sabe. */
    val durationSeconds: Long,
    /** NewPipe da -1 cuando no lo sabe. */
    val viewCount: Long,
    /** Cada miniatura con su ancho en píxeles (0 si no se sabe). */
    val thumbnails: List<Pair<String, Int>>,
    val isLive: Boolean,
    val channelUrl: String? = null,
)

/**
 * El resultado convertido en una fila de Explorar, o null si no sirve: sin enlace o título no se
 * puede mostrar, y un directo no se puede reproducir (no es un archivo, sino una emisión que no
 * acaba nunca).
 */
internal fun SearchHit.toOnlineVideo(): OnlineVideo? {
    if (isLive) return null
    val link = url?.takeIf { it.isNotBlank() } ?: return null
    val id = youtubeIdOrNull(link) ?: return null
    val name = title?.takeIf { it.isNotBlank() } ?: return null
    return OnlineVideo(
        id = id,
        url = link,
        title = name,
        channel = channel?.takeIf { it.isNotBlank() },
        durationSeconds = durationSeconds.takeIf { it > 0 },
        thumbnailUrl = bestThumbnail(thumbnails),
        viewCount = viewCount.takeIf { it > 0 },
        channelUrl = channelUrl?.takeIf { it.startsWith("http") },
    )
}

/**
 * La miniatura más grande que no pase de [MAX_THUMBNAIL_WIDTH], igual que con yt-dlp: en una
 * fila de lista no se nota más y así no se gastan datos en una imagen enorme.
 */
internal fun bestThumbnail(thumbnails: List<Pair<String, Int>>): String? {
    val usable = thumbnails.filter { it.first.isNotBlank() }
    return (usable.filter { it.second in 1..MAX_THUMBNAIL_WIDTH }.maxByOrNull { it.second } ?: usable.firstOrNull())
        ?.first
}

private const val MAX_THUMBNAIL_WIDTH = 800
