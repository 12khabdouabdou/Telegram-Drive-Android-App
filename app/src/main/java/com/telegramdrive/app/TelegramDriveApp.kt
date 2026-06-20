package com.telegramdrive.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.telegramdrive.app.data.local.AppDatabase
import com.telegramdrive.app.data.remote.telegram.TdLibManager
import com.telegramdrive.app.data.backup.BackupScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Wires up:
 *  - Hilt dependency injection
 *  - WorkManager with Hilt-aware WorkerFactory
 *  - Notification channels (transfers / backup / vault / general)
 *  - TDLib initialization (per-account)
 *  - WorkManager-based backup scheduler (rescheduled on app start)
 */
@HiltAndroidApp
class TelegramDriveApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var tdLibManager: TdLibManager
    @Inject lateinit var backupScheduler: BackupScheduler
    @Inject lateinit var database: AppDatabase

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        // Initialise TDLib JNI bridge eagerly so first login is fast
        tdLibManager.bootstrap()
        // Re-arm scheduled backup jobs (they survive reboot via BootReceiver too)
        backupScheduler.rescheduleAll()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        listOf(
            Triple(CHANNEL_TRANSFER, getString(R.string.channel_transfer_name), getString(R.string.channel_transfer_desc)),
            Triple(CHANNEL_BACKUP, getString(R.string.channel_backup_name), getString(R.string.channel_backup_desc)),
            Triple(CHANNEL_VAULT, getString(R.string.channel_vault_name), getString(R.string.channel_vault_desc)),
            Triple(CHANNEL_GENERAL, getString(R.string.channel_general_name), getString(R.string.channel_general_desc))
        ).forEach { (id, name, desc) ->
            val ch = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = desc
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_TRANSFER = "transfers"
        const val CHANNEL_BACKUP = "backup"
        const val CHANNEL_VAULT = "vault"
        const val CHANNEL_GENERAL = "general"

        @Volatile lateinit var instance: TelegramDriveApp
            private set
    }
}
