/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexEditorScreen(state: HexEditorUiState, viewModel: HexEditorViewModel, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    var editOffset by remember { mutableStateOf<Long?>(null) }
    var gotoDialog by remember { mutableStateOf(false) }
    var searchDialog by remember { mutableStateOf(false) }
    var confirmBack by remember { mutableStateOf(false) }
    val tryBack = { if (state.edits.isNotEmpty()) confirmBack = true else onBack() }
    BackHandler(onBack = tryBack)
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = tryBack) { Icon(Icons.Outlined.ArrowBack, "Voltar") } },
                title = {
                    Column {
                        Text(state.entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "HEX · ${state.page?.totalBytes ?: 0} bytes · ${state.edits.size} alteração(ões)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.undo.isNotEmpty()) { Icon(Icons.Outlined.Undo, "Desfazer") }
                    IconButton(onClick = viewModel::redo, enabled = state.redo.isNotEmpty()) { Icon(Icons.Outlined.Redo, "Refazer") }
                    IconButton(onClick = { searchDialog = true }) { Icon(Icons.Outlined.Search, "Buscar bytes") }
                    IconButton(onClick = viewModel::toggleBookmark, enabled = state.selectedOffset != null) {
                        Icon(
                            if (state.selectedOffset?.let { it in state.bookmarks } == true) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            "Favoritar offset",
                        )
                    }
                    IconButton(onClick = viewModel::save, enabled = state.edits.isNotEmpty() && !state.saving) {
                        Icon(Icons.Outlined.Save, "Salvar")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HexNavigationBar(
                page = state.page,
                onPrevious = viewModel::previousPage,
                onNext = viewModel::nextPage,
                onGoto = { gotoDialog = true },
            )
            Box(Modifier.fillMaxSize()) {
                state.page?.let { page ->
                    val rows = remember(page) { page.bytes.indices.chunked(16) }
                    LazyColumn(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        items(rows, key = { it.firstOrNull() ?: -1 }) { indexes ->
                            HexRow(
                                baseOffset = page.offset,
                                indexes = indexes,
                                byteAt = { index -> viewModel.byteAt(page.offset + index) ?: 0 },
                                selectedOffset = state.selectedOffset,
                                edits = state.edits.keys,
                                bookmarks = state.bookmarks,
                                onSelect = { offset -> viewModel.select(offset); editOffset = offset },
                            )
                        }
                    }
                }
                if (state.loading || state.saving || state.searching) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            SelectedByteInspector(state, viewModel)
        }
    }

    editOffset?.let { offset ->
        ByteEditDialog(
            offset = offset,
            current = viewModel.byteAt(offset) ?: 0,
            onDismiss = { editOffset = null },
            onConfirm = { value -> viewModel.edit(offset, value); editOffset = null },
        )
    }
    if (gotoDialog) OffsetDialog("Ir para offset", onDismiss = { gotoDialog = false }) {
        viewModel.goTo(it); gotoDialog = false
    }
    if (searchDialog) SearchBytesDialog(
        onDismiss = { searchDialog = false },
        onSearch = { value, asHex -> viewModel.search(value, asHex); searchDialog = false },
    )
    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("Descartar alterações de bytes?") },
            text = { Text("O arquivo original ainda não foi modificado.") },
            confirmButton = { Button(onClick = onBack) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmBack = false }) { Text("Continuar") } },
        )
    }
}

@Composable
private fun HexNavigationBar(
    page: dev.hexora.core.editor.HexPage?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGoto: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = (page?.offset ?: 0) > 0) {
                Icon(Icons.Outlined.ChevronLeft, "Página anterior")
            }
            TextButton(onClick = onGoto, modifier = Modifier.weight(1f)) {
                Text("0x${(page?.offset ?: 0).toString(16).uppercase().padStart(8, '0')}", fontFamily = FontFamily.Monospace)
            }
            IconButton(
                onClick = onNext,
                enabled = page != null && page.offset + page.bytes.size < page.totalBytes,
            ) { Icon(Icons.Outlined.ChevronRight, "Próxima página") }
        }
    }
}

@Composable
private fun HexRow(
    baseOffset: Long,
    indexes: List<Int>,
    byteAt: (Int) -> Byte,
    selectedOffset: Long?,
    edits: Set<Long>,
    bookmarks: Set<Long>,
    onSelect: (Long) -> Unit,
) {
    Row(
        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val firstOffset = baseOffset + (indexes.firstOrNull() ?: 0)
        Text(
            firstOffset.toString(16).uppercase().padStart(8, '0'),
            modifier = Modifier.width(84.dp),
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
        )
        indexes.forEach { index ->
            val offset = baseOffset + index
            val value = byteAt(index)
            val background = when {
                offset == selectedOffset -> MaterialTheme.colorScheme.primaryContainer
                offset in edits -> MaterialTheme.colorScheme.tertiaryContainer
                offset in bookmarks -> MaterialTheme.colorScheme.secondaryContainer
                else -> Color.Transparent
            }
            Text(
                text = "%02X".format(value.toInt() and 0xFF),
                modifier = Modifier.background(background, MaterialTheme.shapes.extraSmall)
                    .clickable { onSelect(offset) }.padding(horizontal = 4.dp, vertical = 3.dp),
                fontFamily = FontFamily.Monospace,
                fontWeight = if (offset in edits) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            indexes.joinToString("") { index ->
                val value = byteAt(index).toInt() and 0xFF
                if (value in 32..126) value.toChar().toString() else "."
            },
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectedByteInspector(state: HexEditorUiState, viewModel: HexEditorViewModel) {
    val offset = state.selectedOffset ?: return
    val byte = viewModel.byteAt(offset) ?: return
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Offset 0x${offset.toString(16).uppercase()}", fontFamily = FontFamily.Monospace)
            Text("int8 ${byte.toInt()}", fontFamily = FontFamily.Monospace)
            Text("uint8 ${byte.toInt() and 0xFF}", fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ByteEditDialog(offset: Long, current: Byte, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(offset) { mutableStateOf("%02X".format(current.toInt() and 0xFF)) }
    val valid = value.removePrefix("0x").length in 1..2 && value.removePrefix("0x").toIntOrNull(16) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar 0x${offset.toString(16).uppercase()}") },
        text = {
            OutlinedTextField(value, { value = it.take(4) }, label = { Text("Byte hexadecimal") }, singleLine = true)
        },
        confirmButton = { Button(onClick = { onConfirm(value) }, enabled = valid) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun OffsetDialog(title: String, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var value by remember { mutableStateOf("") }
    val parsed = value.trim().let {
        if (it.startsWith("0x", true)) it.substring(2).toLongOrNull(16) else it.toLongOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it.take(18) }, label = { Text("Decimal ou 0xHEX") }) },
        confirmButton = { Button(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text("Ir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SearchBytesDialog(onDismiss: () -> Unit, onSearch: (String, Boolean) -> Unit) {
    var value by remember { mutableStateOf("") }
    var asHex by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar no arquivo") },
        text = {
            Column {
                OutlinedTextField(
                    value,
                    { value = it.take(2048) },
                    label = { Text(if (asHex) "Bytes: DE AD BE EF" else "Texto UTF-8") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Interpretar como hexadecimal")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = asHex, onCheckedChange = { asHex = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSearch(value, asHex) }, enabled = value.isNotBlank()) { Text("Buscar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
