package Com.ak.testseries.data.backup.drive

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import Com.ak.testseries.data.backup.BackupCompleteness
import Com.ak.testseries.data.backup.BackupStats
import Com.ak.testseries.viewmodel.NEETViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric tests for Cloud Backup Retention Manager and Fresh Device Recovery (Phase 5).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class GoogleDriveRetentionAndRecoveryTest {

    private lateinit var context: Context
    private lateinit var app: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as Application
        context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun testRetentionPolicyDoesNotPruneWhenUnderLimit() = runBlocking {
        val deletedIds = mutableListOf<String>()
        val fakeClient = object : DriveApiClient {
            override suspend fun ensureBackupFolder(accessToken: String): Result<String> = Result.success("folder_1")
            override suspend fun uploadBackupFile(accessToken: String, folderId: String, file: File, manifest: Com.ak.testseries.data.backup.BackupManifest): Result<GoogleDriveRemoteBackup> = Result.failure(Exception())
            override suspend fun listBackups(accessToken: String, folderId: String, ownerUid: String): Result<List<GoogleDriveRemoteBackup>> {
                return Result.success(
                    listOf(
                        createMockBackup("f1", 1000L, ownerUid),
                        createMockBackup("f2", 2000L, ownerUid),
                        createMockBackup("f3", 3000L, ownerUid)
                    )
                )
            }
            override suspend fun downloadBackupFile(accessToken: String, fileId: String, destinationFile: File): Result<File> = Result.success(destinationFile)
            override suspend fun deleteBackupFile(accessToken: String, fileId: String): Result<Boolean> {
                deletedIds.add(fileId)
                return Result.success(true)
            }
        }

        val result = GoogleDriveRetentionManager.pruneExcessBackups(
            driveApiClient = fakeClient,
            accessToken = "mock_token",
            folderId = "folder_1",
            ownerUid = "uid_1",
            maxToKeep = 5
        )

        assertEquals(0, result.deletedCount)
        assertEquals(3, result.preservedCount)
        assertTrue(deletedIds.isEmpty())
    }

    @Test
    fun testRetentionPolicyDeletesOldestExcessBackups() = runBlocking {
        val deletedIds = mutableListOf<String>()
        val backups = listOf(
            createMockBackup("f1_oldest", 1000L, "uid_1"),
            createMockBackup("f2_second_oldest", 2000L, "uid_1"),
            createMockBackup("f3", 3000L, "uid_1"),
            createMockBackup("f4", 4000L, "uid_1"),
            createMockBackup("f5", 5000L, "uid_1"),
            createMockBackup("f6", 6000L, "uid_1"),
            createMockBackup("f7_newest", 7000L, "uid_1")
        )

        val fakeClient = object : DriveApiClient {
            override suspend fun ensureBackupFolder(accessToken: String): Result<String> = Result.success("folder_1")
            override suspend fun uploadBackupFile(accessToken: String, folderId: String, file: File, manifest: Com.ak.testseries.data.backup.BackupManifest): Result<GoogleDriveRemoteBackup> = Result.failure(Exception())
            override suspend fun listBackups(accessToken: String, folderId: String, ownerUid: String): Result<List<GoogleDriveRemoteBackup>> = Result.success(backups)
            override suspend fun downloadBackupFile(accessToken: String, fileId: String, destinationFile: File): Result<File> = Result.success(destinationFile)
            override suspend fun deleteBackupFile(accessToken: String, fileId: String): Result<Boolean> {
                deletedIds.add(fileId)
                return Result.success(true)
            }
        }

        // Keep 5 out of 7 -> Should delete f1_oldest and f2_second_oldest
        val result = GoogleDriveRetentionManager.pruneExcessBackups(
            driveApiClient = fakeClient,
            accessToken = "mock_token",
            folderId = "folder_1",
            ownerUid = "uid_1",
            maxToKeep = 5
        )

        assertEquals(2, result.deletedCount)
        assertEquals(5, result.preservedCount)
        assertTrue(deletedIds.contains("f1_oldest"))
        assertTrue(deletedIds.contains("f2_second_oldest"))
        assertFalse(deletedIds.contains("f7_newest"))
    }

    @Test
    fun testDismissCloudRestoreRecommendation() {
        val viewModel = NEETViewModel(app)
        viewModel.dismissCloudRestoreRecommendation()
        assertNull(viewModel.cloudRestoreRecommendation.value)
    }

    private fun createMockBackup(fileId: String, timestamp: Long, ownerUid: String): GoogleDriveRemoteBackup {
        return GoogleDriveRemoteBackup(
            fileId = fileId,
            name = "ak_backup_${ownerUid}_$timestamp.aktsbak",
            sizeBytes = 1024L,
            createdTimeMillis = timestamp,
            modifiedTimeMillis = timestamp,
            ownerUid = ownerUid,
            schemaVersion = 19,
            stats = BackupStats(1, 10, 2, 0, 0, 1),
            completeness = BackupCompleteness.COMPLETE_USER_DATA
        )
    }
}
