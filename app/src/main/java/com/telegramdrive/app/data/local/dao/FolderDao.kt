package com.telegramdrive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.telegramdrive.app.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE accountId = :accountId AND (parentFolderId IS :parentId OR (parentFolderId IS NULL AND :parentId IS NULL)) ORDER BY name COLLATE NOCASE")
    fun observeChildren(accountId: Long, parentId: Long?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND parentFolderId IS NULL ORDER BY name COLLATE NOCASE")
    fun observeRoots(accountId: Long): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE folders SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE folders SET parentFolderId = :newParent, updatedAt = :now WHERE id = :id")
    suspend fun move(id: Long, newParent: Long?, now: Long = System.currentTimeMillis())
}
