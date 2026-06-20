package com.telegramdrive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.telegramdrive.app.data.local.entity.TransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_queue WHERE accountId = :accountId ORDER BY priority DESC, createdAt ASC")
    fun observeForAccount(accountId: Long): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer_queue WHERE status IN ('QUEUED','RUNNING','PAUSED') ORDER BY priority DESC, createdAt ASC")
    fun observeActive(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer_queue WHERE status = 'QUEUED' ORDER BY priority DESC, createdAt ASC LIMIT :limit")
    suspend fun nextQueued(limit: Int): List<TransferEntity>

    @Query("SELECT * FROM transfer_queue WHERE status = 'FAILED' AND attempts < maxAttempts ORDER BY createdAt ASC")
    suspend fun retryable(): List<TransferEntity>

    @Query("SELECT * FROM transfer_queue WHERE id = :id")
    suspend fun getById(id: Long): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transfer: TransferEntity): Long

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("UPDATE transfer_queue SET bytesTransferred = :bytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, status: TransferEntity.Status)

    @Query("UPDATE transfer_queue SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: TransferEntity.Status)

    @Query("UPDATE transfer_queue SET status = :status, error = :err, attempts = attempts + 1, finishedAt = :now WHERE id = :id")
    suspend fun fail(id: Long, status: TransferEntity.Status, err: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE transfer_queue SET status = :status, finishedAt = :now WHERE id = :id")
    suspend fun complete(id: Long, status: TransferEntity.Status = TransferEntity.Status.COMPLETED, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM transfer_queue WHERE status IN ('COMPLETED','CANCELLED') AND finishedAt < :before")
    suspend fun cleanupOld(before: Long)

    @Query("SELECT COUNT(*) FROM transfer_queue WHERE status = 'RUNNING'")
    suspend fun runningCount(): Int
}
