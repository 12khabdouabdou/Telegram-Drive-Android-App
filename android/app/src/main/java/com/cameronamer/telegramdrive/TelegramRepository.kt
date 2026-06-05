package com.cameronamer.telegramdrive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelegramRepository {

    suspend fun requestCode(phone: String, apiId: Int, apiHash: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = uniffi.telegram_drive.requestCode(phone, apiId, apiHash)
                if (result.startsWith("Error")) {
                    throw Exception(result)
                }
                result
            }
        }

    suspend fun signIn(code: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = uniffi.telegram_drive.signIn(code)
                if (result.startsWith("Error")) {
                    throw Exception(result)
                }
                result
            }
        }

    suspend fun checkPassword(password: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                uniffi.telegram_drive.checkPassword(password)
            }
        }

    suspend fun logout(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                uniffi.telegram_drive.logout()
            }
        }

    suspend fun listFiles(folderId: Long? = null): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                uniffi.telegram_drive.listFiles(folderId)
            }
        }

    suspend fun uploadFile(path: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = uniffi.telegram_drive.uploadFile(path)
                if (result.startsWith("Error")) {
                    throw Exception(result)
                }
                result
            }
        }

    suspend fun deleteFile(messageId: Int, folderId: Long? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                uniffi.telegram_drive.deleteFile(messageId, folderId)
            }
        }
}
