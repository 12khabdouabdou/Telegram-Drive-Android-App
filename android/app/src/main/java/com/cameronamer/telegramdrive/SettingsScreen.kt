package com.cameronamer.telegramdrive

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cameronamer.telegramdrive.services.AutoBackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.telegram_drive.logout

private object SettingsPrefs {
    private const val PREFS = "telegram_drive_settings"
    private const val KEY_NIGHT_MODE = "night_mode"
    private const val KEY_AUTO_BACKUP = "auto_backup"

    fun loadNightMode(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_NIGHT_MODE, MainActivity.MODE_FOLLOW_SYSTEM)

    fun saveNightMode(ctx: Context, mode: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_NIGHT_MODE, mode)
            .apply()
    }

    fun loadAutoBackup(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_BACKUP, false)

    fun saveAutoBackup(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_BACKUP, enabled)
            .apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var nightMode by remember { mutableStateOf(SettingsPrefs.loadNightMode(context)) }
    var autoBackupEnabled by remember { mutableStateOf(SettingsPrefs.loadAutoBackup(context)) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = nightMode == MainActivity.MODE_DARK,
                        onCheckedChange = { isChecked ->
                            nightMode = if (isChecked) MainActivity.MODE_DARK
                            else MainActivity.MODE_LIGHT
                            SettingsPrefs.saveNightMode(context, nightMode)
                            AppCompatDelegate.setDefaultNightMode(
                                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                                else AppCompatDelegate.MODE_NIGHT_NO
                            )
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        nightMode = MainActivity.MODE_FOLLOW_SYSTEM
                        SettingsPrefs.saveNightMode(context, nightMode)
                        AppCompatDelegate.setDefaultNightMode(
                            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        )
                    }) {
                        Text("Follow system")
                    }
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Auto Backup", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Automatically upload new photos and videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = {
                        autoBackupEnabled = it
                        SettingsPrefs.saveAutoBackup(context, it)
                        try {
                            if (it) AutoBackupService.start(context)
                            else AutoBackupService.stop(context)
                        } catch (t: Throwable) {
                            statusMessage =
                                "Failed to toggle auto-backup: ${t.message ?: t::class.java.simpleName}"
                        }
                    }
                )
            }

            Divider()

            statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (isLoggingOut) return@Button
                    isLoggingOut = true
                    statusMessage = null
                    coroutineScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { logout() }.getOrDefault(false)
                        }
                        isLoggingOut = false
                        if (ok) {
                            onLoggedOut()
                        } else {
                            statusMessage = "Logout failed — try again."
                        }
                    }
                },
                enabled = !isLoggingOut,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (isLoggingOut) "Logging out..." else "Logout")
            }
        }
    }
}