package com.telegramdrive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.telegramdrive.app.data.local.entity.VaultEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultEntryDao {
    @Query("SELECT * FROM vault_entries WHERE accountId = :accountId ORDER BY favorite DESC, title COLLATE NOCASE ASC")
    fun observeForAccount(accountId: Long): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries WHERE accountId = :accountId AND type = :type ORDER BY title COLLATE NOCASE")
    fun observeByType(accountId: Long, type: VaultEntryEntity.VaultType): Flow<List<VaultEntryEntity>>

    @Query("""
        SELECT * FROM vault_entries
        WHERE accountId = :accountId
          AND (title LIKE '%' || :q || '%' OR username LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%')
        ORDER BY title COLLATE NOCASE
    """)
    fun search(accountId: Long, q: String): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries WHERE id = :id")
    suspend fun getById(id: Long): VaultEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VaultEntryEntity): Long

    @Update
    suspend fun update(entry: VaultEntryEntity)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE vault_entries SET lastAccessedAt = :now WHERE id = :id")
    suspend fun touchAccessed(id: Long, now: Long = System.currentTimeMillis())
}
