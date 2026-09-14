/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.storage

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dev.hexora.core.file.FileAccessProvider
import dev.hexora.core.file.FileConflictException
import dev.hexora.core.model.AccessMode
import dev.hexora.core.model.FileCapability
import dev.hexora.core.model.FileEntry
import dev.hexora.core.model.FileRef
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SAF provider backed by opaque in-process capabilities. A caller cannot forge a document URI:
 * only roots explicitly granted by the user and children returned by that tree are registered.
 */
class SafFileAccessProvider(private val context: Context) : FileAccessProvider {
    override val id: String = "saf"
    override val accessMode: AccessMode = AccessMode.SAF
    override val capabilities: Set<FileCapability> = setOf(
        FileCapability.READ,
        FileCapability.WRITE,
        FileCapability.CREATE,
        FileCapability.RENAME,
        FileCapability.DELETE,
    )

    private data class GrantedDocument(
        val document: DocumentFile,
        val rootUri: Uri,
        val parentToken: String?,
    )

    private val documents = ConcurrentHashMap<String, GrantedDocument>()
    private val tokenByUri = ConcurrentHashMap<String, String>()
    private val rootTokens = ConcurrentHashMap.newKeySet<String>()

    fun replaceRoots(uriStrings: Set<String>) {
        documents.clear()
        tokenByUri.clear()
        rootTokens.clear()
        uriStrings.forEach { raw ->
            val uri = runCatching { raw.toUri() }.getOrNull() ?: return@forEach
            val document = DocumentFile.fromTreeUri(context, uri) ?: return@forEach
            val token = register(document, uri, parentToken = null)
            rootTokens += token
        }
    }

    override suspend fun roots(): List<FileEntry> = withContext(Dispatchers.IO) {
        rootTokens.mapNotNull { token -> documents[token]?.let { toEntry(token, it.document) } }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun stat(ref: FileRef): FileEntry = withContext(Dispatchers.IO) {
        val granted = requireGranted(ref)
        toEntry(ref.opaqueId, granted.document)
    }

    override suspend fun parent(ref: FileRef): FileEntry? = withContext(Dispatchers.IO) {
        val granted = requireGranted(ref)
        val parentToken = granted.parentToken ?: return@withContext null
        val parent = documents[parentToken] ?: return@withContext null
        toEntry(parentToken, parent.document)
    }

    override suspend fun list(directory: FileRef): List<FileEntry> = withContext(Dispatchers.IO) {
        val granted = requireGranted(directory)
        require(granted.document.isDirectory) { "Not a directory" }
        granted.document.listFiles().map { child ->
            val token = register(child, granted.rootUri, directory.opaqueId)
            toEntry(token, child)
        }.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun findChild(parent: FileRef, displayName: String): FileEntry? =
        withContext(Dispatchers.IO) {
            validateDisplayName(displayName)
            val granted = requireGranted(parent)
            val child = granted.document.findFile(displayName) ?: return@withContext null
            val token = register(child, granted.rootUri, parent.opaqueId)
            toEntry(token, child)
        }

    // Ownership is intentionally transferred to FileAccessProvider callers, which close via use().
    @SuppressLint("Recycle")
    override suspend fun openInput(ref: FileRef): InputStream = withContext(Dispatchers.IO) {
        val document = requireGranted(ref).document
        require(document.isFile && document.canRead()) { "Document is not readable" }
        context.contentResolver.openInputStream(document.uri)
            ?: throw FileNotFoundException("Document provider returned no input stream")
    }

    // Ownership is intentionally transferred to FileAccessProvider callers, which close via use().
    @SuppressLint("Recycle")
    override suspend fun openOutput(ref: FileRef, truncate: Boolean): OutputStream = withContext(Dispatchers.IO) {
        val document = requireGranted(ref).document
        require(document.isFile && document.canWrite()) { "Document is not writable" }
        val mode = if (truncate) "wt" else "wa"
        context.contentResolver.openOutputStream(document.uri, mode)
            ?: throw FileNotFoundException("Document provider returned no output stream")
    }

    override suspend fun createFile(parent: FileRef, displayName: String, mimeType: String?): FileEntry =
        withContext(Dispatchers.IO) {
            validateDisplayName(displayName)
            val granted = requireGranted(parent)
            require(granted.document.isDirectory && granted.document.canWrite()) { "Directory is not writable" }
            if (granted.document.findFile(displayName) != null) throw FileConflictException(displayName)
            val child = granted.document.createFile(mimeType ?: "application/octet-stream", displayName)
                ?: throw IllegalStateException("Document provider could not create the file")
            val token = register(child, granted.rootUri, parent.opaqueId)
            toEntry(token, child)
        }

    override suspend fun createDirectory(parent: FileRef, displayName: String): FileEntry =
        withContext(Dispatchers.IO) {
            validateDisplayName(displayName)
            val granted = requireGranted(parent)
            require(granted.document.isDirectory && granted.document.canWrite()) { "Directory is not writable" }
            if (granted.document.findFile(displayName) != null) throw FileConflictException(displayName)
            val child = granted.document.createDirectory(displayName)
                ?: throw IllegalStateException("Document provider could not create the directory")
            val token = register(child, granted.rootUri, parent.opaqueId)
            toEntry(token, child)
        }

    override suspend fun rename(ref: FileRef, displayName: String): FileEntry = withContext(Dispatchers.IO) {
        validateDisplayName(displayName)
        val granted = requireGranted(ref)
        require(granted.parentToken != null) { "Granted tree roots cannot be renamed" }
        val parent = documents[granted.parentToken]?.document
        if (parent?.findFile(displayName) != null) throw FileConflictException(displayName)
        val oldUri = granted.document.uri.toString()
        require(granted.document.renameTo(displayName)) { "Document provider rejected rename" }
        tokenByUri.remove(oldUri)
        tokenByUri[granted.document.uri.toString()] = ref.opaqueId
        toEntry(ref.opaqueId, granted.document)
    }

    override suspend fun delete(ref: FileRef, recursive: Boolean) = withContext(Dispatchers.IO) {
        val granted = requireGranted(ref)
        require(granted.parentToken != null) { "Granted tree roots cannot be deleted" }
        if (granted.document.isDirectory && !recursive && granted.document.listFiles().isNotEmpty()) {
            throw IllegalStateException("Directory is not empty")
        }
        require(granted.document.delete()) { "Document provider rejected deletion" }
        documents.remove(ref.opaqueId)
        tokenByUri.remove(granted.document.uri.toString())
        Unit
    }

    private fun register(document: DocumentFile, rootUri: Uri, parentToken: String?): String {
        val uriKey = document.uri.toString()
        tokenByUri[uriKey]?.let { existing ->
            documents[existing] = GrantedDocument(document, rootUri, parentToken)
            return existing
        }
        val token = UUID.randomUUID().toString()
        documents[token] = GrantedDocument(document, rootUri, parentToken)
        tokenByUri[uriKey] = token
        return token
    }

    private fun requireGranted(ref: FileRef): GrantedDocument {
        require(ref.providerId == id) { "Reference belongs to another provider" }
        return documents[ref.opaqueId] ?: throw SecurityException("Unknown or expired SAF capability")
    }

    private fun toEntry(token: String, document: DocumentFile): FileEntry = FileEntry(
        ref = FileRef(id, token),
        name = document.name ?: "Documento",
        isDirectory = document.isDirectory,
        size = document.length().takeIf { document.isFile && it >= 0 },
        modifiedAt = document.lastModified().takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        mimeType = document.type,
        isReadable = document.canRead(),
        isWritable = document.canWrite(),
    )

    private fun validateDisplayName(name: String) {
        require(
            name.isNotBlank() && name != "." && name != ".." && name.length <= 255 &&
                name.none { it == '/' || it == '\\' || it == '\u0000' || it.code < 0x20 },
        ) { "Invalid document name" }
    }
}
