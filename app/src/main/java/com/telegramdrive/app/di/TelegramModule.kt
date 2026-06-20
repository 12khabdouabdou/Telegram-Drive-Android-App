package com.telegramdrive.app.di

import com.telegramdrive.app.data.remote.telegram.TelegramAuthService
import com.telegramdrive.app.data.remote.telegram.TelegramChatService
import com.telegramdrive.app.data.remote.telegram.TelegramFileService
import com.telegramdrive.app.data.remote.telegram.TdLibManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelegramModule {

    @Provides @Singleton
    fun provideTdLibManager(
        ctx: android.content.Context,
        accountDao: com.telegramdrive.app.data.local.dao.AccountDao,
        credentials: com.telegramdrive.app.data.security.SecureCredentialsStore,
        @ApplicationScope scope: kotlinx.coroutines.CoroutineScope,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): TdLibManager = TdLibManager(ctx, accountDao, credentials, scope, io)

    @Provides @Singleton
    fun provideTelegramAuthService(
        mgr: TdLibManager,
        dao: com.telegramdrive.app.data.local.dao.AccountDao,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): TelegramAuthService = TelegramAuthService(mgr, dao, io)

    @Provides @Singleton
    fun provideTelegramFileService(
        mgr: TdLibManager,
        crypto: com.telegramdrive.app.data.crypto.AesCrypto,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): TelegramFileService = TelegramFileService(mgr, crypto, io)

    @Provides @Singleton
    fun provideTelegramChatService(
        mgr: TdLibManager,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): TelegramChatService = TelegramChatService(mgr, io)
}
