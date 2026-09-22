package com.example.exotube.data.ytdlp

import android.util.Log
import com.example.exotube.data.history.ListeningDao
import com.example.exotube.domain.model.ListeningSeed
import com.example.exotube.domain.model.MediaError
import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.Recommendation
import com.example.exotube.domain.repository.RecommendationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Recomienda según lo que el usuario escucha, **sin cuentas y sin servidor propio**.
 *
 * La idea: YouTube ya tiene un motor de recomendación buenísimo y lo ofrece sin necesidad de
 * iniciar sesión. Cada video tiene su "Mix" (una lista infinita de temas parecidos, con el
 * identificador `RD` + el del video). Pidiéndole el Mix de lo que el usuario ha escuchado se
 * obtienen recomendaciones de verdad, mientras que el perfil de gustos nunca sale del teléfono:
 * lo único que viaja es "dame el mix de este video", igual que si lo abriera cualquiera.
 *
 * Para lo descargado, que no tiene video de origen, se busca por el artista. Peor que un Mix,
 * pero sigue dando algo relacionado en vez de nada.
 */
class YtDlpRecommendations(
    private val engine: YtDlpEngine,
    private val history: ListeningDao,
) : RecommendationRepository {

    override suspend fun hasEnoughHistory(): Boolean = withContext(Dispatchers.IO) {
        runCatching { history.count() > 0 }.getOrDefault(false)
    }

    override suspend fun forYou(): Result<List<Recommendation>> = try {
        val seeds = withContext(Dispatchers.IO) { history.topPlayed(MAX_SEEDS) }
            .map { ListeningSeed(it.mediaKey, it.title, it.artist, it.videoId) }
            .distinctSeeds()

        if (seeds.isEmpty()) {
            // Sin historial no hay nada que recomendar, y eso no es un fallo: es el primer día.
            Result.success(emptyList())
        } else {
            // En paralelo: cada semilla es un arranque de yt-dlp de varios segundos, y en serie
            // el usuario estaría mirando una rueda girar medio minuto.
            val results = coroutineScope {
                seeds.map { seed -> async { recommendationFor(seed) } }.awaitAll()
            }
            outcomeOf(results)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "No se pudieron traer las recomendaciones", e)
        Result.failure(YtDlpErrorMapper.map(e))
    }

    /** null cuando la semilla no da para recomendar; el fallo se propaga para poder explicarlo. */
    private suspend fun recommendationFor(seed: ListeningSeed): Result<Recommendation?> {
        val videos = when {
            seed.videoId != null -> mixOf(seed.videoId)
            !seed.artist.isNullOrBlank() -> searchOf(seed.artist)
            else -> return Result.success(null)
        }
        return videos.map { list ->
            // Lo que ya escuchó no es una recomendación: fuera de la lista.
            val fresh = list.filterNot { it.id == seed.videoId }
            Recommendation(becauseOf = seed.artist ?: seed.title, videos = fresh.take(PER_BLOCK))
        }
    }

    /**
     * El "Mix" de un video: la lista que YouTube genera con temas parecidos.
     *
     * `--flat-playlist` es imprescindible: sin él, yt-dlp analizaría uno por uno los cincuenta
     * videos de la lista y esto tardaría minutos en vez de segundos.
     */
    private suspend fun mixOf(videoId: String): Result<List<OnlineVideo>> =
        runCatching {
            val request = engine.newRequest("https://www.youtube.com/watch?v=$videoId&list=RD$videoId")
                .addOption("--dump-single-json")
                .addOption("--flat-playlist")
                .addOption("--playlist-end", MIX_SIZE)
                .addOption("--no-warnings")
            val json = engine.run(request).out
            withContext(Dispatchers.Default) {
                OnlineVideoMapper.toOnlineVideos(YtDlpJson.decodeFromString<YtDlpSearchDto>(json))
            }
        }
            .onFailure { Log.i(TAG, "Sin mix para $videoId: ${it.message}") }
            .mapError()

    /** Plan B para lo descargado: buscar por el artista. */
    private suspend fun searchOf(artist: String): Result<List<OnlineVideo>> =
        runCatching {
            val request = engine.newRequest("ytsearch$MIX_SIZE:$artist")
                .addOption("--dump-single-json")
                .addOption("--flat-playlist")
                .addOption("--no-warnings")
            val json = engine.run(request).out
            withContext(Dispatchers.Default) {
                OnlineVideoMapper.toOnlineVideos(YtDlpJson.decodeFromString<YtDlpSearchDto>(json))
            }
        }
            .onFailure { Log.i(TAG, "Sin resultados para $artist: ${it.message}") }
            .mapError()

    private companion object {
        const val TAG = "YtDlpRecommendations"

        /** Bloques de "Porque escuchaste …". Más serían más esperas y más de lo mismo. */
        const val MAX_SEEDS = 3

        const val MIX_SIZE = 25
        const val PER_BLOCK = 15
    }
}

/**
 * Una semilla por artista.
 *
 * Sin esto, escuchar cuatro canciones del mismo grupo llenaría "Para ti" con cuatro bloques
 * iguales titulados "Porque escuchaste Soda Stereo". El objetivo es descubrir cosas, no ver la
 * misma lista tres veces.
 *
 * Función aparte para poder comprobarla con un test.
 */
internal fun List<ListeningSeed>.distinctSeeds(): List<ListeningSeed> =
    distinctBy { it.artist?.lowercase() ?: it.mediaKey }

/**
 * Qué se le enseña al usuario cuando ya se ha intentado recomendar con cada semilla.
 *
 * La distinción que hay que acertar aquí: "no tienes historial" y "no pude traerlo" se ven
 * iguales desde dentro (en los dos casos no hay nada que pintar) pero no tienen nada que ver
 * para quien mira la pantalla. Confundirlos le dice "escucha algo" a alguien que lleva media
 * hora escuchando, y encima le deja sin botón de reintentar cuando lo único que pasaba era que
 * no había internet.
 *
 * Esta función se llama SOLO cuando había semillas, así que aquí "vacío" ya no puede significar
 * "primer día".
 *
 * Función aparte para poder comprobarla con un test, sin red ni yt-dlp.
 */
internal fun outcomeOf(results: List<Result<Recommendation?>>): Result<List<Recommendation>> {
    val blocks = results.mapNotNull { it.getOrNull() }.filter { it.videos.isNotEmpty() }
    if (blocks.isNotEmpty()) return Result.success(blocks)

    val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
    // Sin ningún fallo pero tampoco resultados: las semillas no daban para recomendar (por
    // ejemplo, una grabación propia sin artista). No es un error que explicar.
    return if (failure != null) Result.failure(failure) else Result.success(emptyList())
}

/** Traduce el fallo de yt-dlp a algo que la pantalla sepa explicar ("sin conexión", etc.). */
private fun <T> Result<T>.mapError(): Result<T> =
    fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(YtDlpErrorMapper.map(it)) })
