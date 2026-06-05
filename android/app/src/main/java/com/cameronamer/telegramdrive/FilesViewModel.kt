package com.cameronamer.telegramdrive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FilesViewModel(private val repository: TelegramRepository = TelegramRepository()) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Success(val files: List<String>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadFiles()
    }

    fun loadFiles(folderId: Long? = null) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = repository.listFiles(folderId)
            _uiState.value = when {
                result.isSuccess -> UiState.Success(result.getOrNull() ?: emptyList())
                else -> UiState.Error(result.exceptionOrNull()?.message ?: "Failed to load files")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteFile(messageId: Int, folderId: Long? = null) {
        viewModelScope.launch {
            val result = repository.deleteFile(messageId, folderId)
            if (result.isSuccess) {
                loadFiles(folderId)
            }
        }
    }
}
