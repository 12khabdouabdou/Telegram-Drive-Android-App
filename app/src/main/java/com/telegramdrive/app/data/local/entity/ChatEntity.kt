package com.telegramdrive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Telegram chat cached locally so the in-app chat screen works offline.
 */
@Entity(
    tableName = "chats",
    indices = [
        Index("accountId"),
        Index("lastMessageDate")
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
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val telegramChatId: Long,
    val title: String,
    val type: ChatType,
    val avatarPath: String? = null,
    val lastMessageText: String? = null,
    val lastMessageDate: Long? = null,
    val unreadCount: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val draftText: String? = null
) {
    enum class ChatType { PRIVATE, GROUP, SUPERGROUP, CHANNEL, SELF, BOT }
}
