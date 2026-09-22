package com.example.exotube.data.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * La conexión que usan los videos en línea, de principio a fin: NewPipe al pedir el enlace y el
 * reproductor al descargarlo. Es la MISMA a propósito, porque YouTube ata el enlace a quien lo
 * pidió y lo rechaza si la descarga llega "de otro sitio".
 */
object StreamHttpClient {

    /**
     * Sin "fastFallback": con él, OkHttp probaría a la vez IPv4 e IPv6 y se quedaría con la que
     * contestara antes, que puede ser justo la que YouTube va a rechazar. Así se prueban en el
     * orden de [Ipv4FirstDns] y la IPv6 solo entra si la IPv4 no conecta.
     */
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .dns(Ipv4FirstDns)
        .fastFallback(false)
        .addInterceptor(YoutubeStreamUserAgent)
        .build()
}

/**
 * YouTube da enlaces distintos según con qué "cliente" se pidan (su app de Android, la de iPhone,
 * la de las gafas de Apple...), y algunos solo se descargan si quien los pide dice ser ese mismo
 * cliente. Aquí se pone el User-Agent que toca en cada caso, igual que hace la app NewPipe.
 * A cualquier otra petición no le cambia nada.
 */
private object YoutubeStreamUserAgent : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val userAgent = youtubeStreamUserAgent(request.url.toString()) ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
    }
}

internal fun youtubeStreamUserAgent(url: String): String? = when {
    YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
    YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
    YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
    else -> null
}
