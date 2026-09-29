package com.example.exotube.data.newpipe

import com.example.exotube.data.ytdlp.QuickVideoLists
import com.example.exotube.domain.model.OnlineVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

/**
 * Las listas de "Para ti" con NewPipe: el Mix de un video y la búsqueda por artista.
 *
 * Con yt-dlp, cada bloque de "Para ti" era arrancar Python y esperar varios segundos, y con tres
 * bloques el usuario miraba la rueda girar un buen rato. NewPipe hace la misma consulta desde la
 * propia app, en menos de un segundo.
 */
class NewPipeVideoLists(private val client: OkHttpClient, private val search: NewPipeSearch) : QuickVideoLists {

    /** El Mix de YouTube de un video ("RD" + su id): una lista de temas parecidos. */
    override suspend fun mix(videoId: String): List<OnlineVideo> = runInterruptible(Dispatchers.IO) {
        NewPipeSetup.ensure(client)
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId&list=RD$videoId")
        info.relatedItems.streamHits().mapNotNull { it.toOnlineVideo() }
    }

    override suspend fun search(text: String): List<OnlineVideo> = search.first(text).videos
}
