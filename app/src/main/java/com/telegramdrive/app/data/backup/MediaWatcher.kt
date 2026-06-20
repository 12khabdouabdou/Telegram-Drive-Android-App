package com.telegramdrive.app.data.backup

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.telegramdrive.app.data.local.entity.BackupRuleEntity
import com.telegramdrive.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the MediaStore for new photos/videos that match an active backup rule
 * and emits them as [PendingBackup] candidates.
 *
 * Two complementary mechanisms:
 *
 *  1. **ContentObserver** (real-time, when app is alive):
 *     Registers on MediaStore.Images.Media.EXTERNAL_CONTENT_URI + Video for the
 *     lifetime of the observer flow. New screenshots/camera shots are picked
 *     up almost immediately.
 *
 *  2. **Polling fallback** (for when the observer was missed because the app
 *     was killed): the [AutoBackupWorker] runs every ~15 min via WorkManager
 *     and queries MediaStore for items added since the last sync watermark.
 */
@Singleton
class MediaWatcher @Inject constructor(
    @ApplicationContext private val ctx: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /**
     * Stream of (uri, displayName, size, dateAdded, mimeType, mediaStoreId)
     * for every new media item observed in real-time. Consumers filter by
     * active backup rules.
     */
    fun observeNewMedia(): Flow<PendingBackup> = callbackFlow {
        val resolver = ctx.contentResolver
        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                val u = uri ?: return
                kotlinx.coroutines.runBlocking {
                    probeItem(resolver, u)?.let { trySend(it) }
                }
            }
        }
        uris.forEach { resolver.registerContentObserver(it, true, observer) }
        awaitClose { uris.forEach { resolver.unregisterContentObserver(observer) } }
    }.flowOn(io) as Flow<PendingBackup>

    /**
     * Query MediaStore for media added since [sinceDateAddedSeconds]. Used by
     * the periodic AutoBackupWorker to catch items that arrived while the app
     * wasn't running.
     */
    suspend fun queryAddedSince(
        sinceDateAddedSeconds: Long,
        mimeTypes: List<String>,
        includedFolders: List<String>,
        excludedFolders: List<String>
    ): List<PendingBackup> = withContext(io) {
        val resolver = ctx.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val results = mutableListOf<PendingBackup>()

        // Query both images and videos
        listOf(
            Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/", "Images"),
            Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/", "Videos")
        ).forEach { (baseUri, prefix, _) ->
            val mimeFilter = if (mimeTypes.any { it == "image/*" || it == "video/*" }) {
                // accept any in the prefix
                "$prefix%"
            } else {
                // Specific mime types
                val inClause = mimeTypes.joinToString(",") { "?" }
                "mime_type IN ($inClause)"
            }
            val sel = "date_added >= ? AND ($mimeFilter)"
            val args = mutableListOf(sinceDateAddedSeconds.toString())
            if (!mimeFilter.endsWith("%")) {
                args.addAll(mimeTypes)
            }
            resolver.query(baseUri, arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DATA
            ), sel, args.toTypedArray(), "date_added ASC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val relPathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: continue
                    val size = c.getLong(sizeCol)
                    val date = c.getLong(dateCol)
                    val mime = c.getString(mimeCol) ?: "application/octet-stream"
                    val relPath = c.getString(relPathCol) ?: ""
                    // Folder filters
                    val included = includedFolders.isEmpty() || includedFolders.any { relPath.startsWith(it, ignoreCase = true) }
                    val excluded = excludedFolders.any { relPath.startsWith(it, ignoreCase = true) }
                    if (!included || excluded) continue
                    val itemUri = ContentUris.withAppendedId(baseUri, id)
                    results += PendingBackup(
                        mediaStoreId = id,
                        uri = itemUri,
                        displayName = name,
                        sizeBytes = size,
                        dateAddedSeconds = date,
                        mimeType = mime,
                        relativePath = relPath
                    )
                }
            }
        }
        results
    }

    private suspend fun probeItem(resolver: ContentResolver, uri: Uri): PendingBackup? = withContext(io) {
        resolver.query(uri, arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH
        ), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@withContext null
            PendingBackup(
                mediaStoreId = c.getLong(0),
                uri = uri,
                displayName = c.getString(1) ?: "?",
                sizeBytes = c.getLong(2),
                dateAddedSeconds = c.getLong(3),
                mimeType = c.getString(4) ?: "application/octet-stream",
                relativePath = c.getString(5) ?: ""
            )
        }
    }

    data class PendingBackup(
        val mediaStoreId: Long,
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val dateAddedSeconds: Long,
        val mimeType: String,
        val relativePath: String
    )
}
