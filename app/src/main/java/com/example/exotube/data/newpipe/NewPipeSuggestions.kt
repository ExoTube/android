package com.example.exotube.data.newpipe

import com.example.exotube.domain.repository.SearchSuggestionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList

/**
 * Las predicciones de YouTube mientras se escribe ("soda ste" → "soda stereo", "soda stereo
 * zoom"…), las mismas que ofrece su propia app.
 *
 * Es una consulta muy ligera (unas décimas de segundo), pero se hace a cada letra: por eso se
 * recuerdan las últimas respuestas. Al borrar una letra, o al volver a escribir lo mismo, la
 * lista sale al instante sin preguntar otra vez.
 */
class NewPipeSuggestions(private val client: OkHttpClient) : SearchSuggestionRepository {

    private val cache = object : LinkedHashMap<String, List<String>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>) = size > CACHE_SIZE
    }

    override suspend fun suggest(query: String): List<String> {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return emptyList()
        synchronized(cache) { cache[key] }?.let { return it }
        // runInterruptible: al escribir otra letra se cancela la consulta anterior en el acto.
        val found = runInterruptible(Dispatchers.IO) {
            NewPipeSetup.ensure(client)
            ServiceList.YouTube.suggestionExtractor.suggestionList(key)
        }
        synchronized(cache) { cache[key] = found }
        return found
    }

    private companion object {
        const val CACHE_SIZE = 64
    }
}
