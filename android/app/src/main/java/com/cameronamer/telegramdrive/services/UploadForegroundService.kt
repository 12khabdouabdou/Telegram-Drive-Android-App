package com.cameronamer.telegramdrive.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cameronamer.telegramdrive.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.telegram_drive.uploadFile

class UploadForegroundService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val CHANNEL_ID = "UploadServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "UploadService"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filePath = intent?.getStringExtra("FILE_PATH")
        val fileName = intent?.getStringExtra("FILE_NAME") ?: "File"

        // Required for Android 14+ — promote to foreground *before* doing any
        // work, otherwise the OS kills us with a background-start exception.
        startForeground(
            NOTIFICATION_ID,
            createNotification("Uploading $fileName...", progress = 0, ongoing = true)
        )

        if (filePath.isNullOrBlank()) {
            Log.w(TAG, "No FILE_PATH extra provided; stopping service")
            postFinalNotification("Upload cancelled: no file path", fileName, success = false)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    uploadFile(filePath)
                }
                val success = !result.startsWith("Error") &&
                    !result.startsWith("Security Exception")
                if (success) {
                    postFinalNotification("Uploaded $fileName", fileName, success = true)
                } else {
                    Log.w(TAG, "Upload failed: $result")
                    postFinalNotification("Upload failed: $result", fileName, success = false)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Upload threw", e)
                postFinalNotification(
                    "Upload failed: ${e.message ?: e::class.java.simpleName}",
                    fileName,
                    success = false
                )
            } finally {
                stopForeground(true)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun postFinalNotification(text: String, fileName: String, success: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (success) "Telegram Drive" else "Upload failed")
            .setContentText(text)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_upload_done
                else android.R.drawable.stat_notify_error
            )
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
        manager.notify(NOTIFICATION_ID + 1, notif)
    }

    private fun createNotification(text: String, progress: Int, ongoing: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram Drive Upload")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, progress == 0)
            .setOngoing(ongoing)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Upload Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        /** Helper to enqueue a single-file upload from anywhere with a Context. */
        fun enqueue(ctx: Context, filePath: String, fileName: String? = null) {
            val intent = Intent(ctx, UploadForegroundService::class.java).apply {
                putExtra("FILE_PATH", filePath)
                putExtra("FILE_NAME", fileName ?: filePath.substringAfterLast('/'))
            }
            // Use the AndroidX ContextCompat extension that picks the right
            // startForegroundService vs startService for the API level.
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        }
    }
}
