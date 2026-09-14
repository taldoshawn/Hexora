/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HexoraDao {
    @Query("SELECT * FROM favorites ORDER BY displayName COLLATE NOCASE")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Upsert
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("SELECT * FROM recent_files ORDER BY openedAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<RecentFileEntity>>

    @Upsert
    suspend fun upsertRecent(recent: RecentFileEntity)

    @Query(
        "DELETE FROM recent_files WHERE id NOT IN " +
            "(SELECT id FROM recent_files ORDER BY openedAtEpochMillis DESC LIMIT :keep)",
    )
    suspend fun trimRecent(keep: Int = 100)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOperation(operation: OperationEntity)

    @Query("SELECT * FROM operation_journal ORDER BY startedAtEpochMillis DESC LIMIT :limit")
    fun observeOperations(limit: Int = 200): Flow<List<OperationEntity>>

    @Query("DELETE FROM operation_journal WHERE startedAtEpochMillis < :beforeEpochMillis")
    suspend fun deleteOldOperations(beforeEpochMillis: Long)
}
