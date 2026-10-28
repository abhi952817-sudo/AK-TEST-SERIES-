package Com.ak.testseries.data.backup

import java.io.File

/**
 * State contract for the Storage Access Framework (SAF) local backup export and restore UI.
 */
sealed class BackupUiState {
    object Idle : BackupUiState()
    data class Exporting(val message: String) : BackupUiState()
    data class ExportSuccess(val manifest: BackupManifest, val message: String) : BackupUiState()
    data class Validating(val message: String) : BackupUiState()
    data class ValidationReady(
        val manifest: BackupManifest,
        val stagingFile: File,
        val stagedData: StagedBackupData? = null
    ) : BackupUiState()
    data class Restoring(val message: String) : BackupUiState()
    data class RestoreSuccess(val result: RestoreResult.Success) : BackupUiState()
    data class Error(val title: String, val message: String) : BackupUiState()
}

/**
 * State contract for remote Google Drive cloud backup operations.
 */
sealed class RemoteBackupOperationState {
    object Idle : RemoteBackupOperationState()
    data class Loading(val message: String) : RemoteBackupOperationState()
    data class Success(val message: String) : RemoteBackupOperationState()
    data class Error(val title: String, val message: String) : RemoteBackupOperationState()
}

