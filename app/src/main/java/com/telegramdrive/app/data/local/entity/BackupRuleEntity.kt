package com.telegramdrive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-configured backup rule.
 * Example: "Back up DCIM and Pictures on Wi-Fi + charging, daily at 3 AM, skip duplicates".
 */
@Entity(
    tableName = "backup_rules",
    indices = [
        Index("accountId"),
        Index("enabled")
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
data class BackupRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val name: String,
    val enabled: Boolean = true,
    /** Absolute MediaStore-relative folders, e.g. ["DCIM/Camera", "Pictures/Screenshots"] */
    val includedFolders: List<String>,
    /** Glob-style exclusions, e.g. ["*.tmp", "*/.thumbnails/*"] */
    val excludedFolders: List<String> = emptyList(),
    val mimeTypes: List<String> = listOf("image/*", "video/*"),
    val minSizeBytes: Long = 0,
    val onlyOriginalQuality: Boolean = true,
    val requireWifi: Boolean = true,
    val requireCharging: Boolean = false,
    val schedule: Schedule = Schedule.WATCH,
    val hourOfDay: Int? = null, // for SCHEDULED
    val minuteOfHour: Int? = null,
    val daysOfWeek: Set<Int> = emptySet(), // 1..7 (Mon..Sun)
    val stopAtQuotaBytes: Long? = null,
    val storageSaver: Boolean = false,
    val dedupEnabled: Boolean = true,
    val targetFolderId: Long? = null, // virtual folder on Telegram side
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val lastRunUploaded: Int = 0
) {
    enum class Schedule { WATCH, DAILY, WEEKLY, MANUAL }
}
