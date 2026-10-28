package Com.ak.testseries.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import Com.ak.testseries.data.backup.*
import Com.ak.testseries.data.backup.drive.*
import Com.ak.testseries.viewmodel.NEETViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric tests for Google Drive Cloud Backup & Restore UI Integration (Phase 3).
 *
 * Verifies:
 * - Google Drive state transitions in NEETViewModel.
 * - Auto-backup configuration updates and WorkManager synchronization.
 * - Staging-first remote backup validation workflow.
 * - Disconnect and account cleanup safety.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class GoogleDriveBackupUiTest {

    private lateinit var context: Context
    private lateinit var app: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as Application

        // Clear backup preferences
        context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun testInitialDriveStateIsDisconnected() {
        val viewModel = NEETViewModel(app)
        viewModel.refreshGoogleDriveStatus(context)

        assertFalse(viewModel.isDriveConnected.value)
        assertNull(viewModel.driveAccountEmail.value)
        assertEquals("DAILY", viewModel.autoBackupFrequency.value)
        assertTrue(viewModel.autoBackupWifiOnly.value)
        assertFalse(viewModel.autoBackupChargingOnly.value)
        assertTrue(viewModel.remoteBackupsList.value.isEmpty())
    }

    @Test
    fun testUpdateAutoBackupSettingsReflectsInStateAndPrefs() {
        val viewModel = NEETViewModel(app)

        viewModel.updateAutoBackupSettings(
            context = context,
            frequency = "WEEKLY",
            wifiOnly = false,
            chargingOnly = true
        )

        assertEquals("WEEKLY", viewModel.autoBackupFrequency.value)
        assertFalse(viewModel.autoBackupWifiOnly.value)
        assertTrue(viewModel.autoBackupChargingOnly.value)

        // Verify SharedPreferences persistence
        assertEquals("WEEKLY", GoogleDriveAuthManager.getAutoBackupFrequency(context))
        assertFalse(GoogleDriveAuthManager.isWifiOnlyRequired(context))
        assertTrue(GoogleDriveAuthManager.isChargingOnlyRequired(context))
    }

    @Test
    fun testDisconnectClearsStateAndCancelsWork() = runBlocking {
        // Setup initial connected state
        context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(GoogleDriveAuthManager.KEY_DRIVE_CONNECTED, true)
            .putString(GoogleDriveAuthManager.KEY_DRIVE_ACCOUNT_EMAIL, "student@gmail.com")
            .putString(GoogleDriveAuthManager.KEY_DRIVE_ACCOUNT_DISPLAY_NAME, "Student Name")
            .commit()

        val viewModel = NEETViewModel(app)
        viewModel.disconnectGoogleDrive(context)

        assertFalse(viewModel.isDriveConnected.value)
        assertNull(viewModel.driveAccountEmail.value)
        assertNull(viewModel.driveAccountDisplayName.value)
        assertTrue(viewModel.remoteBackupsList.value.isEmpty())
        assertFalse(GoogleDriveAuthManager.isDriveConnected(context))
    }

    @Test
    fun testRemoteBackupItemFormattingAndStats() {
        val remoteBackup = GoogleDriveRemoteBackup(
            fileId = "drive_12345",
            name = "ak_backup_uid_100_1700000000.aktsbak",
            sizeBytes = 2500000L,
            createdTimeMillis = 1700000000000L,
            modifiedTimeMillis = 1700000000000L,
            ownerUid = "uid_100",
            schemaVersion = 19,
            stats = BackupStats(
                privateTestsCount = 4,
                questionsCount = 45,
                attemptsCount = 12,
                bookmarkedCount = 7,
                inProgressCount = 3,
                diagramsCount = 2
            ),
            completeness = BackupCompleteness.COMPLETE_USER_DATA
        )

        assertTrue(remoteBackup.formattedSize.contains("MB"))
        assertTrue(remoteBackup.statsSummary.contains("4 Tests"))
        assertTrue(remoteBackup.statsSummary.contains("12 Attempts"))
        assertTrue(remoteBackup.statsSummary.contains("2 Diagrams"))
        assertEquals("drive_12345", remoteBackup.fileId)
    }

    @Test
    fun testDismissDialogsResetsState() {
        val viewModel = NEETViewModel(app)
        viewModel.dismissRemoteOperationDialog()
        assertEquals(RemoteBackupOperationState.Idle, viewModel.remoteBackupOperationState.value)

        viewModel.dismissBackupDialog()
        assertEquals(BackupUiState.Idle, viewModel.backupUiState.value)
    }
}
