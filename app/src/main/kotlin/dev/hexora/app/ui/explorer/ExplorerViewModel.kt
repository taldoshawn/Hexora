/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.ui.explorer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.hexora.app.di.AppContainer
import dev.hexora.app.storage.AccessStatus
import dev.hexora.core.file.FileHandlerKind
import dev.hexora.core.file.OperationController
import dev.hexora.core.model.FileEntry
import dev.hexora.core.model.FileRef
import dev.hexora.core.model.OperationProgress
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PanelId { LEFT, RIGHT }

data class FilePanelState(
    val directory: FileEntry? = null,
    val entries: List<FileEntry> = emptyList(),
    val backStack: List<FileRef> = emptyList(),
    val forwardStack: List<FileRef> = emptyList(),
    val selected: Set<FileRef> = emptySet(),
    val loading: Boolean = false,
)

data class TransferUiState(
    val progress: OperationProgress,
    val paused: Boolean,
)

data class ExplorerUiState(
    val left: FilePanelState = FilePanelState(),
    val right: FilePanelState = FilePanelState(),
    val activePanel: PanelId = PanelId.LEFT,
    val roots: List<FileEntry> = emptyList(),
    val searchQuery: String = "",
    val showHidden: Boolean = false,
    val access: List<AccessStatus> = emptyList(),
    val transfer: TransferUiState? = null,
    val message: String? = null,
)

sealed interface ExplorerEvent {
    data class OpenFile(val entry: FileEntry, val handler: FileHandlerKind) : ExplorerEvent
}

class ExplorerViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    private val eventChannel = Channel<ExplorerEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var safRoots: Set<String> = emptySet()
    private var transferController: OperationController? = null
    private var transferJob: Job? = null

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                val rootsChanged = settings.safTreeUris != safRoots
                safRoots = settings.safTreeUris
                if (rootsChanged) container.safProvider.replaceRoots(safRoots)
                _state.update {
                    it.copy(
                        showHidden = settings.showHiddenFiles,
                        access = container.accessStatus.current(safRoots.size),
                    )
                }
                refreshRoots(resetMissingPanels = rootsChanged)
            }
        }
    }

    fun onResume() {
        container.refreshFullStorageProvider()
        viewModelScope.launch {
            _state.update { it.copy(access = container.accessStatus.current(safRoots.size)) }
            refreshRoots(resetMissingPanels = false)
        }
    }

    fun setActivePanel(panelId: PanelId) {
        _state.update { it.copy(activePanel = panelId) }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query.take(256)) }
    }

    fun toggleShowHidden() {
        viewModelScope.launch { container.settingsRepository.setShowHidden(!_state.value.showHidden) }
    }

    fun addSafTree(uri: Uri) {
        viewModelScope.launch { container.settingsRepository.addSafTree(uri.toString()) }
    }

    fun selectRoot(panelId: PanelId, root: FileEntry) {
        navigate(panelId, root, clearForward = true)
    }

    fun activate(entry: FileEntry, panelId: PanelId) {
        setActivePanel(panelId)
        if (entry.isDirectory) {
            navigate(panelId, entry, clearForward = true)
        } else {
            viewModelScope.launch {
                runCatching {
                    container.metadataRepository.recordOpened(entry)
                    container.fileHandlers.resolve(entry)
                }.onSuccess { handler ->
                    eventChannel.send(ExplorerEvent.OpenFile(entry, handler))
                }.onFailure(::showFailure)
            }
        }
    }

    fun toggleSelection(panelId: PanelId, entry: FileEntry) {
        updatePanel(panelId) { panel ->
            val selected = panel.selected.toMutableSet()
            if (!selected.add(entry.ref)) selected.remove(entry.ref)
            panel.copy(selected = selected)
        }
    }

    fun clearSelection(panelId: PanelId) {
        updatePanel(panelId) { it.copy(selected = emptySet()) }
    }

    fun refresh(panelId: PanelId) {
        val directory = panel(panelId).directory ?: return
        loadDirectory(panelId, directory, pushCurrent = false, clearForward = false)
    }

    fun goBack(panelId: PanelId) {
        val current = panel(panelId)
        val target = current.backStack.lastOrNull() ?: return
        viewModelScope.launch {
            val entry = runCatching { container.providerRegistry.require(target).stat(target) }
                .getOrElse { showFailure(it); return@launch }
            updatePanel(panelId) {
                it.copy(
                    backStack = it.backStack.dropLast(1),
                    forwardStack = listOfNotNull(it.directory?.ref) + it.forwardStack,
                )
            }
            loadDirectory(panelId, entry, pushCurrent = false, clearForward = false)
        }
    }

    fun goForward(panelId: PanelId) {
        val current = panel(panelId)
        val target = current.forwardStack.firstOrNull() ?: return
        viewModelScope.launch {
            val entry = runCatching { container.providerRegistry.require(target).stat(target) }
                .getOrElse { showFailure(it); return@launch }
            updatePanel(panelId) {
                it.copy(
                    backStack = it.backStack + listOfNotNull(it.directory?.ref),
                    forwardStack = it.forwardStack.drop(1),
                )
            }
            loadDirectory(panelId, entry, pushCurrent = false, clearForward = false)
        }
    }

    fun goUp(panelId: PanelId) {
        val current = panel(panelId).directory ?: return
        viewModelScope.launch {
            val parent = runCatching { container.providerRegistry.require(current.ref).parent(current.ref) }
                .getOrElse { showFailure(it); null } ?: return@launch
            navigate(panelId, parent, clearForward = true)
        }
    }

    fun createDirectory(panelId: PanelId, displayName: String) {
        val parent = panel(panelId).directory?.ref ?: return
        viewModelScope.launch {
            runCatching { container.operations.createDirectory(parent, displayName) }
                .onSuccess { refresh(panelId) }
                .onFailure(::showFailure)
        }
    }

    fun createFile(panelId: PanelId, displayName: String) {
        val parent = panel(panelId).directory?.ref ?: return
        viewModelScope.launch {
            runCatching { container.operations.createFile(parent, displayName, "text/plain") }
                .onSuccess { refresh(panelId) }
                .onFailure(::showFailure)
        }
    }

    fun rename(panelId: PanelId, entry: FileEntry, displayName: String) {
        viewModelScope.launch {
            runCatching { container.operations.rename(entry.ref, displayName) }
                .onSuccess { refresh(panelId) }
                .onFailure(::showFailure)
        }
    }

    fun delete(panelId: PanelId, entry: FileEntry) {
        viewModelScope.launch {
            runCatching { container.operations.delete(entry.ref, recursive = entry.isDirectory) }
                .onSuccess { refresh(panelId) }
                .onFailure(::showFailure)
        }
    }

    fun transferSelected(sourcePanel: PanelId, move: Boolean) {
        if (transferJob?.isActive == true) {
            showMessage("Já existe uma operação em andamento")
            return
        }
        val source = panel(sourcePanel)
        val destinationPanel = panel(sourcePanel.other())
        val destination = destinationPanel.directory?.ref ?: return
        val selectedEntries = source.entries.filter { it.ref in source.selected }
        if (selectedEntries.isEmpty()) {
            showMessage("Selecione ao menos um item")
            return
        }
        val controller = OperationController()
        transferController = controller
        transferJob = viewModelScope.launch {
            try {
                selectedEntries.forEach { entry ->
                    val progress: (OperationProgress) -> Unit = { value ->
                        _state.update { it.copy(transfer = TransferUiState(value, controller.isPaused)) }
                    }
                    if (move) {
                        container.operations.move(entry.ref, destination, controller = controller, onProgress = progress)
                    } else {
                        container.operations.copy(entry.ref, destination, controller = controller, onProgress = progress)
                    }
                }
                clearSelection(sourcePanel)
                refresh(sourcePanel)
                refresh(sourcePanel.other())
                showMessage(if (move) "Movimentação concluída" else "Cópia concluída")
            } catch (_: CancellationException) {
                showMessage("Operação cancelada")
            } catch (failure: Throwable) {
                showFailure(failure)
            } finally {
                transferController = null
                _state.update { it.copy(transfer = null) }
            }
        }
    }

    fun pauseTransfer() {
        transferController?.pause()
        _state.update { state -> state.copy(transfer = state.transfer?.copy(paused = true)) }
    }

    fun resumeTransfer() {
        transferController?.resume()
        _state.update { state -> state.copy(transfer = state.transfer?.copy(paused = false)) }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun reportMessage(message: String) {
        showMessage(message)
    }

    private fun navigate(panelId: PanelId, entry: FileEntry, clearForward: Boolean) {
        loadDirectory(panelId, entry, pushCurrent = true, clearForward = clearForward)
    }

    private fun loadDirectory(
        panelId: PanelId,
        directory: FileEntry,
        pushCurrent: Boolean,
        clearForward: Boolean,
    ) {
        viewModelScope.launch {
            val before = panel(panelId)
            updatePanel(panelId) { it.copy(loading = true) }
            runCatching {
                val provider = container.providerRegistry.require(directory.ref)
                provider.stat(directory.ref) to provider.list(directory.ref)
            }.onSuccess { (freshDirectory, files) ->
                updatePanel(panelId) { current ->
                    current.copy(
                        directory = freshDirectory,
                        entries = files.filter { _state.value.showHidden || !it.name.startsWith('.') },
                        backStack = if (pushCurrent) before.backStack + listOfNotNull(before.directory?.ref) else current.backStack,
                        forwardStack = if (clearForward) emptyList() else current.forwardStack,
                        selected = emptySet(),
                        loading = false,
                    )
                }
            }.onFailure { failure ->
                updatePanel(panelId) { it.copy(loading = false) }
                showFailure(failure)
            }
        }
    }

    private suspend fun refreshRoots(resetMissingPanels: Boolean) {
        val roots = container.providerRegistry.all().flatMap { provider ->
            runCatching { provider.roots() }.getOrDefault(emptyList())
        }
        _state.update { it.copy(roots = roots) }
        if (roots.isEmpty()) {
            showMessage("Nenhum workspace acessível")
            return
        }
        listOf(PanelId.LEFT, PanelId.RIGHT).forEachIndexed { index, panelId ->
            val current = panel(panelId).directory
            val isValid = current != null && runCatching {
                container.providerRegistry.require(current.ref).stat(current.ref)
            }.isSuccess
            if (current == null || (resetMissingPanels && !isValid)) {
                loadDirectory(panelId, roots.getOrElse(index) { roots.first() }, false, true)
            } else if (isValid) {
                refresh(panelId)
            }
        }
    }

    private fun panel(id: PanelId): FilePanelState = if (id == PanelId.LEFT) _state.value.left else _state.value.right

    private fun updatePanel(id: PanelId, transform: (FilePanelState) -> FilePanelState) {
        _state.update { state ->
            if (id == PanelId.LEFT) state.copy(left = transform(state.left))
            else state.copy(right = transform(state.right))
        }
    }

    private fun showFailure(failure: Throwable) {
        val safeMessage = when (failure) {
            is SecurityException -> "Acesso negado pela política de segurança"
            is java.nio.file.FileAlreadyExistsException -> "Já existe um item com esse nome"
            is java.io.IOException -> "Falha de armazenamento: ${failure.message?.substringAfterLast('/')?.take(100) ?: "erro de I/O"}"
            else -> failure.message?.take(140) ?: "A operação falhou"
        }
        showMessage(safeMessage)
    }

    private fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    private fun PanelId.other(): PanelId = if (this == PanelId.LEFT) PanelId.RIGHT else PanelId.LEFT

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ExplorerViewModel::class.java))
            return ExplorerViewModel(container) as T
        }
    }
}
