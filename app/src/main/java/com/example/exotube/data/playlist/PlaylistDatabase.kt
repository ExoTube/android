package com.example.exotube.data.playlist

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.exotube.data.history.ListeningDao
import com.example.exotube.data.history.ListeningEntity
import kotlinx.coroutines.flow.Flow

// Room: escribes clases y consultas SQL con anotaciones, y KSP genera el código de SQLite.
// Las consultas se comprueban AL COMPILAR: un error de SQL no llega nunca a la app instalada.

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** Ruta de la foto elegida por el usuario; null = usar la carátula de una canción. (Versión 2) */
    val coverPath: String? = null,
)

/**
 * Una canción dentro de una playlist. La clave es (playlist, canción): la misma canción no puede
 * estar dos veces en la misma lista. Al borrar una playlist, CASCADE borra sus elementos.
 */
@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "mediaUri"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaUri")],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val mediaUri: String,
    val position: Int,
    val addedAt: Long,
)

@Dao
interface PlaylistDao {

    // Devolver Flow hace que Room vuelva a emitir cada vez que cambian estas tablas.
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_items ORDER BY playlistId, position")
    fun observeAllItems(): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT playlistId FROM playlist_items WHERE mediaUri = :mediaUri")
    fun observePlaylistIdsContaining(mediaUri: String): Flow<List<Long>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT coverPath FROM playlists WHERE id = :id")
    suspend fun coverPath(id: Long): String?

    @Query("UPDATE playlists SET coverPath = :path WHERE id = :id")
    suspend fun setCoverPath(id: Long, path: String?)

    /** Una sola sentencia calcula la última posición e inserta: no hay carreras entre dos toques rápidos. */
    @Query(
        """
        INSERT OR IGNORE INTO playlist_items (playlistId, mediaUri, position, addedAt)
        SELECT :playlistId, :mediaUri, COALESCE(MAX(position) + 1, 0), :addedAt
        FROM playlist_items WHERE playlistId = :playlistId
        """,
    )
    suspend fun addItem(playlistId: Long, mediaUri: String, addedAt: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaUri = :mediaUri")
    suspend fun removeItem(playlistId: Long, mediaUri: String)
}

/**
 * Versión 2 añade `playlists.coverPath` y la 3, la tabla del historial de escucha.
 *
 * Quien ya tenía la app NO pierde sus playlists: AutoMigration compara los esquemas guardados en
 * app/schemas/ con el nuevo y genera el SQL necesario. (Sin migración, Room se negaría a abrir la
 * base de datos y la app no arrancaría.)
 */
@Database(
    entities = [PlaylistEntity::class, PlaylistItemEntity::class, ListeningEntity::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class ExoTubeDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    abstract fun listeningDao(): ListeningDao

    companion object {
        fun create(context: Context): ExoTubeDatabase =
            Room.databaseBuilder(context.applicationContext, ExoTubeDatabase::class.java, "exotube.db").build()
    }
}
