/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.hexora.app.di.AppContainer
import dev.hexora.core.archive.ArchiveEntryInfo
import dev.hexora.core.archive.ArchiveProgress
import dev.hexora.core.file.LocalFileAccessProvider
import dev.hexora.core.model.FileEntry
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ArchiveUiState(
    val file: FileEntry,
    val entries: List<ArchiveEntryInfo> = emptyList(),
    val loading: Boolean = true,
    val extracting: ArchiveProgress? = null,
    val message: String? = null,
)

class ArchiveViewModel(
    file: FileEntry,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(ArchiveUiState(file))
    val state: StateFlow<ArchiveUiState> = _state.asStateFlow()

    init { load() }

    fun extractAll() {
        if (_state.value.extracting != null) return
        viewModelScope.launch {
            val path = localPath() ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    val parent = path.parent ?: error("Archive has no parent directory")
                    val safeStem = path.fileName.toString().substringBeforeLast('.').ifBlank { "archive" }
                    var destination = parent.resolve("${safeStem}_extracted")
                    var suffix = 1
                    while (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        destination = parent.resolve("${safeStem}_extracted_$suffix")
                        suffix += 1
                    }
                    Files.createDirectory(destination)
                    try {
                        container.zipArchives.extract(path, destination) { progress ->
                            _state.update { it.copy(extracting = progress) }
                        }
                    } catch (failure: Throwable) {
                        if (Files.list(destination).use { !it.findAny().isPresent }) Files.deleteIfExists(destination)
                        throw failure
                    }
                    destination.fileName.toString()
                }
            }.onSuccess { directory ->
                _state.update { it.copy(extracting = null, message = "Extraído com segurança para $directory") }
            }.onFailure(::showFailure)
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun load() {
        viewModelScope.launch {
            val path = localPath() ?: return@launch
            runCatching { withContext(Dispatchers.IO) { container.zipArchives.list(path) } }
                .onSuccess { entries -> _state.update { it.copy(entries = entries, loading = false) } }
                .onFailure(::showFailure)
        }
    }

    private fun localPath() = runCatching {
        val provider = container.providerRegistry.require(_state.value.file.ref) as? LocalFileAccessProvider
            ?: error("Arquivos SAF podem ser analisados no visualizador hexadecimal nesta fase")
        provider.validatedPath(_state.value.file.ref)
    }.getOrElse { failure ->
        showFailure(failure)
        null
    }

    private fun showFailure(failure: Throwable) {
        _state.update {
            it.copy(
                loading = false,
                extracting = null,
                message = if (failure is SecurityException) "Arquivo bloqueado pela política de segurança"
                else failure.message?.take(180) ?: "Falha ao processar o arquivo compactado",
            )
        }
    }

    class Factory(
        private val file: FileEntry,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ArchiveViewModel::class.java))
            return ArchiveViewModel(file, container) as T
        }
    }
}
