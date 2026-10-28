package Com.ak.testseries.data.backup.drive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import Com.ak.testseries.data.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Background WorkManager worker responsible for executing periodic or on-demand
 * backups to the user's personal Google Drive account.
 *
 * Adheres to:
 * - Zero-trust account containment: only uploads data for the currently authenticated user.
 * - Idempotency & transient retry: safely retries on network failures with exponential backoff.
 * - Non-destructive execution: never touches live data during backup.
 */
class GoogleDriveBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    // Internal hook for testing injection
    var driveBackupManager: GoogleDriveBackupManager = GoogleDriveBackupManager()

    override suspend fun doWork(): Result {
        val context = applicationContext

        // 1. Check if Google Drive backup is enabled and connected
        if (!GoogleDriveAuthManager.isDriveConnected(context)) {
            return Result.success(workDataOf("skipped" to "Google Drive not connected"))
        }

        val frequency = GoogleDriveAuthManager.getAutoBackupFrequency(context)
        if (frequency.equals("OFF", ignoreCase = true)) {
            return Result.success(workDataOf("skipped" to "Auto-backup disabled by user"))
        }

        // 2. Identify the active authenticated user
        val firebaseAuth = try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
        val currentUser = firebaseAuth?.currentUser
        val activeUid = currentUser?.uid

        if (activeUid.isNullOrBlank()) {
            return Result.success(workDataOf("skipped" to "No authenticated user logged in"))
        }

        // 2.1 Verify account binding between active Firebase UID and Google Drive account
        val bindingCheck = GoogleDriveAuthManager.verifyAccountBinding(context, activeUid)
        if (bindingCheck.isFailure) {
            return Result.failure(workDataOf("error" to (bindingCheck.exceptionOrNull()?.message ?: "Drive account mismatch with active user")))
        }

        val userEmail = currentUser.email ?: GoogleDriveAuthManager.getConnectedEmail(context)
        val userDisplayName = currentUser.displayName ?: GoogleDriveAuthManager.getConnectedDisplayName(context)

        // 3. Obtain database
        val db = try {
            AppDatabase.getDatabase(context)
        } catch (e: Exception) {
            return Result.failure(workDataOf("error" to (e.message ?: "Database unavailable")))
        }

        // 4. Execute export and upload
        return try {
            val uploadResult = driveBackupManager.exportAndUploadToDrive(
                context = context,
                db = db,
                activeUid = activeUid,
                email = userEmail,
                displayName = userDisplayName,
                includeDiagrams = true
            )

            if (uploadResult.isSuccess) {
                val remoteBackup = uploadResult.getOrThrow()
                GoogleDriveBackupNotificationHelper.showBackupSuccessNotification(
                    context = context,
                    statsSummary = remoteBackup.statsSummary
                )
                Result.success(
                    workDataOf(
                        "file_id" to remoteBackup.fileId,
                        "file_name" to remoteBackup.name,
                        "stats" to remoteBackup.statsSummary
                    )
                )
            } else {
                val error = uploadResult.exceptionOrNull() ?: Exception("Unknown upload failure")
                handleFailure(context, error)
            }
        } catch (e: Exception) {
            handleFailure(context, e)
        }
    }

    private fun handleFailure(context: Context, error: Throwable): Result {
        val isTransient = isTransientNetworkError(error)
        if (isTransient && runAttemptCount < MAX_RETRY_ATTEMPTS) {
            return Result.retry()
        }

        val message = error.message ?: "Backup failed due to unexpected error"
        GoogleDriveBackupNotificationHelper.showBackupFailureNotification(context, message)
        return Result.failure(workDataOf("error" to message))
    }

    private fun isTransientNetworkError(t: Throwable): Boolean {
        var current: Throwable? = t
        while (current != null) {
            if (current is UnknownHostException ||
                current is SocketTimeoutException ||
                current is IOException ||
                current.message?.contains("timeout", ignoreCase = true) == true ||
                current.message?.contains("network", ignoreCase = true) == true ||
                current.message?.contains("503", ignoreCase = true) == true ||
                current.message?.contains("500", ignoreCase = true) == true
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    companion object {
        const val MAX_RETRY_ATTEMPTS = 3
        const val WORK_NAME_PERIODIC = "ak_auto_drive_backup"
        const val WORK_NAME_ONE_TIME = "ak_immediate_drive_backup"
    }
}
