package com.telegramdrive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Encrypted vault item (password / note / card).
 *
 * The ciphertext field holds the AES-256-GCM ciphertext of the structured
 * payload (see VaultEntryPayload). The key is derived from the user's
 * passphrase via Argon2id (see VaultCrypto). The local DB is *also* encrypted
 * at rest via SQLCipher, so this is double protection.
 */
@Entity(
    tableName = "vault_entries",
    indices = [
        Index("accountId"),
        Index("type"),
        Index("title")
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
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val type: VaultType,
    val title: String,
    /** AES-256-GCM ciphertext (Base64) of the JSON payload */
    val ciphertext: String,
    /** Initialization vector (Base64, 12 bytes) */
    val iv: String,
    /** Auth tag is appended to ciphertext (GCM mode) */
    val username: String? = null, // optional, plaintext for fast search
    val url: String? = null,
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long? = null,
    /** Telegram sync: message id of the encrypted blob in Saved Messages */
    val telegramMessageId: Long? = null
) {
    enum class VaultType { PASSWORD, NOTE, CARD, TOTP }
}
