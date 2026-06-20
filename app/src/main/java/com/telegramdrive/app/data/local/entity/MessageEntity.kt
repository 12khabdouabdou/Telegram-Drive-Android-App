package com.telegramdrive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index("accountId"),
        Index("chatLocalId"),
        Index("telegramMessageId", unique = true),
        Index("date")
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
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val chatLocalId: Long,
    val telegramMessageId: Long,
    val senderId: Long,
    val senderName: String,
    val text: String? = null,
    val date: Long,
    val isOutgoing: Boolean,
    val edited: Boolean = false,
    val replyToMessageId: Long? = null,
    val attachmentFileId: Long? = null, // link to files table if message has an attachment
    val attachmentMime: String? = null,
    val attachmentSize: Long? = null,
    val read: Boolean = true
)
