/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.hexora.app.di.AppContainer
import dev.hexora.core.editor.TextDocument
import dev.hexora.core.model.FileEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TextEditorUiState(
    val entry: FileEntry,
    val document: TextDocument? = null,
    val value: TextFieldValue = TextFieldValue(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val dirty: Boolean = false,
    val searchVisible: Boolean = false,
    val search: String = "",
    val replace: String = "",
    val regex: Boolean = false,
    val message: String? = null,
)

class TextEditorViewModel(
    entry: FileEntry,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(TextEditorUiState(entry))
    val state: StateFlow<TextEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { container.textDocuments.load(entry.ref) }
                .onSuccess { document ->
                    _state.update {
                        it.copy(
                            document = document,
                            value = TextFieldValue(document.text),
                            loading = false,
                        )
                    }
                }
                .onFailure { showFailure(it) }
        }
    }

    fun updateValue(value: TextFieldValue) {
        val document = _state.value.document ?: return
        if (!document.editable) return
        _state.update { it.copy(value = value, dirty = value.text != document.text) }
    }

    fun toggleSearch() {
        _state.update { it.copy(searchVisible = !it.searchVisible) }
    }

    fun updateSearch(value: String) {
        _state.update { it.copy(search = value.take(4_096)) }
    }

    fun updateReplace(value: String) {
        _state.update { it.copy(replace = value.take(4_096)) }
    }

    fun toggleRegex() {
        _state.update { it.copy(regex = !it.regex) }
    }

    fun findNext() {
        val current = _state.value
        if (current.search.isEmpty()) return
        val start = current.value.selection.end.coerceAtMost(current.value.text.length)
        val match = runCatching {
            if (current.regex) {
                Regex(current.search).find(current.value.text, start)
                    ?: Regex(current.search).find(current.value.text, 0)
            } else {
                val direct = current.value.text.indexOf(current.search, start, ignoreCase = true)
                val index = if (direct >= 0) direct else current.value.text.indexOf(current.search, 0, ignoreCase = true)
                index.takeIf { it >= 0 }?.let { LiteralMatch(it, it + current.search.length) }
            }
        }.getOrElse {
            showMessage("Expressão regular inválida")
            return
        }
        val range = when (match) {
            is MatchResult -> match.range.first to match.range.last + 1
            is LiteralMatch -> match.start to match.end
            else -> null
        }
        if (range == null) {
            showMessage("Nenhuma ocorrência encontrada")
        } else {
            _state.update { it.copy(value = it.value.copy(selection = TextRange(range.first, range.second))) }
        }
    }

    fun replaceSelection() {
        val current = _state.value
        val selection = current.value.selection
        if (selection.collapsed) return
        val newText = current.value.text.replaceRange(selection.min, selection.max, current.replace)
        val cursor = selection.min + current.replace.length
        updateValue(TextFieldValue(newText, TextRange(cursor)))
        findNext()
    }

    fun save(onSaved: (FileEntry) -> Unit = {}) {
        val current = _state.value
        val document = current.document ?: return
        if (!document.editable || current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching { container.textDocuments.save(document, current.value.text) }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            entry = saved,
                            document = document.copy(ref = saved.ref, text = current.value.text),
                            dirty = false,
                            saving = false,
                            message = "Arquivo salvo com backup de recuperação",
                        )
                    }
                    onSaved(saved)
                }
                .onFailure(::showFailure)
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun showFailure(failure: Throwable) {
        _state.update {
            it.copy(
                loading = false,
                saving = false,
                message = if (failure is SecurityException) "Acesso negado pela política de segurança"
                else failure.message?.take(160) ?: "Não foi possível abrir o arquivo",
            )
        }
    }

    private fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    private data class LiteralMatch(val start: Int, val end: Int)

    class Factory(
        private val entry: FileEntry,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TextEditorViewModel::class.java))
            return TextEditorViewModel(entry, container) as T
        }
    }
}
