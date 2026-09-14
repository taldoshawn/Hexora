/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.capability

import dev.hexora.core.file.ProviderRegistry
import dev.hexora.core.model.AccessMode
import dev.hexora.core.model.FileCapability
import dev.hexora.core.model.FileRef

data class CapabilityDecision(
    val allowed: Boolean,
    val accessMode: AccessMode?,
    val reason: String,
)

/** Rechecks provider capabilities at execution time, independent of what the UI displayed. */
class CapabilityManager(private val registry: ProviderRegistry) {
    fun authorize(ref: FileRef, capability: FileCapability): CapabilityDecision {
        val provider = runCatching { registry.require(ref) }.getOrNull()
            ?: return CapabilityDecision(false, null, "Storage provider is unavailable")
        return if (capability in provider.capabilities) {
            CapabilityDecision(true, provider.accessMode, "Capability granted by ${provider.id}")
        } else {
            CapabilityDecision(false, provider.accessMode, "${provider.accessMode} cannot perform $capability")
        }
    }

    fun require(ref: FileRef, capability: FileCapability) {
        val decision = authorize(ref, capability)
        if (!decision.allowed) throw SecurityException(decision.reason)
    }
}
