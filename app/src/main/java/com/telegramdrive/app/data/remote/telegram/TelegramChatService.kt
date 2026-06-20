package com.telegramdrive.app.data.remote.telegram

import com.telegramdrive.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides chat-list / message-list / send-message operations for the in-app
 * Telegram client (so the user doesn't have to leave the app to read their
 * Saved Messages or reply to a contact).
 *
 * This is intentionally a thin wrapper — TDLib handles the heavy lifting
 * (caching, pagination, optimistic updates). We just translate TdApi objects
 * into our local Room models (see ChatRepository for that mapping).
 */
@Singleton
class TelegramChatService @Inject constructor(
    private val tdLibManager: TdLibManager,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    suspend fun loadChats(accountId: Long, limit: Int = 50): List<TdApi.Chat> = withContext(io) {
        val client = tdLibManager.clientFor(accountId) ?: return@withContext emptyList()
        try {
            val resp = client.send<TdApi.Chats>(TdApi.GetChats(TdApi.ChatListMain(), limit))
            resp.chatIds.map { id -> client.send<TdApi.Chat>(TdApi.GetChat(id)) }
        } catch (e: TdException) {
            emptyList()
        }
    }

    suspend fun loadMessages(
        accountId: Long,
        chatId: Long,
        fromMessageId: Long = 0,
        limit: Int = 50
    ): List<TdApi.Message> = withContext(io) {
        val client = tdLibManager.clientFor(accountId) ?: return@withContext emptyList()
        val resp = client.send<TdApi.Messages>(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false))
        resp.messages?.toList() ?: emptyList()
    }

    suspend fun sendText(
        accountId: Long,
        chatId: Long,
        text: String,
        replyToMessageId: Long = 0
    ): TdApi.Message = withContext(io) {
        val client = tdLibManager.clientFor(accountId) ?: throw TdException("No client")
        client.send<TdApi.Message>(
            TdApi.SendMessage(
                chatId,
                replyToMessageId,
                null,
                null,
                TdApi.InputMessageText(TdApi.FormattedText(text, null), false, false)
            )
        )
    }

    suspend fun deleteMessages(
        accountId: Long,
        chatId: Long,
        messageIds: List<Long>,
        revoke: Boolean = true
    ) = withContext(io) {
        val client = tdLibManager.clientFor(accountId) ?: return@withContext
        client.send<TdApi.Ok>(TdApi.DeleteMessages(chatId, messageIds.toLongArray(), revoke))
    }

    suspend fun markRead(accountId: Long, chatId: Long, lastReadMessageId: Long) = withContext(io) {
        val client = tdLibManager.clientFor(accountId) ?: return@withContext
        client.send<TdApi.Ok>(TdApi.ViewMessages(chatId, longArrayOf(lastReadMessageId), null, true))
    }
}
