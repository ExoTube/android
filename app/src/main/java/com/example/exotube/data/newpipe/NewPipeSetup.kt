package com.example.exotube.data.newpipe

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

/**
 * Prepara NewPipeExtractor una sola vez para toda la app.
 *
 * La librería guarda su configuración de forma global (idioma, país y conexión), así que da igual
 * quién la use primero —el buscador o quien pide los enlaces—: el primero la prepara y el resto
 * la encuentra lista.
 */
internal object NewPipeSetup {

    private const val DEFAULT_COUNTRY = "US"

    @Volatile
    private var isReady = false

    fun ensure(client: OkHttpClient) {
        if (isReady) return
        synchronized(this) {
            if (isReady) return
            // Los resultados y los títulos, en el idioma y el país del teléfono.
            val locale = Locale.getDefault()
            NewPipe.init(
                NewPipeDownloader(client),
                Localization.fromLocale(locale),
                ContentCountry(locale.country.ifBlank { DEFAULT_COUNTRY }),
            )
            isReady = true
        }
    }
}
