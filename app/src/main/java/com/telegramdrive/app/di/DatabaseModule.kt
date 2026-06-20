package com.telegramdrive.app.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.telegramdrive.app.data.local.AppDatabase
import com.telegramdrive.app.data.local.SqlCipherKeyProvider
import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.data.local.dao.BackupRuleDao
import com.telegramdrive.app.data.local.dao.ChatDao
import com.telegramdrive.app.data.local.dao.FileDao
import com.telegramdrive.app.data.local.dao.FolderDao
import com.telegramdrive.app.data.local.dao.MessageDao
import com.telegramdrive.app.data.local.dao.TransferDao
import com.telegramdrive.app.data.local.dao.VaultEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context,
        sqlCipher: SqlCipherKeyProvider
    ): AppDatabase {
        return Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.DB_NAME)
            .openHelperFactory(sqlCipher.supportFactory())
            .fallbackToDestructiveMigrationOnDowngrade()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedDefaultBackupRules(db)
                }
            })
            .build()
    }

    /**
     * Create a sensible default backup rule on first launch — back up DCIM/Camera
     * and Pictures/Screenshots on Wi-Fi only, original quality, with dedup.
     * Users can edit or delete this rule.
     */
    private fun seedDefaultBackupRules(db: SupportSQLiteDatabase) {
        // Defer seeding until an account actually exists (we don't know accountId yet).
        // The BackupRepository will insert the default rule on first account creation.
    }

    @Provides fun accountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun folderDao(db: AppDatabase): FolderDao = db.folderDao()
    @Provides fun fileDao(db: AppDatabase): FileDao = db.fileDao()
    @Provides fun transferDao(db: AppDatabase): TransferDao = db.transferDao()
    @Provides fun backupRuleDao(db: AppDatabase): BackupRuleDao = db.backupRuleDao()
    @Provides fun vaultEntryDao(db: AppDatabase): VaultEntryDao = db.vaultEntryDao()
    @Provides fun chatDao(db: AppDatabase): ChatDao = db.chatDao()
    @Provides fun messageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides @Singleton
    fun provideSqlCipherKeyProvider(
        securePrefs: javax.inject.Provider<android.content.SharedPreferences>
    ): SqlCipherKeyProvider = SqlCipherKeyProvider(securePrefs)
}
