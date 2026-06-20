package com.telegramdrive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent upload/download queue.
 * Driven by TransferService (foreground) for active items + WorkManager for
 * retries.
 */
@Entity(
    tableName = "transfer_queue",
    indices = [
        Index("accountId"),
        Index("fileId"),
        Index("status"),
        Index("direction")
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val fileId: Long?, // null for uploads not yet registered
    val direction: Direction,
    val localPath: String,
    val remoteName: String,
    val remoteFolderId: Long?,
    val sizeBytes: Long,
    val bytesTransferred: Long = 0,
    val status: Status = Status.QUEUED,
    val mimeType: String,
    val encrypted: Boolean = false,
    val error: String? = null,
    val attempts: Int = 0,
    val maxAttempts: Int = 5,
    val priority: Int = 0, // higher = sooner
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null
) {
    enum class Direction { UPLOAD, DOWNLOAD }
    enum class Status { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
}
