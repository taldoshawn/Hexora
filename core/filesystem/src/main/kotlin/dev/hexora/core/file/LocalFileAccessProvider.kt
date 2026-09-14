/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.file

import dev.hexora.core.model.AccessMode
import dev.hexora.core.model.FileCapability
import dev.hexora.core.model.FileEntry
import dev.hexora.core.model.FileRef
import dev.hexora.core.security.SafePathPolicy
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import kotlin.io.path.name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFileAccessProvider(
    override val id: String,
    allowedRoots: Collection<Path>,
    override val accessMode: AccessMode = AccessMode.NORMAL,
) : FileAccessProvider {
    private val roots = allowedRoots.map { it.toAbsolutePath().normalize() }.distinct()
    private val policy = SafePathPolicy(roots)

    override val capabilities: Set<FileCapability> = setOf(
        FileCapability.READ,
        FileCapability.WRITE,
        FileCapability.CREATE,
        FileCapability.RENAME,
        FileCapability.DELETE,
        FileCapability.NATIVE_MOVE,
        FileCapability.RANDOM_ACCESS,
    )

    override suspend fun roots(): List<FileEntry> = withContext(Dispatchers.IO) {
        roots.filter { Files.exists(it, LinkOption.NOFOLLOW_LINKS) }.map(::toEntry)
    }

    override suspend fun stat(ref: FileRef): FileEntry = withContext(Dispatchers.IO) {
        toEntry(resolve(ref))
    }

    override suspend fun parent(ref: FileRef): FileEntry? = withContext(Dispatchers.IO) {
        val path = resolve(ref)
        val parent = path.parent ?: return@withContext null
        if (!policy.isAllowed(parent) || roots.contains(path)) return@withContext null
        toEntry(policy.validateExisting(parent))
    }

    override suspend fun list(directory: FileRef): List<FileEntry> = withContext(Dispatchers.IO) {
        val path = resolve(directory)
        require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { "Not a directory" }
        Files.newDirectoryStream(path).use { stream ->
            stream.map(::toEntry).sortedWith(
                compareByDescending<FileEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
        }
    }

    override suspend fun findChild(parent: FileRef, displayName: String): FileEntry? =
        withContext(Dispatchers.IO) {
            val candidate = policy.validateDestination(resolve(parent), displayName)
            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) toEntry(candidate) else null
        }

    override suspend fun openInput(ref: FileRef): InputStream = withContext(Dispatchers.IO) {
        val path = resolve(ref)
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Not a regular file" }
        Files.newInputStream(path)
    }

    override suspend fun openOutput(ref: FileRef, truncate: Boolean): OutputStream = withContext(Dispatchers.IO) {
        val path = resolve(ref)
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Not a regular file" }
        FileOutputStream(path.toFile(), !truncate)
    }

    override suspend fun createFile(parent: FileRef, displayName: String, mimeType: String?): FileEntry =
        withContext(Dispatchers.IO) {
            val target = policy.validateDestination(resolve(parent), displayName)
            toEntry(Files.createFile(target))
        }

    override suspend fun createDirectory(parent: FileRef, displayName: String): FileEntry =
        withContext(Dispatchers.IO) {
            val target = policy.validateDestination(resolve(parent), displayName)
            toEntry(Files.createDirectory(target))
        }

    override suspend fun rename(ref: FileRef, displayName: String): FileEntry = withContext(Dispatchers.IO) {
        val source = resolve(ref)
        val parent = source.parent ?: error("Root cannot be renamed")
        val target = policy.validateDestination(parent, displayName)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw FileConflictException(displayName)
        toEntry(moveWithoutReplace(source, target))
    }

    override suspend fun delete(ref: FileRef, recursive: Boolean) = withContext(Dispatchers.IO) {
        val path = resolve(ref)
        require(path !in roots) { "Configured roots cannot be deleted" }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && recursive) {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            Files.delete(path)
        }
    }

    override suspend fun moveNative(
        source: FileRef,
        targetParent: FileRef,
        displayName: String,
    ): FileEntry = withContext(Dispatchers.IO) {
        val sourcePath = resolve(source)
        require(sourcePath !in roots) { "Configured roots cannot be moved" }
        val target = policy.validateDestination(resolve(targetParent), displayName)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw FileConflictException(displayName)
        toEntry(moveWithoutReplace(sourcePath, target))
    }

    fun validatedPath(ref: FileRef): Path = resolve(ref)

    private fun resolve(ref: FileRef): Path {
        require(ref.providerId == id) { "Reference belongs to another provider" }
        return policy.validateExisting(Path.of(ref.opaqueId))
    }

    private fun moveWithoutReplace(source: Path, target: Path): Path = try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target)
    }

    private fun toEntry(path: Path): FileEntry {
        val safePath = policy.validateExisting(path)
        val attributes = Files.readAttributes(
            safePath,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        return FileEntry(
            ref = FileRef(id, safePath.toString()),
            name = safePath.fileName?.name ?: safePath.toString(),
            isDirectory = attributes.isDirectory,
            size = if (attributes.isRegularFile) attributes.size() else null,
            modifiedAt = Instant.ofEpochMilli(attributes.lastModifiedTime().toMillis()),
            mimeType = if (attributes.isRegularFile) runCatching { Files.probeContentType(safePath) }.getOrNull() else null,
            isSymlink = attributes.isSymbolicLink,
            isReadable = Files.isReadable(safePath),
            isWritable = Files.isWritable(safePath),
        )
    }
}
