package Com.ak.testseries.data.backup.drive

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import Com.ak.testseries.data.AppDatabase
import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.data.backup.BackupCompleteness
import Com.ak.testseries.data.backup.BackupStats
import Com.ak.testseries.viewmodel.NEETViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric unit and policy tests for Automatic Cloud Restore on New Device (Phase 6).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class CloudAutoRestoreTest {

    private lateinit var context: Context
    private lateinit var app: Application
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as Application
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("test_progress", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testDefaultAutoRestorePreferenceIsFalse() {
        assertFalse(GoogleDriveAuthManager.isAutoRestoreOnNewDeviceEnabled(context))
        val viewModel = NEETViewModel(app)
        assertFalse(viewModel.autoRestoreOnNewDeviceEnabled.value)
    }

    @Test
    fun testSetAutoRestorePreferencePersists() {
        val viewModel = NEETViewModel(app)
        viewModel.setAutoRestoreOnNewDeviceEnabled(context, true)

        assertTrue(viewModel.autoRestoreOnNewDeviceEnabled.value)
        assertTrue(GoogleDriveAuthManager.isAutoRestoreOnNewDeviceEnabled(context))

        viewModel.setAutoRestoreOnNewDeviceEnabled(context, false)
        assertFalse(viewModel.autoRestoreOnNewDeviceEnabled.value)
        assertFalse(GoogleDriveAuthManager.isAutoRestoreOnNewDeviceEnabled(context))
    }

    @Test
    fun testIsLocalVaultGenuinelyEmptyWhenNoData() {
        runBlocking {
            val isEmpty = CloudAutoRestorePolicyEngine.isLocalVaultGenuinelyEmpty(context, db, "user_101")
            assertTrue(isEmpty)
        }
    }

    @Test
    fun testIsLocalVaultGenuinelyEmptyReturnsFalseWhenVaultTestsExist() {
        runBlocking {
            db.testDao().insertTest(
                TestEntity(
                    id = "vault_test_1",
                    title = "My Private Biology Test",
                    description = "Test Description",
                    institute = "Self",
                    batchName = "2026",
                    facultyName = "Self",
                    language = "English",
                    subject = "Biology",
                    difficulty = "Medium",
                    testType = "Chapter Test",
                    questionsCount = 10,
                    isOfficial = false,
                    isVerifiedCommunity = false,
                    isPublic = false,
                    uploaderId = "user_101"
                )
            )

            val isEmpty = CloudAutoRestorePolicyEngine.isLocalVaultGenuinelyEmpty(context, db, "user_101")
            assertFalse("Vault should not be empty when private test exists", isEmpty)
        }
    }

    @Test
    fun testIsLocalVaultGenuinelyEmptyReturnsFalseWhenAttemptsExist() {
        runBlocking {
            db.attemptDao().insertAttempt(
                AttemptEntity(
                    id = 1,
                    userId = "user_101",
                    testId = "test_1",
                    testTitle = "Sample Test",
                    score = 650.0,
                    maxScore = 720.0,
                    percentile = 90.0,
                    accuracy = 88.0,
                    correctCount = 160,
                    incorrectCount = 20,
                    skippedCount = 0,
                    timeSpentSeconds = 3600L,
                    dateTaken = System.currentTimeMillis(),
                    answersJson = "{}"
                )
            )

            val isEmpty = CloudAutoRestorePolicyEngine.isLocalVaultGenuinelyEmpty(context, db, "user_101")
            assertFalse("Vault should not be empty when attempt history exists", isEmpty)
        }
    }

    @Test
    fun testIsLocalVaultGenuinelyEmptyReturnsFalseWhenBookmarksExist() {
        runBlocking {
            val prefs = context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("bookmarks_user_101", setOf("test_bookmark_1")).commit()

            val isEmpty = CloudAutoRestorePolicyEngine.isLocalVaultGenuinelyEmpty(context, db, "user_101")
            assertFalse("Vault should not be empty when bookmarks exist", isEmpty)
        }
    }

    @Test
    fun testFindBestCompatibleBackupZeroTrustContainment() {
        val backups = listOf(
            createBackup("f_other", 5000L, "other_user_uid", schema = 19),
            createBackup("f_user_old", 2000L, "user_101", schema = 19),
            createBackup("f_user_new", 4000L, "user_101", schema = 19),
            createBackup("f_user_future", 6000L, "user_101", schema = 999) // Incompatible future schema
        )

        val best = CloudAutoRestorePolicyEngine.findBestCompatibleBackup(backups, "user_101")
        assertNotNull(best)
        assertEquals("f_user_new", best?.fileId)
        assertEquals("user_101", best?.ownerUid)
    }

    @Test
    fun testEvaluateEligibilityWhenPreferenceOffRecommendsOnly() {
        runBlocking {
            GoogleDriveAuthManager.setAutoRestoreOnNewDeviceEnabled(context, false)
            val backups = listOf(createBackup("f_1", 3000L, "user_101", schema = 19))

            val decision = CloudAutoRestorePolicyEngine.evaluateEligibility(
                context = context,
                db = db,
                authenticatedUid = "user_101",
                isDriveAuthorized = true,
                remoteBackups = backups,
                isRestoreRunning = false
            )

            assertTrue(decision is AutoRestoreDecision.ShouldRecommendOnly)
            val rec = decision as AutoRestoreDecision.ShouldRecommendOnly
            assertEquals("f_1", rec.backup.fileId)
        }
    }

    @Test
    fun testEvaluateEligibilityWhenPreferenceOnTriggersAutoRestore() {
        runBlocking {
            GoogleDriveAuthManager.setAutoRestoreOnNewDeviceEnabled(context, true)
            val backups = listOf(createBackup("f_1", 3000L, "user_101", schema = 19))

            val decision = CloudAutoRestorePolicyEngine.evaluateEligibility(
                context = context,
                db = db,
                authenticatedUid = "user_101",
                isDriveAuthorized = true,
                remoteBackups = backups,
                isRestoreRunning = false
            )

            assertTrue(decision is AutoRestoreDecision.ShouldAutoRestore)
            val auto = decision as AutoRestoreDecision.ShouldAutoRestore
            assertEquals("f_1", auto.backup.fileId)
        }
    }

    @Test
    fun testEvaluateEligibilitySkipsWhenVaultIsNotEmpty() {
        runBlocking {
            GoogleDriveAuthManager.setAutoRestoreOnNewDeviceEnabled(context, true)
            // Add existing attempt
            db.attemptDao().insertAttempt(
                AttemptEntity(
                    id = 2,
                    userId = "user_101",
                    testId = "test_1",
                    testTitle = "Sample Test",
                    score = 500.0,
                    maxScore = 720.0,
                    percentile = 70.0,
                    accuracy = 86.0,
                    correctCount = 125,
                    incorrectCount = 20,
                    skippedCount = 35,
                    timeSpentSeconds = 3600L,
                    dateTaken = System.currentTimeMillis(),
                    answersJson = "{}"
                )
            )
            val backups = listOf(createBackup("f_1", 3000L, "user_101", schema = 19))

            val decision = CloudAutoRestorePolicyEngine.evaluateEligibility(
                context = context,
                db = db,
                authenticatedUid = "user_101",
                isDriveAuthorized = true,
                remoteBackups = backups,
                isRestoreRunning = false
            )

            assertTrue(decision is AutoRestoreDecision.Skip)
            assertTrue((decision as AutoRestoreDecision.Skip).reason.contains("prevent overwriting"))
        }
    }

    @Test
    fun testIsLocalVaultGenuinelyEmptyReturnsFalseWhenInProgressTestExists() {
        runBlocking {
            val prefs = context.getSharedPreferences("test_progress", Context.MODE_PRIVATE)
            prefs.edit().putString("progress_user_101_test_99", "{state: 'active'}").commit()

            val isEmpty = CloudAutoRestorePolicyEngine.isLocalVaultGenuinelyEmpty(context, db, "user_101")
            assertFalse("Vault should not be empty when in-progress test exists", isEmpty)
        }
    }

    @Test
    fun testIsLocalVaultGenuinelyEmptyReturnsFalseWhenSyllabusProgressExists() {
        runBlocking {
            val prefs = context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE)
            prefs.edit().putInt("progress_user_101_physics", 45).commit()

            val isEmpty = CloudAutoRestorePolicyEngine.isLocalVaultGenuinelyEmpty(context, db, "user_101")
            assertFalse("Vault should not be empty when syllabus progress exists", isEmpty)
        }
    }

    @Test
    fun testIsLocalVaultGenuinelyEmptyReturnsFalseWhenPrivateFilesDirExists() {
        runBlocking {
            val dir = File(context.filesDir, "private_tests/user_101")
            dir.mkdirs()
            File(dir, "local_test.json").writeText("{}")

            val isEmpty = CloudAutoRestorePolicyEngine.isLocalVaultGenuinelyEmpty(context, db, "user_101")
            assertFalse("Vault should not be empty when private filesDir files exist", isEmpty)

            // Cleanup
            dir.deleteRecursively()
        }
    }

    @Test
    fun testEvaluateEligibilitySkipsWhenRestoreAlreadyRunning() {
        runBlocking {
            GoogleDriveAuthManager.setAutoRestoreOnNewDeviceEnabled(context, true)
            val backups = listOf(createBackup("f_1", 3000L, "user_101", schema = 19))

            val decision = CloudAutoRestorePolicyEngine.evaluateEligibility(
                context = context,
                db = db,
                authenticatedUid = "user_101",
                isDriveAuthorized = true,
                remoteBackups = backups,
                isRestoreRunning = true
            )

            assertTrue(decision is AutoRestoreDecision.Skip)
            assertTrue((decision as AutoRestoreDecision.Skip).reason.contains("already in progress"))
        }
    }

    @Test
    fun testEvaluateEligibilitySkipsWhenDriveNotAuthorized() {
        runBlocking {
            GoogleDriveAuthManager.setAutoRestoreOnNewDeviceEnabled(context, true)
            val backups = listOf(createBackup("f_1", 3000L, "user_101", schema = 19))

            val decision = CloudAutoRestorePolicyEngine.evaluateEligibility(
                context = context,
                db = db,
                authenticatedUid = "user_101",
                isDriveAuthorized = false,
                remoteBackups = backups,
                isRestoreRunning = false
            )

            assertTrue(decision is AutoRestoreDecision.Skip)
            assertTrue((decision as AutoRestoreDecision.Skip).reason.contains("Google Drive is not authorized"))
        }
    }

    @Test
    fun testEvaluateEligibilitySkipsWhenNoBackupsFound() {
        runBlocking {
            GoogleDriveAuthManager.setAutoRestoreOnNewDeviceEnabled(context, true)

            val decision = CloudAutoRestorePolicyEngine.evaluateEligibility(
                context = context,
                db = db,
                authenticatedUid = "user_101",
                isDriveAuthorized = true,
                remoteBackups = emptyList(),
                isRestoreRunning = false
            )

            assertTrue(decision is AutoRestoreDecision.Skip)
            assertTrue((decision as AutoRestoreDecision.Skip).reason.contains("No compatible cloud backup found"))
        }
    }

    @Test
    fun testEvaluateEligibilitySkipsWhenAuthenticatedUidIsBlank() {
        runBlocking {
            GoogleDriveAuthManager.setAutoRestoreOnNewDeviceEnabled(context, true)
            val backups = listOf(createBackup("f_1", 3000L, "user_101", schema = 19))

            val decision = CloudAutoRestorePolicyEngine.evaluateEligibility(
                context = context,
                db = db,
                authenticatedUid = "   ",
                isDriveAuthorized = true,
                remoteBackups = backups,
                isRestoreRunning = false
            )

            assertTrue(decision is AutoRestoreDecision.Skip)
            assertTrue((decision as AutoRestoreDecision.Skip).reason.contains("No authenticated Firebase UID"))
        }
    }

    @Test
    fun testDismissAutoRestoreStatusResetsToIdle() {
        val viewModel = NEETViewModel(app)
        viewModel.dismissAutoRestoreStatus()
        assertEquals(AutoRestoreStatus.Idle, viewModel.autoRestoreStatus.value)
    }

    private fun createBackup(fileId: String, timestamp: Long, ownerUid: String, schema: Int): GoogleDriveRemoteBackup {
        return GoogleDriveRemoteBackup(
            fileId = fileId,
            name = "ak_backup_${ownerUid}_$timestamp.aktsbak",
            sizeBytes = 2048L,
            createdTimeMillis = timestamp,
            modifiedTimeMillis = timestamp,
            ownerUid = ownerUid,
            schemaVersion = schema,
            stats = BackupStats(2, 20, 5, 0, 1, 1),
            completeness = BackupCompleteness.COMPLETE_USER_DATA
        )
    }
}
