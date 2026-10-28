package Com.ak.testseries.data.backup.drive

import android.content.Context
import Com.ak.testseries.data.AppDatabase
import Com.ak.testseries.data.backup.BackupArchiveEngine
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

/**
 * Granular statuses for the Automatic Cloud Restore workflow on a new device.
 */
sealed class AutoRestoreStatus {
    object Idle : AutoRestoreStatus()
    object CheckingForBackup : AutoRestoreStatus()
    data class BackupFound(val backup: GoogleDriveRemoteBackup) : AutoRestoreStatus()
    object WaitingForDriveAuth : AutoRestoreStatus()
    object Downloading : AutoRestoreStatus()
    object Validating : AutoRestoreStatus()
    object Restoring : AutoRestoreStatus()
    data class Completed(val message: String) : AutoRestoreStatus()
    object NoBackupAvailable : AutoRestoreStatus()
    data class Failed(val reason: String) : AutoRestoreStatus()
    data class Incompatible(val reason: String) : AutoRestoreStatus()
    data class AccountMismatch(val expectedUid: String, val actualUid: String) : AutoRestoreStatus()
}

/**
 * Tri-state result for account-owned local vault emptiness check.
 * Strictly prevents database read errors from being falsely interpreted as empty.
 */
sealed class VaultEmptinessResult {
    object Empty : VaultEmptinessResult()
    data class NotEmpty(val reason: String) : VaultEmptinessResult()
    data class Error(val cause: Throwable) : VaultEmptinessResult()
}

/**
 * Result of evaluating auto-restore eligibility for a logged-in user on a device.
 */
sealed class AutoRestoreDecision {
    data class ShouldAutoRestore(val backup: GoogleDriveRemoteBackup) : AutoRestoreDecision()
    data class ShouldRecommendOnly(val backup: GoogleDriveRemoteBackup, val reason: String) : AutoRestoreDecision()
    data class Skip(val reason: String) : AutoRestoreDecision()
}

/**
 * Policy engine governing discovery, vault emptiness detection, zero-trust account containment,
 * and restore decision evaluation for Phase 6.
 */
object CloudAutoRestorePolicyEngine {

    /**
     * Determines whether the account-owned local vault is genuinely empty.
     * Checks all account-scoped entities across Room tables, file repositories, and SharedPreferences.
     * Fails closed: on database or filesystem read failure, returns [VaultEmptinessResult.Error].
     */
    suspend fun checkLocalVaultEmptiness(
        context: Context,
        db: AppDatabase,
        uid: String
    ): VaultEmptinessResult {
        val safeUid = uid.trim()
        if (safeUid.isBlank()) {
            return VaultEmptinessResult.Error(IllegalArgumentException("Authenticated UID is blank."))
        }

        // 1. Check Room TestEntity records owned by user
        val userVaultTestsCount = try {
            val allTests = db.testDao().getAllTestsList()
            allTests.count { !it.isOfficial && !it.isPublic && it.uploaderId == safeUid }
        } catch (e: Exception) {
            return VaultEmptinessResult.Error(e)
        }
        if (userVaultTestsCount > 0) {
            return VaultEmptinessResult.NotEmpty("Found $userVaultTestsCount user-owned tests in Room database.")
        }

        // 2. Check local private test files directory
        try {
            val privateTestsDir = File(context.filesDir, "private_tests/$safeUid")
            if (privateTestsDir.exists() && (privateTestsDir.listFiles()?.isNotEmpty() == true)) {
                return VaultEmptinessResult.NotEmpty("Found user private test files in storage.")
            }
        } catch (e: Exception) {
            return VaultEmptinessResult.Error(e)
        }

        // 3. Check Room AttemptEntity records owned by user
        val attemptsCount = try {
            val attempts = db.attemptDao().getAttemptsForUser(safeUid).firstOrNull() ?: emptyList()
            attempts.size
        } catch (e: Exception) {
            return VaultEmptinessResult.Error(e)
        }
        if (attemptsCount > 0) {
            return VaultEmptinessResult.NotEmpty("Found $attemptsCount attempt records in Room database.")
        }

        // 4. Check user bookmarks preference store
        try {
            val bookmarksPrefs = context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
            val userBookmarks = bookmarksPrefs.getStringSet("bookmarks_$safeUid", null)
                ?: bookmarksPrefs.getStringSet("bookmarks", null)
            if (!userBookmarks.isNullOrEmpty()) {
                return VaultEmptinessResult.NotEmpty("Found saved test bookmarks.")
            }
        } catch (e: Exception) {
            return VaultEmptinessResult.Error(e)
        }

        // 5. Check in-progress tests
        try {
            val progressPrefs = context.getSharedPreferences("test_progress", Context.MODE_PRIVATE)
            val userProgressKeys = progressPrefs.all.keys.filter { it.startsWith("progress_${safeUid}_") }
            if (userProgressKeys.isNotEmpty()) {
                return VaultEmptinessResult.NotEmpty("Found in-progress test sessions.")
            }
        } catch (e: Exception) {
            return VaultEmptinessResult.Error(e)
        }

        // 6. Check syllabus progress
        try {
            val syllabusPrefs = context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE)
            val syllabusKeys = syllabusPrefs.all.keys.filter { it.contains(safeUid) }
            if (syllabusKeys.isNotEmpty()) {
                return VaultEmptinessResult.NotEmpty("Found recorded syllabus progress.")
            }
        } catch (e: Exception) {
            return VaultEmptinessResult.Error(e)
        }

        // 7. Check user profile preferences (test folders or custom mappings)
        try {
            val profilePrefs = context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
            val folders = profilePrefs.getString("test_folders", null)
            val folderMappings = profilePrefs.getString("test_folder_mappings", null)
            if (!folders.isNullOrBlank() || !folderMappings.isNullOrBlank()) {
                return VaultEmptinessResult.NotEmpty("Found user folder configurations.")
            }
        } catch (e: Exception) {
            return VaultEmptinessResult.Error(e)
        }

        return VaultEmptinessResult.Empty
    }

    suspend fun isLocalVaultGenuinelyEmpty(
        context: Context,
        db: AppDatabase,
        uid: String
    ): Boolean {
        return checkLocalVaultEmptiness(context, db, uid) is VaultEmptinessResult.Empty
    }

    /**
     * Filters and finds the newest compatible, valid backup belonging strictly to the authenticated Firebase UID.
     * Enforces zero-trust account containment: foreign account backups are rejected.
     */
    fun findBestCompatibleBackup(
        backups: List<GoogleDriveRemoteBackup>,
        authenticatedUid: String
    ): GoogleDriveRemoteBackup? {
        val safeUid = authenticatedUid.trim()
        if (safeUid.isBlank()) return null

        return backups
            .filter { it.ownerUid == safeUid }
            .filter { it.schemaVersion <= BackupArchiveEngine.CURRENT_SCHEMA_VERSION }
            .sortedByDescending {
                if (it.modifiedTimeMillis > 0) it.modifiedTimeMillis else it.createdTimeMillis
            }
            .firstOrNull()
    }

    /**
     * Evaluates whether an automatic restore should proceed, a recommendation should be shown,
     * or the operation should be skipped entirely.
     */
    suspend fun evaluateEligibility(
        context: Context,
        db: AppDatabase,
        authenticatedUid: String,
        isDriveAuthorized: Boolean,
        remoteBackups: List<GoogleDriveRemoteBackup>,
        isRestoreRunning: Boolean
    ): AutoRestoreDecision {
        val safeUid = authenticatedUid.trim()
        if (safeUid.isBlank()) {
            return AutoRestoreDecision.Skip("No authenticated Firebase UID.")
        }

        if (isRestoreRunning) {
            return AutoRestoreDecision.Skip("A restore operation is already in progress.")
        }

        if (!isDriveAuthorized) {
            return AutoRestoreDecision.Skip("Google Drive is not authorized on this device.")
        }

        // Verify Firebase UID <-> Google Drive account binding
        val bindingCheck = GoogleDriveAuthManager.verifyAccountBinding(context, safeUid)
        if (bindingCheck.isFailure) {
            return AutoRestoreDecision.Skip(bindingCheck.exceptionOrNull()?.message ?: "Drive account mismatch.")
        }

        val matchingBackup = findBestCompatibleBackup(remoteBackups, safeUid)
            ?: return AutoRestoreDecision.Skip("No compatible cloud backup found for UID: $safeUid")

        when (val emptinessResult = checkLocalVaultEmptiness(context, db, safeUid)) {
            is VaultEmptinessResult.NotEmpty -> {
                return AutoRestoreDecision.Skip("Local vault contains existing user data (${emptinessResult.reason}). Skipping auto-restore to prevent overwriting.")
            }
            is VaultEmptinessResult.Error -> {
                return AutoRestoreDecision.Skip("Local vault emptiness check failed with error: ${emptinessResult.cause.message}. Failing closed for safety.")
            }
            is VaultEmptinessResult.Empty -> {
                // Proceed
            }
        }

        val isAutoRestoreOptIn = GoogleDriveAuthManager.isAutoRestoreOnNewDeviceEnabled(context, safeUid)
        return if (isAutoRestoreOptIn) {
            AutoRestoreDecision.ShouldAutoRestore(matchingBackup)
        } else {
            AutoRestoreDecision.ShouldRecommendOnly(
                matchingBackup,
                "Auto-restore on new device is OFF. Prompting user to inspect & confirm."
            )
        }
    }
}
