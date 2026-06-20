package com.telegramdrive.app.data.remote.telegram

import android.content.Context
import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.data.local.entity.AccountEntity
import com.telegramdrive.app.di.ApplicationScope
import com.telegramdrive.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages one [TdClient] per Telegram account. Each account gets:
 *   - Its own TDLib session directory under [sessionsRoot]/<accountId>
 *   - Its own TDLib client instance (with separate auth state)
 *
 * This is what makes multi-account work — TDLib keeps state in the filesystem
 * per-client, so we just point each client at its own dir.
 *
 * The active account's client is started eagerly on app launch. Switching
 * accounts pauses the previous client's network (TdApi.SetOption("online", false))
 * and starts the new one.
 */
@Singleton
class TdLibManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val accountDao: AccountDao,
    private val credentials: com.telegramdrive.app.data.security.SecureCredentialsStore,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val sessionsRoot: File = File(ctx.filesDir, "tdlib-sessions").apply { mkdirs() }

    private val clients = mutableMapOf<Long, TdClient>()
    private val _activeAccountId = MutableStateFlow<Long?>(null)
    val activeAccountId: StateFlow<Long?> = _activeAccountId.asStateFlow()

    @Volatile private var bootstrapped = false

    /**
     * Called once from Application.onCreate(). Starts the active account's
     * TDLib client so the app can begin receiving updates immediately.
     * Silently skips if no API credentials have been entered yet — the
     * onboarding flow will trigger bootstrap() again once credentials are saved.
     */
    fun bootstrap() {
        if (bootstrapped) return
        bootstrapped = true
        appScope.launch(io) {
            if (!credentials.hasCredentials()) {
                android.util.Log.i("TdLibManager", "Skipping bootstrap — no API credentials stored yet")
                return@launch
            }
            val active = accountDao.getActive()
            if (active != null) {
                runCatching { startClientFor(active) }
                _activeAccountId.value = active.id
            }
        }
    }

    /**
     * Re-attempt bootstrap after the user has entered API credentials during
     * onboarding. Safe to call multiple times.
     */
    fun bootstrapAfterCredentials() {
        appScope.launch(io) {
            bootstrapped = false
            bootstrap()
        }
    }

    private fun newClient(): TdClient = TdClient(credentials)

    /**
     * Create a brand-new TDLib session for a freshly-registered phone number.
     * Returns the [TdClient] (already started) so the caller can drive auth.
     */
    suspend fun createSession(phoneNumber: String): Pair<TdClient, Long> = withContext(io) {
        require(credentials.hasCredentials()) {
            "Cannot start TDLib session — no API credentials stored. Run onboarding first."
        }
        // Provisional account row (real telegramUserId filled in after auth)
        val provisional = AccountEntity(
            phoneNumber = phoneNumber,
            firstName = "(authenticating)",
            tdlibUserId = 0,
            savedMessagesChatId = 0,
            sessionDir = ""
        )
        val id = accountDao.upsert(provisional.copy(sessionDir = ""))
        val sessionDir = File(sessionsRoot, id.toString()).apply { mkdirs() }
        accountDao.update(provisional.copy(id = id, sessionDir = sessionDir.absolutePath))
        val client = newClient().also { it.start(sessionDir.absolutePath) }
        synchronized(clients) { clients[id] = client }
        client to id
    }

    suspend fun startClientFor(account: AccountEntity): TdClient = withContext(io) {
        synchronized(clients) {
            clients[account.id]?.let { return@withContext it }
        }
        require(credentials.hasCredentials()) {
            "Cannot start TDLib session — no API credentials stored. Run onboarding first."
        }
        val sessionDir = File(sessionsRoot, account.id.toString()).apply { mkdirs() }
        val client = newClient().also { it.start(sessionDir.absolutePath) }
        synchronized(clients) { clients[account.id] = client }
        client
    }

    suspend fun setActive(accountId: Long) = withContext(io) {
        val active = accountDao.getActive()
        if (active?.id == accountId) return@withContext
        // Pause the previous
        active?.let { prev ->
            try { clients[prev.id]?.sendBlocking(TdApi.SetOption("online", TdApi.OptionValueBoolean(false))) } catch (_: Throwable) {}
        }
        accountDao.setActive(accountId)
        val new = accountDao.getById(accountId) ?: return@withContext
        startClientFor(new)
        // Bring it online
        try { clients[new.id]?.sendBlocking(TdApi.SetOption("online", TdApi.OptionValueBoolean(true))) } catch (_: Throwable) {}
        _activeAccountId.value = accountId
        accountDao.touchActive(accountId)
    }

    fun clientFor(accountId: Long): TdClient? = synchronized(clients) { clients[accountId] }

    fun activeClient(): TdClient? = _activeAccountId.value?.let { clientFor(it) }

    suspend fun close(accountId: Long) = withContext(io) {
        synchronized(clients) { clients.remove(accountId) }?.stop()
    }
}
