package com.telegramdrive.app.ui.vault

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramdrive.app.R
import com.telegramdrive.app.data.local.entity.VaultEntryEntity

@Composable
fun VaultScreen(vm: VaultViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle(initialValue = emptyList())

    when (state) {
        VaultUiState.NotInitialized -> SetupVault(vm)
        VaultUiState.Locked -> LockedVault(vm)
        VaultUiState.Unlocked -> VaultList(entries, vm)
    }
}

@Composable
private fun SetupVault(vm: VaultViewModel) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.vault_setup_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.vault_setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = passphrase, onValueChange = { passphrase = it },
            label = { Text("Passphrase") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it },
            label = { Text("Confirm") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (passphrase != confirm) error = "Passphrases don't match"
            else if (passphrase.length < 8) error = "Min 8 characters"
            else { vm.initialize(passphrase); passphrase = ""; confirm = "" }
        }) { Text(stringResource(R.string.action_done)) }
    }
}

@Composable
private fun LockedVault(vm: VaultViewModel) {
    val ctx = LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.vault_locked), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.vault_unlock_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = passphrase, onValueChange = { passphrase = it },
            label = { Text("Passphrase") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (!vm.unlock(passphrase)) error = "Wrong passphrase"
            else { passphrase = ""; error = null }
        }) { Text(stringResource(R.string.vault_unlock)) }
        Spacer(Modifier.height(8.dp))
        // Biometric unlock
        val canUseBiometric = remember {
            BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        }
        if (canUseBiometric) {
            TextButton(onClick = {
                val activity = ctx as? FragmentActivity ?: return@TextButton
                val prompt = BiometricPrompt(activity,
                    androidx.core.content.ContextCompat.getMainExecutor(ctx),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            // For biometric, we re-use a stored passphrase wrapped by the keystore
                            vm.unlockWithBiometric()
                        }
                    })
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock vault")
                        .setSubtitle("Authenticate to view your passwords")
                        .setNegativeButtonText("Cancel")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build()
                )
            }) { Text("Use fingerprint") }
        }
    }
}

@Composable
private fun VaultList(entries: List<VaultEntryEntity>, vm: VaultViewModel) {
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.showAddDialog() },
                icon = { Icon(androidx.compose.material.icons.Icons.Filled.Add, contentDescription = null) },
                text = { Text("New entry") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.title) },
                    supportingContent = { entry.username?.let { Text(it) } },
                    leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) }
                )
                HorizontalDivider()
            }
        }
    }
}
