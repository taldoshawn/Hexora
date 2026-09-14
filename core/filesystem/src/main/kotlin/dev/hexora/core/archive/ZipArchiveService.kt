/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.archive

import dev.hexora.core.file.AtomicPathWriter
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ZipSafetyLimits(
    val maxEntries: Int = 20_000,
    val maxTotalUncompressedBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxEntryUncompressedBytes: Long = 512L * 1024 * 1024,
    val maxCompressionRatio: Double = 150.0,
    val bufferBytes: Int = 256 * 1024,
)

data class ArchiveEntryInfo(
    val path: String,
    val isDirectory: Boolean,
    val compressedBytes: Long,
    val uncompressedBytes: Long,
    val crc32: Long,
    val method: Int,
)

data class ArchiveProgress(
    val entry: String,
    val entriesProcessed: Int,
    val totalEntries: Int,
    val bytesWritten: Long,
)

class UnsafeArchiveException(message: String) : SecurityException(message)

/** ZIP/APK/JAR reader with traversal and decompression-bomb defenses. */
class ZipArchiveService(private val limits: ZipSafetyLimits = ZipSafetyLimits()) {
    fun list(archive: Path): List<ArchiveEntryInfo> = ZipFile(archive.toFile()).use { zip ->
        val entries = zip.entries().asSequence().toList()
        validateMetadata(entries)
        entries.map { entry ->
            ArchiveEntryInfo(
                path = sanitizeEntryName(entry.name),
                isDirectory = entry.isDirectory,
                compressedBytes = entry.compressedSize,
                uncompressedBytes = entry.size,
                crc32 = entry.crc,
                method = entry.method,
            )
        }
    }

    suspend fun extract(
        archive: Path,
        destination: Path,
        selectedPaths: Set<String>? = null,
        onProgress: (ArchiveProgress) -> Unit = {},
    ) {
        val root = destination.toAbsolutePath().normalize()
        Files.createDirectories(root)
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) { "Destination is not a directory" }
        require(!Files.isSymbolicLink(root)) { "Symlink destination is blocked" }

        ZipFile(archive.toFile()).use { zip ->
            val extractionContext = currentCoroutineContext()
            val allEntries = zip.entries().asSequence().toList()
            validateMetadata(allEntries)
            val entries = allEntries.filter { entry ->
                selectedPaths == null || selectedPaths.any { selected ->
                    val clean = sanitizeEntryName(selected).trimEnd('/')
                    val entryName = sanitizeEntryName(entry.name)
                    entryName == clean || entryName.startsWith("$clean/")
                }
            }
            var totalWritten = 0L
            entries.forEachIndexed { index, entry ->
                currentCoroutineContext().ensureActive()
                val cleanName = sanitizeEntryName(entry.name)
                val outputPath = root.resolve(cleanName).normalize()
                if (!outputPath.startsWith(root)) throw UnsafeArchiveException("Archive entry escapes destination")
                if (entry.isDirectory) {
                    createSafeDirectories(root, outputPath)
                } else {
                    createSafeDirectories(root, outputPath.parent)
                    if (Files.exists(outputPath, LinkOption.NOFOLLOW_LINKS)) {
                        throw UnsafeArchiveException("Archive would overwrite an existing file")
                    }
                    var entryWritten = 0L
                    AtomicPathWriter.createNew(outputPath) { output ->
                        zip.getInputStream(entry).use { input ->
                            val buffer = ByteArray(limits.bufferBytes)
                            while (true) {
                                extractionContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                entryWritten += read
                                totalWritten += read
                                if (entryWritten > limits.maxEntryUncompressedBytes) {
                                    throw UnsafeArchiveException("Archive entry exceeds extraction limit")
                                }
                                if (totalWritten > limits.maxTotalUncompressedBytes) {
                                    throw UnsafeArchiveException("Archive exceeds total extraction limit")
                                }
                                output.write(buffer, 0, read)
                                onProgress(
                                    ArchiveProgress(cleanName, index, entries.size, totalWritten),
                                )
                            }
                        }
                    }
                }
                onProgress(ArchiveProgress(cleanName, index + 1, entries.size, totalWritten))
            }
        }
    }

    private fun validateMetadata(entries: List<ZipEntry>) {
        if (entries.size > limits.maxEntries) throw UnsafeArchiveException("Archive contains too many entries")
        var declaredTotal = 0L
        entries.forEach { entry ->
            sanitizeEntryName(entry.name)
            if (entry.size > limits.maxEntryUncompressedBytes) {
                throw UnsafeArchiveException("Archive entry is too large")
            }
            if (entry.size >= 0) {
                declaredTotal = safeAdd(declaredTotal, entry.size)
                if (declaredTotal > limits.maxTotalUncompressedBytes) {
                    throw UnsafeArchiveException("Archive expands beyond the configured limit")
                }
            }
            if (entry.size > 0 && entry.compressedSize >= 0) {
                val ratio = entry.size.toDouble() / entry.compressedSize.coerceAtLeast(1).toDouble()
                if (ratio > limits.maxCompressionRatio) {
                    throw UnsafeArchiveException("Suspicious archive compression ratio")
                }
            }
        }
    }

    private fun sanitizeEntryName(rawName: String): String {
        if (rawName.isBlank() || rawName.indexOf('\u0000') >= 0) {
            throw UnsafeArchiveException("Archive contains an invalid entry name")
        }
        val normalized = rawName.replace('\\', '/').trimEnd('/')
        if (normalized.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(normalized)) {
            throw UnsafeArchiveException("Absolute archive paths are blocked")
        }
        val segments = normalized.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            throw UnsafeArchiveException("Archive path traversal is blocked")
        }
        if (segments.any { segment -> segment.length > 255 || segment.any { it.code < 0x20 } }) {
            throw UnsafeArchiveException("Archive entry name is invalid")
        }
        return normalized
    }

    private fun createSafeDirectories(root: Path, directory: Path) {
        if (!directory.startsWith(root)) throw UnsafeArchiveException("Directory escapes destination")
        var current = root
        root.relativize(directory).forEach { segment ->
            current = current.resolve(segment)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw UnsafeArchiveException("Unsafe extraction path")
                }
            } else {
                Files.createDirectory(current)
            }
        }
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (right > Long.MAX_VALUE - left) throw UnsafeArchiveException("Archive size overflow")
        return left + right
    }
}
