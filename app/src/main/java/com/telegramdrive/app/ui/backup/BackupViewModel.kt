package com.telegramdrive.app.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramdrive.app.data.local.entity.BackupRuleEntity
import com.telegramdrive.app.data.repository.AccountRepository
import com.telegramdrive.app.data.repository.BackupRepository
import com.telegramdrive.app.data.repository.FileRepository
import com.telegramdrive.app.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepo: BackupRepository,
    private val fileRepo: FileRepository,
    private val accountRepo: AccountRepository,
    private val prefs: PreferencesRepository
) : ViewModel() {

    val rules: StateFlow<List<BackupRuleEntity>> = backupRepo.observeForActiveAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoBackupEnabled: StateFlow<Boolean> = prefs.autoBackupEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val storageStats: StateFlow<FileRepository.StorageStats?> = accountRepo.observeActive()
        .flatMapLatest { acc ->
            if (acc == null) flowOf(null)
            else flow { emit(fileRepo.storageStats(acc.id)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setAutoBackup(v: Boolean) = viewModelScope.launch { prefs.setAutoBackupEnabled(v) }

    fun toggleRule(id: Long, enabled: Boolean) = viewModelScope.launch {
        backupRepo.setEnabled(id, enabled)
    }

    fun createDefaultRule() = viewModelScope.launch {
        val acc = accountRepo.getActive() ?: return@launch
        backupRepo.create(BackupRuleEntity(
            accountId = acc.id,
            name = "New rule",
            includedFolders = listOf("DCIM/Camera"),
            mimeTypes = listOf("image/*", "video/*"),
            requireWifi = true,
            schedule = BackupRuleEntity.Schedule.WATCH,
            dedupEnabled = true,
            onlyOriginalQuality = true
        ))
    }
}
