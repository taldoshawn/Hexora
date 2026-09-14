/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.ui.archive

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(state: ArchiveUiState, viewModel: ArchiveViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Voltar") } },
                title = {
                    Column {
                        Text(state.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${state.entries.size} entradas · navegação ZIP virtual",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::extractAll, enabled = !state.loading && state.extracting == null) {
                        Icon(Icons.Outlined.Unarchive, "Extrair tudo")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.FolderZip, null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Entradas são validadas contra ZIP Slip, path traversal, tamanho e taxa de compressão antes da extração.",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                items(state.entries, key = { it.path }) { entry ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Text(entry.path, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                        Text(
                            if (entry.isDirectory) "Diretório"
                            else "${formatArchiveBytes(entry.uncompressedBytes)} → ${formatArchiveBytes(entry.compressedBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.extracting?.let { progress ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    tonalElevation = 6.dp,
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(progress.entry, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${progress.entriesProcessed}/${progress.totalEntries} · ${formatArchiveBytes(progress.bytesWritten)}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatArchiveBytes(bytes: Long): String {
    if (bytes < 0) return "desconhecido"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = -1
    do { value /= 1024; unit += 1 } while (value >= 1024 && unit < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}
