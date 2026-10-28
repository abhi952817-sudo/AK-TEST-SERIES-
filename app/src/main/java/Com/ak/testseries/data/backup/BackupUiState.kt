package Com.ak.testseries.data.backup

import java.io.File

/**
 * Diagnostic failure classification for backup and restore failures.
 */
enum class BackupErrorCode {
    NETWORK_ERROR,
    INSUFFICIENT_STORAGE,
    ACCOUNT_MISMATCH,
    CORRUPTED_ARCHIVE,
    QUOTA_EXCEEDED,
    UNAUTHENTICATED,
    PERMISSION_DENIED,
    UNKNOWN
}

/**
 * State contract for the Storage Access Framework (SAF) local backup export and restore UI.
 */
sealed class BackupUiState {
    object Idle : BackupUiState()
    data class Exporting(val message: String, val progressFraction: Float? = null) : BackupUiState()
    data class ExportSuccess(val manifest: BackupManifest, val message: String) : BackupUiState()
    data class Validating(val message: String, val progressFraction: Float? = null) : BackupUiState()
    data class ValidationReady(
        val manifest: BackupManifest,
        val stagingFile: File,
        val stagedData: StagedBackupData? = null
    ) : BackupUiState()
    data class Restoring(val message: String) : BackupUiState()
    data class RestoreSuccess(val result: RestoreResult.Success) : BackupUiState()
    data class Error(
        val title: String,
        val message: String,
        val errorCode: BackupErrorCode = BackupErrorCode.UNKNOWN
    ) : BackupUiState()
}

/**
 * State contract for remote Google Drive cloud backup operations.
 */
sealed class RemoteBackupOperationState {
    object Idle : RemoteBackupOperationState()
    data class Loading(val message: String) : RemoteBackupOperationState()
    data class Progress(
        val message: String,
        val progressFraction: Float,
        val currentBytes: Long = 0L,
        val totalBytes: Long = 0L
    ) : RemoteBackupOperationState()
    data class Success(val message: String) : RemoteBackupOperationState()
    data class Error(
        val title: String,
        val message: String,
        val errorCode: BackupErrorCode = BackupErrorCode.UNKNOWN
    ) : RemoteBackupOperationState()
}
