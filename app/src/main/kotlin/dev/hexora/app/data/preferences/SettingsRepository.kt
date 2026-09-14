/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.hexoraDataStore by preferencesDataStore(name = "hexora_settings")

data class UserSettings(
    val showHiddenFiles: Boolean = false,
    val safTreeUris: Set<String> = emptySet(),
    val crashReportsOptIn: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val ShowHidden = booleanPreferencesKey("show_hidden_files")
        val SafTrees = stringSetPreferencesKey("saf_tree_uris")
        val CrashReports = booleanPreferencesKey("crash_reports_opt_in")
    }

    val settings: Flow<UserSettings> = context.hexoraDataStore.data
        .catch { failure ->
            if (failure is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw failure
        }
        .map { preferences ->
            UserSettings(
                showHiddenFiles = preferences[Keys.ShowHidden] ?: false,
                safTreeUris = preferences[Keys.SafTrees].orEmpty(),
                crashReportsOptIn = preferences[Keys.CrashReports] ?: false,
            )
        }

    suspend fun setShowHidden(show: Boolean) {
        context.hexoraDataStore.edit { it[Keys.ShowHidden] = show }
    }

    suspend fun addSafTree(uri: String) {
        context.hexoraDataStore.edit { preferences ->
            preferences[Keys.SafTrees] = preferences[Keys.SafTrees].orEmpty() + uri
        }
    }

    suspend fun removeSafTree(uri: String) {
        context.hexoraDataStore.edit { preferences ->
            preferences[Keys.SafTrees] = preferences[Keys.SafTrees].orEmpty() - uri
        }
    }
}
