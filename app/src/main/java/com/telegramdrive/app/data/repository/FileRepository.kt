package com.telegramdrive.app.data.repository

import android.content.Context
import com.telegramdrive.app.data.crypto.AesCrypto
import com.telegramdrive.app.data.local.dao.FileDao
import com.telegramdrive.app.data.local.dao.FolderDao
import com.telegramdrive.app.data.local.dao.TransferDao
import com.telegramdrive.app.data.local.entity.FileEntity
import com.telegramdrive.app.data.local.entity.FolderEntity
import com.telegramdrive.app.data.local.entity.TransferEntity
import com.telegramdrive.app.data.remote.telegram.TelegramFileService
import com.telegramdrive.app.data.remote.telegram.TdException
import com.telegramdrive.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level operations on the virtual filesystem backed by Telegram Saved Messages.
 * Coordinates local DB metadata + TelegramFileService + transfer queue.
 */
@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val fileDao: FileDao,
    private val folderDao: FolderDao,
    private val transferDao: TransferDao,
    private val tgFileService: TelegramFileService,
    private val aesCrypto: AesCrypto,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    // ---------- Folders ----------

    fun observeFolders(accountId: Long, parentId: Long?): Flow<List<FolderEntity>> =
        folderDao.observeChildren(accountId, parentId).flowOn(io)

    fun observeRoots(accountId: Long): Flow<List<FolderEntity>> =
        folderDao.observeRoots(accountId).flowOn(io)

    suspend fun createFolder(accountId: Long, parentId: Long?, name: String): Long =
        withContext(io) {
            folderDao.insert(FolderEntity(accountId = accountId, parentFolderId = parentId, name = name))
        }

    suspend fun renameFolder(id: Long, name: String) = withContext(io) { folderDao.rename(id, name) }
    suspend fun moveFolder(id: Long, newParent: Long?) = withContext(io) { folderDao.move(id, newParent) }
    suspend fun deleteFolder(id: Long) = withContext(io) { folderDao.delete(id) }

    // ---------- Files ----------

    fun observeFilesInFolder(accountId: Long, folderId: Long?): Flow<List<FileEntity>> =
        fileDao.observeInFolder(accountId, folderId).flowOn(io)

    fun observeMedia(accountId: Long): Flow<List<FileEntity>> =
        fileDao.observeMedia(accountId).flowOn(io)

    fun observePhotos(accountId: Long): Flow<List<FileEntity>> =
        fileDao.observePhotos(accountId).flowOn(io)

    fun observeVideos(accountId: Long): Flow<List<FileEntity>> =
        fileDao.observeVideos(accountId).flowOn(io)

    fun observeFavorites(accountId: Long): Flow<List<FileEntity>> =
        fileDao.observeFavorites(accountId).flowOn(io)

    suspend fun search(accountId: Long, query: String): List<FileEntity> =
        withContext(io) { fileDao.search(accountId, query) }

    suspend fun setFavorite(id: Long, fav: Boolean) = withContext(io) { fileDao.setFavorite(id, fav) }
    suspend fun renameFile(id: Long, name: String) = withContext(io) { fileDao.rename(id, name) }
    suspend fun moveFile(id: Long, newFolder: Long?) = withContext(io) { fileDao.move(id, newFolder) }

    // ---------- Upload / Download ----------

    /**
     * Enqueue an upload. The TransferService will pick it up and call
     * [runUpload] when the transfer is at the front of the queue.
     */
    suspend fun enqueueUpload(
        accountId: Long,
        localPath: String,
        remoteName: String,
        remoteFolderId: Long?,
        encrypted: Boolean,
        passphrase: CharArray? = null
    ): Long = withContext(io) {
        val transfer = TransferEntity(
            accountId = accountId,
            fileId = null,
            direction = TransferEntity.Direction.UPLOAD,
            localPath = localPath,
            remoteName = remoteName,
            remoteFolderId = remoteFolderId,
            sizeBytes = File(localPath).length(),
            mimeType = guessMime(localPath),
            encrypted = encrypted
        )
        transferDao.insert(transfer)
    }

    /**
     * Actually run the upload — invoked by TransferService.
     * Streams the file to Telegram in chunks via TelegramFileService.
     */
    suspend fun runUpload(transfer: TransferEntity, passphrase: CharArray? = null): Long = withContext(io) {
        val localFile = File(transfer.localPath)
        require(localFile.exists()) { "Local file gone" }

        val key = if (transfer.encrypted && passphrase != null) {
            // Use a per-file salt; we don't persist the key, only the salt + IV (in header on Telegram)
            val salt = aesCrypto.randomSalt()
            aesCrypto.deriveKeyFromPassphrase(passphrase, salt)
        } else null

        var headerMessageId: Long? = null
        tgFileService.upload(transfer.accountId, localFile, transfer.encrypted, key).collect { progress ->
            transferDao.updateProgress(transfer.id, (progress.fraction * transfer.sizeBytes).toLong(),
                TransferEntity.Status.RUNNING)
            progress.headerMessageId?.let { headerMessageId = it }
        }
        val hdrId = headerMessageId ?: throw TdException("No header message")

        // Register the file metadata locally
        val file = FileEntity(
            accountId = transfer.accountId,
            folderId = transfer.remoteFolderId,
            name = transfer.remoteName,
            sizeBytes = transfer.sizeBytes,
            mimeType = transfer.mimeType,
            sha256 = null,
            encrypted = transfer.encrypted,
            headerMessageId = hdrId
        )
        val fileId = fileDao.insert(file)
        transferDao.complete(transfer.id)
        fileId
    }

    suspend fun enqueueDownload(file: FileEntity): Long = withContext(io) {
        val transfer = TransferEntity(
            accountId = file.accountId,
            fileId = file.id,
            direction = TransferEntity.Direction.DOWNLOAD,
            localPath = File(ctx.cacheDir, "downloads/${file.id}_${file.name}").absolutePath,
            remoteName = file.name,
            remoteFolderId = file.folderId,
            sizeBytes = file.sizeBytes,
            mimeType = file.mimeType,
            encrypted = file.encrypted
        )
        transferDao.insert(transfer)
    }

    suspend fun deleteFile(id: Long) = withContext(io) {
        val file = fileDao.getById(id) ?: return@withContext
        // TODO: send TdApi.DeleteMessages for the header + chunk messages
        fileDao.delete(id)
    }

    // ---------- Storage insights ----------

    suspend fun storageStats(accountId: Long): StorageStats = withContext(io) {
        val total = fileDao.totalSizeForAccount(accountId) ?: 0
        val count = fileDao.countForAccount(accountId)
        val byType = fileDao.sizeByMimeType(accountId)
            .map { it.key to it.value }
            .toMap()
        StorageStats(totalSize = total, fileCount = count, byMimeType = byType)
    }

    data class StorageStats(
        val totalSize: Long,
        val fileCount: Int,
        val byMimeType: Map<String, Long>
    )

    private fun guessMime(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
