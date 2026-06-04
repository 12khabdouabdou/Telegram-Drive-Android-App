package com.cameronamer.telegramdrive

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.telegram_drive.checkPassword
import uniffi.telegram_drive.requestCode
import uniffi.telegram_drive.signIn

enum class AuthStep {
    SETUP, PHONE, CODE, PASSWORD
}

private object AuthPrefs {
    private const val PREFS = "telegram_drive_auth"
    private const val KEY_API_ID = "api_id"
    private const val KEY_API_HASH = "api_hash"
    private const val KEY_SETUP_DONE = "setup_done"

    fun loadApiId(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API_ID, "") ?: ""

    fun loadApiHash(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API_HASH, "") ?: ""

    fun save(ctx: Context, apiId: String, apiHash: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_API_ID, apiId)
            .putString(KEY_API_HASH, apiHash)
            .putBoolean(KEY_SETUP_DONE, true)
            .apply()
    }

    fun isSetupDone(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SETUP_DONE, false)
}

@Composable
fun AuthScreen(
    onLoginComplete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Pre-fill from SharedPreferences if a previous session exists. If setup was
    // completed before, skip straight to the PHONE step so the user doesn't have
    // to re-enter credentials on every launch.
    val initialApiId = remember { AuthPrefs.loadApiId(context) }
    val initialApiHash = remember { AuthPrefs.loadApiHash(context) }
    val initialSetupDone = remember { AuthPrefs.isSetupDone(context) }

    var currentStep by remember {
        mutableStateOf(if (initialSetupDone && initialApiId.isNotBlank() && initialApiHash.isNotBlank()) AuthStep.PHONE else AuthStep.SETUP)
    }
    var phoneNumber by remember { mutableStateOf("") }
    var apiId by remember { mutableStateOf(initialApiId) }
    var apiHash by remember { mutableStateOf(initialApiHash) }
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

        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        when (currentStep) {
            AuthStep.SETUP -> {
                Text(
                    text = "Enter your Telegram API credentials",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Get these from my.telegram.org → API development tools",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(
                    onClick = {
                        errorMessage = null
                        // Validate
                        if (apiId.isBlank() || apiHash.isBlank()) {
                            errorMessage = "Both API ID and API Hash are required."
                            return@Button
                        }
                        if (apiId.contains(' ') || apiHash.contains(' ')) {
                            errorMessage = "API credentials cannot contain spaces."
                            return@Button
                        }
                        val idInt = apiId.toIntOrNull()
                        if (idInt == null || idInt <= 0) {
                            errorMessage = "API ID must be a positive number."
                            return@Button
                        }
                        if (apiHash.length < 16) {
                            errorMessage = "API Hash looks too short — double-check it."
                            return@Button
                        }
                        // Persist for next launch
                        AuthPrefs.save(context, apiId, apiHash)
                        currentStep = AuthStep.PHONE
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Continue")
                }
            }

            AuthStep.PHONE -> {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it.trim() },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+1 234 567 8900") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        // Defensive: re-validate credentials before JNI call
                        val idInt = apiId.toIntOrNull()
                        if (apiId.isBlank() || idInt == null || idInt <= 0 || apiHash.isBlank()) {
                            errorMessage = "API credentials are missing or invalid. Please re-enter them."
                            currentStep = AuthStep.SETUP
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    requestCode(phoneNumber, idInt, apiHash)
                                }
                                if (result.startsWith("Error")) {
                                    errorMessage = result
                                } else {
                                    currentStep = AuthStep.CODE
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: e.javaClass.simpleName
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = phoneNumber.isNotBlank() && !isLoading
                        && apiId.isNotBlank() && apiId.toIntOrNull() != null
                        && apiHash.isNotBlank()
                ) {
                    Text(if (isLoading) "Sending..." else "Send Code")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        errorMessage = null
                        currentStep = AuthStep.SETUP
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change API credentials")
                }
            }

            AuthStep.CODE -> {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim() },
                    label = { Text("Login Code") },
                    placeholder = { Text("5-digit code from Telegram") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    signIn(code)
                                }
                                if (result == "SUCCESS") {
                                    onLoginComplete()
                                } else if (result == "PASSWORD_REQUIRED") {
                                    currentStep = AuthStep.PASSWORD
                                } else {
                                    errorMessage = result
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: e.javaClass.simpleName
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = code.isNotBlank() && !isLoading
                ) {
                    Text(if (isLoading) "Verifying..." else "Verify Code")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        errorMessage = null
                        currentStep = AuthStep.PHONE
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change phone number")
                }
            }

            AuthStep.PASSWORD -> {
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
                        errorMessage = null
                        coroutineScope.launch {
                            try {
                                val success = withContext(Dispatchers.IO) {
                                    checkPassword(password)
                                }
                                if (success) {
                                    onLoginComplete()
                                } else {
                                    errorMessage = "Invalid password"
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: e.javaClass.simpleName
                            } finally {
                                isLoading = false
                            }
                        }
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
