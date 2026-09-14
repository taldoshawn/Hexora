/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.core.hash

import dev.hexora.core.file.LocalFileAccessProvider
import dev.hexora.core.file.ProviderRegistry
import dev.hexora.core.model.AccessMode
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HashServiceTest {
    @Test
    fun `calculates SHA-256 with streaming provider IO`() = runBlocking {
        val root = Files.createTempDirectory("hexora-hash")
        Files.writeString(root.resolve("value.txt"), "abc")
        val provider = LocalFileAccessProvider("test", listOf(root), AccessMode.NORMAL)
        val entry = provider.list(provider.roots().single().ref).single()

        val digest = HashService(ProviderRegistry(listOf(provider))).digest(entry.ref)

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            digest[HashAlgorithm.SHA256],
        )
    }
}
