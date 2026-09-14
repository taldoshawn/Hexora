/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import dev.hexora.core.model.AccessMode

data class AccessStatus(
    val mode: AccessMode,
    val title: String,
    val detail: String,
    val available: Boolean,
    val enabled: Boolean,
)

class AccessStatusProvider(private val context: Context) {
    fun current(safRoots: Int): List<AccessStatus> = listOf(
        AccessStatus(AccessMode.NORMAL, "Armazenamento do app", "Workspace privado e seguro", true, true),
        AccessStatus(AccessMode.SAF, "Pastas escolhidas", "Acesso concedido pelo seletor do Android", true, safRoots > 0),
        AccessStatus(
            AccessMode.FULL_STORAGE,
            "Todos os arquivos",
            "Acesso amplo sujeito às proteções do Android",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            Environment.isExternalStorageManager(),
        ),
        AccessStatus(AccessMode.SHIZUKU, "Shizuku", "Planejado para a Fase 2", false, false),
        AccessStatus(AccessMode.ADB_SHELL, "ADB sem fio", "Planejado para a Fase 2", false, false),
        AccessStatus(AccessMode.ROOT, "Root", "Desativado; nunca solicitado automaticamente", false, false),
    )
}
