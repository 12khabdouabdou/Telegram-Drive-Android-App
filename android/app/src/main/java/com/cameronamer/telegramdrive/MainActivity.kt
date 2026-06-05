package com.cameronamer.telegramdrive

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            Log.d("Permissions", "${it.key} granted: ${it.value}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        handleIntent(intent)

        var coreError: String? = null
        try {
            System.loadLibrary("telegram_drive_core")
            uniffi.telegram_drive.initCore(filesDir.absolutePath)
        } catch (e: Throwable) {
            coreError = "Core initialization failed: ${e.message}"
            Log.e("MainActivity", coreError, e)
        }

        requestRuntimePermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (coreError != null) {
                        ErrorScreen(message = coreError)
                    } else {
                        var isAuthenticated by rememberSaveable { mutableStateOf(false) }

                        when {
                            !isAuthenticated -> {
                                AuthScreen(onLoginComplete = { isAuthenticated = true })
                            }
                            else -> {
                                MainScreen(onLoggedOut = { isAuthenticated = false })
                            }
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
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
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
                    Log.d("ShareIntent", "Received single file: ")
                    enqueueUpload(uri, "shared_file")
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (uris != null) {
                    Log.d("ShareIntent", "Received multiple files: ${uris.size}")
                    uris.forEachIndexed { idx, u -> enqueueUpload(u, "shared_file_") }
                }
            }
        }
    }

    private fun enqueueUpload(uri: Uri, fileName: String) {
        runCatching {
            com.cameronamer.telegramdrive.services.UploadForegroundService
                .enqueue(this, uri.toString(), fileName)
        }.onFailure { t ->
            Log.e("ShareIntent", "Failed to enqueue upload for ", t)
        }
    }
}
