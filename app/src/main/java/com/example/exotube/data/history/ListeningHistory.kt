package com.example.exotube.data.history

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Lo que el usuario ha escuchado, guardado **solo en este teléfono**.
 *
 * Esto es lo que alimenta las recomendaciones de "Para ti" sin que haga falta ninguna cuenta.
 * No sale del dispositivo, no se sube a ningún sitio y se borra al desinstalar la app: no hay
 * servidor que lo guarde ni nadie con quien compartirlo.
 *
 * La clave es [mediaKey]: el enlace de YouTube en lo que se escucha en línea, o la Uri del
 * archivo en lo descargado. Así una misma canción suma aunque se escuche muchas veces.
 */
@Entity(tableName = "listening_history")
data class ListeningEntity(
    @PrimaryKey val mediaKey: String,
    val title: String,
    /** Artista de la canción, o canal del video. Es la señal más útil para recomendar. */
    val artist: String?,
    /** El identificador del video en YouTube, si lo que se escuchó estaba en línea. */
    val videoId: String?,
    val playCount: Int,
    val lastPlayedAt: Long,
)

@Dao
interface ListeningDao {

    /**
     * Apunta una escucha: si la canción ya estaba, le suma una y actualiza la fecha.
     *
     * Va en una sola sentencia SQL a propósito. Con un "leer, sumar, escribir" desde Kotlin, dos
     * canciones terminando a la vez podrían pisarse la cuenta.
     */
    @Query(
        """
        INSERT INTO listening_history (mediaKey, title, artist, videoId, playCount, lastPlayedAt)
        VALUES (:mediaKey, :title, :artist, :videoId, 1, :playedAt)
        ON CONFLICT(mediaKey) DO UPDATE SET
            playCount = playCount + 1,
            lastPlayedAt = :playedAt,
            title = :title,
            artist = COALESCE(:artist, artist)
        """,
    )
    suspend fun record(
        mediaKey: String,
        title: String,
        artist: String?,
        videoId: String?,
        playedAt: Long,
    )

    /**
     * Lo más escuchado últimamente, para sacar de ahí las semillas de las recomendaciones.
     *
     * Ordena por número de escuchas y, a igualdad, por lo más reciente: así lo que se escucha
     * mucho manda, pero un descubrimiento de ayer no se queda enterrado para siempre.
     */
    @Query("SELECT * FROM listening_history ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    suspend fun topPlayed(limit: Int): List<ListeningEntity>

    @Query("SELECT COUNT(*) FROM listening_history")
    suspend fun count(): Int

    /** Al borrar una canción del teléfono: que deje de servir para recomendar. */
    @Query("DELETE FROM listening_history WHERE mediaKey = :mediaKey")
    suspend fun forget(mediaKey: String)

    /** Para el día que se quiera ofrecer "borrar mi historial" desde los ajustes. */
    @Query("DELETE FROM listening_history")
    suspend fun clear()
}
