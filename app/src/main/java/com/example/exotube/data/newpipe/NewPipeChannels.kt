package com.example.exotube.data.newpipe

import android.util.Log
import com.example.exotube.data.ytdlp.YtDlpErrorMapper
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineChannel
import com.example.exotube.domain.model.VideoPage
import com.example.exotube.domain.repository.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Los canales de YouTube con NewPipeExtractor: la cabecera (nombre, foto, suscriptores) y la
 * pestaña "Videos", por páginas, igual que la búsqueda.
 */
class NewPipeChannels(private val client: OkHttpClient) : ChannelRepository {

    /** Por dónde seguir el canal abierto. Solo se recuerda uno: el que se está mirando. */
    @Volatile
    private var cursor: Cursor? = null

    private class Cursor(val channelUrl: String, val videosTab: ListLinkHandler, val next: Page?)

    override suspend fun open(channelUrl: String?, videoUrl: String?): Result<Pair<OnlineChannel, VideoPage>> =
        attempt {
            runInterruptible(Dispatchers.IO) {
                NewPipeSetup.ensure(client)
                // Algunos videos llegan sin su canal (por ejemplo, los de "Para ti"): entonces
                // se pregunta al propio video quién lo subió.
                val url = channelUrl
                    ?: videoUrl?.let { StreamInfo.getInfo(ServiceList.YouTube, it).uploaderUrl }
                    ?: throw MediaError.NoMediaFound
                val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
                val channel = OnlineChannel(
                    url = url,
                    name = info.name,
                    avatarUrl = info.avatars.best(MAX_AVATAR_WIDTH),
                    bannerUrl = info.banners.best(MAX_BANNER_WIDTH),
                    subscriberCount = info.subscriberCount.takeIf { it >= 0 },
                    isVerified = info.isVerified,
                )
                val videosTab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
                    ?: return@runInterruptible channel to VideoPage(emptyList(), hasMore = false)
                val tab = ChannelTabInfo.getInfo(ServiceList.YouTube, videosTab)
                cursor = Cursor(url, videosTab, tab.nextPage)
                channel to VideoPage(tab.relatedItems.toVideos(channel), hasMore = tab.nextPage != null)
            }
        }

    override suspend fun more(channelUrl: String): Result<VideoPage> = attempt {
        val current = cursor?.takeIf { it.channelUrl == channelUrl }
        val next = current?.next ?: return@attempt VideoPage(emptyList(), hasMore = false)
        runInterruptible(Dispatchers.IO) {
            NewPipeSetup.ensure(client)
            val page = ChannelTabInfo.getMoreItems(ServiceList.YouTube, current.videosTab, next)
            cursor = Cursor(channelUrl, current.videosTab, page.nextPage)
            VideoPage(page.items.toVideos(null), hasMore = page.nextPage != null)
        }
    }

    /**
     * En la pestaña de un canal, YouTube no repite en cada video quién lo subió (se da por
     * sabido). Se rellena con el propio canal, para que la fila no salga sin autor.
     */
    private fun List<InfoItem>.toVideos(channel: OnlineChannel?) = streamHits().mapNotNull { hit ->
        hit.copy(
            channel = hit.channel?.takeIf { it.isNotBlank() } ?: channel?.name,
            channelUrl = hit.channelUrl?.takeIf { it.isNotBlank() } ?: channel?.url,
        ).toOnlineVideo()
    }

    /** Traduce cualquier fallo a los errores que la app ya sabe explicar. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: MediaError) {
        Result.failure(e)
    } catch (e: IOException) {
        Log.w(TAG, "Sin conexión con YouTube", e)
        Result.failure(MediaError.NoConnection)
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo abrir el canal", e)
        Result.failure(YtDlpErrorMapper.map(e))
    }

    private companion object {
        const val TAG = "NewPipeChannels"
        const val MAX_AVATAR_WIDTH = 240
        const val MAX_BANNER_WIDTH = 1280
    }
}

/** La imagen más grande que no pase de [maxWidth] (o la primera, si ninguna dice su ancho). */
private fun List<Image>.best(maxWidth: Int): String? {
    val usable = filter { it.url.isNotBlank() }
    return (usable.filter { it.width in 1..maxWidth }.maxByOrNull { it.width } ?: usable.firstOrNull())?.url
}
