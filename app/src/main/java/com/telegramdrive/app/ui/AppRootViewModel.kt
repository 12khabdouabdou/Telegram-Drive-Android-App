package com.telegramdrive.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.data.repository.PreferencesRepository
import com.telegramdrive.app.data.security.SecureCredentialsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    accountDao: AccountDao,
    private val credentials: SecureCredentialsStore
) : ViewModel() {

    val onboardingDone: StateFlow<Boolean> = prefs.onboardingDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasAccount: StateFlow<Boolean> = accountDao.observeAll()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * Whether the user has entered their Telegram API credentials yet.
     * If false, we route them through onboarding so they can enter them.
     */
    val hasCredentials: StateFlow<Boolean> = kotlinx.coroutines.flow.flow {
        emit(credentials.hasCredentials())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun markOnboardingDone() {
        viewModelScope.launch { prefs.setOnboardingDone(true) }
    }
}
