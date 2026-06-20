package com.telegramdrive.app.data.crypto

import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vault master-key lifecycle:
 *
 *   1. User sets passphrase → we generate a random 32-byte master key (DEK).
 *   2. DEK is wrapped by a KEK derived from the passphrase via PBKDF2.
 *   3. Wrapped DEK + salt + iterations are stored in EncryptedSharedPreferences
 *      (so the OS-level MasterKey protects them at rest, AND the user's
 *      passphrase protects them in-memory).
 *   4. On unlock: user enters passphrase → re-derive KEK → unwrap DEK → keep
 *      in-memory only for the session (zeroised on lock).
 *
 * This double-encryption means a stolen device + disk image still can't read
 * the vault without the passphrase.
 */
@Singleton
class VaultCrypto @Inject constructor(
    private val securePrefs: SharedPreferences,
    private val aesCrypto: AesCrypto
) {

    @Volatile private var unwrappedDek: ByteArray? = null
    @Volatile var isUnlocked: Boolean = false
        private set

    fun isInitialized(): Boolean = securePrefs.getString(KEY_WRAPPED_DEK, null) != null

    /**
     * Set up the vault for the first time. Throws if already initialised.
     */
    fun initialize(passphrase: CharArray) {
        require(!isInitialized()) { "Vault already initialised" }
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) {
            "Passphrase must be at least $MIN_PASSPHRASE_LENGTH chars"
        }
        val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val salt = aesCrypto.randomSalt(16)
        val kek = aesCrypto.deriveKeyFromPassphrase(passphrase, salt)
        val wrapped = aesCrypto.encryptSmall(dek, kek)
        securePrefs.edit()
            .putString(KEY_WRAPPED_DEK, b64(wrapped))
            .putString(KEY_SALT, b64(salt))
            .putInt(KEY_ITERATIONS, DEFAULT_ITERATIONS)
            .apply()
        // Zeroise kek + dek in memory except keep dek for current session
        kek.fill(0)
        unwrappedDek = dek
        isUnlocked = true
    }

    /**
     * Unlock the vault with a passphrase. Returns true on success.
     * On failure, [unwrappedDek] is left null and [isUnlocked] stays false.
     */
    fun unlock(passphrase: CharArray): Boolean {
        val wrappedB64 = securePrefs.getString(KEY_WRAPPED_DEK, null) ?: return false
        val saltB64 = securePrefs.getString(KEY_SALT, null) ?: return false
        val iterations = securePrefs.getInt(KEY_ITERATIONS, DEFAULT_ITERATIONS)
        val wrapped = b64dec(wrappedB64)
        val salt = b64dec(saltB64)
        val kek = aesCrypto.deriveKeyFromPassphrase(passphrase, salt, iterations)
        return try {
            unwrappedDek = aesCrypto.decryptSmall(wrapped, kek)
            isUnlocked = true
            true
        } catch (e: Throwable) {
            // AEAD bad-tag = wrong passphrase
            kek.fill(0)
            false
        } finally {
            kek.fill(0)
        }
    }

    fun lock() {
        unwrappedDek?.fill(0)
        unwrappedDek = null
        isUnlocked = false
    }

    fun changePassphrase(oldPassphrase: CharArray, newPassphrase: CharArray): Boolean {
        if (!unlock(oldPassphrase)) return false
        val dek = unwrappedDek ?: return false
        // Re-wrap with new passphrase
        val newSalt = aesCrypto.randomSalt(16)
        val newKek = aesCrypto.deriveKeyFromPassphrase(newPassphrase, newSalt)
        val wrapped = aesCrypto.encryptSmall(dek, newKek)
        securePrefs.edit()
            .putString(KEY_WRAPPED_DEK, b64(wrapped))
            .putString(KEY_SALT, b64(newSalt))
            .apply()
        newKek.fill(0)
        return true
    }

    fun encryptVaultPayload(plaintext: ByteArray): Pair<String, String> {
        val dek = unwrappedDek ?: throw IllegalStateException("Vault is locked")
        val payload = aesCrypto.encryptSmall(plaintext, dek)
        val iv = payload.copyOfRange(0, 12)
        val ct = payload.copyOfRange(12, payload.size)
        return b64(ct) to b64(iv)
    }

    fun decryptVaultPayload(ciphertextB64: String, ivB64: String): ByteArray {
        val dek = unwrappedDek ?: throw IllegalStateException("Vault is locked")
        val iv = b64dec(ivB64)
        val ct = b64dec(ciphertextB64)
        return aesCrypto.decryptSmall(iv + ct, dek)
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun b64dec(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
        const val DEFAULT_ITERATIONS = 600_000
        private const val KEY_WRAPPED_DEK = "vault_wrapped_dek_v1"
        private const val KEY_SALT = "vault_salt_v1"
        private const val KEY_ITERATIONS = "vault_iterations_v1"
    }
}
