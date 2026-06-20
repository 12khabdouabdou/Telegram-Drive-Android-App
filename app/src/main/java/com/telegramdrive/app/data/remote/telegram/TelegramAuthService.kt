package com.telegramdrive.app.data.remote.telegram

import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the Telegram authentication state machine for the active client.
 *
 *   phone → WAIT_CODE → WAIT_PASSWORD (optional) → READY
 *
 * Each step is surfaced as [AuthState] via a StateFlow the UI subscribes to.
 */
@Singleton
class TelegramAuthService @Inject constructor(
    private val tdLibManager: TdLibManager,
    private val accountDao: AccountDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    sealed class AuthState {
        object Idle : AuthState()
        data class PhoneNumber(val tempAccountId: Long) : AuthState()
        data class CodeRequired(val tempAccountId: Long, val nextType: String?) : AuthState()
        data class PasswordRequired(val tempAccountId: Long, val hint: String?, val recoveryEnabled: Boolean) : AuthState()
        data class Ready(val accountId: Long, val userId: Long) : AuthState()
        data class Failed(val message: String) : AuthState()
    }

    suspend fun startWithPhone(phone: String) = withContext(io) {
        val (client, accId) = tdLibManager.createSession(phone)
        _state.value = AuthState.PhoneNumber(accId)
        client.send<TdApi.Ok>(TdApi.SetAuthenticationPhoneNumber(phone, null))
        // The TDLib update handler will flip _state to CodeRequired
    }

    suspend fun submitCode(code: String) = withContext(io) {
        val accId = (_state.value as? AuthState.CodeRequired)?.tempAccountId
            ?: (_state.value as? AuthState.PhoneNumber)?.tempAccountId
            ?: return@withContext
        val client = tdLibManager.clientFor(accId) ?: return@withContext
        try {
            client.send<TdApi.Ok>(TdApi.CheckAuthenticationAuthenticationCode(code))
            // Either READY or PASSWORD_REQUIRED follows
        } catch (e: TdException) {
            _state.value = AuthState.Failed(e.message ?: "Code rejected")
        }
    }

    suspend fun submitPassword(password: String) = withContext(io) {
        val accId = (_state.value as? AuthState.PasswordRequired)?.tempAccountId ?: return@withContext
        val client = tdLibManager.clientFor(accId) ?: return@withContext
        try {
            client.send<TdApi.Ok>(TdApi.CheckAuthenticationPassword(password))
        } catch (e: TdException) {
            _state.value = AuthState.Failed(e.message ?: "Password rejected")
        }
    }

    /**
     * Called by TdLibManager (or an Update handler) when TDLib emits an
     * authorization-state update. Updates local DB + active account.
     */
    suspend fun handleAuthStateUpdate(state: TdApi.UpdateAuthorizationState) = withContext(io) {
        when (val s = state.authorizationState) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                // Already set by startWithPhone
            }
            is TdApi.AuthorizationStateWaitCode -> {
                val accId = (_state.value as? AuthState.PhoneNumber)?.tempAccountId ?: return@withContext
                _state.value = AuthState.CodeRequired(accId, s.codeInfo.type?.let { it::class.simpleName })
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                val accId = (_state.value as? AuthState.PhoneNumber)?.tempAccountId
                    ?: (_state.value as? AuthState.CodeRequired)?.tempAccountId
                    ?: return@withContext
                _state.value = AuthState.PasswordRequired(accId, s.passwordHint, s.hasRecoveryEmailAddress)
            }
            is TdApi.AuthorizationStateReady -> {
                val accId = (_state.value as? AuthState.PhoneNumber)?.tempAccountId
                    ?: (_state.value as? AuthState.CodeRequired)?.tempAccountId
                    ?: (_state.value as? AuthState.PasswordRequired)?.tempAccountId
                    ?: return@withContext
                val client = tdLibManager.clientFor(accId) ?: return@withContext
                val me = client.send<TdApi.User>(TdApi.GetMe())
                val existing = accountDao.getById(accId)
                if (existing != null) {
                    accountDao.update(existing.copy(
                        tdlibUserId = me.id,
                        firstName = me.firstName,
                        lastName = me.lastName,
                        username = me.username,
                        premium = me.isPremium,
                        maxFileSizeBytes = if (me.isPremium) 4L * 1024 * 1024 * 1024 else 2L * 1024 * 1024 * 1024,
                        isActive = true
                    ))
                }
                tdLibManager.setActive(accId)
                _state.value = AuthState.Ready(accId, me.id)
            }
            is TdApi.AuthorizationStateClosed -> {
                _state.value = AuthState.Idle
            }
            else -> { /* loggingOut, waitRegistration, etc. */ }
        }
    }
}
