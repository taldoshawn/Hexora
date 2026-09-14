/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.file

import dev.hexora.core.model.AccessMode
import dev.hexora.core.model.FileCapability
import dev.hexora.core.model.FileEntry
import dev.hexora.core.model.FileRef
import java.io.InputStream
import java.io.OutputStream

/**
 * The only boundary through which Hexora file operations may access storage.
 * Implementations must revalidate every reference; UI state is never an authorization source.
 */
interface FileAccessProvider {
    val id: String
    val accessMode: AccessMode
    val capabilities: Set<FileCapability>

    suspend fun roots(): List<FileEntry>
    suspend fun stat(ref: FileRef): FileEntry
    suspend fun parent(ref: FileRef): FileEntry?
    suspend fun list(directory: FileRef): List<FileEntry>
    suspend fun findChild(parent: FileRef, displayName: String): FileEntry?
    suspend fun openInput(ref: FileRef): InputStream
    suspend fun openOutput(ref: FileRef, truncate: Boolean = true): OutputStream
    suspend fun createFile(parent: FileRef, displayName: String, mimeType: String?): FileEntry
    suspend fun createDirectory(parent: FileRef, displayName: String): FileEntry
    suspend fun rename(ref: FileRef, displayName: String): FileEntry
    suspend fun delete(ref: FileRef, recursive: Boolean = false)

    /** Must validate both references again. Return null when a native move is unavailable. */
    suspend fun moveNative(source: FileRef, targetParent: FileRef, displayName: String): FileEntry? = null
}

class ProviderNotFoundException(providerId: String) :
    IllegalArgumentException("Unknown file provider: $providerId")

class FileConflictException(displayName: String) :
    IllegalStateException("A file named '$displayName' already exists")

class UnsupportedFileOperationException(message: String) : UnsupportedOperationException(message)
