package com.telegramdrive.app.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramdrive.app.data.local.entity.FileEntity
import com.telegramdrive.app.data.local.entity.FolderEntity
import com.telegramdrive.app.data.repository.AccountRepository
import com.telegramdrive.app.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FilesViewModel @Inject constructor(
    private val fileRepo: FileRepository,
    private val accountRepo: AccountRepository
) : ViewModel() {

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId

    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog

    val folders: StateFlow<List<FolderEntity>> = accountRepo.observeActive()
        .flatMapLatest { acc ->
            if (acc == null) flowOf(emptyList())
            else fileRepo.observeFolders(acc.id, _currentFolderId.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val files: StateFlow<List<FileEntity>> = accountRepo.observeActive()
        .flatMapLatest { acc ->
            if (acc == null) flowOf(emptyList())
            else fileRepo.observeFilesInFolder(acc.id, _currentFolderId.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openFolder(id: Long) { _currentFolderId.value = id }
    fun openFile(file: FileEntity) { /* navigate to viewer */ }
    fun showCreateFolder() { _showCreateFolderDialog.value = true }
    fun hideCreateFolder() { _showCreateFolderDialog.value = false }

    fun createFolder(name: String) = viewModelScope.launch {
        val acc = accountRepo.getActive() ?: return@launch
        fileRepo.createFolder(acc.id, _currentFolderId.value, name)
    }

    fun pickFiles() {
        // Wire up to Activity Result API in the screen — VM triggers a flow event
    }
}
