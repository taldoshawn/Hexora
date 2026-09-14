/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.data.local

import dev.hexora.core.file.OperationJournal
import dev.hexora.core.model.OperationRecord

class RoomOperationJournal(private val dao: HexoraDao) : OperationJournal {
    override suspend fun started(record: OperationRecord) = dao.putOperation(record.toEntity())

    override suspend fun updated(record: OperationRecord) = dao.putOperation(record.toEntity())

    private fun OperationRecord.toEntity() = OperationEntity(
        id = id,
        kind = kind.name,
        sourceProviderId = source?.providerId,
        sourceOpaqueId = source?.opaqueId,
        destinationProviderId = destination?.providerId,
        destinationOpaqueId = destination?.opaqueId,
        startedAtEpochMillis = startedAt.toEpochMilli(),
        completedAtEpochMillis = completedAt?.toEpochMilli(),
        status = status.name,
        error = error,
    )
}
