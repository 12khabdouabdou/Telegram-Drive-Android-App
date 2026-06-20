package com.telegramdrive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.telegramdrive.app.data.local.entity.ChatEntity
import com.telegramdrive.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE accountId = :accountId ORDER BY pinned DESC, lastMessageDate DESC")
    fun observeChats(accountId: Long): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :localId")
    suspend fun getById(localId: Long): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatEntity): Long

    @Update suspend fun update(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :localId") suspend fun delete(localId: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatLocalId = :chatLocalId ORDER BY date DESC LIMIT :limit OFFSET :offset")
    fun observeMessages(chatLocalId: Long, limit: Int = 100, offset: Int = 0): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id") suspend fun delete(id: Long)

    @Query("SELECT MAX(date) FROM messages WHERE chatLocalId = :chatLocalId")
    suspend fun newestDate(chatLocalId: Long): Long?
}
