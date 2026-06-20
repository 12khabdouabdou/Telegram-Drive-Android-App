package com.telegramdrive.app.data.remote.telegram

import com.telegramdrive.app.BuildConfig
import com.telegramdrive.app.data.security.SecureCredentialsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Thin wrapper around TDLib's [Client] for a single Telegram account.
 *
 * TDLib is the official Telegram Database Library. It runs on a native thread,
 * delivers updates via a long-lived handler, and exposes a request/response
 * API for every Telegram method. This wrapper:
 *
 *  - Sends typed requests and suspends until the response arrives.
 *  - Surfaces updates as a Kotlin SharedFlow so the rest of the app can react.
 *  - Provides per-account isolation by giving each account its own TdClient
 *    instance + own session directory (see TdLibManager).
 *  - Reads the Telegram API credentials from [SecureCredentialsStore] at
 *    [start] time — the user enters them via onboarding, NOT baked into the
 *    APK at build time.
 *
 * One [TdClient] = one logged-in Telegram account.
 */
@Singleton
class TdClient @Inject constructor(
    private val credentials: SecureCredentialsStore
) {

    @Volatile private var client: Client? = null
    @Volatile private var clientId: Int = 0

    val updateFlow: MutableSharedFlow<TdApi.Object> = MutableSharedFlow(extraBufferCapacity = 256)

    /**
     * Boot this client with a fresh TDLib instance pointed at [sessionDir].
     * Throws if no API credentials have been stored yet.
     */
    fun start(sessionDir: String) {
        if (client != null) return
        val apiId = credentials.getApiId()
            ?: throw IllegalStateException("No Telegram API credentials stored. Run onboarding first.")
        val apiHash = credentials.getApiHash()
            ?: throw IllegalStateException("No Telegram API credentials stored. Run onboarding first.")

        Client.setLogVerbosityLevel(2)
        client = Client({ update ->
            // Route updates to consumers via the SharedFlow.
            // Responses to requests are routed via the per-request callback (see `send`).
            updateFlow.tryEmit(update)
        }, { code, msg ->
            android.util.Log.e("TdClient", "TDLib fatal error: $code $msg")
        }, sessionDir).also {
            clientId = it.clientId
        }

        // Initial parameters — required for TDLib to operate
        sendBlocking<TdApi.Object>(TdApi.SetTdlibParameters(TdApi.TdlibParameters().apply {
            databaseDirectory = sessionDir
            filesDirectory = "$sessionDir/files"
            useMessageDatabase = true
            useChatInfoDatabase = true
            useFileDatabase = true
            useChatSorter = true
            useNetworkType = true
            this.apiId = apiId
            this.apiHash = apiHash
            systemLanguageCode = java.util.Locale.getDefault().toLanguageTag()
            deviceModel = android.os.Build.MODEL
            applicationVersion = BuildConfig.APP_VERSION
            systemVersion = "Android ${android.os.Build.VERSION.RELEASE}"
        }))
        runCatching { sendBlocking<TdApi.Object>(TdApi.SetOption("notification_group_count_max", TdApi.OptionValueInteger(10))) }
    }

    fun stop() {
        runCatching { sendBlocking<TdApi.Object>(TdApi.Close()) }
        client = null
    }

    val activeClientId: Int get() = clientId

    /**
     * Send a TDLib request and suspend until the response arrives.
     * Throws [TdException] if TDLib returned an error or the client isn't started.
     */
    suspend fun <T : TdApi.Object> send(request: TdApi.Function): T {
        val clientRef = client ?: throw TdException("Client not started")
        val response: TdApi.Object = suspendCancellableCoroutine { cont ->
            clientRef.send(request) { obj ->
                if (cont.isActive) cont.resume(obj)
            }
        }
        if (response is TdApi.Error) throw TdException(response)
        @Suppress("UNCHECKED_CAST")
        return response as T
    }

    /** Synchronous variant — only use from a background thread. */
    fun <T : TdApi.Object> sendBlocking(request: TdApi.Function): T {
        val latch = java.util.concurrent.CountDownLatch(1)
        @Volatile var response: TdApi.Object? = null
        client?.send(request) { obj ->
            response = obj
            latch.countDown()
        } ?: throw TdException("Client not started")
        latch.await(60, java.util.concurrent.TimeUnit.SECONDS)
        if (response is TdApi.Error) throw TdException(response as TdApi.Error)
        @Suppress("UNCHECKED_CAST")
        return response as T
    }

    /**
     * Subscribe to TDLib updates of a specific type. Returns a cold Flow.
     */
    inline fun <reified T : TdApi.Update> updatesOfType(): kotlinx.coroutines.flow.Flow<T> =
        updateFlow.filter { it is T }.map { it as T }
}

/** Wrap TDLib errors into a typed exception. */
class TdException : RuntimeException {
    val code: Int
    val data: String?

    constructor(error: TdApi.Error) : super("${error.code}: ${error.message}") {
        code = error.code
        data = null
    }
    constructor(msg: String) : super(msg) {
        code = -1
        data = null
    }
}
