package com.example.exotube.domain.repository

import com.example.exotube.domain.model.AppUpdate

/** Averigua si hay una versión de ExoTube más nueva que la instalada. */
interface UpdateRepository {

    /**
     * @return la versión nueva, o `null` si ya estamos en la última. Falla solo cuando no se pudo
     *   preguntar (sin internet, servidor caído): eso no es lo mismo que "no hay novedad", y
     *   quien llama decide si merece la pena decir algo.
     */
    suspend fun findUpdate(): Result<AppUpdate?>
}
