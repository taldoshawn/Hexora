/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.file

import dev.hexora.core.model.FileEntry
import java.io.BufferedInputStream

enum class FileHandlerKind {
    DIRECTORY,
    TEXT_EDITOR,
    HEX_EDITOR,
    ZIP_ARCHIVE,
    APK_INSPECTOR,
    IMAGE_VIEWER,
    AUDIO_PLAYER,
    VIDEO_PLAYER,
    SQLITE_VIEWER,
    ELF_INSPECTOR,
}

/** Magic bytes take precedence over extension so renamed/untrusted files are not blindly parsed. */
class FileHandlerRegistry(private val registry: ProviderRegistry) {
    private val textExtensions = setOf(
        "kt", "kts", "java", "smali", "xml", "json", "yaml", "yml", "toml", "html",
        "htm", "css", "js", "jsx", "ts", "tsx", "py", "sh", "c", "h", "cpp", "hpp",
        "rs", "go", "sql", "md", "txt", "properties", "gradle", "gitignore", "csv", "log",
    )
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "ico", "avif", "heic", "heif")
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")
    private val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v")

    suspend fun resolve(entry: FileEntry): FileHandlerKind {
        if (entry.isDirectory) return FileHandlerKind.DIRECTORY
        val provider = registry.require(entry.ref)
        val header = provider.openInput(entry.ref).use { raw ->
            BufferedInputStream(raw).use { input ->
                val bytes = ByteArray(16)
                var offset = 0
                while (offset < bytes.size) {
                    val read = input.read(bytes, offset, bytes.size - offset)
                    if (read < 0) break
                    offset += read
                }
                bytes.copyOf(offset)
            }
        }
        val extension = entry.name.substringAfterLast('.', "").lowercase()
        return when {
            header.startsWith(0x7F, 'E'.code, 'L'.code, 'F'.code) -> FileHandlerKind.ELF_INSPECTOR
            header.startsWith('S'.code, 'Q'.code, 'L'.code, 'i'.code, 't'.code, 'e'.code) -> FileHandlerKind.SQLITE_VIEWER
            header.startsWith('P'.code, 'K'.code, 0x03, 0x04) && extension == "apk" -> FileHandlerKind.APK_INSPECTOR
            header.startsWith('P'.code, 'K'.code, 0x03, 0x04) -> FileHandlerKind.ZIP_ARCHIVE
            extension in textExtensions || isProbablyText(header) -> FileHandlerKind.TEXT_EDITOR
            extension in imageExtensions -> FileHandlerKind.IMAGE_VIEWER
            extension in audioExtensions -> FileHandlerKind.AUDIO_PLAYER
            extension in videoExtensions -> FileHandlerKind.VIDEO_PLAYER
            else -> FileHandlerKind.HEX_EDITOR
        }
    }

    private fun isProbablyText(header: ByteArray): Boolean {
        if (header.isEmpty()) return true
        if (header.any { it == 0.toByte() }) return false
        val controls = header.count { byte ->
            val value = byte.toInt() and 0xFF
            value < 0x20 && value !in setOf(0x09, 0x0A, 0x0D)
        }
        return controls <= 1
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { index -> (this[index].toInt() and 0xFF) == expected[index] }
}
