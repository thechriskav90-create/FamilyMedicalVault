package com.familymedicalvault.app.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("vault_settings")

class VaultPreferences(private val context: Context) {
    private val rootUriKey = stringPreferencesKey("root_tree_uri")

    val rootUri: Flow<String?> = context.dataStore.data.map { it[rootUriKey] }

    suspend fun saveRootUri(uri: String) {
        context.dataStore.edit { it[rootUriKey] = uri }
    }
}
