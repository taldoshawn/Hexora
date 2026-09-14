/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.model

import java.time.Instant

/** Opaque reference owned and validated by a [dev.hexora.core.file.FileAccessProvider]. */
data class FileRef(
    val providerId: String,
    val opaqueId: String,
)

data class FileEntry(
    val ref: FileRef,
    val name: String,
    val isDirectory: Boolean,
    val size: Long?,
    val modifiedAt: Instant?,
    val mimeType: String? = null,
    val isSymlink: Boolean = false,
    val isReadable: Boolean = true,
    val isWritable: Boolean = false,
)

enum class AccessMode {
    NORMAL,
    SAF,
    FULL_STORAGE,
    SHIZUKU,
    ADB_SHELL,
    ROOT,
}

enum class FileCapability {
    READ,
    WRITE,
    CREATE,
    RENAME,
    DELETE,
    NATIVE_MOVE,
    RANDOM_ACCESS,
    POSIX_ATTRIBUTES,
}

enum class FileOperationKind {
    COPY,
    MOVE,
    DELETE,
    RENAME,
    CREATE_FILE,
    CREATE_DIRECTORY,
    ATOMIC_WRITE,
    EXTRACT_ARCHIVE,
}

data class OperationProgress(
    val kind: FileOperationKind,
    val currentItem: String,
    val processedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long,
    val etaMillis: Long?,
)

data class OperationRecord(
    val id: String,
    val kind: FileOperationKind,
    val source: FileRef?,
    val destination: FileRef?,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val status: OperationStatus,
    val error: String? = null,
)

enum class OperationStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}
