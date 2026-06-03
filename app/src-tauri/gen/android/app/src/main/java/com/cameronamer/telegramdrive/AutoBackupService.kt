package com.cameronamer.telegramdrive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat

class AutoBackupService : Service() {
    private val CHANNEL_ID = "AutoBackupChannel"
    private val NOTIFICATION_ID = 2
    private var mediaObserver: ContentObserver? = null
    private var targetFolders: Array<String>? = null

    companion object {
        @JvmStatic
        fun updateNotificationStats(context: android.content.Context, queued: Int, completed: Int) {
            val notification = NotificationCompat.Builder(context, "AutoBackupChannel")
                .setContentTitle("Telegram Drive Auto-Backup")
                .setContentText("Queued: $queued | Backed up: $completed")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(2, notification)
        }
    }

    // Declare the native JNI function that communicates with our Rust backend
    private external fun onFileDiscovered(fileUri: String)

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
        targetFolders = intent?.getStringArrayExtra("folders")

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram Drive Auto-Backup")
            .setContentText("Monitoring for new photos...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        
        startWatchingMedia()

        return START_STICKY
    }

    private fun checkAndNotifyDiscovered(uri: Uri) {
        val folders = targetFolders
        if (folders.isNullOrEmpty()) {
            onFileDiscovered(uri.toString())
            return
        }

        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val path = cursor.getString(dataIndex)
                    if (path != null) {
                        for (folder in folders) {
                            if (path.contains(folder)) {
                                onFileDiscovered(uri.toString())
                                return
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AutoBackupService", "Error checking path for $uri", e)
        }
    }

    private fun startWatchingMedia() {
        // Stop any existing observers
        if (mediaObserver != null) {
            contentResolver.unregisterContentObserver(mediaObserver!!)
            mediaObserver = null
        }

        mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri != null && uri.lastPathSegment?.toLongOrNull() != null) {
                    Log.i("AutoBackupService", "New media directly detected from URI: $uri")
                    try {
                        checkAndNotifyDiscovered(uri)
                    } catch (e: Exception) {
                        Log.e("AutoBackupService", "Failed to call Rust JNI", e)
                    }
                } else {
                    handleMediaChange()
                }
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver!!
            )
            Log.i("AutoBackupService", "Started watching MediaStore")
        } catch (e: Exception) {
            Log.e("AutoBackupService", "Failed to register ContentObserver", e)
        }
    }
    
    private fun handleMediaChange() {
        try {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            
            // Query latest image
            var latestUri: Uri? = null
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val id = cursor.getLong(idColumn)
                    latestUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                }
            }
            
            // Query latest video and compare if necessary, or just query Files depending on needs.
            // For simplicity and speed, just check both and see which is newer, or rely on the trigger.
            // Since this runs on any change, let's just grab the absolute newest from Files.
            
            val filesUri = MediaStore.Files.getContentUri("external")
            val filesProjection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            val filesSelection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val filesSelectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )
            
            contentResolver.query(
                filesUri,
                filesProjection,
                filesSelection,
                filesSelectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val id = cursor.getLong(idColumn)
                    val type = cursor.getInt(typeColumn)
                    
                    val contentUri = if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                        Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    } else {
                        Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                    }
                    
                    Log.i("AutoBackupService", "New media detected: $contentUri")
                    checkAndNotifyDiscovered(contentUri)
                }
            }
        } catch (e: Exception) {
            Log.e("AutoBackupService", "Error handling media change", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaObserver != null) {
            contentResolver.unregisterContentObserver(mediaObserver!!)
            mediaObserver = null
        }
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

