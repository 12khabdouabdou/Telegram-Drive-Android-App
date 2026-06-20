package com.telegramdrive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.telegramdrive.app.data.local.entity.FileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE accountId = :accountId AND (folderId IS :folderId OR (folderId IS NULL AND :folderId IS NULL)) ORDER BY name COLLATE NOCASE")
    fun observeInFolder(accountId: Long, folderId: Long?): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE accountId = :accountId AND mimeType LIKE 'image/%' OR mimeType LIKE 'video/%' ORDER BY mediaStoreDateAdded DESC, createdAt DESC")
    fun observeMedia(accountId: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE accountId = :accountId AND mimeType LIKE 'image/%' ORDER BY createdAt DESC")
    fun observePhotos(accountId: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE accountId = :accountId AND mimeType LIKE 'video/%' ORDER BY createdAt DESC")
    fun observeVideos(accountId: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getById(id: Long): FileEntity?

    @Query("SELECT * FROM files WHERE accountId = :accountId AND sha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(accountId: Long, sha256: String): FileEntity?

    @Query("SELECT * FROM files WHERE accountId = :accountId AND mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun findByMediaStoreId(accountId: Long, mediaStoreId: Long): FileEntity?

    @Query("""
        SELECT * FROM files
        WHERE accountId = :accountId
          AND (name LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
        LIMIT 200
    """)
    suspend fun search(accountId: Long, query: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE accountId = :accountId AND favorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(accountId: Long): Flow<List<FileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity): Long

    @Update
    suspend fun update(file: FileEntity)

    @Query("UPDATE files SET favorite = :fav, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE files SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE files SET folderId = :newFolder, updatedAt = :now WHERE id = :id")
    suspend fun move(id: Long, newFolder: Long?, now: Long = System.currentTimeMillis())

    @Query("UPDATE files SET localPath = :path WHERE id = :id")
    suspend fun setLocalCache(id: Long, path: String?)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM files WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("SELECT SUM(sizeBytes) FROM files WHERE accountId = :accountId")
    suspend fun totalSizeForAccount(accountId: Long): Long?

    @Query("""
        SELECT mimeType AS key, SUM(sizeBytes) AS value
        FROM files
        WHERE accountId = :accountId
        GROUP BY mimeType
    """)
    suspend fun sizeByMimeType(accountId: Long): List<MimeTypeSize>
}

data class MimeTypeSize(val key: String, val value: Long)
