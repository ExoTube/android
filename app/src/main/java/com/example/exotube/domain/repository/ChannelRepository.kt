package com.example.exotube.domain.repository

import com.example.exotube.domain.model.OnlineChannel
import com.example.exotube.domain.model.VideoPage

/** Los canales de YouTube: quién es y qué videos ha subido, del más nuevo al más viejo. */
interface ChannelRepository {

    /**
     * Abre el canal de [channelUrl] y trae su primera página de videos. Si no se sabe el canal
     * pero sí un video suyo ([videoUrl]), se averigua a partir del video.
     */
    suspend fun open(channelUrl: String?, videoUrl: String?): Result<Pair<OnlineChannel, VideoPage>>

    /** La página siguiente del canal abierto con [open]. */
    suspend fun more(channelUrl: String): Result<VideoPage>
}
