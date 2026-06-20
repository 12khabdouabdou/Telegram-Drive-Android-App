package com.telegramdrive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.telegramdrive.app.data.local.entity.BackupRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupRuleDao {
    @Query("SELECT * FROM backup_rules WHERE accountId = :accountId ORDER BY createdAt ASC")
    fun observeForAccount(accountId: Long): Flow<List<BackupRuleEntity>>

    @Query("SELECT * FROM backup_rules WHERE enabled = 1")
    fun observeEnabled(): Flow<List<BackupRuleEntity>>

    @Query("SELECT * FROM backup_rules WHERE enabled = 1 AND accountId = :accountId")
    suspend fun enabledForAccount(accountId: Long): List<BackupRuleEntity>

    @Query("SELECT * FROM backup_rules WHERE id = :id")
    suspend fun getById(id: Long): BackupRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BackupRuleEntity): Long

    @Update
    suspend fun update(rule: BackupRuleEntity)

    @Query("UPDATE backup_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE backup_rules SET lastRunAt = :now, lastRunUploaded = :uploaded WHERE id = :id")
    suspend fun markRun(id: Long, uploaded: Int, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM backup_rules WHERE id = :id")
    suspend fun delete(id: Long)
}
