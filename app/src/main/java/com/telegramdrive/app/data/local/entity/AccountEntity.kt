package com.telegramdrive.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Telegram account signed in on this device.
 * Each account has its own TDLib session DB and a saved-messages chat used as the
 * file root.
 */
@Entity(
    tableName = "accounts",
    indices = [Index("phoneNumber", unique = true), Index("isActive")]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val firstName: String,
    val lastName: String? = null,
    val username: String? = null,
    val tdlibUserId: Long,
    val savedMessagesChatId: Long,
    val premium: Boolean = false,
    val maxFileSizeBytes: Long = 2L * 1024 * 1024 * 1024, // 2 GB free; 4 GB premium
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val sessionDir: String
)
