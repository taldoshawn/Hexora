/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.editor

import dev.hexora.core.file.FileOperationEngine
import dev.hexora.core.file.ProviderRegistry
import dev.hexora.core.model.FileRef
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

enum class TextEncoding(val charset: Charset) {
    UTF8(StandardCharsets.UTF_8),
    UTF16_LE(StandardCharsets.UTF_16LE),
    UTF16_BE(StandardCharsets.UTF_16BE),
    ISO_8859_1(StandardCharsets.ISO_8859_1),
}

enum class LineEnding(val sequence: String) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r"),
}

data class TextDocument(
    val ref: FileRef,
    val text: String,
    val encoding: TextEncoding,
    val lineEnding: LineEnding,
    val editable: Boolean,
    val totalBytes: Long,
    val truncated: Boolean,
)

class TextDocumentService(
    private val registry: ProviderRegistry,
    private val operations: FileOperationEngine,
    private val maxEditableBytes: Long = 2L * 1024 * 1024,
    private val previewBytes: Int = 256 * 1024,
) {
    suspend fun load(ref: FileRef): TextDocument {
        val provider = registry.require(ref)
        val entry = provider.stat(ref)
        require(!entry.isDirectory) { "Cannot edit a directory" }
        val size = entry.size ?: 0L
        val editable = size <= maxEditableBytes
        val readLimit = if (editable) {
            (size + 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            previewBytes
        }
        val raw = provider.openInput(ref).use { it.readNBytes(readLimit) }
        val (encoding, bomBytes) = detectEncoding(raw)
        val content = raw.copyOfRange(bomBytes, raw.size)
        val decoded = decode(content, encoding)
        return TextDocument(
            ref = ref,
            text = decoded,
            encoding = encoding,
            lineEnding = detectLineEnding(decoded),
            editable = editable,
            totalBytes = size,
            truncated = !editable,
        )
    }

    suspend fun save(document: TextDocument, newText: String, keepBom: Boolean = false) =
        operations.atomicWrite(document.ref, mimeType = "text/plain") { output ->
            if (keepBom) output.write(bomFor(document.encoding))
            output.write(newText.toByteArray(document.encoding.charset))
        }

    private fun detectEncoding(bytes: ByteArray): Pair<TextEncoding, Int> = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            TextEncoding.UTF8 to 3
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            TextEncoding.UTF16_LE to 2
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            TextEncoding.UTF16_BE to 2
        else -> TextEncoding.UTF8 to 0
    }

    private fun decode(bytes: ByteArray, preferred: TextEncoding): String = try {
        preferred.charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        String(bytes, StandardCharsets.ISO_8859_1)
    }

    private fun detectLineEnding(text: String): LineEnding = when {
        "\r\n" in text -> LineEnding.CRLF
        '\r' in text -> LineEnding.CR
        else -> LineEnding.LF
    }

    private fun bomFor(encoding: TextEncoding): ByteArray = when (encoding) {
        TextEncoding.UTF8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        TextEncoding.UTF16_LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        TextEncoding.UTF16_BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        TextEncoding.ISO_8859_1 -> byteArrayOf()
    }
}
