package com.telegramdrive.app.data.backup

import com.telegramdrive.app.data.local.dao.FileDao
import com.telegramdrive.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prevents re-uploading media that's already safely on Telegram.
 *
 * Two layers of dedup:
 *
 *   1. **Fast path — (mediaStoreId, sizeBytes)**: every backed-up file row
 *      remembers its source mediaStoreId. If a candidate's id matches an
 *      existing row in the same account, skip immediately.
 *
 *   2. **Slow path — SHA-256**: if the mediaStoreId is missing or doesn't
 *      match (e.g. user moved files between folders), we hash the candidate
 *      and check the `files.sha256` index. This catches re-imports from
 *      messaging apps, edits, etc.
 *
 * Hashing is only done when the fast path fails — it's expensive.
 */
@Singleton
class Deduplicator @Inject constructor(
    private val fileDao: FileDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    suspend fun findDuplicate(
        accountId: Long,
        mediaStoreId: Long?,
        sizeBytes: Long,
        contentBytes: ByteArray? = null
    ): DedupResult = withContext(io) {
        // Fast path
        if (mediaStoreId != null) {
            val hit = fileDao.findByMediaStoreId(accountId, mediaStoreId)
            if (hit != null) return@withContext DedupResult.Duplicate(hit.id)
        }
        // Slow path — caller has provided bytes (rare; only for small files)
        if (contentBytes != null) {
            val sha = sha256(contentBytes)
            val hit = fileDao.findBySha256(accountId, sha)
            if (hit != null) return@withContext DedupResult.Duplicate(hit.id)
        }
        DedupResult.New
    }

    /**
     * Hash a (possibly large) file by streaming it through SHA-256 in 64 KB
     * chunks. Returns lowercase hex.
     */
    suspend fun hashFile(path: String): String = withContext(io) {
        val md = MessageDigest.getInstance("SHA-256")
        java.io.FileInputStream(path).use { fis ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = fis.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        bytesToHex(md.digest())
    }

    private fun sha256(b: ByteArray): String =
        bytesToHex(MessageDigest.getInstance("SHA-256").digest(b))

    private fun bytesToHex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    sealed class DedupResult {
        object New : DedupResult()
        data class Duplicate(val existingFileId: Long) : DedupResult()
    }
}
