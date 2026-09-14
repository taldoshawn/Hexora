/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.hexora.app.di.AppContainer
import dev.hexora.core.editor.HexPage
import dev.hexora.core.editor.ProviderHexDocumentService
import dev.hexora.core.model.FileEntry
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HexChange(val offset: Long, val before: Byte, val after: Byte)

data class HexEditorUiState(
    val entry: FileEntry,
    val page: HexPage? = null,
    val edits: Map<Long, Byte> = emptyMap(),
    val undo: List<HexChange> = emptyList(),
    val redo: List<HexChange> = emptyList(),
    val bookmarks: Set<Long> = emptySet(),
    val selectedOffset: Long? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val searching: Boolean = false,
    val message: String? = null,
)

class HexEditorViewModel(
    entry: FileEntry,
    private val service: ProviderHexDocumentService,
) : ViewModel() {
    private val _state = MutableStateFlow(HexEditorUiState(entry))
    val state: StateFlow<HexEditorUiState> = _state.asStateFlow()

    init { loadPage(0) }

    fun previousPage() {
        val page = _state.value.page ?: return
        loadPage((page.offset - page.pageSize).coerceAtLeast(0))
    }

    fun nextPage() {
        val page = _state.value.page ?: return
        if (page.offset + page.bytes.size < page.totalBytes) loadPage(page.offset + page.pageSize)
    }

    fun goTo(offset: Long) {
        val total = _state.value.page?.totalBytes ?: return
        if (offset !in 0 until total) {
            showMessage("Offset fora do arquivo")
        } else {
            loadPage(offset)
            _state.update { it.copy(selectedOffset = offset) }
        }
    }

    fun select(offset: Long) {
        _state.update { it.copy(selectedOffset = offset) }
    }

    fun edit(offset: Long, hex: String) {
        val parsed = hex.trim().removePrefix("0x").toIntOrNull(16)
        if (parsed == null || parsed !in 0..255) {
            showMessage("Use exatamente um byte hexadecimal, por exemplo AF")
            return
        }
        val before = byteAt(offset) ?: return
        val after = parsed.toByte()
        if (before == after) return
        _state.update { current ->
            current.copy(
                edits = current.edits + (offset to after),
                undo = (current.undo + HexChange(offset, before, after)).takeLast(10_000),
                redo = emptyList(),
                selectedOffset = offset,
            )
        }
    }

    fun undo() {
        val change = _state.value.undo.lastOrNull() ?: return
        _state.update { current ->
            current.copy(
                edits = current.edits + (change.offset to change.before),
                undo = current.undo.dropLast(1),
                redo = current.redo + change,
            )
        }
    }

    fun redo() {
        val change = _state.value.redo.lastOrNull() ?: return
        _state.update { current ->
            current.copy(
                edits = current.edits + (change.offset to change.after),
                undo = current.undo + change,
                redo = current.redo.dropLast(1),
            )
        }
    }

    fun toggleBookmark() {
        val offset = _state.value.selectedOffset ?: return
        _state.update { current ->
            val bookmarks = current.bookmarks.toMutableSet()
            if (!bookmarks.add(offset)) bookmarks.remove(offset)
            current.copy(bookmarks = bookmarks)
        }
    }

    fun search(value: String, asHex: Boolean) {
        val pattern = if (asHex) parseHexPattern(value) else value.toByteArray(StandardCharsets.UTF_8)
        if (pattern == null || pattern.isEmpty()) {
            showMessage("Padrão de busca inválido")
            return
        }
        val current = _state.value
        val start = (current.selectedOffset ?: current.page?.offset ?: 0) + 1
        viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            runCatching { service.find(current.entry.ref, pattern, start) }
                .onSuccess { found ->
                    if (found == null) showMessage("Padrão não encontrado") else goTo(found)
                    _state.update { it.copy(searching = false) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(searching = false) }
                    showFailure(failure)
                }
        }
    }

    fun save() {
        val current = _state.value
        if (current.edits.isEmpty() || current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching { service.saveOverwrite(current.entry.ref, current.edits) }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            entry = saved,
                            edits = emptyMap(),
                            undo = emptyList(),
                            redo = emptyList(),
                            saving = false,
                            message = "Bytes salvos; backup de recuperação mantido",
                        )
                    }
                    loadPage(current.page?.offset ?: 0)
                }
                .onFailure { failure ->
                    _state.update { it.copy(saving = false) }
                    showFailure(failure)
                }
        }
    }

    fun byteAt(offset: Long): Byte? {
        _state.value.edits[offset]?.let { return it }
        val page = _state.value.page ?: return null
        val index = (offset - page.offset).toInt()
        return page.bytes.getOrNull(index)
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun loadPage(offset: Long) {
        val ref = _state.value.entry.ref
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { service.readPage(ref, offset) }
                .onSuccess { page -> _state.update { it.copy(page = page, loading = false) } }
                .onFailure(::showFailure)
        }
    }

    private fun parseHexPattern(value: String): ByteArray? {
        val normalized = value.replace(" ", "").replace("0x", "", ignoreCase = true)
        if (normalized.isEmpty() || normalized.length % 2 != 0 || normalized.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun showFailure(failure: Throwable) {
        _state.update {
            it.copy(
                loading = false,
                saving = false,
                searching = false,
                message = if (failure is SecurityException) "Acesso negado pela política de segurança"
                else failure.message?.take(160) ?: "Falha no editor hexadecimal",
            )
        }
    }

    private fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    class Factory(entry: FileEntry, container: AppContainer) : ViewModelProvider.Factory {
        private val entry = entry
        private val service = ProviderHexDocumentService(container.providerRegistry, container.operations)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HexEditorViewModel::class.java))
            return HexEditorViewModel(entry, service) as T
        }
    }
}
