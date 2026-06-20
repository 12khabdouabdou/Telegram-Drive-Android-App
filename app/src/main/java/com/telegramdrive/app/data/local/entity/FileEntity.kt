package com.telegramdrive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A file uploaded to (or downloaded from) Telegram.
 *
 * Files larger than Telegram's single-message limit are split into chunks; each
 * chunk is one Telegram message in the account's Saved Messages chat. The
 * `caption` of the first message stores a JSON header with chunk list + AES
 * metadata if the file is encrypted.
 *
 * Note: the local row is the metadata only. The actual file bytes live on
 * Telegram's servers (downloaded to local cache on demand).
 */
@Entity(
    tableName = "files",
    indices = [
        Index("accountId"),
        Index("folderId"),
        Index("sha256", unique = false),
        Index("mediaStoreId"),
        Index("mimeType"),
        Index("favorite"),
        Index("accountId", "folderId", "name", unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val folderId: Long?, // null = root
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256: String?, // dedup key — null while hashing in progress
    val md5: String? = null,
    val encrypted: Boolean = false,
    val encryptionKeyIv: String? = null, // base64 IV (key derived from passphrase)
    val chunkCount: Int = 1,
    val chunkMessageIds: List<Long> = emptyList(),
    val headerMessageId: Long? = null, // first chunk message
    val mediaStoreId: Long? = null, // link back to MediaStore for backup dedup
    val mediaStoreDateAdded: Long? = null,
    val localPath: String? = null, // local cache path if downloaded
    val localThumbnailPath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long? = null
)
