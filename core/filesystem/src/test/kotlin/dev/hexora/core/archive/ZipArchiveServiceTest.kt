/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.core.archive

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipArchiveServiceTest {
    @Test
    fun `lists and extracts a safe archive`() = runBlocking {
        val root = Files.createTempDirectory("hexora-zip-safe")
        val archive = root.resolve("safe.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("folder/readme.txt"))
            zip.write("Hexora".toByteArray())
            zip.closeEntry()
        }
        val destination = Files.createDirectory(root.resolve("out"))
        val service = ZipArchiveService()

        assertEquals("folder/readme.txt", service.list(archive).single().path)
        service.extract(archive, destination)
        assertEquals("Hexora", Files.readString(destination.resolve("folder/readme.txt")))
    }

    @Test
    fun `blocks zip slip before writing outside destination`() = runBlocking {
        val root = Files.createTempDirectory("hexora-zip-slip")
        val archive = root.resolve("attack.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("../escaped.txt"))
            zip.write("attack".toByteArray())
            zip.closeEntry()
        }
        val destination = Files.createDirectory(root.resolve("out"))

        assertTrue(runCatching { ZipArchiveService().extract(archive, destination) }.exceptionOrNull() is UnsafeArchiveException)
        assertFalse(Files.exists(root.resolve("escaped.txt")))
    }

    @Test
    fun `blocks suspicious compression ratio`() {
        val root = Files.createTempDirectory("hexora-zip-bomb")
        val archive = root.resolve("bomb.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("zeros.bin"))
            zip.write(ByteArray(1024 * 1024))
            zip.closeEntry()
        }
        val strict = ZipArchiveService(ZipSafetyLimits(maxCompressionRatio = 2.0))

        assertTrue(runCatching { strict.list(archive) }.exceptionOrNull() is UnsafeArchiveException)
    }
}
