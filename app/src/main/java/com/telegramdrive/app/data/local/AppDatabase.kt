package com.telegramdrive.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.data.local.dao.BackupRuleDao
import com.telegramdrive.app.data.local.dao.ChatDao
import com.telegramdrive.app.data.local.dao.FileDao
import com.telegramdrive.app.data.local.dao.FolderDao
import com.telegramdrive.app.data.local.dao.MessageDao
import com.telegramdrive.app.data.local.dao.TransferDao
import com.telegramdrive.app.data.local.dao.VaultEntryDao
import com.telegramdrive.app.data.local.entity.AccountEntity
import com.telegramdrive.app.data.local.entity.BackupRuleEntity
import com.telegramdrive.app.data.local.entity.ChatEntity
import com.telegramdrive.app.data.local.entity.FileEntity
import com.telegramdrive.app.data.local.entity.FolderEntity
import com.telegramdrive.app.data.local.entity.MessageEntity
import com.telegramdrive.app.data.local.entity.TransferEntity
import com.telegramdrive.app.data.local.entity.VaultEntryEntity

class Converters {
    @TypeConverter fun fromStringList(v: List<String>?): String = v?.joinToString("\u0001") ?: ""
    @TypeConverter fun toStringList(v: String?): List<String> =
        v?.takeIf { it.isNotEmpty() }?.split("\u0001") ?: emptyList()

    @TypeConverter fun fromLongList(v: List<Long>?): String = v?.joinToString(",") ?: ""
    @TypeConverter fun toLongList(v: String?): List<Long> =
        v?.takeIf { it.isNotEmpty() }?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    @TypeConverter fun fromIntSet(v: Set<Int>?): String = v?.joinToString(",") ?: ""
    @TypeConverter fun toIntSet(v: String?): Set<Int> =
        v?.takeIf { it.isNotEmpty() }?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    @TypeConverter fun fromSchedule(v: BackupRuleEntity.Schedule?): String? = v?.name
    @TypeConverter fun toSchedule(v: String?): BackupRuleEntity.Schedule? =
        v?.let { runCatching { BackupRuleEntity.Schedule.valueOf(it) }.getOrNull() }

    @TypeConverter fun fromVaultType(v: VaultEntryEntity.VaultType?): String? = v?.name
    @TypeConverter fun toVaultType(v: String?): VaultEntryEntity.VaultType? =
        v?.let { runCatching { VaultEntryEntity.VaultType.valueOf(it) }.getOrNull() }

    @TypeConverter fun fromChatType(v: ChatEntity.ChatType?): String? = v?.name
    @TypeConverter fun toChatType(v: String?): ChatEntity.ChatType? =
        v?.let { runCatching { ChatEntity.ChatType.valueOf(it) }.getOrNull() }

    @TypeConverter fun fromTransferDirection(v: TransferEntity.Direction?): String? = v?.name
    @TypeConverter fun toTransferDirection(v: String?): TransferEntity.Direction? =
        v?.let { runCatching { TransferEntity.Direction.valueOf(it) }.getOrNull() }

    @TypeConverter fun fromTransferStatus(v: TransferEntity.Status?): String? = v?.name
    @TypeConverter fun toTransferStatus(v: String?): TransferEntity.Status? =
        v?.let { runCatching { TransferEntity.Status.valueOf(it) }.getOrNull() }
}

@Database(
    entities = [
        AccountEntity::class,
        FolderEntity::class,
        FileEntity::class,
        TransferEntity::class,
        BackupRuleEntity::class,
        VaultEntryEntity::class,
        ChatEntity::class,
        MessageEntity::class
    ],
    version = AppDatabase.DB_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun folderDao(): FolderDao
    abstract fun fileDao(): FileDao
    abstract fun transferDao(): TransferDao
    abstract fun backupRuleDao(): BackupRuleDao
    abstract fun vaultEntryDao(): VaultEntryDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao

    companion object {
        const val DB_NAME = "telegramdrive.db"
        const val DB_VERSION = 1
    }
}
