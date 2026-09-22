package com.example.exotube.domain.model

/**
 * Un comentario de un video de YouTube.
 *
 * Solo se leen: ExoTube no publica comentarios ni puede hacerlo, porque para escribir haría falta
 * iniciar sesión con una cuenta de Google, que es justo lo que esta app no pide.
 */
data class VideoComment(
    val id: String,
    val author: String,
    val authorAvatarUrl: String?,
    val text: String,
    val likeCount: Long?,
    /** Tal como lo escribe YouTube ("hace 2 años"): no llega una fecha, llega ese texto. */
    val publishedText: String?,
    /** true si lo escribió el dueño del canal: en YouTube se destaca, y aquí también. */
    val isFromCreator: Boolean,
    /** Cuántas respuestas cuelgan de este comentario. */
    val replyCount: Int,
)
