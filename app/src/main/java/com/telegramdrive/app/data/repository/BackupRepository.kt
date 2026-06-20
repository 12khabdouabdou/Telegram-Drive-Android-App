package com.telegramdrive.app.data.repository

import com.telegramdrive.app.data.local.dao.BackupRuleDao
import com.telegramdrive.app.data.local.entity.BackupRuleEntity
import com.telegramdrive.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val dao: BackupRuleDao,
    private val accountDao: com.telegramdrive.app.data.local.dao.AccountDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    fun observeForActiveAccount(): Flow<List<BackupRuleEntity>> =
        kotlinx.coroutines.flow.transformLatest(accountDao.observeActive()) { acc ->
            if (acc == null) emit(emptyList())
            else emitAll(dao.observeForAccount(acc.id))
        }.flowOn(io)

    fun observeEnabled(): Flow<List<BackupRuleEntity>> = dao.observeEnabled().flowOn(io)

    suspend fun getEnabledForActiveAccount(): List<BackupRuleEntity> = withContext(io) {
        val acc = accountDao.getActive() ?: return@withContext emptyList()
        dao.enabledForAccount(acc.id)
    }

    suspend fun getById(id: Long): BackupRuleEntity? = withContext(io) { dao.getById(id) }

    suspend fun create(rule: BackupRuleEntity): Long = withContext(io) {
        dao.insert(rule)
    }

    suspend fun update(rule: BackupRuleEntity) = withContext(io) { dao.update(rule) }
    suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(io) { dao.setEnabled(id, enabled) }
    suspend fun delete(id: Long) = withContext(io) { dao.delete(id) }
    suspend fun markRun(id: Long, uploaded: Int) = withContext(io) { dao.markRun(id, uploaded) }

    /**
     * Create a sensible default rule when an account first signs in.
     */
    suspend fun seedDefaultRule(accountId: Long) = withContext(io) {
        val existing = dao.enabledForAccount(accountId)
        if (existing.isNotEmpty()) return@withContext
        dao.insert(BackupRuleEntity(
            accountId = accountId,
            name = "Photos & Videos",
            includedFolders = listOf("DCIM/Camera", "Pictures/Screenshots"),
            mimeTypes = listOf("image/*", "video/*"),
            onlyOriginalQuality = true,
            requireWifi = true,
            requireCharging = false,
            schedule = BackupRuleEntity.Schedule.WATCH,
            dedupEnabled = true,
            storageSaver = false
        ))
    }
}
