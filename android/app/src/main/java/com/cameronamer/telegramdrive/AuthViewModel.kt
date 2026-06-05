package com.cameronamer.telegramdrive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: TelegramRepository = TelegramRepository()) : ViewModel() {

    sealed class AuthStep {
        object Setup : AuthStep()
        object PhoneCode : AuthStep()
        object Password : AuthStep()
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: String) : UiState()
        data class Success(val step: AuthStep) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentStep = MutableStateFlow<AuthStep>(AuthStep.Setup)
    val currentStep: StateFlow<AuthStep> = _currentStep.asStateFlow()

    fun requestCode(phone: String, apiId: Int, apiHash: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = repository.requestCode(phone, apiId, apiHash)
            _uiState.value = when {
                result.isSuccess -> {
                    _currentStep.value = AuthStep.PhoneCode
                    UiState.Success(AuthStep.PhoneCode)
                }
                else -> UiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun signIn(code: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = repository.signIn(code)
            _uiState.value = when {
                result.isSuccess -> {
                    when (result.getOrNull()) {
                        "SUCCESS" -> UiState.Success(AuthStep.Password)
                        "PASSWORD_REQUIRED" -> {
                            _currentStep.value = AuthStep.Password
                            UiState.Success(AuthStep.Password)
                        }
                        else -> UiState.Error("Unexpected response")
                    }
                }
                else -> UiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun checkPassword(password: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = repository.checkPassword(password)
            _uiState.value = when {
                result.isSuccess && result.getOrNull() == true -> UiState.Success(AuthStep.Password)
                else -> UiState.Error("Invalid password")
            }
        }
    }

    fun resetError() {
        _uiState.value = UiState.Idle
    }
}
