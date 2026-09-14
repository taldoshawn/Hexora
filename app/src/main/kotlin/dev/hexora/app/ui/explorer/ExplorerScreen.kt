/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.ui.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hexora.app.storage.AccessStatus
import dev.hexora.core.model.AccessMode
import dev.hexora.core.model.FileEntry
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private sealed interface ExplorerDialog {
    data class Create(val panel: PanelId, val directory: Boolean) : ExplorerDialog
    data class Rename(val panel: PanelId, val entry: FileEntry) : ExplorerDialog
    data class Delete(val panel: PanelId, val entry: FileEntry) : ExplorerDialog
    data object Access : ExplorerDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    state: ExplorerUiState,
    viewModel: ExplorerViewModel,
    onChooseSafTree: () -> Unit,
    onRequestAllFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<ExplorerDialog?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = "H",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Black,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Hexora", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "File engineering workspace",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { dialog = ExplorerDialog.Access }) {
                        Icon(Icons.Outlined.Lock, contentDescription = "Central de acesso")
                    }
                    IconButton(onClick = viewModel::toggleShowHidden) {
                        Icon(
                            if (state.showHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = "Alternar arquivos ocultos",
                        )
                    }
                },
            )
        },
        bottomBar = {
            state.transfer?.let { transfer ->
                TransferBar(
                    state = transfer,
                    onPause = viewModel::pauseTransfer,
                    onResume = viewModel::resumeTransfer,
                    onCancel = viewModel::cancelTransfer,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(state.searchQuery, viewModel::setSearchQuery)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val dualPane = maxWidth >= 760.dp
                if (dualPane) {
                    Row(Modifier.fillMaxSize()) {
                        FilePane(
                            panelId = PanelId.LEFT,
                            state = state.left,
                            roots = state.roots,
                            query = state.searchQuery,
                            isActive = state.activePanel == PanelId.LEFT,
                            viewModel = viewModel,
                            onDialog = { dialog = it },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        Box(
                            Modifier.width(1.dp).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        FilePane(
                            panelId = PanelId.RIGHT,
                            state = state.right,
                            roots = state.roots,
                            query = state.searchQuery,
                            isActive = state.activePanel == PanelId.RIGHT,
                            viewModel = viewModel,
                            onDialog = { dialog = it },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        MobilePanelSwitcher(state.activePanel, viewModel::setActivePanel)
                        val id = state.activePanel
                        FilePane(
                            panelId = id,
                            state = if (id == PanelId.LEFT) state.left else state.right,
                            roots = state.roots,
                            query = state.searchQuery,
                            isActive = true,
                            viewModel = viewModel,
                            onDialog = { dialog = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        is ExplorerDialog.Create -> NameDialog(
            title = if (current.directory) "Nova pasta" else "Novo arquivo",
            confirmLabel = "Criar",
            initialValue = if (current.directory) "Nova pasta" else "arquivo.txt",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                if (current.directory) viewModel.createDirectory(current.panel, name)
                else viewModel.createFile(current.panel, name)
                dialog = null
            },
        )
        is ExplorerDialog.Rename -> NameDialog(
            title = "Renomear",
            confirmLabel = "Salvar",
            initialValue = current.entry.name,
            onDismiss = { dialog = null },
            onConfirm = { name ->
                viewModel.rename(current.panel, current.entry, name)
                dialog = null
            },
        )
        is ExplorerDialog.Delete -> DeleteDialog(
            entry = current.entry,
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.delete(current.panel, current.entry)
                dialog = null
            },
        )
        ExplorerDialog.Access -> AccessCenterDialog(
            statuses = state.access,
            onDismiss = { dialog = null },
            onChooseSafTree = {
                dialog = null
                onChooseSafTree()
            },
            onRequestAllFiles = {
                dialog = null
                onRequestAllFiles()
            },
        )
        null -> Unit
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        placeholder = { Text("Filtrar nesta pasta") },
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun MobilePanelSwitcher(active: PanelId, onChange: (PanelId) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(PanelId.LEFT to "Painel A", PanelId.RIGHT to "Painel B").forEach { (id, title) ->
            if (id == active) {
                FilledTonalButton(onClick = { onChange(id) }, modifier = Modifier.weight(1f)) { Text(title) }
            } else {
                TextButton(onClick = { onChange(id) }, modifier = Modifier.weight(1f)) { Text(title) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilePane(
    panelId: PanelId,
    state: FilePanelState,
    roots: List<FileEntry>,
    query: String,
    isActive: Boolean,
    viewModel: ExplorerViewModel,
    onDialog: (ExplorerDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rootsExpanded by remember { mutableStateOf(false) }
    var createExpanded by remember { mutableStateOf(false) }
    val visibleEntries = remember(state.entries, query) {
        if (query.isBlank()) state.entries else state.entries.filter { it.name.contains(query, ignoreCase = true) }
    }
    Column(
        modifier = modifier
            .background(if (isActive) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .combinedClickable(
                role = Role.Tab,
                onClick = { viewModel.setActivePanel(panelId) },
                onLongClick = { viewModel.setActivePanel(panelId) },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { rootsExpanded = true }) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        state.directory?.name ?: "Carregando…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(expanded = rootsExpanded, onDismissRequest = { rootsExpanded = false }) {
                    roots.forEach { root ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(root.name)
                                    Text(root.ref.providerId, style = MaterialTheme.typography.labelMedium)
                                }
                            },
                            onClick = {
                                rootsExpanded = false
                                viewModel.selectRoot(panelId, root)
                            },
                        )
                    }
                }
            }
            IconButton(onClick = { viewModel.goBack(panelId) }, enabled = state.backStack.isNotEmpty()) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Voltar")
            }
            IconButton(onClick = { viewModel.goForward(panelId) }, enabled = state.forwardStack.isNotEmpty()) {
                Icon(Icons.Outlined.ArrowForward, contentDescription = "Avançar")
            }
            IconButton(onClick = { viewModel.goUp(panelId) }, enabled = state.directory != null) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "Diretório pai")
            }
            IconButton(onClick = { viewModel.refresh(panelId) }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar")
            }
            Box {
                IconButton(onClick = { createExpanded = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Criar")
                }
                DropdownMenu(expanded = createExpanded, onDismissRequest = { createExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Nova pasta") },
                        leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                        onClick = {
                            createExpanded = false
                            onDialog(ExplorerDialog.Create(panelId, directory = true))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Novo arquivo") },
                        leadingIcon = { Icon(Icons.Outlined.Description, null) },
                        onClick = {
                            createExpanded = false
                            onDialog(ExplorerDialog.Create(panelId, directory = false))
                        },
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.selected.isNotEmpty()) {
            SelectionBar(
                count = state.selected.size,
                onCopy = { viewModel.transferSelected(panelId, move = false) },
                onMove = { viewModel.transferSelected(panelId, move = true) },
                onRename = {
                    state.entries.firstOrNull { it.ref in state.selected }?.let {
                        onDialog(ExplorerDialog.Rename(panelId, it))
                    }
                },
                onDelete = {
                    state.entries.firstOrNull { it.ref in state.selected }?.let {
                        onDialog(ExplorerDialog.Delete(panelId, it))
                    }
                },
                renameEnabled = state.selected.size == 1,
                deleteEnabled = state.selected.size == 1,
                onClear = { viewModel.clearSelection(panelId) },
            )
        }
        Box(Modifier.fillMaxSize()) {
            if (visibleEntries.isEmpty() && !state.loading) {
                EmptyDirectory(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visibleEntries, key = { "${it.ref.providerId}:${it.ref.opaqueId}" }) { entry ->
                        FileRow(
                            entry = entry,
                            selected = entry.ref in state.selected,
                            selectionMode = state.selected.isNotEmpty(),
                            onActivate = { viewModel.activate(entry, panelId) },
                            onSelect = { viewModel.toggleSelection(panelId, entry) },
                            onRename = { onDialog(ExplorerDialog.Rename(panelId, entry)) },
                            onDelete = { onDialog(ExplorerDialog.Delete(panelId, entry)) },
                        )
                    }
                }
            }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(30.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onActivate: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
            .combinedClickable(
                onClick = { if (selectionMode) onSelect() else onActivate() },
                onLongClick = onSelect,
            )
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (entry.isDirectory) "Pasta" else formatBytes(entry.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.modifiedAt?.let {
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date.from(it)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Ações de ${entry.name}")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Selecionar") },
                    onClick = { menuExpanded = false; onSelect() },
                )
                DropdownMenuItem(
                    text = { Text("Renomear") },
                    onClick = { menuExpanded = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Excluir") },
                    onClick = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    renameEnabled: Boolean,
    deleteEnabled: Boolean,
    onClear: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$count selecionado(s)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "Copiar para o outro painel") }
            IconButton(onClick = onMove) { Icon(Icons.Outlined.DriveFileMove, "Mover para o outro painel") }
            IconButton(onClick = onRename, enabled = renameEnabled) { Icon(Icons.Outlined.History, "Renomear") }
            IconButton(onClick = onDelete, enabled = deleteEnabled) { Icon(Icons.Outlined.DeleteOutline, "Excluir") }
            IconButton(onClick = onClear) { Icon(Icons.Outlined.Cancel, "Limpar seleção") }
        }
    }
}

@Composable
private fun EmptyDirectory(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(38.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(8.dp))
        Text("Esta pasta está vazia", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TransferBar(
    state: TransferUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(state.progress.currentItem, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatBytes(state.progress.processedBytes)} · ${formatBytes(state.progress.bytesPerSecond)}/s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = if (state.paused) onResume else onPause) {
                    Icon(if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, "Pausar ou retomar")
                }
                IconButton(onClick = onCancel) { Icon(Icons.Outlined.Cancel, "Cancelar operação") }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    confirmLabel: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val valid = value.isNotBlank() && value != "." && value != ".." &&
        value.none { it == '/' || it == '\\' || it == '\u0000' || it.code < 0x20 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(255) },
                singleLine = true,
                isError = !valid,
                label = { Text("Nome") },
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DeleteDialog(entry: FileEntry, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excluir ${entry.name}?") },
        text = {
            Text(
                if (entry.isDirectory) "A pasta e todo o conteúdo serão removidos. Esta ação exige confirmação explícita."
                else "O arquivo será removido. Esta ação não pode ser desfeita nesta versão.",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AccessCenterDialog(
    statuses: List<AccessStatus>,
    onDismiss: () -> Unit,
    onChooseSafTree: () -> Unit,
    onRequestAllFiles: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Central de acesso") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(statuses, key = { it.mode }) { status ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (status.enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                if (status.enabled) "ATIVO" else if (status.available) "OPCIONAL" else "EM BREVE",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(status.title, fontWeight = FontWeight.SemiBold)
                            Text(status.detail, style = MaterialTheme.typography.labelMedium)
                        }
                        when (status.mode) {
                            AccessMode.SAF -> TextButton(onClick = onChooseSafTree) { Text("Adicionar") }
                            AccessMode.FULL_STORAGE -> if (!status.enabled) {
                                TextButton(onClick = onRequestAllFiles) { Text("Configurar") }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024.0
        unit += 1
    } while (value >= 1024 && unit < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}
