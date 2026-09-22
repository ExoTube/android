package com.example.exotube.domain.repository

import com.example.exotube.domain.model.OnlineVideo
import com.example.exotube.domain.model.VideoComment

/**
 * Los comentarios de un video, solo para leer.
 *
 * ExoTube no publica comentarios y no puede: escribir uno exige iniciar sesión con una cuenta de
 * Google, que es justo lo que esta app no pide a nadie.
 */
interface CommentsRepository {

    /**
     * @return los comentarios principales del video, los más votados primero. Lista vacía cuando
     *   el video los tiene desactivados, que no es un error.
     */
    suspend fun comments(video: OnlineVideo): Result<List<VideoComment>>
}
