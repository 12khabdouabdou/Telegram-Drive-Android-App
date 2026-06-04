package com.cameronamer.telegramdrive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

enum class AuthStep {
    PHONE, CODE, PASSWORD
}

@Composable
fun AuthScreen(
    onLoginComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf(AuthStep.PHONE) }
    var phoneNumber by remember { mutableStateOf("") }
    var apiId by remember { mutableStateOf("2040") } // Default Telegram API ID
    var apiHash by remember { mutableStateOf("b18441a1ff607e10a989891a5462e627") } // Default Hash
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Telegram Drive",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        when (currentStep) {
            AuthStep.PHONE -> {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        // TODO: Call UniFFI telegram_drive.request_code()
                        currentStep = AuthStep.CODE
                        isLoading = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = phoneNumber.isNotBlank() && !isLoading
                ) {
                    Text(if (isLoading) "Sending..." else "Send Code")
                }
            }
            AuthStep.CODE -> {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Login Code") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        // TODO: Call UniFFI telegram_drive.sign_in()
                        currentStep = AuthStep.PASSWORD
                        isLoading = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = code.isNotBlank() && !isLoading
                ) {
                    Text(if (isLoading) "Verifying..." else "Verify Code")
                }
            }
            AuthStep.PASSWORD -> {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("2FA Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        // TODO: Call UniFFI telegram_drive.check_password()
                        onLoginComplete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.isNotBlank() && !isLoading
                ) {
                    Text(if (isLoading) "Logging in..." else "Login")
                }
            }
        }
    }
}