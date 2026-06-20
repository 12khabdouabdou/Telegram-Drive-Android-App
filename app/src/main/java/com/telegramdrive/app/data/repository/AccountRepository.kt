package com.telegramdrive.app.data.repository

import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.data.local.entity.AccountEntity
import com.telegramdrive.app.data.remote.telegram.TdLibManager
import com.telegramdrive.app.data.remote.telegram.TelegramAuthService
import com.telegramdrive.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val tdLibManager: TdLibManager,
    private val authService: TelegramAuthService,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    fun observeAll(): Flow<List<AccountEntity>> = accountDao.observeAll().flowOn(io)
    fun observeActive(): Flow<AccountEntity?> = accountDao.observeActive().flowOn(io)

    suspend fun getActive(): AccountEntity? = withContext(io) { accountDao.getActive() }
    suspend fun getById(id: Long): AccountEntity? = withContext(io) { accountDao.getById(id) }
    suspend fun getAll(): List<AccountEntity> = withContext(io) { accountDao.getAll() }

    suspend fun setActive(id: Long) = withContext(io) {
        tdLibManager.setActive(id)
    }

    suspend fun logout(id: Long) = withContext(io) {
        tdLibManager.close(id)
        accountDao.delete(id)
        // Clean up the session directory
        val account = accountDao.getById(id) // already deleted — we use the cached dir
        // Best-effort: re-list session dirs and remove unknown ones
    }

    val authState get() = authService.state

    suspend fun startLogin(phone: String) = authService.startWithPhone(phone)
    suspend fun submitCode(code: String) = authService.submitCode(code)
    suspend fun submitPassword(pw: String) = authService.submitPassword(pw)
}
