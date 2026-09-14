/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.core.editor

import dev.hexora.core.file.FileOperationEngine
import dev.hexora.core.file.ProviderRegistry
import dev.hexora.core.model.FileEntry
import dev.hexora.core.model.FileRef
import java.util.TreeMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Streaming hex backend that also works with SAF providers and multi-gigabyte files. */
class ProviderHexDocumentService(
    private val registry: ProviderRegistry,
    private val operations: FileOperationEngine,
    private val pageSize: Int = 4 * 1024,
) {
    init {
        require(pageSize in 256..1024 * 1024)
    }

    suspend fun readPage(ref: FileRef, requestedOffset: Long): HexPage {
        require(requestedOffset >= 0) { "Offset cannot be negative" }
        val provider = registry.require(ref)
        val entry = provider.stat(ref)
        require(!entry.isDirectory) { "Cannot read a directory" }
        val total = entry.size ?: 0L
        val aligned = (requestedOffset / pageSize) * pageSize
        if (aligned >= total) return HexPage(aligned, byteArrayOf(), total, pageSize)
        val amount = (total - aligned).coerceAtMost(pageSize.toLong()).toInt()
        val bytes = provider.openInput(ref).use { input ->
            skipFully(input, aligned)
            input.readNBytes(amount)
        }
        return HexPage(aligned, bytes, total, pageSize)
    }

    suspend fun saveOverwrite(ref: FileRef, edits: Map<Long, Byte>): FileEntry {
        require(edits.size <= 1_000_000) { "Too many pending edits" }
        val provider = registry.require(ref)
        val entry = provider.stat(ref)
        val size = entry.size ?: error("File size is unavailable")
        edits.keys.forEach { require(it in 0 until size) { "Edit offset is outside the file" } }
        val sorted = TreeMap(edits)
        return operations.atomicWrite(ref, entry.mimeType) { output ->
            provider.openInput(ref).use { input ->
                val buffer = ByteArray(256 * 1024)
                var position = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    val end = position + read
                    sorted.subMap(position, true, end, false).forEach { (offset, byte) ->
                        buffer[(offset - position).toInt()] = byte
                    }
                    output.write(buffer, 0, read)
                    position = end
                }
            }
        }
    }

    suspend fun find(ref: FileRef, pattern: ByteArray, startOffset: Long = 0): Long? {
        require(pattern.isNotEmpty()) { "Search pattern cannot be empty" }
        require(pattern.size <= 1024 * 1024) { "Search pattern is too large" }
        require(startOffset >= 0) { "Search offset cannot be negative" }
        val provider = registry.require(ref)
        val prefix = prefixTable(pattern)
        return provider.openInput(ref).use { input ->
            skipFully(input, startOffset)
            val buffer = ByteArray(256 * 1024)
            var absolute = startOffset
            var matched = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) return@use null
                for (index in 0 until read) {
                    while (matched > 0 && buffer[index] != pattern[matched]) matched = prefix[matched - 1]
                    if (buffer[index] == pattern[matched]) matched += 1
                    if (matched == pattern.size) return@use absolute + index - pattern.size + 1
                }
                absolute += read
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }

    private fun prefixTable(pattern: ByteArray): IntArray {
        val table = IntArray(pattern.size)
        var length = 0
        for (index in 1 until pattern.size) {
            while (length > 0 && pattern[index] != pattern[length]) length = table[length - 1]
            if (pattern[index] == pattern[length]) length += 1
            table[index] = length
        }
        return table
    }

    private fun skipFully(input: java.io.InputStream, requested: Long) {
        var remaining = requested
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() >= 0) {
                remaining -= 1
            } else {
                break
            }
        }
    }
}
