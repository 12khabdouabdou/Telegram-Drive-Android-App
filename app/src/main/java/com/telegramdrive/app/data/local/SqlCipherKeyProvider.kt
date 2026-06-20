package com.telegramdrive.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.sqlcipher.database.SupportFactory
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Provides a [SupportSQLiteOpenHelper.Factory] backed by SQLCipher, using
 * a per-installation passphrase stored in EncryptedSharedPreferences.
 *
 * The passphrase is generated on first launch (32 random bytes, Base64-encoded)
 * and stored encrypted by Android's MasterKey — so the local database is fully
 * encrypted at rest, but unlocks automatically when the app runs.
 *
 * For the vault feature specifically, secrets are *additionally* wrapped by
 * the user's passphrase (see VaultCrypto). The DB-level encryption here is
 * defense-in-depth.
 */
@Singleton
class SqlCipherKeyProvider @Inject constructor(
    private val securePrefs: Provider<android.content.SharedPreferences>
) {
    @Volatile private var cachedPassphrase: ByteArray? = null

    fun passphrase(): ByteArray {
        cachedPassphrase?.let { return it }
        synchronized(this) {
            cachedPassphrase?.let { return it }
            val prefs = securePrefs.get()
            val existing = prefs.getString(DB_PASSPHRASE_KEY, null)
            val bytes = existing?.toByteArray(Charsets.UTF_8)
                ?: ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                    .also { arr ->
                        prefs.edit().putString(DB_PASSPHRASE_KEY, String(arr, Charsets.UTF_8)).apply()
                    }
            cachedPassphrase = bytes
            return bytes
        }
    }

    fun supportFactory(): SupportFactory {
        val factory = SupportFactory(passphrase())
        return factory
    }

    companion object {
        private const val DB_PASSPHRASE_KEY = "db_passphrase_v1"
    }
}

/**
 * Custom callback hooking into the database lifecycle — used to seed default
 * backup rules on first creation only.
 */
class AppDatabaseCallback(
    private val onCreate: (SupportSQLiteDatabase) -> Unit = {}
) : SupportSQLiteOpenHelper.Callback(AppDatabase.DB_VERSION) {
    override fun onCreate(db: SupportSQLiteDatabase) = onCreate.invoke(db)
    override fun onConfigure(db: SupportSQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrations are registered on Room via addMigrations() in DatabaseModule.
        // We don't fall through to destructive migration unless explicitly added.
    }
    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No-op: explicit migrations only
    }
}
