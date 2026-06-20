package com.telegramdrive.app.di

import android.content.Context
import com.telegramdrive.app.data.backup.AutoBackupWorker
import com.telegramdrive.app.data.backup.BackupNotifier
import com.telegramdrive.app.data.backup.BackupScheduler
import com.telegramdrive.app.data.backup.Deduplicator
import com.telegramdrive.app.data.backup.MediaWatcher
import com.telegramdrive.app.data.backup.StorageSaver
import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.data.local.dao.FileDao
import com.telegramdrive.app.data.repository.BackupRepository
import com.telegramdrive.app.data.repository.FileRepository
import com.telegramdrive.app.data.repository.PreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides @Singleton
    fun mediaWatcher(
        @ApplicationContext ctx: Context,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): MediaWatcher = MediaWatcher(ctx, io)

    @Provides @Singleton
    fun deduplicator(
        fileDao: FileDao,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): Deduplicator = Deduplicator(fileDao, io)

    @Provides @Singleton
    fun storageSaver(
        @ApplicationContext ctx: Context,
        fileDao: FileDao,
        accountDao: AccountDao,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): StorageSaver = StorageSaver(ctx, fileDao, accountDao, io)

    @Provides @Singleton
    fun notifier(@ApplicationContext ctx: Context): BackupNotifier = BackupNotifier(ctx)

    @Provides @Singleton
    fun scheduler(
        @ApplicationContext ctx: Context,
        prefs: PreferencesRepository,
        @ApplicationScope scope: CoroutineScope
    ): BackupScheduler = BackupScheduler(ctx, prefs, scope)
}
