/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.core.security

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafePathPolicyTest {
    @Test
    fun `allows files under configured root and blocks traversal`() {
        val root = Files.createTempDirectory("hexora-safe-root")
        val file = Files.writeString(root.resolve("safe.txt"), "ok")
        val policy = SafePathPolicy(listOf(root))

        assertTrue(policy.isAllowed(file))
        assertFalse(policy.isAllowed(root.resolve("..").resolve("escape.txt")))
    }

    @Test(expected = UnsafePathException::class)
    fun `rejects separator injection in display names`() {
        val root = Files.createTempDirectory("hexora-name-root")
        SafePathPolicy(listOf(root)).validateDestination(root, "../escape.txt")
    }

    @Test
    fun `rejects symbolic link traversal when supported`() {
        val root = Files.createTempDirectory("hexora-link-root")
        val outside = Files.createTempDirectory("hexora-link-outside")
        val link = root.resolve("link")
        val created = runCatching { Files.createSymbolicLink(link, outside) }.isSuccess
        if (created) {
            val policy = SafePathPolicy(listOf(root))
            assertFalse(runCatching { policy.validateExisting(link) }.isSuccess)
        }
    }
}
