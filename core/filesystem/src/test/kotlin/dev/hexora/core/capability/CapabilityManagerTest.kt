/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.core.capability

import dev.hexora.core.file.LocalFileAccessProvider
import dev.hexora.core.file.ProviderRegistry
import dev.hexora.core.model.AccessMode
import dev.hexora.core.model.FileCapability
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityManagerTest {
    @Test
    fun `execution authorization uses provider capability not UI claims`() = runBlocking {
        val root = Files.createTempDirectory("hexora-capability")
        val provider = LocalFileAccessProvider("normal", listOf(root), AccessMode.NORMAL)
        val ref = provider.roots().single().ref
        val manager = CapabilityManager(ProviderRegistry(listOf(provider)))

        assertTrue(manager.authorize(ref, FileCapability.READ).allowed)
        assertFalse(manager.authorize(ref, FileCapability.POSIX_ATTRIBUTES).allowed)
    }
}
