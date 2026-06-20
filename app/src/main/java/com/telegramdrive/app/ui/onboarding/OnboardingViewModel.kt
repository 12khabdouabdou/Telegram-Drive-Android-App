package com.telegramdrive.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramdrive.app.data.remote.telegram.TelegramAuthService
import com.telegramdrive.app.data.repository.AccountRepository
import com.telegramdrive.app.data.repository.BackupRepository
import com.telegramdrive.app.data.repository.PreferencesRepository
import com.telegramdrive.app.data.security.SecureCredentialsStore
import com.telegramdrive.app.data.remote.telegram.TdLibManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val backupRepo: BackupRepository,
    private val prefs: PreferencesRepository,
    private val credentials: SecureCredentialsStore,
    private val tdLibManager: TdLibManager
) : ViewModel() {

    val authState: StateFlow<TelegramAuthService.AuthState> = accountRepo.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TelegramAuthService.AuthState.Idle)

    private val _credentialsSaved = MutableStateFlow(credentials.hasCredentials())
    val credentialsSaved: StateFlow<Boolean> = _credentialsSaved.asStateFlow()

    private val _credentialError = MutableStateFlow<String?>(null)
    val credentialError: StateFlow<String?> = _credentialError.asStateFlow()

    /**
     * Save the user's Telegram API credentials to EncryptedSharedPreferences.
     * Once saved, the TdLibManager is asked to bootstrap so TDLib is ready for
     * the phone-login step that follows.
     */
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
                    _credentialsSaved.value = true
                    // Now that we have credentials, kick off TDLib bootstrap
                    tdLibManager.bootstrapAfterCredentials()
                } catch (e: Exception) {
                    _credentialError.value = e.message ?: "Failed to save credentials"
                }
            }
        }
    }

    fun submitPhone(phone: String) = viewModelScope.launch {
        accountRepo.startLogin(phone)
    }

    fun submitCode(code: String) = viewModelScope.launch {
        accountRepo.submitCode(code)
    }

    fun submitPassword(pw: String) = viewModelScope.launch {
        accountRepo.submitPassword(pw)
        // Once authed, seed a default backup rule for the new account
        val acc = accountRepo.getActive()
        if (acc != null) backupRepo.seedDefaultRule(acc.id)
    }

    fun enableBackup() = viewModelScope.launch {
        prefs.setAutoBackupEnabled(true)
    }
}
