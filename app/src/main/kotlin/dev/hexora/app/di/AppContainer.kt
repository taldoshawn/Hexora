/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.di

import android.content.Context
import android.os.Environment
import androidx.room.Room
import dev.hexora.app.data.local.FileMetadataRepository
import dev.hexora.app.data.local.HexoraDatabase
import dev.hexora.app.data.local.RoomOperationJournal
import dev.hexora.app.data.preferences.SettingsRepository
import dev.hexora.app.storage.AccessStatusProvider
import dev.hexora.app.storage.SafFileAccessProvider
import dev.hexora.core.archive.ZipArchiveService
import dev.hexora.core.capability.CapabilityManager
import dev.hexora.core.editor.TextDocumentService
import dev.hexora.core.file.FileHandlerRegistry
import dev.hexora.core.file.FileOperationEngine
import dev.hexora.core.file.FileSearchEngine
import dev.hexora.core.file.LocalFileAccessProvider
import dev.hexora.core.file.ProviderRegistry
import dev.hexora.core.hash.HashService
import dev.hexora.core.model.AccessMode
import java.nio.file.Files

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: HexoraDatabase by lazy {
        Room.databaseBuilder(appContext, HexoraDatabase::class.java, "hexora.db")
            .build()
    }
    val settingsRepository = SettingsRepository(appContext)
    val metadataRepository by lazy { FileMetadataRepository(database.dao()) }

    val normalProvider: LocalFileAccessProvider
    val safProvider = SafFileAccessProvider(appContext)
    val providerRegistry: ProviderRegistry
    val capabilityManager: CapabilityManager
    val operations: FileOperationEngine
    val search: FileSearchEngine
    val hashService: HashService
    val fileHandlers: FileHandlerRegistry
    val textDocuments: TextDocumentService
    val zipArchives = ZipArchiveService()
    val accessStatus = AccessStatusProvider(appContext)

    init {
        val internalWorkspace = appContext.filesDir.toPath().resolve("workspace")
        val externalWorkspace = appContext.getExternalFilesDir(null)?.toPath()?.resolve("workspace")
        val roots = listOfNotNull(internalWorkspace, externalWorkspace).distinct()
        roots.forEach(Files::createDirectories)
        normalProvider = LocalFileAccessProvider("normal", roots, AccessMode.NORMAL)
        providerRegistry = ProviderRegistry(listOf(normalProvider, safProvider))
        capabilityManager = CapabilityManager(providerRegistry)
        operations = FileOperationEngine(
            registry = providerRegistry,
            capabilityManager = capabilityManager,
            journal = RoomOperationJournal(database.dao()),
        )
        search = FileSearchEngine(providerRegistry)
        hashService = HashService(providerRegistry)
        fileHandlers = FileHandlerRegistry(providerRegistry)
        textDocuments = TextDocumentService(providerRegistry, operations)
        refreshFullStorageProvider()
    }

    fun refreshFullStorageProvider() {
        if (Environment.isExternalStorageManager()) {
            val provider = LocalFileAccessProvider(
                id = "full-storage",
                allowedRoots = listOf(Environment.getExternalStorageDirectory().toPath()),
                accessMode = AccessMode.FULL_STORAGE,
            )
            providerRegistry.register(provider)
        } else {
            providerRegistry.unregister("full-storage")
        }
    }
}
