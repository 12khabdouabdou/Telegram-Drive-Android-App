package com.telegramdrive.app.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier annotation class IoDispatcher
@Qualifier annotation class DefaultDispatcher
@Qualifier annotation class MainDispatcher
@Qualifier annotation class ApplicationScope

/**
 * App-wide singletons: dispatchers, encrypted prefs, application coroutine scope.
 *
 * EncryptedSharedPreferences is used for:
 *  - TDLib session DB passphrase (per-account)
 *  - Vault master key (wrapped by user passphrase via VaultCrypto)
 *  - User preferences (theme, dynamic color, language)
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @IoDispatcher fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
    @Provides @MainDispatcher fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    @Provides @ApplicationScope
    fun applicationScope(@DefaultDispatcher d: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + d)

    @Provides @Singleton
    fun provideEncryptedPrefs(@ApplicationContext ctx: Context): EncryptedSharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            "tgdrive_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }
}
