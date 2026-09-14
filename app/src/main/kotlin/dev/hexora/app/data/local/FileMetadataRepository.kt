/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.data.local

import dev.hexora.core.model.FileEntry
import dev.hexora.core.model.FileRef
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow

class FileMetadataRepository(private val dao: HexoraDao) {
    val favorites: Flow<List<FavoriteEntity>> = dao.observeFavorites()
    val recent: Flow<List<RecentFileEntity>> = dao.observeRecent()

    suspend fun toggleFavorite(entry: FileEntry, currentlyFavorite: Boolean) {
        val entity = FavoriteEntity(
            id = entry.ref.stableId(),
            providerId = entry.ref.providerId,
            opaqueId = entry.ref.opaqueId,
            displayName = entry.name,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        if (currentlyFavorite) dao.deleteFavorite(entity) else dao.upsertFavorite(entity)
    }

    suspend fun recordOpened(entry: FileEntry) {
        dao.upsertRecent(
            RecentFileEntity(
                id = entry.ref.stableId(),
                providerId = entry.ref.providerId,
                opaqueId = entry.ref.opaqueId,
                displayName = entry.name,
                openedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        dao.trimRecent()
    }

    private fun FileRef.stableId(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$providerId\u0000$opaqueId".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
