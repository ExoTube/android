package com.example.exotube.domain.repository

import com.example.exotube.domain.model.Recommendation

/**
 * Sugerencias basadas en lo que el usuario escucha, sin cuentas ni perfil en la nube.
 *
 * Quien implemente esto decide de dónde salen; la pantalla solo pide "recomiéndame algo".
 */
interface RecommendationRepository {

    /** false mientras el usuario no haya escuchado nada: entonces no hay nada que recomendar. */
    suspend fun hasEnoughHistory(): Boolean

    /**
     * Bloques de recomendaciones, cada uno con el motivo por el que aparece.
     *
     * Lista vacía significa "todavía no hay de dónde sacar nada", que no es un error.
     */
    suspend fun forYou(): Result<List<Recommendation>>
}
