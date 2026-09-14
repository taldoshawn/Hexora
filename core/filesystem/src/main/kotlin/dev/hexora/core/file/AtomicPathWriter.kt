/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.file

import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

object AtomicPathWriter {
    fun createNew(target: Path, writer: (OutputStream) -> Unit): Path {
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "Destination already exists" }
        val parent = target.parent ?: error("Destination has no parent")
        require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) { "Destination parent is invalid" }
        val temp = parent.resolve(".hexora-${UUID.randomUUID()}.part")
        Files.createFile(temp)
        try {
            FileOutputStream(temp.toFile()).use { output ->
                writer(output)
                output.flush()
                output.fd.sync()
            }
            moveWithoutReplace(temp, target)
            return target
        } catch (failure: Throwable) {
            Files.deleteIfExists(temp)
            throw failure
        }
    }

    fun replace(target: Path, keepBackup: Boolean = true, writer: (OutputStream) -> Unit): Path {
        require(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) { "Target is not a regular file" }
        require(!Files.isSymbolicLink(target)) { "Symbolic-link writes are blocked" }
        val parent = target.parent ?: error("Target has no parent")
        val token = UUID.randomUUID().toString()
        val temp = parent.resolve(".hexora-$token.part")
        val backup = parent.resolve(".${target.fileName}.hexora-$token.bak")
        Files.createFile(temp)
        try {
            FileOutputStream(temp.toFile()).use { output ->
                writer(output)
                output.flush()
                output.fd.sync()
            }
            moveWithoutReplace(target, backup)
            try {
                moveWithoutReplace(temp, target)
            } catch (failure: Throwable) {
                runCatching { moveWithoutReplace(backup, target) }
                throw failure
            }
            if (!keepBackup) Files.deleteIfExists(backup)
            return backup
        } catch (failure: Throwable) {
            Files.deleteIfExists(temp)
            throw failure
        }
    }

    private fun moveWithoutReplace(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }
}
