/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.core.editor

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedHexDocumentTest {
    @Test
    fun `reads aligned pages without loading whole file`() {
        val path = Files.createTempFile("hexora-hex-page", ".bin")
        Files.write(path, ByteArray(2048) { (it and 0xFF).toByte() })
        val document = PagedHexDocument(path, pageSize = 256)

        val page = document.readPage(300)

        assertEquals(256, page.offset)
        assertEquals(256, page.bytes.size)
        assertEquals(0, page.bytes.first().toInt())
    }

    @Test
    fun `overwrite save is transactional and retains backup`() {
        val path = Files.createTempFile("hexora-hex-write", ".bin")
        val original = byteArrayOf(1, 2, 3, 4)
        Files.write(path, original)
        val document = PagedHexDocument(path, pageSize = 256)

        val backup = document.saveOverwrite(mapOf(1L to 0x7F.toByte()))

        assertArrayEquals(byteArrayOf(1, 0x7F, 3, 4), Files.readAllBytes(path))
        assertArrayEquals(original, Files.readAllBytes(backup))
        assertTrue(Files.exists(backup))
    }
}
