/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.hexora.core.file

import dev.hexora.core.model.FileEntry
import dev.hexora.core.model.FileRef
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.ArrayDeque
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class FileSearchQuery(
    val name: String = "",
    val extension: String? = null,
    val regex: Boolean = false,
    val minBytes: Long? = null,
    val maxBytes: Long? = null,
    val modifiedAfter: Instant? = null,
    val textContains: String? = null,
    val caseSensitive: Boolean = false,
    val maxResults: Int = 500,
    val maxDepth: Int = 64,
    val maxContentBytesPerFile: Long = 2L * 1024 * 1024,
)

class FileSearchEngine(private val registry: ProviderRegistry) {
    suspend fun search(root: FileRef, query: FileSearchQuery): List<FileEntry> {
        require(query.name.length <= 512) { "Search term is too long" }
        require((query.textContains?.length ?: 0) <= 4_096) { "Content search term is too long" }
        require(query.maxResults in 1..10_000) { "Invalid result limit" }
        require(query.maxDepth in 0..256) { "Invalid depth limit" }
        val expression = if (query.regex && query.name.isNotEmpty()) {
            Regex(query.name, if (query.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
        } else {
            null
        }
        val provider = registry.require(root)
        val result = mutableListOf<FileEntry>()
        val pending = ArrayDeque<Pair<FileRef, Int>>()
        pending.add(root to 0)
        while (pending.isNotEmpty() && result.size < query.maxResults) {
            currentCoroutineContext().ensureActive()
            val (directory, depth) = pending.removeFirst()
            provider.list(directory).forEach { entry ->
                if (entry.isDirectory && depth < query.maxDepth && !entry.isSymlink) {
                    pending.addLast(entry.ref to depth + 1)
                }
                if (matches(provider, entry, query, expression)) result += entry
                if (result.size >= query.maxResults) return@forEach
            }
        }
        return result
    }

    private suspend fun matches(
        provider: FileAccessProvider,
        entry: FileEntry,
        query: FileSearchQuery,
        expression: Regex?,
    ): Boolean {
        val nameMatches = when {
            query.name.isEmpty() -> true
            expression != null -> expression.containsMatchIn(entry.name)
            query.caseSensitive -> entry.name.contains(query.name)
            else -> entry.name.contains(query.name, ignoreCase = true)
        }
        if (!nameMatches) return false
        if (query.extension != null && !entry.name.endsWith(".${query.extension.trimStart('.')}", ignoreCase = true)) return false
        if (query.minBytes != null && (entry.size ?: 0) < query.minBytes) return false
        if (query.maxBytes != null && (entry.size ?: Long.MAX_VALUE) > query.maxBytes) return false
        if (query.modifiedAfter != null && (entry.modifiedAt == null || entry.modifiedAt < query.modifiedAfter)) return false
        val needle = query.textContains ?: return true
        if (entry.isDirectory || entry.size == null || entry.size > query.maxContentBytesPerFile) return false
        val content = provider.openInput(entry.ref).use { input ->
            val maximum = query.maxContentBytesPerFile.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            input.readNBytes(maximum).toString(StandardCharsets.UTF_8)
        }
        return content.contains(needle, ignoreCase = !query.caseSensitive)
    }
}
