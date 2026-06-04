package com.cameronamer.telegramdrive.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoBackupService : Service() {
    private val TAG = "AutoBackupService"
    private val CHANNEL_ID = "AutoBackupChannel"
    private val FG_NOTIFICATION_ID = 100
    private val PREFS = "auto_backup_prefs"
    private val KEY_LAST_SCAN = "last_scan_ms"

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d(TAG, "Media changed: $uri — scheduling scan")
            // Debounce: MediaStore fires multiple change events per save.
            handler.postDelayed({ scanAndEnqueue() }, DEBOUNCE_MS)
        }
    }
    private val handler = Handler(Looper.getMainLooper())

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
        startInForeground()
        // Do an initial sweep to catch anything added while the service was stopped.
        scope.launch { scanAndEnqueue() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startInForeground() {
        createChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram Drive")
            .setContentText("Backing up new photos and videos…")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // Avoid ForegroundServiceStartNotAllowedException on Android 12+
        // when called from a broadcast — the service is started explicitly
        // from the Settings screen, so this is safe.
        startForeground(FG_NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Auto Backup",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    /**
     * Queries MediaStore for images and videos added since the last successful
     * scan, and enqueues each new file via [UploadForegroundService]. The
     * timestamp is persisted in SharedPreferences so we don't re-upload on
     * the next service restart.
     */
    private fun scanAndEnqueue() {
        val prefs: SharedPreferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastScanMs = prefs.getLong(KEY_LAST_SCAN, 0L)
        val nowMs = System.currentTimeMillis()

        val enqueued = scanUriSince(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, lastScanMs) +
            scanUriSince(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, lastScanMs)

        if (enqueued > 0) {
            Log.i(TAG, "Auto-backup enqueued $enqueued new file(s)")
        }
        prefs.edit().putLong(KEY_LAST_SCAN, nowMs).apply()
    }

    private fun scanUriSince(collection: Uri, sinceMs: Long): Int {
        var count = 0
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        // DATE_ADDED is in seconds; convert sinceMs to seconds for the comparison.
        val sinceSec = sinceMs / 1000
        val selection = "${MediaStore.MediaColumns.DATE_ADDED} >= ?"
        val args = arrayOf(sinceSec.toString())

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_ADDED} ASC"
            ) ?: return 0

            cursor.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: "media"
                    val itemUri = Uri.withAppendedPath(collection, id.toString())
                    try {
                        // We hand the content:// URI directly to the uploader —
                        // the Rust core reads via ContentResolver on Android.
                        UploadForegroundService.enqueue(this, itemUri.toString(), name)
                        count++
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to enqueue $name", t)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MediaStore scan failed for $collection", t)
        } finally {
            cursor?.close()
        }
        return count
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(mediaObserver)
        handler.removeCallbacksAndMessages(null)
        job.cancel()
        Log.d(TAG, "AutoBackupService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val DEBOUNCE_MS = 5_000L

        fun start(ctx: Context) {
            val intent = Intent(ctx, AutoBackupService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AutoBackupService::class.java))
        }
    }
}