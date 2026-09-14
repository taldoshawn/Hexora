/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hexora.app.di.AppContainer
import dev.hexora.app.ui.archive.ArchiveScreen
import dev.hexora.app.ui.archive.ArchiveViewModel
import dev.hexora.app.ui.editor.HexEditorScreen
import dev.hexora.app.ui.editor.HexEditorViewModel
import dev.hexora.app.ui.editor.TextEditorScreen
import dev.hexora.app.ui.editor.TextEditorViewModel
import dev.hexora.app.ui.explorer.ExplorerEvent
import dev.hexora.app.ui.explorer.ExplorerScreen
import dev.hexora.app.ui.explorer.ExplorerViewModel
import dev.hexora.core.file.FileHandlerKind
import dev.hexora.core.file.LocalFileAccessProvider
import dev.hexora.core.model.FileEntry

private sealed interface WorkspaceScreen {
    data object Explorer : WorkspaceScreen
    data class TextEditor(val file: FileEntry) : WorkspaceScreen
    data class HexEditor(val file: FileEntry) : WorkspaceScreen
    data class Archive(val file: FileEntry) : WorkspaceScreen
}

@Composable
fun HexoraApp(container: AppContainer) {
    val context = LocalContext.current
    val explorerViewModel: ExplorerViewModel = viewModel(factory = ExplorerViewModel.Factory(container))
    val explorerState by explorerViewModel.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<WorkspaceScreen>(WorkspaceScreen.Explorer) }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, readWrite)
            }.recoverCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (persisted) explorerViewModel.addSafTree(uri)
            else explorerViewModel.reportMessage("O Android não concedeu acesso persistente a esta pasta")
        }
    }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        explorerViewModel.onResume()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) explorerViewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(explorerViewModel) {
        explorerViewModel.events.collect { event ->
            when (event) {
                is ExplorerEvent.OpenFile -> {
                    screen = when (event.handler) {
                        FileHandlerKind.TEXT_EDITOR -> WorkspaceScreen.TextEditor(event.entry)
                        FileHandlerKind.ZIP_ARCHIVE,
                        FileHandlerKind.APK_INSPECTOR -> if (
                            container.providerRegistry.require(event.entry.ref) is LocalFileAccessProvider
                        ) {
                            WorkspaceScreen.Archive(event.entry)
                        } else {
                            WorkspaceScreen.HexEditor(event.entry)
                        }
                        else -> WorkspaceScreen.HexEditor(event.entry)
                    }
                }
            }
        }
    }

    val returnToExplorer = {
        screen = WorkspaceScreen.Explorer
        explorerViewModel.refresh(explorerState.activePanel)
    }

    when (val current = screen) {
        WorkspaceScreen.Explorer -> ExplorerScreen(
            state = explorerState,
            viewModel = explorerViewModel,
            onChooseSafTree = { safLauncher.launch(null) },
            onRequestAllFiles = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:${context.packageName}".toUri(),
                )
                runCatching { allFilesLauncher.launch(intent) }.onFailure {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            },
        )
        is WorkspaceScreen.TextEditor -> {
            val editor: TextEditorViewModel = viewModel(
                key = "text:${current.file.ref.providerId}:${current.file.ref.opaqueId}",
                factory = TextEditorViewModel.Factory(current.file, container),
            )
            val state by editor.state.collectAsStateWithLifecycle()
            TextEditorScreen(state, editor, returnToExplorer)
        }
        is WorkspaceScreen.HexEditor -> {
            val editor: HexEditorViewModel = viewModel(
                key = "hex:${current.file.ref.providerId}:${current.file.ref.opaqueId}",
                factory = HexEditorViewModel.Factory(current.file, container),
            )
            val state by editor.state.collectAsStateWithLifecycle()
            HexEditorScreen(state, editor, returnToExplorer)
        }
        is WorkspaceScreen.Archive -> {
            val archive: ArchiveViewModel = viewModel(
                key = "archive:${current.file.ref.providerId}:${current.file.ref.opaqueId}",
                factory = ArchiveViewModel.Factory(current.file, container),
            )
            val state by archive.state.collectAsStateWithLifecycle()
            ArchiveScreen(state, archive, returnToExplorer)
        }
    }
}
