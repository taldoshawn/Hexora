/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.file

import dev.hexora.core.model.OperationRecord

interface OperationJournal {
    suspend fun started(record: OperationRecord)
    suspend fun updated(record: OperationRecord)
}

object NoOpOperationJournal : OperationJournal {
    override suspend fun started(record: OperationRecord) = Unit
    override suspend fun updated(record: OperationRecord) = Unit
}
