/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.core.file

import dev.hexora.core.capability.CapabilityManager
import dev.hexora.core.model.AccessMode
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationEngineTest {
    @Test
    fun `copies a file between isolated providers`() = runBlocking {
        val sourceRoot = Files.createTempDirectory("hexora-copy-source")
        val targetRoot = Files.createTempDirectory("hexora-copy-target")
        Files.writeString(sourceRoot.resolve("readme.txt"), "streamed")
        val sourceProvider = LocalFileAccessProvider("source", listOf(sourceRoot), AccessMode.NORMAL)
        val targetProvider = LocalFileAccessProvider("target", listOf(targetRoot), AccessMode.NORMAL)
        val registry = ProviderRegistry(listOf(sourceProvider, targetProvider))
        val engine = FileOperationEngine(registry, CapabilityManager(registry))
        val source = sourceProvider.list(sourceProvider.roots().single().ref).single()

        val copied = engine.copy(source.ref, targetProvider.roots().single().ref)

        assertEquals("streamed", Files.readString(targetProvider.validatedPath(copied.ref)))
        assertTrue(Files.exists(sourceProvider.validatedPath(source.ref)))
    }
}
