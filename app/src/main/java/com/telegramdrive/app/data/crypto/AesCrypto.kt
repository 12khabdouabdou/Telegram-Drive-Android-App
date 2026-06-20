package com.telegramdrive.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM streaming encryption for file chunks and vault entries.
 *
 * Design:
 *  - For chunked file encryption we use **counter-based IV** so each chunk's
 *    IV is derived deterministically from (master-IV, chunk-index). This means
 *    we only need to store ONE 12-byte IV per file (in the header) instead of
 *    one per chunk.
 *  - Auth tag (16 bytes) is appended to each ciphertext chunk.
 *  - For small vault entries we use a fresh random IV per entry.
 *
 * Key derivation from a user passphrase uses Argon2id via [VaultCrypto].
 */
@Singleton
class AesCrypto @Inject constructor() {

    /** 12-byte GCM IV is the recommended length. */
    fun randomIv(): ByteArray = ByteArray(12).also { SecureRandom().nextBytes(it) }

    /** Encrypt a single file chunk. Returns ciphertext + 16-byte tag appended. */
    fun encryptChunk(plaintext: ByteArray, key: ByteArray, masterIv: ByteArray, chunkIndex: Long): ByteArray {
        val iv = deriveChunkIv(masterIv, chunkIndex)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)
    }

    fun decryptChunk(ciphertext: ByteArray, key: ByteArray, masterIv: ByteArray, chunkIndex: Long): ByteArray {
        val iv = deriveChunkIv(masterIv, chunkIndex)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    /** Encrypt a small payload (vault entry). Returns iv (12B) || ciphertext || tag (16B). */
    fun encryptSmall(plaintext: ByteArray, key: ByteArray): ByteArray {
        val iv = randomIv()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext)
        return iv + ct // iv + ciphertext + tag
    }

    fun decryptSmall(payload: ByteArray, key: ByteArray): ByteArray {
        require(payload.size > 28) { "Payload too small" }
        val iv = payload.copyOfRange(0, 12)
        val ct = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    /** Derive a chunk IV deterministically: masterIv XOR (chunkIndex as 12-byte big-endian). */
    private fun deriveChunkIv(masterIv: ByteArray, chunkIndex: Long): ByteArray {
        require(masterIv.size == 12)
        val out = masterIv.copyOf()
        // Mix chunkIndex into the lower 8 bytes via XOR
        for (i in 0 until 8) {
            out[11 - i] = (out[11 - i].toInt() xor ((chunkIndex ushr (i * 8)) and 0xFF).toInt()).toByte()
        }
        return out
    }

    /**
     * Derive a 32-byte AES key from a passphrase using PBKDF2-HMAC-SHA256 with
     * 600k iterations (OWASP 2023 recommendation for PBKDF2).
     *
     * For vault master-key derivation we use Argon2id via [VaultCrypto] — see
     * that class. PBKDF2 is used here for file-encryption keys because it's
     * available in stock Android without external libs.
     */
    fun deriveKeyFromPassphrase(passphrase: CharArray, salt: ByteArray, iterations: Int = 600_000): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(passphrase, salt, iterations, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun randomSalt(size: Int = 16): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
}
