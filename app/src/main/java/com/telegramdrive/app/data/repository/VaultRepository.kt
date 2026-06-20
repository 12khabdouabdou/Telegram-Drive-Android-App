package com.telegramdrive.app.data.repository

import com.telegramdrive.app.data.crypto.VaultCrypto
import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.data.local.dao.VaultEntryDao
import com.telegramdrive.app.data.local.entity.VaultEntryEntity
import com.telegramdrive.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class VaultPayload(
    val password: String? = null,
    val note: String? = null,
    val cardNumber: String? = null,
    val cardHolder: String? = null,
    val cardExpiry: String? = null,
    val cardCvv: String? = null,
    val totpSecret: String? = null,
    val customFields: Map<String, String> = emptyMap()
)

@Singleton
class VaultRepository @Inject constructor(
    private val dao: VaultEntryDao,
    private val accountDao: AccountDao,
    private val crypto: VaultCrypto,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeForActiveAccount(type: VaultEntryEntity.VaultType? = null): Flow<List<VaultEntryEntity>> =
        kotlinx.coroutines.flow.transformLatest(accountDao.observeActive()) { acc ->
            if (acc == null) emit(emptyList())
            else if (type == null) emitAll(dao.observeForAccount(acc.id))
            else emitAll(dao.observeByType(acc.id, type))
        }.flowOn(io)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun search(query: String): Flow<List<VaultEntryEntity>> =
        kotlinx.coroutines.flow.transformLatest(accountDao.observeActive()) { acc ->
            if (acc == null) emit(emptyList())
            else emitAll(dao.search(acc.id, query))
        }.flowOn(io)

    suspend fun create(
        type: VaultEntryEntity.VaultType,
        title: String,
        username: String? = null,
        url: String? = null,
        tags: List<String> = emptyList(),
        payload: VaultPayload
    ): Long = withContext(io) {
        val acc = accountDao.getActive() ?: throw IllegalStateException("No active account")
        require(crypto.isUnlocked) { "Vault is locked" }
        val payloadJson = json.encodeToString(payload).toByteArray()
        val (ct, iv) = crypto.encryptVaultPayload(payloadJson)
        dao.insert(VaultEntryEntity(
            accountId = acc.id,
            type = type,
            title = title,
            username = username,
            url = url,
            tags = tags,
            ciphertext = ct,
            iv = iv
        ))
    }

    suspend fun decrypt(entry: VaultEntryEntity): VaultPayload = withContext(io) {
        val plaintext = crypto.decryptVaultPayload(entry.ciphertext, entry.iv)
        dao.touchAccessed(entry.id)
        json.decodeFromString<VaultPayload>(String(plaintext, Charsets.UTF_8))
    }

    suspend fun update(entry: VaultEntryEntity, payload: VaultPayload) = withContext(io) {
        val payloadJson = json.encodeToString(payload).toByteArray()
        val (ct, iv) = crypto.encryptVaultPayload(payloadJson)
        dao.update(entry.copy(ciphertext = ct, iv = iv, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = withContext(io) { dao.delete(id) }

    fun initializeVault(passphrase: CharArray) = crypto.initialize(passphrase)
    fun unlockVault(passphrase: CharArray): Boolean = crypto.unlock(passphrase)
    fun lockVault() = crypto.lock()
    fun isVaultInitialized() = crypto.isInitialized()
    val isVaultUnlocked get() = crypto.isUnlocked
}
