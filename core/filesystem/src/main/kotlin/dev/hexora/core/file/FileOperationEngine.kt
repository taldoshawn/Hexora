/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.file

import dev.hexora.core.capability.CapabilityManager
import dev.hexora.core.model.FileCapability
import dev.hexora.core.model.FileEntry
import dev.hexora.core.model.FileOperationKind
import dev.hexora.core.model.FileRef
import dev.hexora.core.model.OperationProgress
import dev.hexora.core.model.OperationRecord
import dev.hexora.core.model.OperationStatus
import java.io.FileOutputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

data class OperationLimits(
    val bufferBytes: Int = 256 * 1024,
    val maxEntries: Int = 100_000,
    val maxDepth: Int = 128,
)

class OperationController {
    private val paused = MutableStateFlow(false)

    fun pause() { paused.value = true }
    fun resume() { paused.value = false }
    val isPaused: Boolean get() = paused.value

    suspend fun checkpoint() {
        currentCoroutineContext().ensureActive()
        paused.first { value -> !value }
        currentCoroutineContext().ensureActive()
    }
}

class FileOperationEngine(
    private val registry: ProviderRegistry,
    private val capabilityManager: CapabilityManager,
    private val journal: OperationJournal = NoOpOperationJournal,
    private val limits: OperationLimits = OperationLimits(),
) {
    suspend fun copy(
        source: FileRef,
        targetParent: FileRef,
        displayName: String? = null,
        controller: OperationController = OperationController(),
        onProgress: (OperationProgress) -> Unit = {},
    ): FileEntry = execute(FileOperationKind.COPY, source, targetParent) {
        capabilityManager.require(source, FileCapability.READ)
        capabilityManager.require(targetParent, FileCapability.CREATE)
        val sourceProvider = registry.require(source)
        val targetProvider = registry.require(targetParent)
        val sourceEntry = sourceProvider.stat(source)
        val targetName = displayName ?: sourceEntry.name
        require(targetProvider.findChild(targetParent, targetName) == null) {
            "Destination already exists"
        }
        val counter = EntryCounter(limits.maxEntries)
        copyEntry(
            sourceProvider = sourceProvider,
            targetProvider = targetProvider,
            source = sourceEntry,
            targetParent = targetParent,
            targetName = targetName,
            depth = 0,
            counter = counter,
            controller = controller,
            onProgress = onProgress,
        )
    }

    suspend fun move(
        source: FileRef,
        targetParent: FileRef,
        displayName: String? = null,
        controller: OperationController = OperationController(),
        onProgress: (OperationProgress) -> Unit = {},
    ): FileEntry = execute(FileOperationKind.MOVE, source, targetParent) {
        capabilityManager.require(source, FileCapability.READ)
        capabilityManager.require(source, FileCapability.DELETE)
        capabilityManager.require(targetParent, FileCapability.CREATE)
        val sourceProvider = registry.require(source)
        val targetProvider = registry.require(targetParent)
        val sourceEntry = sourceProvider.stat(source)
        val targetName = displayName ?: sourceEntry.name
        require(targetProvider.findChild(targetParent, targetName) == null) {
            "Destination already exists"
        }
        if (sourceProvider === targetProvider && FileCapability.NATIVE_MOVE in sourceProvider.capabilities) {
            sourceProvider.moveNative(source, targetParent, targetName)?.let { return@execute it }
        }
        val copied = copyEntry(
            sourceProvider,
            targetProvider,
            sourceEntry,
            targetParent,
            targetName,
            depth = 0,
            counter = EntryCounter(limits.maxEntries),
            controller = controller,
            onProgress = onProgress,
        )
        sourceProvider.delete(source, recursive = sourceEntry.isDirectory)
        copied
    }

    suspend fun rename(ref: FileRef, displayName: String): FileEntry =
        execute(FileOperationKind.RENAME, ref, null) {
            capabilityManager.require(ref, FileCapability.RENAME)
            registry.require(ref).rename(ref, displayName)
        }

    suspend fun createDirectory(parent: FileRef, displayName: String): FileEntry =
        execute(FileOperationKind.CREATE_DIRECTORY, null, parent) {
            capabilityManager.require(parent, FileCapability.CREATE)
            registry.require(parent).createDirectory(parent, displayName)
        }

    suspend fun createFile(parent: FileRef, displayName: String, mimeType: String? = null): FileEntry =
        execute(FileOperationKind.CREATE_FILE, null, parent) {
            capabilityManager.require(parent, FileCapability.CREATE)
            registry.require(parent).createFile(parent, displayName, mimeType)
        }

    suspend fun delete(ref: FileRef, recursive: Boolean = false) =
        execute(FileOperationKind.DELETE, ref, null) {
            capabilityManager.require(ref, FileCapability.DELETE)
            registry.require(ref).delete(ref, recursive)
        }

    /**
     * Writes via a sibling temporary file, syncs it, swaps the original to a backup and rolls back
     * if the final rename fails. The returned backup is intentionally retained for recovery.
     */
    suspend fun atomicWrite(
        target: FileRef,
        mimeType: String? = null,
        writer: suspend (java.io.OutputStream) -> Unit,
    ): FileEntry = execute(FileOperationKind.ATOMIC_WRITE, target, null) {
        capabilityManager.require(target, FileCapability.WRITE)
        val provider = registry.require(target)
        val original = provider.stat(target)
        require(!original.isDirectory) { "Cannot write a directory" }
        val parent = provider.parent(target) ?: error("Cannot replace a provider root")
        val token = UUID.randomUUID().toString()
        val temp = provider.createFile(parent.ref, ".hexora-$token.part", mimeType ?: original.mimeType)
        var backup: FileEntry? = null
        try {
            provider.openOutput(temp.ref).use { output ->
                writer(output)
                output.flush()
                (output as? FileOutputStream)?.fd?.sync()
            }
            backup = provider.rename(original.ref, ".${original.name}.hexora-$token.bak")
            try {
                provider.rename(temp.ref, original.name)
            } catch (failure: Throwable) {
                val recovery = requireNotNull(backup)
                runCatching { provider.rename(recovery.ref, original.name) }
                throw failure
            }
        } catch (failure: Throwable) {
            runCatching { provider.delete(temp.ref) }
            throw failure
        }
    }

    private suspend fun copyEntry(
        sourceProvider: FileAccessProvider,
        targetProvider: FileAccessProvider,
        source: FileEntry,
        targetParent: FileRef,
        targetName: String,
        depth: Int,
        counter: EntryCounter,
        controller: OperationController,
        onProgress: (OperationProgress) -> Unit,
    ): FileEntry {
        require(depth <= limits.maxDepth) { "Directory nesting limit exceeded" }
        counter.increment()
        controller.checkpoint()
        if (source.isDirectory) {
            val created = targetProvider.createDirectory(targetParent, targetName)
            try {
                sourceProvider.list(source.ref).forEach { child ->
                    copyEntry(
                        sourceProvider,
                        targetProvider,
                        child,
                        created.ref,
                        child.name,
                        depth + 1,
                        counter,
                        controller,
                        onProgress,
                    )
                }
            } catch (failure: Throwable) {
                runCatching { targetProvider.delete(created.ref, recursive = true) }
                throw failure
            }
            return created
        }

        val tempName = ".hexora-${UUID.randomUUID()}.part"
        val temp = targetProvider.createFile(targetParent, tempName, source.mimeType)
        val started = System.nanoTime()
        var processed = 0L
        try {
            sourceProvider.openInput(source.ref).use { input ->
                targetProvider.openOutput(temp.ref).use { output ->
                    val buffer = ByteArray(limits.bufferBytes)
                    while (true) {
                        controller.checkpoint()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        processed += read
                        val seconds = ((System.nanoTime() - started) / 1_000_000_000.0).coerceAtLeast(0.001)
                        val speed = (processed / seconds).toLong()
                        val remaining = source.size?.minus(processed)?.coerceAtLeast(0)
                        onProgress(
                            OperationProgress(
                                kind = FileOperationKind.COPY,
                                currentItem = source.name,
                                processedBytes = processed,
                                totalBytes = source.size,
                                bytesPerSecond = speed,
                                etaMillis = remaining?.let { if (speed > 0) it * 1_000 / speed else null },
                            ),
                        )
                    }
                    output.flush()
                    (output as? FileOutputStream)?.fd?.sync()
                }
            }
            return targetProvider.rename(temp.ref, targetName)
        } catch (failure: Throwable) {
            runCatching { targetProvider.delete(temp.ref) }
            throw failure
        }
    }

    private suspend fun <T> execute(
        kind: FileOperationKind,
        source: FileRef?,
        destination: FileRef?,
        block: suspend () -> T,
    ): T {
        val id = UUID.randomUUID().toString()
        val started = Instant.now()
        val initial = OperationRecord(id, kind, source, destination, started, status = OperationStatus.RUNNING)
        journal.started(initial)
        return try {
            block().also {
                journal.updated(
                    initial.copy(completedAt = Instant.now(), status = OperationStatus.SUCCEEDED),
                )
            }
        } catch (failure: Throwable) {
            val status = if (failure is kotlinx.coroutines.CancellationException) {
                OperationStatus.CANCELLED
            } else {
                OperationStatus.FAILED
            }
            journal.updated(
                initial.copy(
                    completedAt = Instant.now(),
                    status = status,
                    error = failure.message?.take(512),
                ),
            )
            throw failure
        }
    }

    private class EntryCounter(private val maximum: Int) {
        private var value = 0
        fun increment() {
            value += 1
            require(value <= maximum) { "Operation entry limit exceeded" }
        }
    }
}
