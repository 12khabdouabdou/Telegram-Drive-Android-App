package com.telegramdrive.app.data.remote.telegram

import com.telegramdrive.app.data.crypto.AesCrypto
import com.telegramdrive.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements Telegram-Drive's core file-storage strategy:
 *
 *   - Each user has a single "Saved Messages" chat (or a private channel for
 *     Premium users who want more storage).
 *   - A large file is split into chunks of ≤ [CHUNK_SIZE] bytes.
 *   - Each chunk is uploaded as a separate document message to Saved Messages.
 *   - The first chunk's caption is a JSON header describing the file:
 *       { "v": 1, "name": "...", "size": 12345, "sha256": "...", "mime": "...",
 *         "encrypted": false, "chunks": [msg_id_1, msg_id_2, ...], "iv": "..." }
 *   - To download, read the header, fetch each chunk's file, concatenate.
 *
 * Because Saved Messages is unlimited (per-message ≤ 2 GB), this gives
 * users effectively unlimited cloud storage.
 */
@Singleton
class TelegramFileService @Inject constructor(
    private val tdLibManager: TdLibManager,
    private val aesCrypto: AesCrypto,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    companion object {
        // 1.5 GB per chunk — comfortably below Telegram's 2 GB / 4 GB (Premium) cap
        const val CHUNK_SIZE: Long = 1_500L * 1024 * 1024
        const val HEADER_VERSION = 1
    }

    /**
     * Upload a file to the given account's Saved Messages, splitting into
     * chunks as needed. Emits progress (0..1) on every chunk boundary.
     *
     * @param accountId active Telegram account
     * @param localFile file on disk to upload
     * @param encrypt if true, encrypt chunks with AES-256-GCM using [key]
     *                (passphrase-derived; see AesCrypto.deriveKey)
     * @param key raw AES key (32 bytes) — only used when [encrypt] is true
     * @return [UploadedFile] with chunk message ids + sha256 + size
     */
    fun upload(
        accountId: Long,
        localFile: File,
        encrypt: Boolean,
        key: ByteArray?
    ): Flow<UploadProgress> = flow {
        require(localFile.exists()) { "File not found: ${localFile.absolutePath}" }
        val client = tdLibManager.clientFor(accountId)
            ?: throw TdException("No TDLib client for account $accountId")

        val totalSize = localFile.length()
        val chunkCount = ((totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)
        val savedMessagesChatId = getSavedMessagesChatId(client)
        val sha = MessageDigest.getInstance("SHA-256")
        val iv = if (encrypt) aesCrypto.randomIv() else null

        val chunkMessageIds = mutableListOf<Long>()
        var bytesSent = 0L

        // Upload chunks sequentially (parallel uploads are at the transfer-queue level)
        RandomAccessFile(localFile, "r").use { raf ->
            for (chunkIndex in 0 until chunkCount) {
                val offset = chunkIndex * CHUNK_SIZE
                val size = minOf(CHUNK_SIZE, totalSize - offset)
                val chunkBytes = ByteArray(size.toInt())
                raf.seek(offset)
                raf.readFully(chunkBytes)

                val (payload, encFlag) = if (encrypt && key != null) {
                    aesCrypto.encryptChunk(chunkBytes, key, iv!!, offset) to true
                } else chunkBytes to false

                if (!encrypt) sha.update(chunkBytes)

                val tmp = File.createTempFile("chunk_${chunkIndex}_", ".bin", localFile.parentFile)
                tmp.writeBytes(payload)

                val msg = uploadDocument(
                    client = client,
                    chatId = savedMessagesChatId,
                    file = tmp,
                    caption = if (chunkIndex == 0) headerCaption(
                        name = localFile.name,
                        size = totalSize,
                        mime = detectMime(localFile),
                        encrypted = encFlag,
                        chunkCount = chunkCount,
                        iv = iv
                    ) else ""
                )
                chunkMessageIds += msg.id
                tmp.delete()

                bytesSent += size
                emit(UploadProgress(bytesSent.toFloat() / totalSize, chunkIndex + 1, chunkCount))
            }
        }

        // Build header message — re-post the first chunk with the full header
        // (TDLib doesn't let us edit a caption we just sent in one round-trip cleanly,
        //  so we send an explicit header message after all chunks.)
        val finalSha = if (!encrypt) bytesToHex(sha.digest()) else null
        val headerJson = buildHeaderJson(
            name = localFile.name,
            size = totalSize,
            mime = detectMime(localFile),
            encrypted = encrypt,
            chunkCount = chunkCount,
            chunkMessageIds = chunkMessageIds,
            iv = iv,
            sha256 = finalSha
        )
        val headerMsg = client.send<TdApi.Message>(TdApi.SendMessage(
            savedMessagesChatId,
            0,
            null,
            null,
            TdApi.InputMessageText(TdApi.FormattedText(headerJson, null), false, false)
        ))
        emit(UploadProgress(1f, chunkCount, chunkCount, headerMessageId = headerMsg.id))

    }.flowOn(io)

    /**
     * Download a file that was previously uploaded. Reads the header message,
     * fetches each chunk's file, optionally decrypts, and writes to [outFile].
     */
    fun download(
        accountId: Long,
        header: FileHeader,
        outFile: File
    ): Flow<DownloadProgress> = flow {
        val client = tdLibManager.clientFor(accountId)
            ?: throw TdException("No TDLib client for account $accountId")

        val key = header.encryptionKey // already derived upstream
        outFile.parentFile?.mkdirs()
        val raf = RandomAccessFile(outFile, "rw")
        raf.setLength(header.size)
        try {
            for ((i, msgId) in header.chunkMessageIds.withIndex()) {
                val chunkFile = downloadMessageFile(client, msgId)
                val bytes = chunkFile.readBytes()
                chunkFile.delete()
                val payload = if (header.encrypted && key != null) {
                    aesCrypto.decryptChunk(bytes, key, header.iv!!, i.toLong() * CHUNK_SIZE)
                } else bytes
                raf.seek(i.toLong() * CHUNK_SIZE)
                raf.write(payload)
                emit(DownloadProgress((i + 1).toFloat() / header.chunkMessageIds.size))
            }
        } finally {
            raf.close()
        }
    }.flowOn(io)

    private suspend fun getSavedMessagesChatId(client: TdClient): Long {
        val chat = client.send<TdApi.Chat>(TdApi.CreatePrivateChat(0L, true))
        return chat.id // TdApi "Saved Messages" uses chatId == self user id resolved via CreatePrivateChat(0)
    }

    private suspend fun uploadDocument(
        client: TdClient,
        chatId: Long,
        file: File,
        caption: String
    ): TdApi.Message {
        val inputFile = TdApi.InputFileLocal(file.absolutePath)
        val content = TdApi.InputMessageDocument(
            inputFile,
            null,
            false,
            TdApi.FormattedText(caption, null)
        )
        return client.send<TdApi.Message>(TdApi.SendMessage(chatId, 0, null, null, content))
    }

    private suspend fun downloadMessageFile(client: TdClient, messageId: Long): File {
        val msg = client.send<TdApi.Message>(TdApi.GetMessage(messageId))
        val doc = (msg.content as? TdApi.MessageDocument)?.document
            ?: throw TdException("Message $messageId is not a document")
        val fileId = doc.document.id
        val localPath = doc.document.local.path
        if (localPath.isNotEmpty()) return File(localPath)
        // Trigger download
        client.send<TdApi.File>(TdApi.DownloadFile(fileId, 1, 0, 0, true))
        // Poll for completion
        var file: TdApi.File
        do {
            file = client.send<TdApi.File>(TdApi.GetFile(fileId))
            if (file.local.isDownloadingCompleted) break
            kotlinx.coroutines.delay(200)
        } while (file.local.isDownloadingActive)
        return File(file.local.path)
    }

    private fun detectMime(f: File): String {
        val ext = f.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            "json" -> "application/json"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    private fun headerCaption(
        name: String, size: Long, mime: String, encrypted: Boolean,
        chunkCount: Int, iv: ByteArray?
    ): String {
        // Captions are limited to 1024 chars — we post a marker here, the real
        // header goes in its own message after all chunks are uploaded.
        return "[tgdrive:v$HEADER_VERSION:${java.util.UUID.randomUUID()}]"
    }

    private fun buildHeaderJson(
        name: String, size: Long, mime: String, encrypted: Boolean,
        chunkCount: Int, chunkMessageIds: List<Long>, iv: ByteArray?, sha256: String?
    ): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"v\":$HEADER_VERSION,")
        sb.append("\"name\":${escape(name)},")
        sb.append("\"size\":$size,")
        sb.append("\"mime\":${escape(mime)},")
        sb.append("\"encrypted\":$encrypted,")
        sb.append("\"chunks\":$chunkCount,")
        if (iv != null) sb.append("\"iv\":\"${android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)}\",")
        if (sha256 != null) sb.append("\"sha256\":\"$sha256\",")
        sb.append("\"msg_ids\":[${chunkMessageIds.joinToString(",")}]")
        sb.append('}')
        return sb.toString()
    }

    private fun escape(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun bytesToHex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    data class UploadProgress(
        val fraction: Float,
        val chunksUploaded: Int,
        val totalChunks: Int,
        val headerMessageId: Long? = null
    )

    data class DownloadProgress(val fraction: Float)

    data class FileHeader(
        val name: String,
        val size: Long,
        val mime: String,
        val encrypted: Boolean,
        val iv: ByteArray?,
        val sha256: String?,
        val chunkMessageIds: List<Long>,
        val encryptionKey: ByteArray? = null
    )
}
