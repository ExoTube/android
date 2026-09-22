package com.example.exotube.data.newpipe

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * Cómo se conecta NewPipeExtractor a internet.
 *
 * La librería no trae conexión propia: pide que la app le dé una, y así cada app usa la que ya
 * tiene. Aquí es OkHttp con el mismo [client] que el reproductor, que sale siempre por IPv4.
 * Eso importa: YouTube ata el enlace del video a la IP que lo pidió, y si NewPipe lo pidiera por
 * una y el reproductor lo descargara por otra, YouTube lo rechazaría.
 */
internal class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), bodyOf(request))
            .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            // YouTube contesta 429 cuando cree que habla con un robot. NewPipe espera esta
            // excepción concreta para decir "demasiadas peticiones" en vez de un error raro.
            if (response.code == TOO_MANY_REQUESTS) {
                throw ReCaptchaException("YouTube pide un captcha", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }

    /** GET y HEAD no llevan cuerpo; un POST siempre lleva uno, aunque vaya vacío. */
    private fun bodyOf(request: Request) = request.dataToSend()?.toRequestBody()
        ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null

    private companion object {
        const val TOO_MANY_REQUESTS = 429

        /** El mismo que usa la app NewPipe: un navegador normal de escritorio. */
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
