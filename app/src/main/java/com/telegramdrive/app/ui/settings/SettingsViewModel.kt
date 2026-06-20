package com.telegramdrive.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramdrive.app.data.local.entity.AccountEntity
import com.telegramdrive.app.data.remote.telegram.TdLibManager
import com.telegramdrive.app.data.repository.AccountRepository
import com.telegramdrive.app.data.repository.PreferencesRepository
import com.telegramdrive.app.data.security.SecureCredentialsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val accountRepo: AccountRepository,
    private val credentials: SecureCredentialsStore,
    private val tdLibManager: TdLibManager
) : ViewModel() {

    val dynamicColor = prefs.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val darkMode = prefs.darkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")
    val encryptUploads = prefs.encryptUploads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val parallelUploads = prefs.parallelUploads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)
    val language = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val accounts = accountRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeAccount = accountRepo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _credentialsSet = MutableStateFlow(credentials.hasCredentials())
    val credentialsSet: StateFlow<Boolean> = _credentialsSet.asStateFlow()

    private val _credentialError = MutableStateFlow<String?>(null)
    val credentialError: StateFlow<String?> = _credentialError.asStateFlow()

    fun setDynamicColor(v: Boolean) = viewModelScope.launch { prefs.setDynamicColor(v) }
    fun setDarkMode(v: String) = viewModelScope.launch { prefs.setDarkMode(v) }
    fun setEncryptUploads(v: Boolean) = viewModelScope.launch { prefs.setEncryptUploads(v) }
    fun setParallelUploads(v: Int) = viewModelScope.launch { prefs.setParallelUploads(v) }
    fun setLanguage(v: String) = viewModelScope.launch { prefs.setLanguage(v) }

    fun addAccount() {
        // Trigger onboarding again with login step
    }

    fun saveCredentials(apiIdText: String, apiHash: String) {
        val apiId = apiIdText.trim().toIntOrNull()
        when {
            apiId == null || apiId <= 0 -> {
                _credentialError.value = "API ID must be a positive number"
            }
            apiHash.isBlank() || apiHash.length < 16 -> {
                _credentialError.value = "API Hash looks too short — check my.telegram.org/apps"
            }
            else -> {
                try {
                    credentials.saveCredentials(apiId, apiHash.trim())
                    _credentialError.value = null
                    _credentialsSet.value = true
                    // Kick TDLib so it picks up the new credentials
                    tdLibManager.bootstrapAfterCredentials()
                } catch (e: Exception) {
                    _credentialError.value = e.message ?: "Failed to save credentials"
                }
            }
        }
    }

    fun clearCredentials() {
        credentials.clear()
        _credentialsSet.value = false
    }
}
