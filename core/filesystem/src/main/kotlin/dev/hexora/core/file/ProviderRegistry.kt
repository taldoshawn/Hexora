/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.file

import dev.hexora.core.model.FileRef
import java.util.concurrent.ConcurrentHashMap

class ProviderRegistry(initialProviders: Collection<FileAccessProvider> = emptyList()) {
    private val providers = ConcurrentHashMap<String, FileAccessProvider>()

    init {
        initialProviders.forEach(::register)
    }

    fun register(provider: FileAccessProvider) {
        require(provider.id.isNotBlank()) { "Provider id cannot be blank" }
        providers[provider.id] = provider
    }

    fun unregister(providerId: String) {
        providers.remove(providerId)
    }

    fun require(providerId: String): FileAccessProvider =
        providers[providerId] ?: throw ProviderNotFoundException(providerId)

    fun require(ref: FileRef): FileAccessProvider = require(ref.providerId)

    fun all(): List<FileAccessProvider> = providers.values.sortedBy(FileAccessProvider::id)
}
