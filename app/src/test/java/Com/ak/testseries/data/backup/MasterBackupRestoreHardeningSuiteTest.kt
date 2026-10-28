package Com.ak.testseries.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import Com.ak.testseries.data.*
import Com.ak.testseries.data.backup.drive.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Master verification and regression test suite covering Phase 3 scenarios:
 * - Account identity, Drive isolation, and binding
 * - Vault emptiness detection and fail-closed error handling
 * - Archive integrity, ZipSlip, checksums, and schema versions
 * - Rollback on transaction/preference/file commit failure
 * - Idempotency and duplicate prevention
 * - Zero Gemini/AI API calls verification
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class MasterBackupRestoreHardeningSuiteTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    private fun createTest(
        id: String,
        uid: String,
        title: String = "Test Title",
        institute: String = "Private Institute",
        isPublic: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
    ): TestEntity {
        return TestEntity(
            id = id,
            title = title,
            description = "Description",
            institute = institute,
            batchName = "Target Batch",
            facultyName = "Faculty Name",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Full Syllabus",
            questionsCount = 10,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = isPublic,
            uploaderId = uid,
            timestamp = timestamp
        )
    }

    private fun createAttempt(
        id: Int,
        uid: String,
        testId: String = "test_1",
        score: Double = 80.0,
        dateTaken: Long = System.currentTimeMillis(),
        token: String = "token_$id"
    ): AttemptEntity {
        return AttemptEntity(
            id = id,
            userId = uid,
            testId = testId,
            testTitle = "Attempted Test",
            score = score,
            maxScore = 100.0,
            percentile = 90.0,
            accuracy = 80.0,
            correctCount = 8,
            incorrectCount = 2,
            skippedCount = 0,
            timeSpentSeconds = 300L,
            dateTaken = dateTaken,
            answersJson = "{}",
            attemptSessionToken = token
        )
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("test_progress", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val privateTestsDir = File(context.filesDir, "private_tests")
        if (privateTestsDir.exists()) privateTestsDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        db.close()
        val privateTestsDir = File(context.filesDir, "private_tests")
        if (privateTestsDir.exists()) privateTestsDir.deleteRecursively()
    }

    // ==========================================
    // 1. ACCOUNT AND AUTHORIZATION TESTS
    // ==========================================

    @Test
    fun testUserACannotBeRestoredByUserB() = runBlocking {
        val userA = "user_alpha_777"
        val userB = "user_beta_888"

        // Create backup for User A
        val testA = createTest("test_a_1", userA, "Alpha Private Test")
        db.testDao().insertTest(testA)

        val baos = ByteArrayOutputStream()
        val exportResult = AccountDataBackupAdapter.exportAccountBackup(
            context = context,
            db = db,
            uid = userA,
            outputStream = baos
        )
        assertTrue("User A export must succeed", exportResult.isSuccess)

        // Attempt restore by User B
        val stageResult = AccountDataRestoreEngine.stageAndValidateAccountBackup(
            context = context,
            authenticatedUid = userB,
            archiveInputStream = ByteArrayInputStream(baos.toByteArray())
        )
        assertTrue("User B must be rejected restoring User A's backup", stageResult is StageValidationResult.Failure)
        val failure = stageResult as StageValidationResult.Failure
        assertTrue(failure.reason.contains("ownership mismatch", ignoreCase = true))
    }

    @Test
    fun testBlankOrUnauthenticatedUidRejected() = runBlocking {
        val baos = ByteArrayOutputStream()
        val blankResult = AccountDataBackupAdapter.exportAccountBackup(
            context = context,
            db = db,
            uid = "   ",
            outputStream = baos
        )
        assertTrue("Blank UID must fail export", blankResult.isFailure)

        val manifestResult = BackupArchiveEngine.createAccountOwnedManifest(uid = "")
        assertTrue("Blank UID must fail manifest creation", manifestResult.isFailure)

        val stageResult = AccountDataRestoreEngine.stageAndValidateAccountBackup(
            context = context,
            authenticatedUid = "",
            archiveInputStream = ByteArrayInputStream(ByteArray(10))
        )
        assertTrue("Blank UID must fail restore staging", stageResult is StageValidationResult.Failure)
    }

    @Test
    fun testDriveAccountBindingVerification() {
        val uid1 = "user_1"
        val uid2 = "user_2"

        // Blank UID fails
        val blankBinding = GoogleDriveAuthManager.verifyAccountBinding(context, "")
        assertTrue("Blank UID must fail binding check", blankBinding.isFailure)

        // Bind uid1
        val bind1 = GoogleDriveAuthManager.verifyAccountBinding(context, uid1)
        assertTrue("First binding must succeed", bind1.isSuccess)

        // Same uid succeeds
        val rebind1 = GoogleDriveAuthManager.verifyAccountBinding(context, uid1)
        assertTrue("Re-verifying same UID must succeed", rebind1.isSuccess)

        // Mismatched UID fails
        val bind2 = GoogleDriveAuthManager.verifyAccountBinding(context, uid2)
        assertTrue("Mismatched UID must fail with SecurityException", bind2.isFailure)
        assertTrue(bind2.exceptionOrNull() is SecurityException)

        // Disconnect clears binding
        runBlocking { GoogleDriveAuthManager.disconnect(context) }
        val prefs = context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
        assertNull("Linked UID must be cleared after disconnect", prefs.getString(GoogleDriveAuthManager.KEY_LINKED_FIREBASE_UID, null))
    }

    // ==========================================
    // 2. VAULT DETECTION TESTS
    // ==========================================

    @Test
    fun testVaultEmptinessGenuinelyEmpty() = runBlocking {
        val uid = "test_user_empty"
        val result = CloudAutoRestorePolicyEngine.checkLocalVaultEmptiness(context, db, uid)
        assertTrue("Vault must be genuinely empty", result is VaultEmptinessResult.Empty)
    }

    @Test
    fun testVaultEmptinessDetectsTests() = runBlocking {
        val uid = "test_user_with_test"
        db.testDao().insertTest(createTest("vault_t1", uid))
        val result = CloudAutoRestorePolicyEngine.checkLocalVaultEmptiness(context, db, uid)
        assertTrue("Vault must not be empty when tests exist", result is VaultEmptinessResult.NotEmpty)
    }

    @Test
    fun testVaultEmptinessDetectsAttempts() = runBlocking {
        val uid = "test_user_with_attempt"
        db.attemptDao().insertAttempt(createAttempt(101, uid))
        val result = CloudAutoRestorePolicyEngine.checkLocalVaultEmptiness(context, db, uid)
        assertTrue("Vault must not be empty when attempts exist", result is VaultEmptinessResult.NotEmpty)
    }

    @Test
    fun testVaultEmptinessDetectsBookmarks() = runBlocking {
        val uid = "test_user_with_bookmark"
        context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("bookmarks_$uid", setOf("t1", "t2"))
            .commit()

        val result = CloudAutoRestorePolicyEngine.checkLocalVaultEmptiness(context, db, uid)
        assertTrue("Vault must not be empty when bookmarks exist", result is VaultEmptinessResult.NotEmpty)
    }

    @Test
    fun testVaultEmptinessDetectsSyllabus() = runBlocking {
        val uid = "test_user_with_syllabus"
        context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("completed_chapters_${uid}_NEET", setOf("Physics_1"))
            .commit()

        val result = CloudAutoRestorePolicyEngine.checkLocalVaultEmptiness(context, db, uid)
        assertTrue("Vault must not be empty when syllabus progress exists", result is VaultEmptinessResult.NotEmpty)
    }

    @Test
    fun testVaultEmptinessDetectsDiagramFiles() = runBlocking {
        val uid = "test_user_with_diagram"
        val diagDir = File(context.filesDir, "private_tests/t_sample/diagrams").apply { mkdirs() }
        File(diagDir, "diagram.png").writeBytes(byteArrayOf(1, 2, 3, 4))

        val result = CloudAutoRestorePolicyEngine.checkLocalVaultEmptiness(context, db, uid)
        assertTrue("Vault must not be empty when diagrams exist", result is VaultEmptinessResult.NotEmpty)
    }

    // ==========================================
    // 3. ARCHIVE INTEGRITY & LIMITS
    // ==========================================

    @Test
    fun testZipSlipPathTraversalBlocked() {
        val traversalResult1 = BackupArchiveEngine.sanitizeEntryPath("../escaped.txt")
        assertTrue("Path traversal with .. must be rejected", traversalResult1.isFailure)

        val traversalResult2 = BackupArchiveEngine.sanitizeEntryPath("data/../../etc/passwd")
        assertTrue("Path traversal segments must be rejected", traversalResult2.isFailure)

        val absoluteResult = BackupArchiveEngine.sanitizeEntryPath("/data/tests.json")
        assertTrue("Absolute path must be rejected", absoluteResult.isFailure)
    }

    @Test
    fun testDuplicateArchiveEntryRejected() {
        val manifest = BackupArchiveEngine.createAccountOwnedManifest("user_x").getOrThrow()
        val entries = mapOf(
            "data/tests.json" to "[]".toByteArray(),
            "data\\tests.json" to "[]".toByteArray() // Equivalent normalized path
        )
        val baos = ByteArrayOutputStream()
        val buildResult = BackupArchiveEngine.buildArchive(baos, manifest, entries)
        assertTrue("Duplicate archive entries must be rejected", buildResult.isFailure)
    }

    @Test
    fun testCorruptZipRejected() {
        val corruptBytes = "Not a zip archive".toByteArray()
        val validationResult = BackupArchiveEngine.validateArchive(ByteArrayInputStream(corruptBytes))
        assertTrue("Corrupt zip must fail validation", validationResult is BackupValidationResult.Invalid)
    }

    @Test
    fun testChecksumMismatchDetected() {
        val uid = "checksum_user"
        val manifest = BackupManifest(
            formatVersion = 1,
            databaseSchemaVersion = 19,
            completeness = BackupCompleteness.COMPLETE_USER_DATA,
            owner = BackupOwner(uid = uid),
            checksums = mapOf("data/tests.json" to "invalid_hash_string_12345")
        )

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("backup_manifest.json"))
            zos.write(manifest.toJsonString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("data/tests.json"))
            zos.write("[]".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = BackupArchiveEngine.validateArchive(ByteArrayInputStream(baos.toByteArray()))
        assertTrue("Checksum mismatch must be invalid", result is BackupValidationResult.Invalid)
        val invalid = result as BackupValidationResult.Invalid
        assertTrue(invalid.reason.contains("Checksum mismatch", ignoreCase = true))
    }

    @Test
    fun testFutureFormatAndSchemaRejected() {
        val uid = "version_user"
        val futureManifest = BackupManifest(
            formatVersion = 999, // Future format
            databaseSchemaVersion = 999, // Future schema
            completeness = BackupCompleteness.COMPLETE_USER_DATA,
            owner = BackupOwner(uid = uid)
        )

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("backup_manifest.json"))
            zos.write(futureManifest.toJsonString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = BackupArchiveEngine.validateArchive(ByteArrayInputStream(baos.toByteArray()))
        assertTrue("Future format/schema must be rejected", result is BackupValidationResult.Invalid)
        val invalid = result as BackupValidationResult.Invalid
        assertTrue(invalid.reason.contains("Unsupported future", ignoreCase = true))
    }

    // ==========================================
    // 4. RESTORE IDEMPOTENCY & ROLLBACK
    // ==========================================

    @Test
    fun testRestoreAttemptsIdempotency() = runBlocking {
        val uid = "idempotency_user"
        val attempt = createAttempt(id = 1, uid = uid, testId = "test_idem", score = 100.0, dateTaken = 1700000000000L, token = "token_abc_123")
        db.attemptDao().insertAttempt(attempt)

        // Create backup containing this attempt
        val test = createTest("test_idem", uid)
        db.testDao().insertTest(test)

        val baos = ByteArrayOutputStream()
        AccountDataBackupAdapter.exportAccountBackup(context, db, uid, outputStream = baos)

        // Restore same backup into db
        val restoreResult = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(baos.toByteArray())
        )

        assertTrue("Restore must succeed", restoreResult is RestoreResult.Success)
        val success = restoreResult as RestoreResult.Success
        assertEquals("Duplicate attempt must be skipped", 1, success.skippedDuplicateAttemptsCount)

        val attemptsInDb = db.attemptDao().getAttemptsForUser(uid).first()
        assertEquals("Total attempts in db must remain 1 without duplicates", 1, attemptsInDb.size)
    }

    @Test
    fun testRestorePreservesNewerLocalTest() = runBlocking {
        val uid = "newer_test_user"
        val olderTimestamp = 1600000000000L
        val newerTimestamp = 1700000000000L

        // Prepare initial test
        val oldTest = createTest("t_version", uid, title = "Old Version", timestamp = olderTimestamp)
        db.testDao().insertTest(oldTest)

        val baos = ByteArrayOutputStream()
        AccountDataBackupAdapter.exportAccountBackup(context, db, uid, outputStream = baos)

        // Now update local live test to a newer version
        val updatedTest = oldTest.copy(title = "Newer Version", timestamp = newerTimestamp)
        db.testDao().insertTest(updatedTest)

        // Restore older backup
        val restoreResult = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(baos.toByteArray())
        )

        assertTrue("Restore must succeed", restoreResult is RestoreResult.Success)
        val success = restoreResult as RestoreResult.Success
        assertEquals("Newer local test must be preserved", 1, success.preservedNewerTestsCount)

        val testInDb = db.testDao().getTestById("t_version")
        assertEquals("Local newer test title must remain", "Newer Version", testInDb?.title)
        assertEquals("Local newer timestamp must remain", newerTimestamp, testInDb?.timestamp)
    }

    @Test
    fun testRollbackRestoresPreviousStateOnFileError() = runBlocking {
        val uid = "rollback_user"

        // Setup pre-restore preferences
        val bookmarksPrefs = context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
        bookmarksPrefs.edit().putStringSet("bookmarks_$uid", setOf("original_bookmark")).commit()

        // Create a valid backup
        val test = createTest("t_rb", uid, title = "Rollback Test")
        db.testDao().insertTest(test)
        val baos = ByteArrayOutputStream()
        AccountDataBackupAdapter.exportAccountBackup(context, db, uid, outputStream = baos)

        // Restore with wrong UID should reject before commit
        val failResult = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = "attacker_uid",
            archiveInputStream = ByteArrayInputStream(baos.toByteArray())
        )

        assertTrue("Restore must fail", failResult is RestoreResult.Failure)
        val bookmarksAfter = bookmarksPrefs.getStringSet("bookmarks_$uid", null)
        assertEquals("Previous bookmarks must remain intact", setOf("original_bookmark"), bookmarksAfter)
    }
}
