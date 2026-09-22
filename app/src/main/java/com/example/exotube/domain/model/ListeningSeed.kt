package com.example.exotube.domain.model

/**
 * Algo que el usuario escuchó y que sirve de punto de partida para recomendar.
 *
 * De aquí salen las sugerencias de "Para ti": no hay ningún perfil en la nube ni ninguna cuenta,
 * solo esto, guardado en el propio teléfono.
 */
data class ListeningSeed(
    /** El enlace de YouTube, o la Uri del archivo si es algo descargado. */
    val mediaKey: String,
    val title: String,
    val artist: String?,
    /** El identificador del video en YouTube, cuando lo que se escuchó estaba en línea. */
    val videoId: String?,
)

/**
 * Un bloque de recomendaciones, con el motivo por el que aparece.
 *
 * El motivo se enseña siempre ("Porque escuchaste Soda Stereo"). Una lista de sugerencias sin
 * explicar de dónde salen es exactamente lo que hace que la gente desconfíe de que la app la
 * está espiando; aquí la respuesta cabe en una línea.
 */
data class Recommendation(
    /** El artista o el título que lo motivó, para escribir "Porque escuchaste …". */
    val becauseOf: String,
    val videos: List<OnlineVideo>,
)
