package com.cameronamer.telegramdrive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: TelegramRepository = TelegramRepository()) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: String) : UiState()
        object LoggedOut : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = repository.logout()
            _uiState.value = when {
                result.isSuccess -> UiState.LoggedOut
                else -> UiState.Error(result.exceptionOrNull()?.message ?: "Logout failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }
}
