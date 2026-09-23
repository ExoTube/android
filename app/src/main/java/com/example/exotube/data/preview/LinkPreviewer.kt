package com.example.exotube.data.preview

import com.example.exotube.domain.model.MediaFormat
import com.example.exotube.domain.model.MediaInfo
import com.example.exotube.domain.model.MediaType
import com.example.exotube.domain.model.Platform
import com.example.exotube.data.ytdlp.YtDlpMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Una vista previa rápida de enlaces de TikTok y X, para que la hoja de descarga aparezca en un
 * segundo en vez de en cinco.
 *
 * ¿Por qué tarda yt-dlp? Solo arrancar Python ya son 1,5 s en un teléfono, y TikTok además obliga
 * a pedir la página dos veces (la primera responde con un "reto" que hay que resolver). Mientras
 * tanto, el usuario mira una hoja vacía.
 *
 * Estas dos redes tienen una puerta pública y ligera pensada para enseñar sus videos en otras
 * webs (la que usan los periódicos para incrustarlos): una sola consulta, sin Python, que ya dice
 * el título y la miniatura. Con eso se enseña la hoja al momento, y yt-dlp sigue trabajando por
 * detrás para completar las calidades exactas y el peso. Instagram y Facebook no tienen nada
 * parecido sin iniciar sesión: con ellos se espera a yt-dlp, como siempre.
 */
class LinkPreviewer(private val client: OkHttpClient) {

    /** La vista previa de [url], o null si esa red no tiene vista rápida o si falla. */
    suspend fun preview(url: String): MediaInfo? = when (Platform.fromUrl(url)) {
        Platform.TIKTOK -> fetchJson(tiktokOembedUrl(resolveShortLink(url)))?.let { tiktokPreview(it, url) }
        Platform.X -> tweetId(url)?.let { id -> fetchJson(tweetUrl(id))?.let { tweetPreview(it, url) } }
        else -> null
    }

    /**
     * Los enlaces cortos de la app de TikTok (vm.tiktok.com/…, tiktok.com/t/…) no los entiende la
     * puerta pública: primero hay que seguir la redirección hasta el enlace largo del video.
     */
    private suspend fun resolveShortLink(url: String): String {
        val isShort = url.contains("//vm.tiktok.com") || url.contains("//vt.tiktok.com") || url.contains("tiktok.com/t/")
        if (!isShort) return url
        return runInterruptible(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).head().build()).execute().use { it.request.url.toString() }
        }
    }

    private suspend fun fetchJson(url: String): JsonObject? = runInterruptible(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()?.let { Json.parseToJsonElement(it) as? JsonObject }
        }
    }

    private fun tiktokOembedUrl(videoUrl: String) =
        "https://www.tiktok.com/oembed".toHttpUrl().newBuilder().addQueryParameter("url", videoUrl).build().toString()

    // La puerta de X pide un "token", pero no lo comprueba: cualquier texto vale.
    private fun tweetUrl(id: String) = "https://cdn.syndication.twimg.com/tweet-result?id=$id&token=a"
}

/** El número de un tuit: x.com/usuario/status/123… o twitter.com/i/status/123… */
internal fun tweetId(url: String): String? = Regex("/status(?:es)?/(\\d+)").find(url)?.groupValues?.get(1)

/**
 * TikTok: la puerta pública da el título, el autor y la miniatura, pero no las calidades. Se
 * ofrece "Auto" (la mejor que haya, que es lo que casi todo el mundo quiere de un TikTok) y el
 * sonido, con los mismos selectores que usaría yt-dlp.
 */
internal fun tiktokPreview(oembed: JsonObject, url: String): MediaInfo? {
    if (oembed.string("type") != "video") return null
    val title = oembed.string("title")?.takeIf { it.isNotBlank() }
        ?: oembed.string("author_name")
        ?: return null
    return MediaInfo(
        sourceUrl = url,
        platform = Platform.TIKTOK,
        title = title,
        thumbnailUrl = oembed.string("thumbnail_url"),
        durationSeconds = null,
        formats = listOf(AUTO_VIDEO) + audioFormats(durationSeconds = null),
    )
}

/**
 * X: la puerta pública trae cada calidad del video con su resolución y su bitrate, así que aquí
 * las opciones ya son las de verdad. El peso se estima con bitrate × duración.
 *
 * Un tuit puede llevar varios videos: yt-dlp los trata como una lista y descarga el primero, y
 * aquí se hace lo mismo (playlistIndex = 1) para que la descarga coincida con lo que se enseña.
 */
internal fun tweetPreview(tweet: JsonObject, url: String): MediaInfo? {
    val videos = (tweet["mediaDetails"] as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
        .filter { it.string("type") == "video" || it.string("type") == "animated_gif" }
    val video = videos.firstOrNull() ?: return null
    val info = video["video_info"] as? JsonObject ?: return null
    val durationSeconds = info.long("duration_millis")?.let { it / 1_000 }?.takeIf { it > 0 }

    val options = (info["variants"] as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
        .filter { it.string("content_type") == "video/mp4" }
        .mapNotNull { variant ->
            val (width, height) = resolutionIn(variant.string("url").orEmpty()) ?: return@mapNotNull null
            TweetVariant(width, height, variant.int("bitrate") ?: 0)
        }
        .groupBy { minOf(it.width, it.height) }
        .entries
        .sortedByDescending { it.key }
        .map { (quality, same) ->
            val best = same.maxBy { it.bitrate }
            MediaFormat(
                formatId = "b[height<=${best.height}][width<=${best.width}]/b",
                type = MediaType.VIDEO,
                label = "${quality}p",
                extension = "mp4",
                sizeBytes = durationSeconds?.let { seconds -> best.bitrate.toLong() * seconds / 8 }?.takeIf { it > 0 },
            )
        }
        .ifEmpty { listOf(AUTO_VIDEO) }

    val text = tweet.string("text").orEmpty()
        .replace(Regex("https://t\\.co/\\S+"), "") // el enlace al propio video que X añade al final
        .trim()
    val author = (tweet["user"] as? JsonObject)?.string("name")
    return MediaInfo(
        sourceUrl = url,
        platform = Platform.X,
        title = text.take(MAX_TITLE).ifBlank { author ?: url },
        thumbnailUrl = video.string("media_url_https"),
        durationSeconds = durationSeconds,
        formats = options + audioFormats(durationSeconds),
        playlistIndex = if (videos.size > 1) 1 else null,
    )
}

private data class TweetVariant(val width: Int, val height: Int, val bitrate: Int)

/** X escribe la resolución dentro del enlace: …/vid/1280x720/archivo.mp4 */
private fun resolutionIn(url: String): Pair<Int, Int>? =
    Regex("/(\\d+)x(\\d+)/").find(url)?.destructured?.let { (w, h) -> w.toInt() to h.toInt() }

/** La mejor calidad disponible, cuando no se sabe cuáles hay. Igual que "Auto" en yt-dlp. */
private val AUTO_VIDEO = MediaFormat("bv*+ba/b", MediaType.VIDEO, "Auto", "mp4", null)

/** Las mismas opciones de sonido que arma `YtDlpMapper`, para que no cambien al completarse. */
private fun audioFormats(durationSeconds: Long?) = listOf(
    MediaFormat("ba/b", MediaType.AUDIO, "MP3", "mp3", durationSeconds?.let { it * YtDlpMapper.MP3_BITRATE_KBPS * 1000 / 8 }),
    MediaFormat("ba[ext=m4a]/ba/b", MediaType.AUDIO, "M4A", "m4a", null),
)

private const val MAX_TITLE = 120

private fun JsonObject.string(key: String): String? = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

private fun JsonObject.int(key: String): Int? = runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

private fun JsonObject.long(key: String): Long? = runCatching { this[key]?.jsonPrimitive?.longOrNull }.getOrNull()

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
