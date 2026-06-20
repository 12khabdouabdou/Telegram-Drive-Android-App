package com.telegramdrive.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.telegramdrive.app.R
import com.telegramdrive.app.data.local.dao.TransferDao
import com.telegramdrive.app.data.local.entity.TransferEntity
import com.telegramdrive.app.data.repository.FileRepository
import com.telegramdrive.app.data.repository.PreferencesRepository
import com.telegramdrive.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that drains the transfer queue. Posts a sticky
 * notification with current upload/download progress.
 *
 * The service is started whenever the queue has QUEUED items and stopped
 * when it empties out. See [TransferStarter] helper for queue-driven
 * start/stop.
 */
@AndroidEntryPoint
class TransferService : Service() {

    @Inject lateinit var transferDao: TransferDao
    @Inject lateinit var fileRepo: FileRepository
    @Inject lateinit var prefs: PreferencesRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    private var pumpJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildRunningNotification(0, 0))
        if (pumpJob?.isActive != true) {
            pumpJob = scope.launch { pumpQueue() }
        }
        return START_STICKY
    }

    private suspend fun pumpQueue() {
        while (true) {
            val next = transferDao.nextQueued(1).firstOrNull() ?: break
            try {
                transferDao.setStatus(next.id, TransferEntity.Status.RUNNING)
                when (next.direction) {
                    TransferEntity.Direction.UPLOAD -> {
                        val passphrase = if (next.encrypted) {
                            // TODO: reuse a per-device passphrase from secure prefs
                            null
                        } else null
                        fileRepo.runUpload(next, passphrase)
                    }
                    TransferEntity.Direction.DOWNLOAD -> {
                        // TODO: fileRepo.runDownload(next)
                    }
                }
            } catch (e: Exception) {
                transferDao.fail(next.id, TransferEntity.Status.FAILED, e.message)
            }
            updateNotification()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        // Reading from a Flow synchronously from a service is okay here —
        // transferDao.observeActive() is backed by Room, first() returns quickly.
        val active = kotlinx.coroutines.runBlocking {
            transferDao.observeActive().first()
        }
        val running = active.count { it.status == TransferEntity.Status.RUNNING }
        val queued = active.count { it.status == TransferEntity.Status.QUEUED }
        val n = buildRunningNotification(running, queued)
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    private fun buildRunningNotification(running: Int, queued: Int): android.app.Notification {
        val channelId = com.telegramdrive.app.TelegramDriveApp.CHANNEL_TRANSFER
        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_cloud_sync)
            .setContentTitle(getString(R.string.notif_transfer_running, queued))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        return builder.build()
    }

    override fun onDestroy() {
        pumpJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 4002
    }
}
