package com.telegramdrive.app.data.backup

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.telegramdrive.app.MainActivity
import com.telegramdrive.app.R
import com.telegramdrive.app.TelegramDriveApp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts backup-related notifications on the dedicated backup channel.
 * Also exposes the [ForegroundInfo] for WorkManager foreground workers.
 */
@Singleton
class BackupNotifier @Inject constructor(private val ctx: Context) {

    private fun builder(): NotificationCompat.Builder =
        NotificationCompat.Builder(ctx, TelegramDriveApp.CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_cloud_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    fun notifyRunning(total: Int, current: Int) {
        val max = total.coerceAtLeast(1)
        val progress = current.coerceIn(0, max)
        val n = builder()
            .setContentTitle(ctx.getString(R.string.notif_backup_running))
            .setContentText("$progress / $max")
            .setProgress(max, progress, progress == 0)
            .build()
        notify(NOTIF_ID, n)
    }

    fun notifyPaused(reason: String) {
        val n = builder()
            .setContentTitle(ctx.getString(R.string.backup_status_paused))
            .setContentText(reason)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .build()
        notify(NOTIF_ID, n)
    }

    fun notifyDone(uploaded: Int) {
        val n = NotificationCompat.Builder(ctx, TelegramDriveApp.CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_cloud_sync)
            .setContentTitle(ctx.getString(R.string.notif_backup_done, uploaded))
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
        notify(NOTIF_ID, n)
    }

    fun notifyError(message: String) {
        val n = NotificationCompat.Builder(ctx, TelegramDriveApp.CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_cloud_sync)
            .setContentTitle(ctx.getString(R.string.error_upload_failed, ""))
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        notify(NOTIF_ID + 1, n)
    }

    fun foregroundInfo(total: Int, current: Int): ForegroundInfo {
        notifyRunning(total, current)
        return ForegroundInfo(NOTIF_ID, builder()
            .setContentTitle(ctx.getString(R.string.notif_backup_running))
            .setProgress(total.coerceAtLeast(1), current, current == 0)
            .build())
    }

    private fun notify(id: Int, n: Notification) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.notify(id, n)
    }

    companion object {
        const val NOTIF_ID = 4001
    }
}
