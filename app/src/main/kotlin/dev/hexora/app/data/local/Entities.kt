/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val opaqueId: String,
    val displayName: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val opaqueId: String,
    val displayName: String,
    val openedAtEpochMillis: Long,
)

@Entity(tableName = "operation_journal")
data class OperationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val sourceProviderId: String?,
    val sourceOpaqueId: String?,
    val destinationProviderId: String?,
    val destinationOpaqueId: String?,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val status: String,
    val error: String?,
)
