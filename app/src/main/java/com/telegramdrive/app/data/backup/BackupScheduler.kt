package com.telegramdrive.app.data.backup

import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.telegramdrive.app.data.repository.PreferencesRepository
import com.telegramdrive.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates user preferences + backup-rule state into WorkManager schedules.
 *
 *   - Auto-backup ON  → AutoBackupWorker scheduled (15-min periodic) +
 *                       MediaBackupService started (foreground observer)
 *   - Auto-backup OFF → Worker cancelled, service stopped
 *
 * Also runs on app start (via TelegramDriveApp.onCreate) and on boot
 * (via BootReceiver) to re-establish the schedule.
 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: PreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val wm: WorkManager get() = WorkManager.getInstance(ctx)

    /**
     * Observe the auto-backup toggle and react to changes.
     * Called once from Application.onCreate.
     */
    fun bootstrap() {
        prefs.autoBackupEnabled
            .distinctUntilChanged()
            .onEach { enabled ->
                if (enabled) {
                    AutoBackupWorker.schedulePeriodic(wm)
                    startMediaObserverService()
                } else {
                    AutoBackupWorker.cancelPeriodic(wm)
                    stopMediaObserverService()
                }
            }
            .launchIn(scope)
    }

    /**
     * Re-arm everything from scratch. Called from Application.onCreate
     * and from BootReceiver after device boot.
     */
    fun rescheduleAll() {
        scope.launch {
            val enabled = prefs.autoBackupEnabled.first()
            if (enabled) {
                AutoBackupWorker.schedulePeriodic(wm)
                startMediaObserverService()
            } else {
                AutoBackupWorker.cancelPeriodic(wm)
                stopMediaObserverService()
            }
        }
    }

    private fun startMediaObserverService() {
        val intent = Intent(ctx, com.telegramdrive.app.service.MediaBackupService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    private fun stopMediaObserverService() {
        ctx.stopService(Intent(ctx, com.telegramdrive.app.service.MediaBackupService::class.java))
    }
}
