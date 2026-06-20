package com.telegramdrive.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.telegramdrive.app.data.backup.BackupScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-arms the WorkManager periodic backup schedule after device boot or
 * after the app is updated. Without this, scheduled backups would be lost
 * every time the user reboots.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: BackupScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                scheduler.rescheduleAll()
            }
        }
    }
}
