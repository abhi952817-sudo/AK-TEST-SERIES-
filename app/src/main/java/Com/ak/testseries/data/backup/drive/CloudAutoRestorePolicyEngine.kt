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
     */
    suspend fun isLocalVaultGenuinelyEmpty(
        context: Context,
        db: AppDatabase,
        uid: String
    ): Boolean {
        val safeUid = uid.trim()
        if (safeUid.isBlank()) return false

        // 1. Check Room TestEntity records owned by user
        val allTests = try {
            db.testDao().getAllTestsList()
        } catch (_: Exception) {
            emptyList()
        }
        val userVaultTestsCount = allTests.count { !it.isOfficial && !it.isPublic && it.uploaderId == safeUid }
        if (userVaultTestsCount > 0) return false

        // 2. Check local private test files directory
        val privateTestsDir = File(context.filesDir, "private_tests/$safeUid")
        if (privateTestsDir.exists() && (privateTestsDir.listFiles()?.isNotEmpty() == true)) {
            return false
        }

        // 3. Check Room AttemptEntity records owned by user
        val attempts = try {
            db.attemptDao().getAttemptsForUser(safeUid).firstOrNull() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (attempts.isNotEmpty()) return false

        // 4. Check user bookmarks preference store
        val bookmarksPrefs = context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
        val userBookmarks = bookmarksPrefs.getStringSet("bookmarks_$safeUid", null)
            ?: bookmarksPrefs.getStringSet("bookmarks", null)
        if (!userBookmarks.isNullOrEmpty()) return false

        // 5. Check in-progress tests
        val progressPrefs = context.getSharedPreferences("test_progress", Context.MODE_PRIVATE)
        val userProgressKeys = progressPrefs.all.keys.filter { it.startsWith("progress_${safeUid}_") }
        if (userProgressKeys.isNotEmpty()) return false

        // 6. Check syllabus progress
        val syllabusPrefs = context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE)
        val syllabusKeys = syllabusPrefs.all.keys.filter { it.contains(safeUid) }
        if (syllabusKeys.isNotEmpty()) return false

        return true
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

        val matchingBackup = findBestCompatibleBackup(remoteBackups, safeUid)
            ?: return AutoRestoreDecision.Skip("No compatible cloud backup found for UID: $safeUid")

        val isVaultEmpty = isLocalVaultGenuinelyEmpty(context, db, safeUid)
        if (!isVaultEmpty) {
            return AutoRestoreDecision.Skip("Local vault contains existing user data. Skipping auto-restore to prevent overwriting.")
        }

        val isAutoRestoreOptIn = GoogleDriveAuthManager.isAutoRestoreOnNewDeviceEnabled(context)
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
