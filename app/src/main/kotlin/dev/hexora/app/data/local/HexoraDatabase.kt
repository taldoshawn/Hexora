/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FavoriteEntity::class, RecentFileEntity::class, OperationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HexoraDatabase : RoomDatabase() {
    abstract fun dao(): HexoraDao
}
