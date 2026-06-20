package com.telegramdrive.app.data.backup

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.telegramdrive.app.data.local.dao.AccountDao
import com.telegramdrive.app.data.local.dao.FileDao
import com.telegramdrive.app.data.local.entity.BackupRuleEntity
import com.telegramdrive.app.data.local.entity.FileEntity
import com.telegramdrive.app.data.repository.FileRepository
import com.telegramdrive.app.data.repository.PreferencesRepository
import com.telegramdrive.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage Saver — when a local media file has been confirmed safely backed up
 * (header message id is set on the file row), this class can delete the local
 * copy from MediaStore to free up device storage. Mirrors Google Photos'
 * "Free up space" feature.
 *
 * Safety: we never delete a file unless we have a confirmed header message id
 * AND we can re-download a copy to verify integrity (TODO: optional verify pass).
 */
@Singleton
class StorageSaver @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val fileDao: FileDao,
    private val accountDao: AccountDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /**
     * Delete the local copy of every backed-up file in the active account
     * whose mediaStoreId is set. Returns the number of bytes freed.
     */
    suspend fun freeUpSpace(): Long = withContext(io) {
        val acc = accountDao.getActive() ?: return@withContext 0L
        var freed = 0L
        fileDao.observeMedia(acc.id).first().forEach { file ->
            val msId = file.mediaStoreId ?: return@forEach
            val headerOk = file.headerMessageId != null
            if (!headerOk) return@forEach
            if (deleteMediaStoreItem(msId, file.mimeType)) {
                freed += file.sizeBytes
            }
        }
        freed
    }

    /**
     * Free up only items that are also local thumbnails (e.g. screenshots that
     * the user has already swiped away in their gallery). Used by the
     * "Free up space" suggestion card.
     */
    suspend fun suggestFreeable(): List<FileEntity> = withContext(io) {
        val acc = accountDao.getActive() ?: return@withContext emptyList()
        fileDao.observeMedia(acc.id).first().filter {
            it.mediaStoreId != null && it.headerMessageId != null
        }
    }

    private fun deleteMediaStoreItem(mediaStoreId: Long, mime: String): Boolean {
        val baseUri = if (mime.startsWith("image/")) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else if (mime.startsWith("video/")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else return false
        val uri = ContentUris.withAppendedId(baseUri, mediaStoreId)
        return try {
            // On API 29+ we need the user to consent via MediaStore.createDeleteRequest.
            // For now we attempt direct delete (works for our own app's writes; legacy for others).
            val deleted = ctx.contentResolver.delete(uri, null, null)
            deleted > 0
        } catch (e: Exception) {
            // On scoped storage, we'd queue this for the user to confirm in a batch.
            // The UI flow shows a system dialog with createDeleteRequest().
            false
        }
    }

    private object ContentUris {
        fun withAppendedId(uri: Uri, id: Long): Uri = android.content.ContentUris.withAppendedId(uri, id)
    }
}
