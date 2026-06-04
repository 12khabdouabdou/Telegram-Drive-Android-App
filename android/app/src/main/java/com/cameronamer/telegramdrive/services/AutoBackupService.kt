package com.cameronamer.telegramdrive.services

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

class AutoBackupService : Service() {
    private val TAG = "AutoBackupService"
    
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d(TAG, "Media changed: $uri")
            // TODO: Query MediaStore for the new item using uri
            // TODO: Enqueue upload via UniFFI or start UploadForegroundService
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AutoBackupService created, registering ContentObserver")
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Can be started as a foreground service if persistent backup tracking is needed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(mediaObserver)
        Log.d(TAG, "AutoBackupService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}