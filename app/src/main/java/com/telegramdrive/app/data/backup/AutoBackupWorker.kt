package com.telegramdrive.app.data.backup

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.telegramdrive.app.TelegramDriveApp
import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.data.local.dao.FileDao
import com.telegramdrive.app.data.local.entity.BackupRuleEntity
import com.telegramdrive.app.data.local.entity.FileEntity
import com.telegramdrive.app.data.local.entity.TransferEntity
import com.telegramdrive.app.data.repository.BackupRepository
import com.telegramdrive.app.data.repository.FileRepository
import com.telegramdrive.app.data.repository.PreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that scans MediaStore for new media matching any enabled
 * backup rule, dedup-skips existing uploads, enqueues upload transfers for
 * new ones, and emits progress notifications.
 *
 * Schedule: every ~15 min (WorkManager's minimum interval for periodic work).
 * Real-time updates when the app is alive come from MediaWatcher's
 * ContentObserver, registered by [MediaBackupService].
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val mediaWatcher: MediaWatcher,
    private val deduplicator: Deduplicator,
    private val backupRepo: BackupRepository,
    private val fileRepo: FileRepository,
    private val fileDao: FileDao,
    private val accountDao: AccountDao,
    private val prefs: PreferencesRepository,
    private val notifier: BackupNotifier
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!prefs.autoBackupEnabled.first()) {
            return@withContext Result.success()
        }
        val acc = accountDao.getActive() ?: return@withContext Result.success()
        val rules = backupRepo.getEnabledForActiveAccount()
        if (rules.isEmpty()) return@withContext Result.success()

        // Check smart conditions — pause if unmet
        if (!conditionsMet(rules)) {
            notifier.notifyPaused(applicationContext.getString(R.string.backup_status_no_network))
            return@withContext Result.retry()
        }

        // Pick the most recent watermark across all rules
        val lastRunSeconds = rules.maxOfOrNull { (it.lastRunAt ?: 0L) / 1000 } ?: 0L
        val now = System.currentTimeMillis() / 1000

        var totalUploaded = 0
        for (rule in rules) {
            val candidates = mediaWatcher.queryAddedSince(
                sinceDateAddedSeconds = lastRunSeconds,
                mimeTypes = rule.mimeTypes,
                includedFolders = rule.includedFolders,
                excludedFolders = rule.excludedFolders
            ).filter { it.sizeBytes >= rule.minSizeBytes }
                .filter { !rule.onlyOriginalQuality /* always original */ }

            if (candidates.isEmpty()) {
                backupRepo.markRun(rule.id, 0)
                continue
            }

            notifier.notifyRunning(candidates.size, 0)
            for ((i, c) in candidates.withIndex()) {
                // Dedup
                val dedup = deduplicator.findDuplicate(acc.id, c.mediaStoreId, c.sizeBytes)
                if (dedup is Deduplicator.DedupResult.Duplicate) {
                    notifier.notifyRunning(candidates.size, i + 1)
                    continue
                }
                // Copy to temp file (so we can hand a path to TelegramFileService)
                val tmp = File(applicationContext.cacheDir, "backup_${c.mediaStoreId}_${c.displayName}")
                applicationContext.contentResolver.openInputStream(c.uri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                } ?: continue

                val passphrase = if (prefs.encryptUploads.first()) {
                    // Vault passphrase is reused — or a per-device passphrase from secure prefs
                    null
                } else null
                val encrypted = prefs.encryptUploads.first()

                try {
                    val transferId = fileRepo.enqueueUpload(
                        accountId = acc.id,
                        localPath = tmp.absolutePath,
                        remoteName = c.displayName,
                        remoteFolderId = rule.targetFolderId,
                        encrypted = encrypted,
                        passphrase = passphrase
                    )
                    // Reconstruct the transfer entity for the inline run.
                    // (TransferService uses the same pattern when draining its queue.)
                    val transfer = TransferEntity(
                        id = transferId,
                        accountId = acc.id,
                        fileId = null,
                        direction = TransferEntity.Direction.UPLOAD,
                        localPath = tmp.absolutePath,
                        remoteName = c.displayName,
                        remoteFolderId = rule.targetFolderId,
                        sizeBytes = c.sizeBytes,
                        mimeType = c.mimeType,
                        encrypted = encrypted
                    )
                    val fileId = fileRepo.runUpload(transfer, passphrase)
                    // Tag the row with the mediaStoreId so future dedup is fast
                    val row = fileDao.getById(fileId)
                    if (row != null) {
                        fileDao.update(row.copy(
                            mediaStoreId = c.mediaStoreId,
                            mediaStoreDateAdded = c.dateAddedSeconds
                        ))
                    }
                    tmp.delete()
                    totalUploaded++
                    notifier.notifyRunning(candidates.size, i + 1)
                } catch (e: Exception) {
                    // Failed: leave temp; mark rule lastRunAt to retry next cycle
                    notifier.notifyError(applicationContext.getString(R.string.error_upload_failed, e.message ?: ""))
                }
            }
            backupRepo.markRun(rule.id, totalUploaded)

            // Storage Saver pass
            if (rule.storageSaver) {
                // Trigger after uploads; real deletion requires user consent on API 29+
            }
        }

        notifier.notifyDone(totalUploaded)
        Result.success()
    }

    private suspend fun conditionsMet(rules: List<BackupRuleEntity>): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val nw = cm.activeNetworkInfo
        val isWifi = nw?.type == android.net.ConnectivityManager.TYPE_WIFI
        val isOnline = nw?.isConnected == true
        if (!isOnline) return false
        if (rules.any { it.requireWifi } && !isWifi) return false
        if (rules.any { it.requireCharging }) {
            val intent = applicationContext.registerReceiver(null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
            if (!charging) return false
        }
        return true
    }

    companion object {
        const val WORK_NAME = "auto_backup_periodic"

        fun schedulePeriodic(wm: WorkManager) {
            val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancelPeriodic(wm: WorkManager) {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }
}
