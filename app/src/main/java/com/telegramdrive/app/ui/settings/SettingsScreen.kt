package com.telegramdrive.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramdrive.app.BuildConfig
import com.telegramdrive.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
    val darkMode by vm.darkMode.collectAsStateWithLifecycle(initialValue = "system")
    val encryptUploads by vm.encryptUploads.collectAsStateWithLifecycle(initialValue = false)
    val parallelUploads by vm.parallelUploads.collectAsStateWithLifecycle(initialValue = 2)
    val language by vm.language.collectAsStateWithLifecycle(initialValue = "system")
    val accounts by vm.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeAccount by vm.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    val credentialsSet by vm.credentialsSet.collectAsStateWithLifecycle(initialValue = false)
    val credentialError by vm.credentialError.collectAsStateWithLifecycle(initialValue = null)
    var showCredentialDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Account section
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_accounts), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                activeAccount?.let { acc ->
                    ListItem(
                        headlineContent = { Text("${acc.firstName} ${acc.lastName ?: ""}") },
                        supportingContent = { Text(acc.phoneNumber) },
                        leadingContent = { Icon(Icons.Filled.AccountCircle, null) }
                    )
                }
                accounts.drop(1).forEach { acc ->
                    ListItem(
                        headlineContent = { Text("${acc.firstName} ${acc.lastName ?: ""}") },
                        supportingContent = { Text(acc.phoneNumber) },
                        leadingContent = { Icon(Icons.Filled.AccountCircle, null) }
                    )
                }
                TextButton(onClick = { vm.addAccount() }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.login_add_account))
                }
            }
        }

        // Telegram API credentials
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_api_credentials), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.settings_api_credentials_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (credentialsSet) stringResource(R.string.settings_api_credentials_set)
                           else stringResource(R.string.settings_api_credentials_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (credentialsSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { showCredentialDialog = true }) {
                        Text(stringResource(R.string.settings_api_credentials_update))
                    }
                    if (credentialsSet) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { vm.clearCredentials() }) {
                            Text(stringResource(R.string.settings_api_credentials_clear))
                        }
                    }
                }
            }
        }

        // Appearance
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    trailingContent = { Switch(checked = dynamicColor, onCheckedChange = vm::setDynamicColor) }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dark_mode)) },
                    supportingContent = { Text(darkMode) }
                )
                Row(Modifier.padding(horizontal = 16.dp)) {
                    listOf("system", "light", "dark").forEach { mode ->
                        FilterChip(
                            selected = darkMode == mode,
                            onClick = { vm.setDarkMode(mode) },
                            label = { Text(mode.replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    supportingContent = { Text(language) }
                )
            }
        }

        // Security & Upload
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_upload_encryption)) },
                    supportingContent = { Text("AES-256-GCM") },
                    trailingContent = { Switch(checked = encryptUploads, onCheckedChange = vm::setEncryptUploads) }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_parallel_uploads)) },
                    supportingContent = { Text(parallelUploads.toString()) },
                    trailingContent = {
                        Slider(
                            value = parallelUploads.toFloat(),
                            onValueChange = { vm.setParallelUploads(it.toInt()) },
                            valueRange = 1f..4f,
                            steps = 2,
                            modifier = Modifier.width(120.dp)
                        )
                    }
                )
            }
        }

        // About
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_version, BuildConfig.APP_VERSION),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showCredentialDialog) {
        CredentialEditDialog(
            error = credentialError,
            onDismiss = { showCredentialDialog = false },
            onSave = { id, hash ->
                vm.saveCredentials(id, hash)
                if (credentialError == null) showCredentialDialog = false
            }
        )
    }
}

@Composable
private fun CredentialEditDialog(
    error: String?,
    onDismiss: () -> Unit,
    onSave: (apiId: String, apiHash: String) -> Unit
) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_api_credentials)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_api_credentials_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiId,
                    onValueChange = { apiId = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.onboarding_credentials_apiid_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiHash,
                    onValueChange = { apiHash = it },
                    label = { Text(stringResource(R.string.onboarding_credentials_apihash_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = apiId.isNotBlank() && apiHash.isNotBlank(),
                onClick = { onSave(apiId, apiHash) }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
