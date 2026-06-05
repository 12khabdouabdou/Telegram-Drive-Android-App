package com.cameronamer.telegramdrive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onLoginComplete: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthViewModel.UiState.Success -> {
                val step = (uiState as AuthViewModel.UiState.Success).step
                if (step == AuthViewModel.AuthStep.Password) {
                    onLoginComplete()
                }
            }
            is AuthViewModel.UiState.Error -> {
                val message = (uiState as AuthViewModel.UiState.Error).message
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(message)
                }
                viewModel.resetError()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally { it -> it } + fadeIn() togetherWith
                    slideOutHorizontally { it -> -it } + fadeOut()
                },
                label = "AuthStepAnimation"
            ) { step ->
                when (step) {
                    is AuthViewModel.AuthStep.Setup -> SetupStep(viewModel)
                    is AuthViewModel.AuthStep.PhoneCode -> PhoneCodeStep(viewModel)
                    is AuthViewModel.AuthStep.Password -> PasswordStep(viewModel)
                }
            }
        }
    }
}

@Composable
private fun SetupStep(viewModel: AuthViewModel) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Telegram Drive",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = apiId,
            onValueChange = { apiId = it.trim() },
            label = { Text("API ID") },
            placeholder = { Text("e.g. 12345678") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = apiHash,
            onValueChange = { apiHash = it.trim() },
            label = { Text("API Hash") },
            placeholder = { Text("32-character hex string") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLoading = true
                viewModel.requestCode("", apiId.toIntOrNull() ?: 0, apiHash)
            },
            enabled = apiId.isNotBlank() && apiHash.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun PhoneCodeStep(viewModel: AuthViewModel) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var isCodeSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isCodeSent) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.trim() },
                label = { Text("Phone Number") },
                placeholder = { Text("+1 234 567 8900") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isLoading = true
                    viewModel.requestCode(phone, 0, "")
                    isCodeSent = true
                    isLoading = false
                },
                enabled = phone.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("Send Code")
                }
            }
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.trim() },
                label = { Text("Code") },
                placeholder = { Text("5-digit code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isLoading = true
                    viewModel.signIn(code)
                    isLoading = false
                },
                enabled = code.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("Verify")
                }
            }
        }
    }
}

@Composable
private fun PasswordStep(viewModel: AuthViewModel) {
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("2FA Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLoading = true
                viewModel.checkPassword(password)
                isLoading = false
            },
            enabled = password.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Login")
            }
        }
    }
}
