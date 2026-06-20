package com.telegramdrive.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's Telegram API credentials (api_id, api_hash) encrypted at
 * rest using Android's MasterKey + EncryptedSharedPreferences.
 *
 * Why this design:
 *   - The Telegram API credentials belong to the *user*, not the app. Each
 *     user must register their own at https://my.telegram.org/apps.
 *   - Baking them into the APK at build time (BuildConfig fields) means a
 *     different APK per user — awkward for distribution.
 *   - Storing them in plain SharedPreferences is unsafe (rooted devices,
 *     backup extraction).
 *   - EncryptedSharedPreferences uses AES-256-SIV for keys + AES-256-GCM for
 *     values, with the master key held by Android Keystore — so the file is
 *     unreadable without the device's lock screen + keystore-backed key.
 *
 * Credentials are entered on first-launch onboarding (see OnboardingFlow) and
 * can be updated later in Settings → Security.
 */
@Singleton
class SecureCredentialsStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            ctx,
            "tgdrive_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasCredentials(): Boolean {
        val id = prefs.getString(KEY_API_ID, null)
        val hash = prefs.getString(KEY_API_HASH, null)
        return !id.isNullOrBlank() && !hash.isNullOrBlank()
    }

    /** @return the stored api_id, or null if not yet set */
    fun getApiId(): Int? = prefs.getString(KEY_API_ID, null)?.toIntOrNull()

    fun getApiHash(): String? = prefs.getString(KEY_API_HASH, null)

    /** Persist new credentials. Overwrites any previous value. */
    fun saveCredentials(apiId: Int, apiHash: String) {
        require(apiId > 0) { "api_id must be positive" }
        require(apiHash.isNotBlank()) { "api_hash must not be blank" }
        prefs.edit()
            .putString(KEY_API_ID, apiId.toString())
            .putString(KEY_API_HASH, apiHash.trim())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_ID = "telegram_api_id"
        private const val KEY_API_HASH = "telegram_api_hash"
    }
}
