package com.cameronamer.telegramdrive

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            Log.d("Permissions", "${it.key} granted: ${it.value}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the user's saved dark-mode preference (or follow system) BEFORE
        // super.onCreate so the right values-night/theme is resolved for this
        // activity instance.
        applyStoredNightMode()

        super.onCreate(savedInstanceState)

        handleIntent(intent)

        // Ensure the UniFFI core library is loaded. Catch Throwable (not just
        // UnsatisfiedLinkError) so that any native init failure is logged but
        // does not crash the app — the UI will then show the error state.
        try {
            System.loadLibrary("telegram_drive_core")
            uniffi.telegram_drive.initCore(filesDir.absolutePath)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Core initialization failed: ${e.message}", e)
        }

        requestRuntimePermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // rememberSaveable so auth state survives rotation / dark-mode
                    // toggle / low-memory recreation (vs. plain remember).
                    var isAuthenticated by rememberSaveable { mutableStateOf(false) }
                    var showSettings by rememberSaveable { mutableStateOf(false) }

                    when {
                        !isAuthenticated -> {
                            AuthScreen(onLoginComplete = { isAuthenticated = true })
                        }
                        showSettings -> {
                            SettingsScreen(
                                onBack = { showSettings = false },
                                onLoggedOut = {
                                    showSettings = false
                                    isAuthenticated = false
                                }
                            )
                        }
                        else -> {
                            FileListScreen(onOpenSettings = { showSettings = true })
                        }
                    }
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    Log.d("ShareIntent", "Received single file: $uri")
                    enqueueUpload(uri, "shared_file")
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (uris != null) {
                    Log.d("ShareIntent", "Received multiple files: ${uris.size}")
                    uris.forEachIndexed { idx, u -> enqueueUpload(u, "shared_file_$idx") }
                }
            }
        }
    }

    private fun enqueueUpload(uri: Uri, fileName: String) {
        runCatching {
            com.cameronamer.telegramdrive.services.UploadForegroundService
                .enqueue(this, uri.toString(), fileName)
        }.onFailure { t ->
            Log.e("ShareIntent", "Failed to enqueue upload for $uri", t)
        }
    }

    /**
     * Read the user's dark-mode preference from SharedPreferences and apply it
     * via [AppCompatDelegate]. Defaults to "follow system" when the user has
     * not made an explicit choice.
     */
    private fun applyStoredNightMode() {
        val prefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
        val mode = when (prefs.getInt(KEY_NIGHT_MODE, MODE_FOLLOW_SYSTEM)) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // No-op: the manifest declares android:configChanges="uiMode" so the
        // framework updates the theme automatically. We just want to make sure
        // the active rememberSaveable state is preserved across the change,
        // which Compose handles on its own.
    }

    companion object {
        const val SETTINGS_PREFS = "telegram_drive_settings"
        const val KEY_NIGHT_MODE = "night_mode"
        const val MODE_FOLLOW_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
    }
}