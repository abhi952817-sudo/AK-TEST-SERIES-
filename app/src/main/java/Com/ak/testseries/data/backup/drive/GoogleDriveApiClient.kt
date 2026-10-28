package Com.ak.testseries.data.backup.drive

import Com.ak.testseries.data.backup.BackupArchiveEngine
import Com.ak.testseries.data.backup.BackupManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Interface contract for interacting with Google Drive v3 REST API.
 * Pluggable for unit testing without network dependencies.
 */
interface DriveApiClient {
    suspend fun ensureBackupFolder(accessToken: String): Result<String>
    suspend fun uploadBackupFile(
        accessToken: String,
        folderId: String,
        file: File,
        manifest: BackupManifest
    ): Result<GoogleDriveRemoteBackup>
    suspend fun listBackups(
        accessToken: String,
        folderId: String,
        targetUid: String
    ): Result<List<GoogleDriveRemoteBackup>>
    suspend fun downloadBackupFile(
        accessToken: String,
        fileId: String,
        destinationFile: File
    ): Result<File>
    suspend fun deleteBackupFile(
        accessToken: String,
        fileId: String
    ): Result<Boolean>
}

/**
 * Production implementation of Google Drive v3 REST client using OkHttp.
 * Strictly scoped to the authenticated user's private Google Account storage.
 */
class OkHttpGoogleDriveApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : DriveApiClient {

    companion object {
        const val BACKUP_FOLDER_NAME = "AK TEST SERIES Backups"
        private const val BASE_DRIVE_API = "https://www.googleapis.com/drive/v3/files"
        private const val BASE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files"
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
        private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }

    override suspend fun ensureBackupFolder(accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Search for existing folder
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$BACKUP_FOLDER_NAME' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$BASE_DRIVE_API?q=$encodedQuery&fields=files(id,name)&spaces=drive"

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        IOException("Google Drive folder search failed: HTTP ${response.code} $errBody")
                    )
                }

                val jsonResponse = JSONObject(response.body?.string() ?: "{}")
                val files = jsonResponse.optJSONArray("files") ?: JSONArray()
                if (files.length() > 0) {
                    val existingFolderId = files.getJSONObject(0).getString("id")
                    return@withContext Result.success(existingFolderId)
                }
            }

            // 2. Folder does not exist, create it
            val folderMetadata = JSONObject().apply {
                put("name", BACKUP_FOLDER_NAME)
                put("mimeType", "application/vnd.google-apps.folder")
                put("description", "Dedicated vault backups for AK TEST SERIES")
            }

            val createRequest = Request.Builder()
                .url(BASE_DRIVE_API)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(folderMetadata.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(createRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        IOException("Google Drive folder creation failed: HTTP ${response.code} $errBody")
                    )
                }

                val createdJson = JSONObject(response.body?.string() ?: "{}")
                val folderId = createdJson.getString("id")
                Result.success(folderId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadBackupFile(
        accessToken: String,
        folderId: String,
        file: File,
        manifest: BackupManifest
    ): Result<GoogleDriveRemoteBackup> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.isFile) {
                return@withContext Result.failure(IllegalArgumentException("Backup source file not found: ${file.path}"))
            }

            // Drive v3 multipart upload requires multipart/related
            val metadataJson = JSONObject().apply {
                put("name", file.name)
                put("parents", JSONArray().put(folderId))
                put("description", manifest.toJsonString())
                put("appProperties", JSONObject().apply {
                    put("ownerUid", manifest.owner.uid)
                    put("schemaVersion", manifest.databaseSchemaVersion.toString())
                    put("appVersionName", manifest.appVersionName)
                    val manifestChecksum = manifest.checksums[BackupArchiveEngine.MANIFEST_ENTRY_NAME] ?: ""
                    put("checksumSha256", manifestChecksum)
                    put("completeness", manifest.completeness.name)
                    put("privateTestsCount", manifest.stats.privateTestsCount.toString())
                    put("attemptsCount", manifest.stats.attemptsCount.toString())
                    put("questionsCount", manifest.stats.questionsCount.toString())
                    put("diagramsCount", manifest.stats.diagramsCount.toString())
                })
            }

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addPart(file.asRequestBody(OCTET_STREAM_MEDIA_TYPE))
                .build()

            val uploadUrl = "$BASE_UPLOAD_API?uploadType=multipart&fields=id,name,size,createdTime,modifiedTime,description,appProperties"

            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        IOException("Google Drive backup upload failed: HTTP ${response.code} $errBody")
                    )
                }

                val responseBody = response.body?.string() ?: "{}"
                val json = JSONObject(responseBody)
                val remoteBackup = GoogleDriveRemoteBackup.fromDriveFileJson(json)
                if (remoteBackup != null) {
                    Result.success(remoteBackup.copy(driveFolderId = folderId))
                } else {
                    Result.failure(IllegalStateException("Failed to parse remote backup metadata from Drive response."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listBackups(
        accessToken: String,
        folderId: String,
        targetUid: String
    ): Result<List<GoogleDriveRemoteBackup>> = withContext(Dispatchers.IO) {
        val safeUid = targetUid.trim()
        if (safeUid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Authenticated UID is required to list backups."))
        }

        try {
            val query = "'$folderId' in parents and mimeType != 'application/vnd.google-apps.folder' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val listUrl = "$BASE_DRIVE_API?q=$encodedQuery&fields=files(id,name,size,createdTime,modifiedTime,description,appProperties)&orderBy=createdTime desc&pageSize=50"

            val request = Request.Builder()
                .url(listUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        IOException("Google Drive list backups failed: HTTP ${response.code} $errBody")
                    )
                }

                val jsonResponse = JSONObject(response.body?.string() ?: "{}")
                val files = jsonResponse.optJSONArray("files") ?: JSONArray()
                val backupList = mutableListOf<GoogleDriveRemoteBackup>()

                for (i in 0 until files.length()) {
                    val fileObj = files.getJSONObject(i)
                    val backup = GoogleDriveRemoteBackup.fromDriveFileJson(fileObj)
                    // Strict zero-trust filter: only include backups owned by active user
                    if (backup != null && (backup.ownerUid == safeUid || backup.name.contains(safeUid))) {
                        backupList.add(backup.copy(driveFolderId = folderId, ownerUid = safeUid))
                    }
                }

                // Sort newest first
                backupList.sortByDescending { if (it.modifiedTimeMillis > 0) it.modifiedTimeMillis else it.createdTimeMillis }
                Result.success(backupList)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadBackupFile(
        accessToken: String,
        fileId: String,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destinationFile.parentFile?.mkdirs()

            val downloadUrl = "$BASE_DRIVE_API/$fileId?alt=media"
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        IOException("Google Drive download failed: HTTP ${response.code} $errBody")
                    )
                }

                val inputStream = response.body?.byteStream()
                    ?: return@withContext Result.failure(IOException("Empty response body from Google Drive."))

                FileOutputStream(destinationFile).use { output ->
                    inputStream.copyTo(output)
                }

                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteBackupFile(
        accessToken: String,
        fileId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val deleteUrl = "$BASE_DRIVE_API/$fileId"
            val request = Request.Builder()
                .url(deleteUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 404) {
                    Result.success(true)
                } else {
                    val errBody = response.body?.string() ?: ""
                    Result.failure(IOException("Google Drive delete failed: HTTP ${response.code} $errBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
