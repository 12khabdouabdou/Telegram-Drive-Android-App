package com.telegramdrive.app.data.repository

import com.telegramdrive.app.data.local.dao.ChatDao
import com.telegramdrive.app.data.local.dao.MessageDao
import com.telegramdrive.app.data.local.entity.ChatEntity
import com.telegramdrive.app.data.local.entity.MessageEntity
import com.telegramdrive.app.data.remote.telegram.TelegramChatService
import com.telegramdrive.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val accountDao: com.telegramdrive.app.data.local.dao.AccountDao,
    private val tgChatService: TelegramChatService,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    fun observeChats(): Flow<List<ChatEntity>> = kotlinx.coroutines.flow.transformLatest(
        accountDao.observeActive()
    ) { acc ->
        if (acc == null) emit(emptyList())
        else emitAll(chatDao.observeChats(acc.id))
    }.flowOn(io)

    fun observeMessages(chatLocalId: Long): Flow<List<MessageEntity>> =
        messageDao.observeMessages(chatLocalId).flowOn(io)

    suspend fun refreshChatsFromTelegram() = withContext(io) {
        val acc = accountDao.getActive() ?: return@withContext
        val remoteChats = tgChatService.loadChats(acc.id, 100)
        for (c in remoteChats) {
            chatDao.upsert(c.toEntity(acc.id))
        }
    }

    suspend fun refreshMessages(chatLocalId: Long) = withContext(io) {
        val acc = accountDao.getActive() ?: return@withContext
        val local = chatDao.getById(chatLocalId) ?: return@withContext
        val remote = tgChatService.loadMessages(acc.id, local.telegramChatId, 0, 50)
        for (m in remote) {
            messageDao.insert(m.toEntity(acc.id, chatLocalId))
        }
    }

    suspend fun sendText(chatLocalId: Long, text: String, replyTo: Long? = null) = withContext(io) {
        val acc = accountDao.getActive() ?: return@withContext
        val local = chatDao.getById(chatLocalId) ?: return@withContext
        val msg = tgChatService.sendText(acc.id, local.telegramChatId, text, replyTo ?: 0)
        messageDao.insert(msg.toEntity(acc.id, chatLocalId))
    }

    suspend fun deleteMessages(chatLocalId: Long, messageIds: List<Long>) = withContext(io) {
        val acc = accountDao.getActive() ?: return@withContext
        val local = chatDao.getById(chatLocalId) ?: return@withContext
        tgChatService.deleteMessages(acc.id, local.telegramChatId, messageIds, revoke = true)
        for (id in messageIds) messageDao.delete(id)
    }

    suspend fun markRead(chatLocalId: Long, lastReadMessageId: Long) = withContext(io) {
        val acc = accountDao.getActive() ?: return@withContext
        val local = chatDao.getById(chatLocalId) ?: return@withContext
        tgChatService.markRead(acc.id, local.telegramChatId, lastReadMessageId)
    }
}

private fun TdApi.Chat.toEntity(accountId: Long): ChatEntity {
    val type: ChatEntity.ChatType = when (val t = type) {
        is TdApi.ChatTypePrivate -> if (id == 0L) ChatEntity.ChatType.SELF else ChatEntity.ChatType.PRIVATE
        is TdApi.ChatTypeBasicGroup -> ChatEntity.ChatType.GROUP
        is TdApi.ChatTypeSupergroup -> ChatEntity.ChatType.SUPERGROUP
        is TdApi.ChatTypeSecret -> ChatEntity.ChatType.PRIVATE
        else -> ChatEntity.ChatType.PRIVATE
    }
    return ChatEntity(
        accountId = accountId,
        telegramChatId = id,
        title = title,
        type = type,
        lastMessageText = (lastMessage?.content as? TdApi.MessageText)?.text?.text,
        lastMessageDate = lastMessage?.date?.toLong()?.times(1000),
        unreadCount = unreadCount,
        pinned = positions.any { it.source is TdApi.ChatSourcePinnedToTopList }
    )
}

private fun TdApi.Message.toEntity(accountId: Long, chatLocalId: Long): MessageEntity {
    val text = (content as? TdApi.MessageText)?.text?.text
        ?: (content as? TdApi.MessageDocument)?.caption?.text
        ?: (content as? TdApi.MessagePhoto)?.caption?.text
    val doc = (content as? TdApi.MessageDocument)?.document
    return MessageEntity(
        accountId = accountId,
        chatLocalId = chatLocalId,
        telegramMessageId = id,
        senderId = senderId,
        senderName = senderName?.let { (it as? TdApi.MessageSenderUser)?.userId?.toString() } ?: "?",
        text = text,
        date = date * 1000L,
        isOutgoing = isOutgoing,
        replyToMessageId = replyToMessageId,
        attachmentMime = doc?.document?.mimeType,
        attachmentSize = doc?.document?.size?.toLong()
    )
}
