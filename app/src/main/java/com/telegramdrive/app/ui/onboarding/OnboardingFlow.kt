package com.telegramdrive.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.telegramdrive.app.R
import com.telegramdrive.app.data.remote.telegram.TelegramAuthService
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingFlow(
    forceLogin: Boolean = false,
    forceCredentials: Boolean = false,
    onFinished: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val pages = remember(forceLogin, forceCredentials) {
        when {
            forceLogin -> listOf(OnboardingPage.Login)
            forceCredentials -> listOf(OnboardingPage.Credentials, OnboardingPage.Login)
            else -> OnboardingPage.all
        }
    }
    val pagerState = rememberPagerState(initialPage = 0) { pages.size }
    val scope = rememberCoroutineScope()

    val mediaPermState = rememberMultiplePermissionsState(
        permissions = buildList {
            add(android.Manifest.permission.READ_MEDIA_IMAGES)
            add(android.Manifest.permission.READ_MEDIA_VIDEO)
            add(android.Manifest.permission.READ_MEDIA_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    )

    val authState by vm.authState.collectAsStateWithLifecycle()
    val credentialsSaved by vm.credentialsSaved.collectAsStateWithLifecycle()
    val credentialError by vm.credentialError.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (pages[page]) {
                    OnboardingPage.Welcome -> WelcomePage()
                    OnboardingPage.Permissions -> PermissionsPage(
                        allGranted = mediaPermState.allPermissionsGranted,
                        onGrant = { mediaPermState.launchMultiplePermissionRequest() }
                    )
                    OnboardingPage.Credentials -> CredentialsPage(
                        saved = credentialsSaved,
                        error = credentialError,
                        onSave = { id, hash -> vm.saveCredentials(id, hash) }
                    )
                    OnboardingPage.Login -> LoginPage(
                        authState = authState,
                        onSubmitPhone = vm::submitPhone,
                        onSubmitCode = vm::submitCode,
                        onSubmitPassword = vm::submitPassword
                    )
                    OnboardingPage.BackupConfig -> BackupConfigPage(
                        onEnableBackup = vm::enableBackup
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onFinished) { Text(stringResource(R.string.action_skip)) }
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.lastIndex) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onFinished()
                        }
                    }
                ) {
                    Text(
                        if (pagerState.currentPage == pages.lastIndex)
                            stringResource(R.string.action_get_started)
                        else stringResource(R.string.action_next)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

sealed class OnboardingPage {
    object Welcome : OnboardingPage()
    object Permissions : OnboardingPage()
    object Credentials : OnboardingPage()
    object Login : OnboardingPage()
    object BackupConfig : OnboardingPage()

    companion object {
        val all = listOf(Welcome, Permissions, Credentials, Login, BackupConfig)
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CloudUpload,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionsPage(allGranted: Boolean, onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_permissions_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        if (allGranted) {
            Text("✓", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = onGrant) { Text(stringResource(R.string.action_grant)) }
        }
    }
}

@Composable
private fun CredentialsPage(
    saved: Boolean,
    error: String?,
    onSave: (apiId: String, apiHash: String) -> Unit
) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_credentials_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_credentials_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = apiId,
            onValueChange = { apiId = it.filter { ch -> ch.isDigit() } },
            label = { Text(stringResource(R.string.onboarding_credentials_apiid_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiHash,
            onValueChange = { apiHash = it },
            label = { Text(stringResource(R.string.onboarding_credentials_apihash_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation()
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (saved) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_credentials_saved),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(apiId, apiHash) },
            enabled = apiId.isNotBlank() && apiHash.isNotBlank()
        ) {
            Text(stringResource(R.string.onboarding_credentials_save))
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://my.telegram.org/apps"))
            runCatching { context.startActivity(intent) }
        }) {
            Text(stringResource(R.string.onboarding_credentials_get_link))
        }
    }
}

@Composable
private fun LoginPage(
    authState: TelegramAuthService.AuthState,
    onSubmitPhone: (String) -> Unit,
    onSubmitCode: (String) -> Unit,
    onSubmitPassword: (String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.onboarding_login_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        when (authState) {
            is TelegramAuthService.AuthState.Idle,
            is TelegramAuthService.AuthState.PhoneNumber -> {
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.login_phone_hint)) },
                    singleLine = true)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onSubmitPhone(phone) }) { Text(stringResource(R.string.login_request_code)) }
            }
            is TelegramAuthService.AuthState.CodeRequired -> {
                OutlinedTextField(value = code, onValueChange = { code = it },
                    label = { Text(stringResource(R.string.login_code_hint)) },
                    singleLine = true)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onSubmitCode(code) }) { Text(stringResource(R.string.login_verify_code)) }
            }
            is TelegramAuthService.AuthState.PasswordRequired -> {
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text(stringResource(R.string.login_password_hint)) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onSubmitPassword(password) }) { Text(stringResource(R.string.login_verify_password)) }
            }
            is TelegramAuthService.AuthState.Ready -> {
                Text("✓ Ready", color = MaterialTheme.colorScheme.primary)
            }
            is TelegramAuthService.AuthState.Failed -> {
                Text(authState.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BackupConfigPage(onEnableBackup: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.onboarding_backup_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_backup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onEnableBackup) { Text(stringResource(R.string.action_done)) }
    }
}
