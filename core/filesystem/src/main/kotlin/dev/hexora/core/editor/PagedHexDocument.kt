/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.editor

import dev.hexora.core.file.AtomicPathWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.TreeMap

data class HexPage(
    val offset: Long,
    val bytes: ByteArray,
    val totalBytes: Long,
    val pageSize: Int,
)

data class ByteInspection(
    val offset: Long,
    val littleEndian: Map<String, String>,
    val bigEndian: Map<String, String>,
)

/** Random-access page reader. It never loads the whole file and supports files larger than 2 GiB. */
class PagedHexDocument(
    private val path: Path,
    val pageSize: Int = 4 * 1024,
) {
    init {
        require(pageSize in 256..1024 * 1024) { "Page size must be between 256 B and 1 MiB" }
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Hex target is not a regular file" }
        require(!Files.isSymbolicLink(path)) { "Symbolic-link editing is blocked" }
    }

    val size: Long get() = Files.size(path)

    fun readPage(requestedOffset: Long): HexPage {
        val fileSize = size
        require(requestedOffset >= 0) { "Offset cannot be negative" }
        val aligned = (requestedOffset / pageSize) * pageSize
        if (fileSize == 0L || aligned >= fileSize) return HexPage(aligned, byteArrayOf(), fileSize, pageSize)
        val amount = (fileSize - aligned).coerceAtMost(pageSize.toLong()).toInt()
        val buffer = ByteBuffer.allocate(amount)
        FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            channel.position(aligned)
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) Unit
        }
        return HexPage(aligned, buffer.array().copyOf(buffer.position()), fileSize, pageSize)
    }

    /** Applies overwrite edits transactionally and retains a hidden backup beside the original. */
    fun saveOverwrite(edits: Map<Long, Byte>): Path {
        require(edits.size <= 1_000_000) { "Too many pending edits" }
        val fileSize = size
        edits.keys.forEach { offset -> require(offset in 0 until fileSize) { "Edit offset is outside the file" } }
        val sorted = TreeMap(edits)
        return AtomicPathWriter.replace(path, keepBackup = true) { output ->
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(256 * 1024)
                var absoluteOffset = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    val endExclusive = absoluteOffset + read
                    sorted.subMap(absoluteOffset, true, endExclusive, false).forEach { (offset, value) ->
                        buffer[(offset - absoluteOffset).toInt()] = value
                    }
                    output.write(buffer, 0, read)
                    absoluteOffset = endExclusive
                }
            }
        }
    }

    fun inspect(offset: Long): ByteInspection {
        require(offset in 0 until size) { "Offset is outside the file" }
        val bytes = ByteArray(8)
        FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            channel.position(offset)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining() && channel.read(buffer) > 0) Unit
        }
        return ByteInspection(
            offset = offset,
            littleEndian = inspect(bytes, ByteOrder.LITTLE_ENDIAN),
            bigEndian = inspect(bytes, ByteOrder.BIG_ENDIAN),
        )
    }

    private fun inspect(bytes: ByteArray, order: ByteOrder): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val first = bytes[0]
        result["int8"] = first.toString()
        result["uint8"] = (first.toInt() and 0xFF).toString()
        if (bytes.size >= 2) {
            val short = ByteBuffer.wrap(bytes).order(order).short
            result["int16"] = short.toString()
            result["uint16"] = (short.toInt() and 0xFFFF).toString()
        }
        if (bytes.size >= 4) {
            val int = ByteBuffer.wrap(bytes).order(order).int
            result["int32"] = int.toString()
            result["uint32"] = (int.toLong() and 0xFFFF_FFFFL).toString()
            result["float"] = Float.fromBits(int).toString()
        }
        if (bytes.size >= 8) {
            val long = ByteBuffer.wrap(bytes).order(order).long
            result["int64"] = long.toString()
            result["uint64"] = java.lang.Long.toUnsignedString(long)
            result["double"] = Double.fromBits(long).toString()
        }
        return result
    }
}
