/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.core.file

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicPathWriterTest {
    @Test
    fun `writer failure leaves original untouched`() {
        val target = Files.createTempFile("hexora-atomic", ".txt")
        Files.writeString(target, "original")

        val failure = runCatching {
            AtomicPathWriter.replace(target) { output ->
                output.write("partial".toByteArray())
                error("simulated failure")
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("original", Files.readString(target))
    }
}
