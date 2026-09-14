/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.hash

import dev.hexora.core.file.ProviderRegistry
import dev.hexora.core.model.FileCapability
import dev.hexora.core.model.FileRef
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class HashAlgorithm(val jcaName: String) {
    SHA256("SHA-256"),
    SHA512("SHA-512"),
    SHA1("SHA-1"),
    MD5("MD5"),
}

class HashService(
    private val registry: ProviderRegistry,
    private val bufferSize: Int = 256 * 1024,
) {
    suspend fun digest(
        ref: FileRef,
        algorithms: Set<HashAlgorithm> = setOf(HashAlgorithm.SHA256),
        onBytesRead: (Long) -> Unit = {},
    ): Map<HashAlgorithm, String> {
        require(algorithms.isNotEmpty()) { "At least one algorithm is required" }
        val provider = registry.require(ref)
        require(FileCapability.READ in provider.capabilities) { "Provider is not readable" }
        require(!provider.stat(ref).isDirectory) { "Cannot hash a directory" }
        val digests = algorithms.associateWith { MessageDigest.getInstance(it.jcaName) }
        var total = 0L
        provider.openInput(ref).use { input ->
            val buffer = ByteArray(bufferSize)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digests.values.forEach { it.update(buffer, 0, read) }
                total += read
                onBytesRead(total)
            }
        }
        return digests.mapValues { (_, digest) -> digest.digest().toHex() }
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
