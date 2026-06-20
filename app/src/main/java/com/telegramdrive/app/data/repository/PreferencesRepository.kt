package com.telegramdrive.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.telegramdrive.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "tgdrive_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val ENCRYPT_UPLOADS = booleanPreferencesKey("encrypt_uploads")
        val PARALLEL_UPLOADS = intPreferencesKey("parallel_uploads")
        val CHUNK_SIZE_MB = intPreferencesKey("chunk_size_mb")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DARK_MODE = stringPreferencesKey("dark_mode") // "system" | "light" | "dark"
        val LANGUAGE = stringPreferencesKey("language") // BCP-47 tag or "system"
        val VAULT_AUTOLOCK_MIN = intPreferencesKey("vault_autolock_min") // -1 = never, 0 = immediate
    }

    val data: Flow<Preferences> = ctx.dataStore.data.flowOn(io)

    val onboardingDone: Flow<Boolean> = data.map { it[Keys.ONBOARDING_DONE] ?: false }.flowOn(io)
    val autoBackupEnabled: Flow<Boolean> = data.map { it[Keys.AUTO_BACKUP_ENABLED] ?: false }.flowOn(io)
    val encryptUploads: Flow<Boolean> = data.map { it[Keys.ENCRYPT_UPLOADS] ?: false }.flowOn(io)
    val parallelUploads: Flow<Int> = data.map { it[Keys.PARALLEL_UPLOADS] ?: 2 }.flowOn(io)
    val dynamicColor: Flow<Boolean> = data.map { it[Keys.DYNAMIC_COLOR] ?: true }.flowOn(io)
    val darkMode: Flow<String> = data.map { it[Keys.DARK_MODE] ?: "system" }.flowOn(io)
    val language: Flow<String> = data.map { it[Keys.LANGUAGE] ?: "system" }.flowOn(io)
    val vaultAutolockMin: Flow<Int> = data.map { it[Keys.VAULT_AUTOLOCK_MIN] ?: 1 }.flowOn(io)

    suspend fun setOnboardingDone(v: Boolean) = edit { it[Keys.ONBOARDING_DONE] = v }
    suspend fun setAutoBackupEnabled(v: Boolean) = edit { it[Keys.AUTO_BACKUP_ENABLED] = v }
    suspend fun setEncryptUploads(v: Boolean) = edit { it[Keys.ENCRYPT_UPLOADS] = v }
    suspend fun setParallelUploads(v: Int) = edit { it[Keys.PARALLEL_UPLOADS] = v }
    suspend fun setDynamicColor(v: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = v }
    suspend fun setDarkMode(v: String) = edit { it[Keys.DARK_MODE] = v }
    suspend fun setLanguage(v: String) = edit { it[Keys.LANGUAGE] = v }
    suspend fun setVaultAutolockMin(v: Int) = edit { it[Keys.VAULT_AUTOLOCK_MIN] = v }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) =
        withContext(io) {
            ctx.dataStore.edit(block)
        }
}
