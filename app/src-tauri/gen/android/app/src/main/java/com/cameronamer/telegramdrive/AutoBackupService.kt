package com.cameronamer.telegramdrive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.FileObserver
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

class AutoBackupService : Service() {
    private val CHANNEL_ID = "AutoBackupChannel"
    private val NOTIFICATION_ID = 2
    private val observers = mutableListOf<FileObserver>()

    // Declare the native JNI function that communicates with our Rust backend
    private external fun onFileDiscovered(filePath: String)

    init {
        try {
            System.loadLibrary("app_lib") // The name of our Rust cdylib
        } catch (e: Exception) {
            Log.e("AutoBackupService", "Failed to load Rust library", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram Drive Auto-Backup")
            .setContentText("Monitoring for new photos...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        
        var folders: Array<String>? = null
        if (intent != null) {
            folders = intent.getStringArrayExtra("folders")
        }
        
        startWatchingDirectories(folders)

        return START_STICKY
    }

    private fun startWatchingDirectories(foldersFromIntent: Array<String>?) {
        // Stop any existing observers
        observers.forEach { it.stopWatching() }
        observers.clear()

        val foldersToWatch = mutableListOf<File>()
        
        if (foldersFromIntent != null && foldersFromIntent.isNotEmpty()) {
            for (folderName in foldersFromIntent) {
                // If it looks like an absolute path, use it directly, otherwise assume it's relative to external storage
                if (folderName.startsWith("/")) {
                    foldersToWatch.add(File(folderName))
                } else {
                    foldersToWatch.add(File(Environment.getExternalStorageDirectory(), folderName))
                }
            }
        } else {
            val dcimCamera = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            foldersToWatch.add(dcimCamera)
            foldersToWatch.add(pictures)
        }

        for (folder in foldersToWatch) {
            if (folder.exists() && folder.isDirectory) {
                // Using FileObserver(File, int) constructor API 29+
                val observer = object : FileObserver(folder, CREATE) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != null) {
                            val fullPath = File(folder, path).absolutePath
                            Log.i("AutoBackupService", "New file detected: $fullPath")
                            // Pass the newly discovered file path back to the Rust backend
                            try {
                                onFileDiscovered(fullPath)
                            } catch (e: Exception) {
                                Log.e("AutoBackupService", "Failed to call Rust JNI", e)
                            }
                        }
                    }
                }
                observer.startWatching()
                observers.add(observer)
                Log.i("AutoBackupService", "Started watching folder: ${folder.absolutePath}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observers.forEach { it.stopWatching() }
        observers.clear()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Auto Backup Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}

