package com.telegramdrive.app.di

import com.telegramdrive.app.data.repository.AccountRepository
import com.telegramdrive.app.data.repository.BackupRepository
import com.telegramdrive.app.data.repository.ChatRepository
import com.telegramdrive.app.data.repository.FileRepository
import com.telegramdrive.app.data.repository.PreferencesRepository
import com.telegramdrive.app.data.repository.VaultRepository
import com.telegramdrive.app.data.crypto.AesCrypto
import com.telegramdrive.app.data.crypto.VaultCrypto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Provides @Singleton
    fun aesCrypto(): AesCrypto = AesCrypto()

    @Provides @Singleton
    fun vaultCrypto(
        prefs: android.content.SharedPreferences,
        aes: AesCrypto
    ): VaultCrypto = VaultCrypto(prefs, aes)

    @Provides @Singleton
    fun accountRepository(
        dao: com.telegramdrive.app.data.local.dao.AccountDao,
        mgr: com.telegramdrive.app.data.remote.telegram.TdLibManager,
        auth: com.telegramdrive.app.data.remote.telegram.TelegramAuthService,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): AccountRepository = AccountRepository(dao, mgr, auth, io)

    @Provides @Singleton
    fun fileRepository(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: android.content.Context,
        fileDao: com.telegramdrive.app.data.local.dao.FileDao,
        folderDao: com.telegramdrive.app.data.local.dao.FolderDao,
        transferDao: com.telegramdrive.app.data.local.dao.TransferDao,
        tg: com.telegramdrive.app.data.remote.telegram.TelegramFileService,
        aes: AesCrypto,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): FileRepository = FileRepository(ctx, fileDao, folderDao, transferDao, tg, aes, io)

    @Provides @Singleton
    fun backupRepository(
        dao: com.telegramdrive.app.data.local.dao.BackupRuleDao,
        accountDao: com.telegramdrive.app.data.local.dao.AccountDao,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): BackupRepository = BackupRepository(dao, accountDao, io)

    @Provides @Singleton
    fun vaultRepository(
        dao: com.telegramdrive.app.data.local.dao.VaultEntryDao,
        accountDao: com.telegramdrive.app.data.local.dao.AccountDao,
        crypto: VaultCrypto,
        json: Json,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): VaultRepository = VaultRepository(dao, accountDao, crypto, json, io)

    @Provides @Singleton
    fun chatRepository(
        chatDao: com.telegramdrive.app.data.local.dao.ChatDao,
        messageDao: com.telegramdrive.app.data.local.dao.MessageDao,
        accountDao: com.telegramdrive.app.data.local.dao.AccountDao,
        tg: com.telegramdrive.app.data.remote.telegram.TelegramChatService,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): ChatRepository = ChatRepository(chatDao, messageDao, accountDao, tg, io)

    @Provides @Singleton
    fun preferencesRepository(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: android.content.Context,
        @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher
    ): PreferencesRepository = PreferencesRepository(ctx, io)
}
