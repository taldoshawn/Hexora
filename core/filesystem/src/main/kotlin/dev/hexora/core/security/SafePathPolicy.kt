/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.security

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

class UnsafePathException(message: String) : SecurityException(message)

/**
 * Central validation for local paths. It intentionally rejects symlink traversal: privileged
 * providers must never silently widen access through a link controlled by an untrusted archive.
 */
class SafePathPolicy(allowedRoots: Collection<Path>) {
    private val roots: List<Path> = allowedRoots.map { root ->
        val normalized = root.toAbsolutePath().normalize()
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            normalized.toRealPath(LinkOption.NOFOLLOW_LINKS)
        } else {
            normalized
        }
    }.distinct()

    init {
        require(roots.isNotEmpty()) { "At least one allowed root is required" }
    }

    fun validateExisting(path: Path): Path {
        val normalized = normalizeInsideRoot(path)
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw UnsafePathException("Path does not exist")
        }
        rejectSymlinkSegments(normalized)
        return normalized
    }

    fun validateDestination(parent: Path, displayName: String): Path {
        validateDisplayName(displayName)
        val safeParent = validateExisting(parent)
        if (!Files.isDirectory(safeParent, LinkOption.NOFOLLOW_LINKS)) {
            throw UnsafePathException("Destination parent is not a directory")
        }
        return normalizeInsideRoot(safeParent.resolve(displayName))
    }

    fun validateDisplayName(displayName: String) {
        if (
            displayName.isBlank() ||
            displayName == "." ||
            displayName == ".." ||
            displayName.length > 255 ||
            displayName.any { it == '/' || it == '\\' || it == '\u0000' || it.code < 0x20 }
        ) {
            throw UnsafePathException("Invalid file name")
        }
    }

    fun isAllowed(path: Path): Boolean = runCatching { normalizeInsideRoot(path) }.isSuccess

    private fun normalizeInsideRoot(path: Path): Path {
        val normalized = path.toAbsolutePath().normalize()
        if (roots.none(normalized::startsWith)) {
            throw UnsafePathException("Path is outside the configured roots")
        }
        return normalized
    }

    private fun rejectSymlinkSegments(path: Path) {
        val root = roots.filter(path::startsWith).maxByOrNull { it.nameCount }
            ?: throw UnsafePathException("Path is outside the configured roots")
        var current = root
        if (Files.isSymbolicLink(current)) throw UnsafePathException("Root cannot be a symlink")
        root.relativize(path).forEach { segment ->
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) {
                throw UnsafePathException("Symbolic-link traversal is blocked")
            }
        }
    }
}
