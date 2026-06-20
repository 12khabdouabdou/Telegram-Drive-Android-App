package com.telegramdrive.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.telegramdrive.app.R
import com.telegramdrive.app.TelegramDriveApp
import com.telegramdrive.app.data.backup.BackupNotifier
import com.telegramdrive.app.data.backup.Deduplicator
import com.telegramdrive.app.data.backup.MediaWatcher
import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.data.local.dao.FileDao
import com.telegramdrive.app.data.repository.FileRepository
import com.telegramdrive.app.data.repository.PreferencesRepository
import com.telegramdrive.app.di.ApplicationScope
import com.telegramdrive.app.di.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that maintains a real-time [MediaWatcher] observer for
 * new photos/videos and uploads them immediately when they match an active
 * backup rule. Mirrors Google Photos' "as you take them" backup feel.
 *
 * Backed by AutoBackupWorker for the periodic catch-up (every 15 min).
 */
@AndroidEntryPoint
class MediaBackupService : Service() {

    @Inject lateinit var mediaWatcher: MediaWatcher
    @Inject lateinit var deduplicator: Deduplicator
    @Inject lateinit var accountDao: AccountDao
    @Inject lateinit var fileDao: FileDao
    @Inject lateinit var fileRepo: FileRepository
    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var notifier: BackupNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @IoDispatcher lateinit var io: CoroutineDispatcher

    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildIdleNotification())
        watchJob = scope.launch(io) {
            // Only run when auto-backup is enabled
            prefs.autoBackupEnabled.collect { enabled ->
                if (enabled) {
                    mediaWatcher.observeNewMedia().collect { item ->
                        handleNewItem(item)
                    }
                }
            }
        }
    }

    private suspend fun handleNewItem(item: MediaWatcher.PendingBackup) {
        val acc = accountDao.getActive() ?: return
        // Dedup — even if we don't have rules locally, skip what's already uploaded
        val dedup = deduplicator.findDuplicate(acc.id, item.mediaStoreId, item.sizeBytes)
        if (dedup is Deduplicator.DedupResult.Duplicate) return

        // Copy to temp and upload
        val tmp = java.io.File(cacheDir, "rt_backup_${item.mediaStoreId}_${item.displayName}")
        contentResolver.openInputStream(item.uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } ?: return
        try {
            val transferId = fileRepo.enqueueUpload(
                accountId = acc.id,
                localPath = tmp.absolutePath,
                remoteName = item.displayName,
                remoteFolderId = null,
                encrypted = prefs.encryptUploads.first(),
                passphrase = null
            )
            // Run inline (we're already in the right context)
            val transfer = com.telegramdrive.app.data.local.entity.TransferEntity(
                id = transferId,
                accountId = acc.id,
                fileId = null,
                direction = com.telegramdrive.app.data.local.entity.TransferEntity.Direction.UPLOAD,
                localPath = tmp.absolutePath,
                remoteName = item.displayName,
                remoteFolderId = null,
                sizeBytes = item.sizeBytes,
                mimeType = item.mimeType,
                encrypted = prefs.encryptUploads.first()
            )
            val fileId = fileRepo.runUpload(transfer, null)
            val row = fileDao.getById(fileId)
            if (row != null) {
                fileDao.update(row.copy(
                    mediaStoreId = item.mediaStoreId,
                    mediaStoreDateAdded = item.dateAddedSeconds
                ))
            }
            tmp.delete()
        } catch (e: Exception) {
            notifier.notifyError(e.message ?: "")
        }
    }

    override fun onDestroy() {
        watchJob?.cancel()
        super.onDestroy()
    }

    private fun buildIdleNotification(): android.app.Notification =
        NotificationCompat.Builder(this, TelegramDriveApp.CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_cloud_sync)
            .setContentTitle(getString(R.string.backup_status_idle))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object { const val NOTIF_ID = 4003 }
}
