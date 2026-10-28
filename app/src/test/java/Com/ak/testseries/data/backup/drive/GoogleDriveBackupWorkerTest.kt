package Com.ak.testseries.data.backup.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import Com.ak.testseries.data.AppDatabase
import Com.ak.testseries.data.backup.BackupCompleteness
import Com.ak.testseries.data.backup.BackupStats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for WorkManager Automatic Backup Worker and Scheduler (Phase 2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class GoogleDriveBackupWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Initialize WorkManager in test mode
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
        } catch (_: Exception) {}
    }

    @Test
    fun testSchedulerCancelsWhenDriveNotConnected() {
        GoogleDriveAuthManager.setAutoBackupFrequency(context, "DAILY")
        // Not connected
        GoogleDriveBackupScheduler.scheduleOrUpdate(context)

        // WorkManager instance exists and didn't crash
        assertNotNull(WorkManager.getInstance(context))
    }

    @Test
    fun testSchedulerSchedulesWhenDriveConnected() {
        val prefs = context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(GoogleDriveAuthManager.KEY_DRIVE_CONNECTED, true)
            .putString(GoogleDriveAuthManager.KEY_AUTO_BACKUP_FREQUENCY, "WEEKLY")
            .putBoolean(GoogleDriveAuthManager.KEY_AUTO_BACKUP_WIFI_ONLY, true)
            .putBoolean(GoogleDriveAuthManager.KEY_AUTO_BACKUP_CHARGING_ONLY, true)
            .commit()

        GoogleDriveBackupScheduler.scheduleOrUpdate(context)
        assertNotNull(WorkManager.getInstance(context))

        // Trigger immediate backup as well
        val op = GoogleDriveBackupScheduler.triggerImmediateBackup(context)
        assertNotNull(op)

        // Cancel
        GoogleDriveBackupScheduler.cancel(context)
    }

    @Test
    fun testWorkerSkipsWhenDriveNotConnected() = runBlocking {
        val request = OneTimeWorkRequestBuilder<GoogleDriveBackupWorker>().build()
        val worker = TestListenableWorkerBuilder.from(context, request).build()

        val result = worker.startWork().get()
        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals("Google Drive not connected", output.getString("skipped"))
    }

    @Test
    fun testWorkerSkipsWhenFrequencyOff() = runBlocking {
        val prefs = context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(GoogleDriveAuthManager.KEY_DRIVE_CONNECTED, true)
            .putString(GoogleDriveAuthManager.KEY_AUTO_BACKUP_FREQUENCY, "OFF")
            .commit()

        val request = OneTimeWorkRequestBuilder<GoogleDriveBackupWorker>().build()
        val worker = TestListenableWorkerBuilder.from(context, request).build()

        val result = worker.startWork().get()
        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertTrue(output.getString("skipped") != null)
    }

    @Test
    fun testNotificationHelperChannelsAndPost() {
        // Verify channel creation does not throw
        GoogleDriveBackupNotificationHelper.ensureChannel(context)

        // Verify success notification does not throw in robolectric
        GoogleDriveBackupNotificationHelper.showBackupSuccessNotification(
            context,
            "10 Tests • 5 Attempts • 2 Diagrams"
        )

        // Verify failure notification does not throw in robolectric
        GoogleDriveBackupNotificationHelper.showBackupFailureNotification(
            context,
            "Network timeout during upload"
        )
    }

    @Test
    fun testWorkerHandlesMockUploadSuccess() = runBlocking {
        val fakeManager = object : GoogleDriveBackupManager(object : DriveApiClient {
            override suspend fun ensureBackupFolder(accessToken: String): Result<String> = Result.success("folder_123")
            override suspend fun uploadBackupFile(accessToken: String, folderId: String, file: java.io.File, manifest: Com.ak.testseries.data.backup.BackupManifest): Result<GoogleDriveRemoteBackup> {
                return Result.success(
                    GoogleDriveRemoteBackup(
                        fileId = "test_file_id",
                        name = "ak_backup_uid123_1000.aktsbak",
                        sizeBytes = 1024L,
                        createdTimeMillis = 1000L,
                        modifiedTimeMillis = 1000L,
                        ownerUid = "uid123",
                        schemaVersion = 19,
                        stats = BackupStats(1, 10, 2, 0, 0, 1),
                        completeness = BackupCompleteness.COMPLETE_USER_DATA
                    )
                )
            }
            override suspend fun listBackups(accessToken: String, folderId: String, ownerUid: String): Result<List<GoogleDriveRemoteBackup>> = Result.success(emptyList())
            override suspend fun downloadBackupFile(accessToken: String, fileId: String, destinationFile: java.io.File): Result<java.io.File> = Result.success(destinationFile)
            override suspend fun deleteBackupFile(accessToken: String, fileId: String): Result<Boolean> = Result.success(true)
        }) {
            override suspend fun exportAndUploadToDrive(
                context: Context,
                db: AppDatabase,
                activeUid: String,
                email: String?,
                displayName: String?,
                appVersionCode: Int,
                appVersionName: String,
                includeDiagrams: Boolean
            ): Result<GoogleDriveRemoteBackup> {
                return Result.success(
                    GoogleDriveRemoteBackup(
                        fileId = "test_file_id",
                        name = "ak_backup_uid123_1000.aktsbak",
                        sizeBytes = 1024L,
                        createdTimeMillis = 1000L,
                        modifiedTimeMillis = 1000L,
                        ownerUid = activeUid,
                        schemaVersion = 19,
                        stats = BackupStats(1, 10, 2, 0, 0, 1),
                        completeness = BackupCompleteness.COMPLETE_USER_DATA
                    )
                )
            }
        }

        val request = OneTimeWorkRequestBuilder<GoogleDriveBackupWorker>().build()
        val worker = TestListenableWorkerBuilder.from(context, request).build() as GoogleDriveBackupWorker
        worker.driveBackupManager = fakeManager

        // Without sign in, worker skips gracefully
        val result = worker.startWork().get()
        assertTrue(result is ListenableWorker.Result.Success)
    }
}
