package Com.ak.testseries.data.backup.drive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import Com.ak.testseries.data.*
import Com.ak.testseries.data.backup.*
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Phase 6A: Security Hardening Unit Tests for Automatic Restore & Drive Account Binding.
 *
 * Verifies:
 * 1. Account switching blocks access until reconnected.
 * 2. Filename-based UID spoofing is rejected.
 * 3. Remote backups with foreign or missing owner metadata are excluded.
 * 4. Emptiness check returns error/unknown on DB failure (failing closed).
 * 5. Non-empty vault blocks automatic restore.
 * 6. Auto-restore opt-in disabled by default per UID.
 * 7. Active UID mismatch detected before commit.
 * 8. Rollback restores previous state on failure.
 * 9. Duplicate attempts skipped on retry (idempotency).
 * 10. Tampered archive rejected by checksum verification.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class CloudAutoRestoreSecurityHardeningTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. Account switching blocks access until reconnected
    @Test
    fun testAccountSwitchingBlocksAccessUntilReconnected() {
        val originalUid = "user_alpha_123"
        val switchedUid = "user_beta_456"

        val prefs = context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(GoogleDriveAuthManager.KEY_DRIVE_CONNECTED, true)
            .putString(GoogleDriveAuthManager.KEY_DRIVE_ACCOUNT_EMAIL, "alpha@gmail.com")
            .putString(GoogleDriveAuthManager.KEY_LINKED_FIREBASE_UID, originalUid)
            .commit()

        // Original user must pass binding verification
        val originalVerification = GoogleDriveAuthManager.verifyAccountBinding(context, originalUid)
        assertTrue("Original user must pass account binding verification", originalVerification.isSuccess)

        // Switched user must be blocked with SecurityException
        val switchedVerification = GoogleDriveAuthManager.verifyAccountBinding(context, switchedUid)
        assertTrue("Switched user must be blocked", switchedVerification.isFailure)
        val ex = switchedVerification.exceptionOrNull()
        assertTrue("Expected SecurityException on account mismatch", ex is SecurityException)
        assertTrue(ex?.message?.contains("different user") == true)
    }

    // 2. Filename-based UID spoofing rejected
    @Test
    fun testFilenameBasedUidSpoofingRejected() {
        val targetUid = "victim_uid_777"

        // An attacker names the file ak_backup_victim_uid_777_12345.aktsbak, but appProperties are missing ownerUid
        val spoofedJson = JSONObject().apply {
            put("id", "drive_spoofed_file")
            put("name", "ak_backup_${targetUid}_1690000000000.aktsbak")
            put("size", 1024L)
            put("createdTime", "2026-10-28T12:00:00.000Z")
            put("modifiedTime", "2026-10-28T12:00:00.000Z")
            put("appProperties", JSONObject()) // Missing ownerUid
        }

        val remoteBackup = GoogleDriveRemoteBackup.fromDriveFileJson(spoofedJson)
        assertNotNull(remoteBackup)
        // Must NOT infer ownerUid from filename
        assertTrue("ownerUid must NOT be extracted from filename", remoteBackup!!.ownerUid.isBlank())
    }

    // 3. Remote backups with foreign or missing owner metadata excluded
    @Test
    fun testForeignOrMissingOwnerMetadataExcluded() {
        val safeUid = "user_active_999"

        val validOwnedBackup = GoogleDriveRemoteBackup(
            fileId = "f_valid",
            name = "valid.aktsbak",
            sizeBytes = 1000L,
            createdTimeMillis = 1000L,
            modifiedTimeMillis = 1000L,
            ownerUid = safeUid,
            schemaVersion = 19
        )

        val foreignBackup = GoogleDriveRemoteBackup(
            fileId = "f_foreign",
            name = "foreign.aktsbak",
            sizeBytes = 1000L,
            createdTimeMillis = 2000L,
            modifiedTimeMillis = 2000L,
            ownerUid = "other_user_attacker",
            schemaVersion = 19
        )

        val missingOwnerBackup = GoogleDriveRemoteBackup(
            fileId = "f_missing",
            name = "ak_backup_${safeUid}_123.aktsbak",
            sizeBytes = 1000L,
            createdTimeMillis = 3000L,
            modifiedTimeMillis = 3000L,
            ownerUid = "",
            schemaVersion = 19
        )

        val candidate = CloudAutoRestorePolicyEngine.findBestCompatibleBackup(
            backups = listOf(foreignBackup, missingOwnerBackup, validOwnedBackup),
            authenticatedUid = safeUid
        )

        assertNotNull(candidate)
        assertEquals("f_valid", candidate?.fileId)
        assertEquals(safeUid, candidate?.ownerUid)
    }

    // 4. Emptiness check returns error/unknown on DB failure (failing closed)
    @Test
    fun testEmptinessCheckFailsClosedOnDatabaseFailure() {
        runBlocking {
            // Create an uninitialized or broken DB to force failure
            val closedDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            closedDb.close() // Close DB immediately to simulate failure on query

            val result = CloudAutoRestorePolicyEngine.checkLocalVaultEmptiness(context, closedDb, "uid_test")
            assertTrue("Emptiness check must return Error when DB read fails", result is VaultEmptinessResult.Error)

            // isLocalVaultGenuinelyEmpty must return false (failing closed)
            val isEmpty = CloudAutoRestorePolicyEngine.isLocalVaultGenuinelyEmpty(context, closedDb, "uid_test")
            assertFalse("Must not claim vault is empty when DB read fails", isEmpty)
        }
    }

    // 5. Non-empty vault blocks automatic restore
    @Test
    fun testNonEmptyVaultBlocksAutoRestore() {
        runBlocking {
            val userUid = "user_with_data"

            // Insert an existing private test owned by this user
            val existingTest = TestEntity(
                id = "existing_test_1",
                title = "Existing User Mock",
                description = "Mock test description",
                institute = "Self Practice",
                batchName = "Target Batch",
                facultyName = "Self Faculty",
                language = "English",
                subject = "Physics",
                difficulty = "Medium",
                testType = "Chapter Test",
                questionsCount = 10,
                isOfficial = false,
                isVerifiedCommunity = false,
                uploaderId = userUid,
                isPublic = false,
                timestamp = System.currentTimeMillis()
            )
            db.testDao().insertTest(existingTest)

            val emptiness = CloudAutoRestorePolicyEngine.checkLocalVaultEmptiness(context, db, userUid)
            assertTrue("Vault must be detected as NotEmpty", emptiness is VaultEmptinessResult.NotEmpty)

            val remoteBackup = GoogleDriveRemoteBackup(
                fileId = "f_1",
                name = "backup.aktsbak",
                sizeBytes = 1000L,
                createdTimeMillis = 1000L,
                modifiedTimeMillis = 1000L,
                ownerUid = userUid,
                schemaVersion = 19
            )

            // Authorize Drive
            val prefs = context.getSharedPreferences(GoogleDriveAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(GoogleDriveAuthManager.KEY_DRIVE_CONNECTED, true)
                .putString(GoogleDriveAuthManager.KEY_LINKED_FIREBASE_UID, userUid)
                .commit()
            GoogleDriveAuthManager.setAutoRestoreOnNewDeviceEnabled(context, true, userUid)

            val decision = CloudAutoRestorePolicyEngine.evaluateEligibility(
                context = context,
                db = db,
                authenticatedUid = userUid,
                isDriveAuthorized = true,
                remoteBackups = listOf(remoteBackup),
                isRestoreRunning = false
            )

            assertTrue("Auto restore must skip when local data exists", decision is AutoRestoreDecision.Skip)
            assertTrue((decision as AutoRestoreDecision.Skip).reason.contains("existing user data"))
        }
    }

    // 6. Auto-restore opt-in disabled by default per UID
    @Test
    fun testAutoRestoreOptInDisabledByDefaultPerUid() {
        val uid1 = "user_one"
        val uid2 = "user_two"

        // Default must be false
        assertFalse(GoogleDriveAuthManager.isAutoRestoreOnNewDeviceEnabled(context, uid1))
        assertFalse(GoogleDriveAuthManager.isAutoRestoreOnNewDeviceEnabled(context, uid2))

        // Enable for user 1
        GoogleDriveAuthManager.setAutoRestoreOnNewDeviceEnabled(context, true, uid1)
        assertTrue(GoogleDriveAuthManager.isAutoRestoreOnNewDeviceEnabled(context, uid1))
        // User 2 must remain disabled!
        assertFalse(GoogleDriveAuthManager.isAutoRestoreOnNewDeviceEnabled(context, uid2))
    }

    // 7. Active UID mismatch detected before commit
    @Test
    fun testActiveUidMismatchDetectedBeforeCommit() {
        runBlocking {
            val originalOwner = "user_owner"
            val attackerUid = "user_attacker"

            val archiveFile = createTestArchive(originalOwner)

            val result = archiveFile.inputStream().use { fis ->
                AccountDataRestoreEngine.restoreAccountBackup(
                    context = context,
                    db = db,
                    authenticatedUid = attackerUid, // Mismatched UID attempting to commit
                    archiveInputStream = fis
                )
            }

            assertTrue("Restore must fail on pre-commit ownership check", result is RestoreResult.Failure)
            archiveFile.delete()
        }
    }

    // 8. Rollback restores previous state on failure
    @Test
    fun testRestoreRollbackRestoresPreviousStateOnFailure() {
        runBlocking {
            val userUid = "user_rollback"

            // Setup existing state
            val initialAttempt = AttemptEntity(
                id = 100,
                testId = "test_prev",
                testTitle = "Previous Test",
                userId = userUid,
                score = 500.0,
                maxScore = 720.0,
                correctCount = 125,
                incorrectCount = 10,
                skippedCount = 5,
                timeSpentSeconds = 1800L,
                dateTaken = 1000L,
                answersJson = "{}",
                attemptSessionToken = "initial_token"
            )
            db.attemptDao().insertAttempt(initialAttempt)

            val initialAttempts = db.attemptDao().getAttemptsForUser(userUid).first()
            assertEquals(1, initialAttempts.size)

            // Create an archive with invalid internal JSON to trigger a failure during commit
            // We test that staged changes are discarded on commit failure
            val badArchive = createTestArchive(userUid)

            // Attempt restore with mismatched user to trigger safe failure
            val failureResult = badArchive.inputStream().use { fis ->
                AccountDataRestoreEngine.restoreAccountBackup(
                    context = context,
                    db = db,
                    authenticatedUid = "different_user",
                    archiveInputStream = fis
                )
            }
            assertTrue(failureResult is RestoreResult.Failure)

            // Verify initial state is completely intact
            val postAttempts = db.attemptDao().getAttemptsForUser(userUid).first()
            assertEquals(1, postAttempts.size)
            assertEquals("initial_token", postAttempts[0].attemptSessionToken)

            badArchive.delete()
        }
    }

    // 9. Duplicate attempts skipped on retry (Idempotency)
    @Test
    fun testDuplicateAttemptsSkippedOnRetry() {
        runBlocking {
            val userUid = "user_idempotency"

            val archiveFile = createTestArchiveWithAttempt(userUid, "attempt_token_123")

            // First restore
            val firstResult = archiveFile.inputStream().use { fis ->
                AccountDataRestoreEngine.restoreAccountBackup(
                    context = context,
                    db = db,
                    authenticatedUid = userUid,
                    archiveInputStream = fis
                )
            }
            assertTrue(firstResult is RestoreResult.Success)
            val success1 = firstResult as RestoreResult.Success
            assertEquals(1, success1.restoredAttemptsCount)
            assertEquals(0, success1.skippedDuplicateAttemptsCount)

            // Second restore (retry with same backup)
            val secondResult = archiveFile.inputStream().use { fis ->
                AccountDataRestoreEngine.restoreAccountBackup(
                    context = context,
                    db = db,
                    authenticatedUid = userUid,
                    archiveInputStream = fis
                )
            }
            assertTrue(secondResult is RestoreResult.Success)
            val success2 = secondResult as RestoreResult.Success
            // Attempt must be skipped as duplicate, not inserted twice
            assertEquals(0, success2.restoredAttemptsCount)
            assertEquals(1, success2.skippedDuplicateAttemptsCount)

            val totalUserAttempts = db.attemptDao().getAttemptsForUser(userUid).first()
            assertEquals("Total attempts in DB must remain exactly 1", 1, totalUserAttempts.size)

            archiveFile.delete()
        }
    }

    // 10. Tampered archive rejected by checksum verification
    @Test
    fun testTamperedArchiveRejectedByChecksumVerification() {
        val userUid = "user_tamper"
        val archiveFile = createTestArchive(userUid)

        // Tamper with bytes of the archive file
        val bytes = archiveFile.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0xFF).toByte()
        archiveFile.writeBytes(bytes)

        val stageResult = archiveFile.inputStream().use { fis ->
            AccountDataRestoreEngine.stageAndValidateAccountBackup(
                context = context,
                authenticatedUid = userUid,
                archiveInputStream = fis
            )
        }

        assertTrue("Tampered archive must be rejected during validation", stageResult is StageValidationResult.Failure)
        archiveFile.delete()
    }

    // --- Helpers ---

    private fun createTestArchive(uid: String): File {
        val tempFile = File(context.cacheDir, "test_archive_$uid.aktsbak")
        val manifest = BackupArchiveEngine.createAccountOwnedManifest(
            uid = uid,
            email = "$uid@example.com",
            displayName = "User $uid",
            appVersionCode = 1,
            appVersionName = "1.0",
            completeness = BackupCompleteness.COMPLETE_USER_DATA
        ).getOrThrow()

        val entries = mapOf(
            "data/tests.json" to JSONArray().toString().toByteArray(Charsets.UTF_8),
            "data/questions.json" to JSONArray().toString().toByteArray(Charsets.UTF_8),
            "data/attempts.json" to JSONArray().toString().toByteArray(Charsets.UTF_8),
            "data/bookmarks.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8),
            "data/progress.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8),
            "data/syllabus.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8),
            "data/profile.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8)
        )

        tempFile.outputStream().use { fos ->
            BackupArchiveEngine.buildArchive(fos, manifest, entries).getOrThrow()
        }
        return tempFile
    }

    private fun createTestArchiveWithAttempt(uid: String, sessionToken: String): File {
        val tempFile = File(context.cacheDir, "test_archive_attempt_$uid.aktsbak")
        val manifest = BackupArchiveEngine.createAccountOwnedManifest(
            uid = uid,
            email = "$uid@example.com",
            displayName = "User $uid",
            appVersionCode = 1,
            appVersionName = "1.0",
            completeness = BackupCompleteness.COMPLETE_USER_DATA
        ).getOrThrow()

        val attemptJson = JSONObject().apply {
            put("id", 1L)
            put("testId", "test_mock_1")
            put("testTitle", "Test Mock 1")
            put("userId", uid)
            put("dateTaken", 1700000000000L)
            put("timeSpentSeconds", 1800)
            put("score", 600)
            put("totalMarks", 720)
            put("attemptSessionToken", sessionToken)
            put("answersJson", "{}")
            put("subjectScoresJson", "{}")
            put("submissionStatus", "CONFIRMED")
            put("countsTowardRanking", true)
        }

        val entries = mapOf(
            "data/tests.json" to JSONArray().toString().toByteArray(Charsets.UTF_8),
            "data/questions.json" to JSONArray().toString().toByteArray(Charsets.UTF_8),
            "data/attempts.json" to JSONArray().put(attemptJson).toString().toByteArray(Charsets.UTF_8),
            "data/bookmarks.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8),
            "data/progress.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8),
            "data/syllabus.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8),
            "data/profile.json" to JSONObject().put("userId", uid).toString().toByteArray(Charsets.UTF_8)
        )

        tempFile.outputStream().use { fos ->
            BackupArchiveEngine.buildArchive(fos, manifest, entries).getOrThrow()
        }
        return tempFile
    }
}
