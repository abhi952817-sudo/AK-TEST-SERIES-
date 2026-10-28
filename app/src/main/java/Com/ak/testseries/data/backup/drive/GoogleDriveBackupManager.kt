package Com.ak.testseries.data.backup.drive

import android.content.Context
import Com.ak.testseries.data.AppDatabase
import Com.ak.testseries.data.backup.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-level coordinator for Google Drive cloud backup export, listing,
 * downloading, and staging-first validation.
 *
 * Rules:
 * - Backups are tied exclusively to the active user's Google Account and authenticated Firebase UID.
 * - Staging-first validation before any restoration is performed.
 * - Temporary files are strictly cleaned up.
 */
open class GoogleDriveBackupManager(
    val driveApiClient: DriveApiClient = OkHttpGoogleDriveApiClient()
) {

    /**
     * Packages the active user's account data into a local `.aktsbak` archive and
     * uploads it to the user's private Google Drive application folder.
     */
    open suspend fun exportAndUploadToDrive(
        context: Context,
        db: AppDatabase,
        activeUid: String,
        email: String? = null,
        displayName: String? = null,
        appVersionCode: Int = 1,
        appVersionName: String = "1.0",
        includeDiagrams: Boolean = true
    ): Result<GoogleDriveRemoteBackup> = withContext(Dispatchers.IO) {
        val safeUid = activeUid.trim()
        if (safeUid.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Authenticated user ID is required for cloud backup.")
            )
        }

        // 0. Verify account binding between active Firebase UID and Google Drive account
        val bindingResult = GoogleDriveAuthManager.verifyAccountBinding(context, safeUid)
        if (bindingResult.isFailure) {
            return@withContext Result.failure(
                bindingResult.exceptionOrNull() ?: SecurityException("Google Drive account binding verification failed.")
            )
        }

        // 1. Obtain OAuth Access Token
        val tokenResult = GoogleDriveAuthManager.getAccessToken(context)
        if (tokenResult.isFailure) {
            return@withContext Result.failure(
                tokenResult.exceptionOrNull() ?: IllegalStateException("Unable to obtain Google Drive access token.")
            )
        }
        val accessToken = tokenResult.getOrThrow()

        // 2. Prepare temporary export staging directory and file
        val stagingDir = File(context.cacheDir, "drive_export_staging")
        stagingDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        val tempBackupFile = File(stagingDir, "ak_backup_${safeUid}_$timestamp.aktsbak")

        try {
            // 3. Export account data to local archive
            val manifestResult = FileOutputStream(tempBackupFile).use { fos ->
                AccountDataBackupAdapter.exportAccountBackup(
                    context = context,
                    db = db,
                    uid = safeUid,
                    email = email,
                    displayName = displayName,
                    appVersionCode = appVersionCode,
                    appVersionName = appVersionName,
                    outputStream = fos,
                    includeDiagrams = includeDiagrams
                )
            }

            if (manifestResult.isFailure) {
                return@withContext Result.failure(
                    manifestResult.exceptionOrNull() ?: IllegalStateException("Failed to export account backup archive.")
                )
            }
            val manifest = manifestResult.getOrThrow()

            // 3.1 Validate local archive integrity before upload
            val validationCheck = tempBackupFile.inputStream().use { fis ->
                BackupArchiveEngine.validateArchive(fis)
            }
            if (validationCheck is BackupValidationResult.Invalid) {
                return@withContext Result.failure(
                    IllegalStateException("Local backup archive validation failed prior to upload: ${validationCheck.reason}")
                )
            }

            // 4. Ensure destination folder on user's Google Drive
            val folderResult = driveApiClient.ensureBackupFolder(accessToken)
            if (folderResult.isFailure) {
                return@withContext Result.failure(
                    folderResult.exceptionOrNull() ?: IllegalStateException("Failed to find or create Google Drive backup folder.")
                )
            }
            val folderId = folderResult.getOrThrow()
            GoogleDriveAuthManager.saveDriveFolderId(context, folderId)

            // 5. Upload backup archive to Google Drive
            val uploadResult = driveApiClient.uploadBackupFile(
                accessToken = accessToken,
                folderId = folderId,
                file = tempBackupFile,
                manifest = manifest
            )

            if (uploadResult.isSuccess) {
                val remoteBackup = uploadResult.getOrThrow()
                if (remoteBackup.fileId.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("Remote upload succeeded but returned an invalid/blank fileId.")
                    )
                }

                // Update last sync state only after confirmed upload
                GoogleDriveAuthManager.saveLastSyncTimestamp(
                    context = context,
                    timestamp = timestamp,
                    statsSummary = remoteBackup.statsSummary
                )

                // Enforce retention policy: prune excess older backups
                try {
                    GoogleDriveRetentionManager.pruneExcessBackups(
                        driveApiClient = driveApiClient,
                        accessToken = accessToken,
                        folderId = folderId,
                        ownerUid = safeUid,
                        maxToKeep = GoogleDriveRetentionManager.DEFAULT_MAX_BACKUPS_TO_RETAIN
                    )
                } catch (_: Exception) {
                    // Retention pruning is non-fatal
                }

                Result.success(remoteBackup)
            } else {
                uploadResult
            }
        } finally {
            // Always clean up local temporary upload file
            if (tempBackupFile.exists()) {
                tempBackupFile.delete()
            }
        }
    }

    /**
     * Lists all remote `.aktsbak` backups belonging to the authenticated user
     * stored in their Google Drive folder.
     */
    suspend fun fetchUserRemoteBackups(
        context: Context,
        activeUid: String
    ): Result<List<GoogleDriveRemoteBackup>> = withContext(Dispatchers.IO) {
        val safeUid = activeUid.trim()
        if (safeUid.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Authenticated user ID is required to fetch cloud backups.")
            )
        }

        val tokenResult = GoogleDriveAuthManager.getAccessToken(context)
        if (tokenResult.isFailure) {
            return@withContext Result.failure(
                tokenResult.exceptionOrNull() ?: IllegalStateException("Unable to obtain Google Drive access token.")
            )
        }
        val accessToken = tokenResult.getOrThrow()

        // Locate folder ID or find it
        var folderId = GoogleDriveAuthManager.getDriveFolderId(context)
        if (folderId.isNullOrBlank()) {
            val folderResult = driveApiClient.ensureBackupFolder(accessToken)
            if (folderResult.isFailure) {
                return@withContext Result.failure(
                    folderResult.exceptionOrNull() ?: IllegalStateException("Failed to find Google Drive backup folder.")
                )
            }
            folderId = folderResult.getOrThrow()
            GoogleDriveAuthManager.saveDriveFolderId(context, folderId)
        }

        driveApiClient.listBackups(accessToken, folderId, safeUid)
    }

    /**
     * Downloads a selected remote backup from Google Drive, saves it in staging cache,
     * and executes complete safety validation via [AccountDataRestoreEngine.stageAndValidateAccountBackup].
     *
     * Returns [StageValidationResult.Success] ready for user review and confirmation.
     */
    suspend fun downloadAndStageDriveBackup(
        context: Context,
        fileId: String,
        activeUid: String
    ): RemoteStageOutcome = withContext(Dispatchers.IO) {
        val safeUid = activeUid.trim()
        if (safeUid.isBlank()) {
            return@withContext RemoteStageOutcome(
                archiveFile = null,
                stageValidationResult = StageValidationResult.Failure(
                    reason = "Active authenticated UID is required for download and staging."
                )
            )
        }

        val tokenResult = GoogleDriveAuthManager.getAccessToken(context)
        if (tokenResult.isFailure) {
            return@withContext RemoteStageOutcome(
                archiveFile = null,
                stageValidationResult = StageValidationResult.Failure(
                    reason = "Failed to authenticate with Google Drive: ${tokenResult.exceptionOrNull()?.message}",
                    cause = tokenResult.exceptionOrNull()
                )
            )
        }
        val accessToken = tokenResult.getOrThrow()

        val downloadDir = File(context.cacheDir, "drive_download_staging")
        downloadDir.mkdirs()
        val stagedArchiveFile = File(downloadDir, "remote_${fileId}_${System.currentTimeMillis()}.aktsbak")

        try {
            val downloadResult = driveApiClient.downloadBackupFile(accessToken, fileId, stagedArchiveFile)
            if (downloadResult.isFailure) {
                stagedArchiveFile.delete()
                return@withContext RemoteStageOutcome(
                    archiveFile = null,
                    stageValidationResult = StageValidationResult.Failure(
                        reason = "Failed to download backup archive from Google Drive: ${downloadResult.exceptionOrNull()?.message}",
                        cause = downloadResult.exceptionOrNull()
                    )
                )
            }

            // Run staging validation
            val validationResult = stagedArchiveFile.inputStream().use { inputStream ->
                AccountDataRestoreEngine.stageAndValidateAccountBackup(
                    context = context,
                    authenticatedUid = safeUid,
                    archiveInputStream = inputStream
                )
            }

            // If staging validation failed, clean up the downloaded archive
            if (validationResult is StageValidationResult.Failure) {
                stagedArchiveFile.delete()
                RemoteStageOutcome(archiveFile = null, stageValidationResult = validationResult)
            } else {
                RemoteStageOutcome(archiveFile = stagedArchiveFile, stageValidationResult = validationResult)
            }
        } catch (e: Exception) {
            stagedArchiveFile.delete()
            RemoteStageOutcome(
                archiveFile = null,
                stageValidationResult = StageValidationResult.Failure(
                    reason = "Unexpected error during cloud backup download and staging: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Deletes a remote backup file from Google Drive.
     */
    suspend fun deleteRemoteBackup(
        context: Context,
        fileId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val tokenResult = GoogleDriveAuthManager.getAccessToken(context)
        if (tokenResult.isFailure) {
            return@withContext Result.failure(
                tokenResult.exceptionOrNull() ?: IllegalStateException("Unable to obtain Google Drive access token.")
            )
        }
        driveApiClient.deleteBackupFile(tokenResult.getOrThrow(), fileId)
    }
}

/**
 * Encapsulates the downloaded archive file and the result of two-phase staging validation.
 */
data class RemoteStageOutcome(
    val archiveFile: File?,
    val stageValidationResult: StageValidationResult
)

