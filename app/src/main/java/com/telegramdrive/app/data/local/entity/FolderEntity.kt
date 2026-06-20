package com.telegramdrive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A folder in the Telegram-Drive virtual filesystem.
 *
 * Folders are virtual: we store folder rows locally and post messages with
 * a structured caption `{"kind":"folder","name":"…"}` into the account's
 * Saved Messages chat to persist them across devices.
 */
@Entity(
    tableName = "folders",
    indices = [
        Index("accountId"),
        Index("parentFolderId"),
        Index("accountId", "parentFolderId", "name", unique = true)
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
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val parentFolderId: Long?, // null = root
    val name: String,
    val telegramMessageId: Long? = null, // the folder marker message
    val color: Int = 0xFF2AABEE.toInt(),
    val icon: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
