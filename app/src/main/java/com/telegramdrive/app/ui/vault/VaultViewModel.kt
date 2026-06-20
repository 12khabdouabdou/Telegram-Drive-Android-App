package com.telegramdrive.app.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramdrive.app.data.local.entity.VaultEntryEntity
import com.telegramdrive.app.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repo: VaultRepository
) : ViewModel() {

    val uiState: StateFlow<VaultUiState> = kotlinx.coroutines.flow.flow {
        emit(if (repo.isVaultInitialized()) VaultUiState.Locked else VaultUiState.NotInitialized)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultUiState.NotInitialized)

    val entries: StateFlow<List<VaultEntryEntity>> = repo.observeForActiveAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun initialize(passphrase: String) {
        repo.initializeVault(passphrase.toCharArray())
    }

    fun unlock(passphrase: String): Boolean =
        repo.unlockVault(passphrase.toCharArray())

    fun unlockWithBiometric() {
        // TODO: unwrap the DEK with the Android Keystore-wrapped biometric key
    }

    fun showAddDialog() {
        // TODO: show add-entry sheet
    }
}

sealed class VaultUiState {
    object NotInitialized : VaultUiState()
    object Locked : VaultUiState()
    object Unlocked : VaultUiState()
}
