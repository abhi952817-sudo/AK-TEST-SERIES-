package Com.ak.testseries.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import Com.ak.testseries.data.AppDatabase
import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class AccountDataRestoreEngineTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("test_progress", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
        val privateTestsDir = File(context.filesDir, "private_tests")
        if (privateTestsDir.exists()) privateTestsDir.deleteRecursively()
        val secureAiDir = File(context.filesDir, "secure_ai_tests")
        if (secureAiDir.exists()) secureAiDir.deleteRecursively()
    }

    private fun createValidArchiveBytes(
        uid: String,
        testId: String = "test_res_1",
        formatVersion: Int = 1,
        schemaVersion: Int = 19,
        completeness: BackupCompleteness = BackupCompleteness.COMPLETE_USER_DATA,
        testTimestamp: Long = 1000L,
        tamperChecksum: Boolean = false,
        omitCategory: String? = null,
        malformCategory: String? = null
    ): ByteArray {
        val testEntity = TestEntity(
            id = testId,
            title = "Restorable Test",
            description = "Desc",
            institute = "Private Vault",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = uid,
            timestamp = testTimestamp
        )

        val qEntity = QuestionEntity(
            id = "q_res_1",
            testId = testId,
            questionText = "Question text",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "A", explanation = "Exp", subject = "Physics", chapter = "Mechanics"
        )

        val attemptEntity = AttemptEntity(
            id = 10,
            userId = uid,
            testId = testId,
            testTitle = "Restorable Test",
            score = 4.0, maxScore = 4.0, percentile = 99.0, accuracy = 100.0,
            correctCount = 1, incorrectCount = 0, skippedCount = 0, timeSpentSeconds = 50L,
            answersJson = "{\"q_res_1\":\"A\"}",
            attemptSessionToken = "sess_res_token_1"
        )

        val testsArr = JSONArray().put(AccountDataBackupAdapter.testEntityToJson(testEntity))
        val qArr = JSONArray().put(AccountDataBackupAdapter.questionEntityToJson(qEntity))
        val attemptsArr = JSONArray().put(AccountDataBackupAdapter.attemptEntityToJson(attemptEntity))

        val bookmarksObj = JSONObject().apply {
            put("userId", uid)
            put("testBookmarks", JSONArray().put(testId))
            put("questionBookmarks", JSONArray().put("q_res_1"))
        }

        val progressObj = JSONObject().apply {
            put("userId", uid)
            put("inProgressSessions", JSONObject().put(testId, "{\"currentIndex\":0}"))
        }

        val syllabusObj = JSONObject().apply {
            put("userId", uid)
            put("completedChaptersByExam", JSONObject().put("NEET", JSONArray().put("Mechanics")))
        }

        val profileObj = JSONObject().apply {
            put("userId", uid)
            put("name", "Student Restore")
            put("targetExam", "NEET")
            put("classLevel", "12th")
            put("selectedLanguage", "ENGLISH")
        }

        val entries = mutableMapOf<String, ByteArray>()
        entries["data/tests.json"] = testsArr.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/questions.json"] = qArr.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/attempts.json"] = attemptsArr.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/bookmarks.json"] = bookmarksObj.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/progress.json"] = progressObj.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/syllabus.json"] = syllabusObj.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/profile.json"] = profileObj.toString(2).toByteArray(Charsets.UTF_8)

        if (omitCategory != null) {
            entries.remove(omitCategory)
        }
        if (malformCategory != null) {
            entries[malformCategory] = "{bad json:".toByteArray(Charsets.UTF_8)
        }

        val checksums = mutableMapOf<String, String>()
        for ((path, bytes) in entries) {
            val hash = BackupArchiveEngine.computeSha256(bytes)
            checksums[path] = if (tamperChecksum && path == "data/tests.json") "badhash123" else hash
        }

        val manifest = BackupManifest(
            formatVersion = formatVersion,
            createdAt = System.currentTimeMillis(),
            appVersionCode = 1,
            appVersionName = "1.0",
            databaseSchemaVersion = schemaVersion,
            scope = BackupScope.ACCOUNT_OWNED,
            completeness = completeness,
            owner = BackupOwner(uid = uid, email = "$uid@example.com", displayName = "Student Restore"),
            stats = BackupStats(
                privateTestsCount = if (omitCategory == "data/tests.json") 0 else 1,
                questionsCount = if (omitCategory == "data/questions.json") 0 else 1,
                attemptsCount = if (omitCategory == "data/attempts.json") 0 else 1,
                inProgressCount = 1,
                bookmarkedCount = 2,
                diagramsCount = 0,
                entryCount = entries.size + 1
            ),
            compatibility = BackupCompatibility(minSupportedAppVersion = 1, minSupportedSchemaVersion = 15),
            checksums = checksums
        )

        val out = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(out)

        val manifestBytes = manifest.toJsonString().toByteArray(Charsets.UTF_8)
        zipOut.putNextEntry(ZipEntry(BackupArchiveEngine.MANIFEST_ENTRY_NAME))
        zipOut.write(manifestBytes)
        zipOut.closeEntry()

        for ((path, bytes) in entries) {
            zipOut.putNextEntry(ZipEntry(path))
            zipOut.write(bytes)
            zipOut.closeEntry()
        }
        zipOut.finish()
        zipOut.flush()

        return out.toByteArray()
    }

    @Test
    fun testRestore_successfulSameUidRestore() = runBlocking {
        val uid = "user_success_100"
        val archiveBytes = createValidArchiveBytes(uid)

        val result = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )

        assertTrue("Restore must succeed for matching authenticated UID", result is RestoreResult.Success)
        val success = result as RestoreResult.Success
        assertEquals(1, success.restoredTestsCount)
        assertEquals(1, success.restoredQuestionsCount)
        assertEquals(1, success.restoredAttemptsCount)

        // Verify Room contains the restored records
        val testInDb = db.testDao().getTestById("test_res_1")
        assertNotNull("Restored test must exist in Room", testInDb)
        assertEquals("Restorable Test", testInDb!!.title)
        assertEquals(uid, testInDb.uploaderId)

        val qsInDb = db.questionDao().getQuestionsForTestList("test_res_1")
        assertEquals(1, qsInDb.size)
        assertEquals("q_res_1", qsInDb[0].id)

        val attemptsInDb = db.attemptDao().getAttemptsForUser(uid).first()
        assertEquals(1, attemptsInDb.size)
        assertEquals("sess_res_token_1", attemptsInDb[0].attemptSessionToken)

        // Verify SharedPreferences
        val bookmarksPrefs = context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
        val bookmarks = bookmarksPrefs.getStringSet("bookmarks_$uid", null)
        assertNotNull(bookmarks)
        assertTrue(bookmarks!!.contains("test_res_1"))

        val progressPrefs = context.getSharedPreferences("test_progress", Context.MODE_PRIVATE)
        val progress = progressPrefs.getString("progress_${uid}_test_res_1", null)
        assertNotNull(progress)

        val syllabusPrefs = context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE)
        val chapters = syllabusPrefs.getStringSet("completed_chapters_${uid}_NEET", null)
        assertNotNull(chapters)
        assertTrue(chapters!!.contains("Mechanics"))

        val profilePrefs = context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
        assertEquals("ENGLISH", profilePrefs.getString("selected_language", null))
        assertEquals("12th", profilePrefs.getString("class_level", null))
    }

    @Test
    fun testRestore_unauthenticatedRestoreRejection() = runBlocking {
        val uid = "user_auth_check"
        val archiveBytes = createValidArchiveBytes(uid)

        val resultBlank = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = "",
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )
        assertTrue("Blank UID must fail", resultBlank is RestoreResult.Failure)
        assertTrue((resultBlank as RestoreResult.Failure).reason.contains("Unauthenticated"))

        val resultWhitespace = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = "   ",
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )
        assertTrue("Whitespace UID must fail", resultWhitespace is RestoreResult.Failure)
    }

    @Test
    fun testRestore_crossUidRestoreRejection() = runBlocking {
        val archiveOwnerUid = "user_A_original"
        val loggedInUid = "user_B_attacker"
        val archiveBytes = createValidArchiveBytes(archiveOwnerUid)

        val result = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = loggedInUid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )

        assertTrue("Cross-UID restore must be rejected", result is RestoreResult.Failure)
        val failure = result as RestoreResult.Failure
        assertTrue("Reason should mention ownership mismatch", failure.reason.contains("ownership mismatch", ignoreCase = true))

        // Assert nothing was restored into Room or preferences
        val testsInDb = db.testDao().getAllTestsList()
        assertTrue("Room must remain completely empty", testsInDb.isEmpty())
        val attemptsInDb = db.attemptDao().getAllAttempts().first()
        assertTrue("Attempts must remain completely empty", attemptsInDb.isEmpty())
    }

    @Test
    fun testRestore_rejectsIncompleteArchive() = runBlocking {
        val uid = "user_incomplete"
        val archiveBytes = createValidArchiveBytes(
            uid = uid,
            completeness = BackupCompleteness.STRUCTURAL_ONLY
        )

        val result = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )

        assertTrue(result is RestoreResult.Failure)
        val failure = result as RestoreResult.Failure
        assertTrue(failure.reason.contains("Incomplete backup archive rejected"))
    }

    @Test
    fun testRestore_missingMandatoryCategory() = runBlocking {
        val uid = "user_missing_cat"
        val archiveBytes = createValidArchiveBytes(
            uid = uid,
            omitCategory = "data/attempts.json"
        )

        val result = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )

        assertTrue(result is RestoreResult.Failure)
        val failure = result as RestoreResult.Failure
        assertTrue(failure.reason.contains("Missing mandatory backup category: data/attempts.json"))
    }

    @Test
    fun testRestore_invalidJson() = runBlocking {
        val uid = "user_invalid_json"
        val archiveBytes = createValidArchiveBytes(
            uid = uid,
            malformCategory = "data/tests.json"
        )

        val result = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )

        assertTrue(result is RestoreResult.Failure)
        val failure = result as RestoreResult.Failure
        assertTrue(failure.reason.contains("Invalid JSON", ignoreCase = true))
    }

    @Test
    fun testRestore_checksumMismatch() = runBlocking {
        val uid = "user_bad_checksum"
        val archiveBytes = createValidArchiveBytes(
            uid = uid,
            tamperChecksum = true
        )

        val result = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )

        assertTrue(result is RestoreResult.Failure)
        val failure = result as RestoreResult.Failure
        assertTrue(failure.reason.contains("Checksum mismatch", ignoreCase = true))
    }

    @Test
    fun testRestore_unsupportedFutureFormatOrSchema() = runBlocking {
        val uid = "user_future"
        val futureFormatBytes = createValidArchiveBytes(uid = uid, formatVersion = 99)
        val resFormat = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(futureFormatBytes)
        )
        assertTrue(resFormat is RestoreResult.Failure)
        assertTrue((resFormat as RestoreResult.Failure).reason.contains("Unsupported future format version"))

        val futureSchemaBytes = createValidArchiveBytes(uid = uid, schemaVersion = 99)
        val resSchema = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(futureSchemaBytes)
        )
        assertTrue(resSchema is RestoreResult.Failure)
        assertTrue((resSchema as RestoreResult.Failure).reason.contains("Unsupported future database schema version"))
    }

    @Test
    fun testRestore_duplicateRestore_idempotency() = runBlocking {
        val uid = "user_idempotent_1"
        val archiveBytes = createValidArchiveBytes(uid)

        // First restore
        val firstResult = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )
        assertTrue(firstResult is RestoreResult.Success)
        val firstAttempts = db.attemptDao().getAttemptsForUser(uid).first()
        assertEquals(1, firstAttempts.size)

        // Second restore of identical archive
        val secondResult = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )
        assertTrue(secondResult is RestoreResult.Success)
        val secondSuccess = secondResult as RestoreResult.Success
        assertEquals(1, secondSuccess.skippedDuplicateAttemptsCount)

        // Total attempts in database must still be exactly 1 (no duplicate rows created)
        val totalAttempts = db.attemptDao().getAttemptsForUser(uid).first()
        assertEquals("Must not create duplicate attempt rows on re-restore", 1, totalAttempts.size)
    }

    @Test
    fun testRestore_conflictingExistingTest_preservesNewer() = runBlocking {
        val uid = "user_conflict_newer"
        val testId = "test_conflict_1"

        // Seed an existing test in Room with timestamp 5000 (newer)
        val newerLocalTest = TestEntity(
            id = testId,
            title = "Newer Local Edition",
            description = "Edited locally after backup",
            institute = "Private Vault",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Physics",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = uid,
            timestamp = 5000L
        )
        db.testDao().insertTest(newerLocalTest)

        // Archive has the same test with timestamp 1000 (older)
        val archiveBytes = createValidArchiveBytes(
            uid = uid,
            testId = testId,
            testTimestamp = 1000L
        )

        val result = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )

        assertTrue(result is RestoreResult.Success)
        val success = result as RestoreResult.Success
        assertEquals(1, success.preservedNewerTestsCount)

        // Verify the newer local version was preserved
        val testInDb = db.testDao().getTestById(testId)
        assertNotNull(testInDb)
        assertEquals("Newer Local Edition", testInDb!!.title)
        assertEquals(5000L, testInDb.timestamp)
    }

    @Test
    fun testRestore_noMutationOnValidationFailure() = runBlocking {
        val uid = "user_no_mutation"

        // Seed initial data
        val initialTest = TestEntity(
            id = "initial_test",
            title = "Initial Title",
            description = "Desc",
            institute = "Private Vault",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Physics",
            difficulty = "Easy",
            testType = "Part Test",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = uid
        )
        db.testDao().insertTest(initialTest)

        val initialPrefs = context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
        initialPrefs.edit().putStringSet("bookmarks_$uid", setOf("initial_test")).commit()

        // Attempt restore of a corrupted archive
        val corruptArchiveBytes = createValidArchiveBytes(
            uid = uid,
            tamperChecksum = true
        )

        val result = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(corruptArchiveBytes)
        )

        assertTrue(result is RestoreResult.Failure)

        // Verify Room and preferences are 100% untouched
        val testsInDb = db.testDao().getAllTestsList()
        assertEquals(1, testsInDb.size)
        assertEquals("initial_test", testsInDb[0].id)

        val bookmarksAfter = initialPrefs.getStringSet("bookmarks_$uid", null)
        assertEquals(setOf("initial_test"), bookmarksAfter)
    }

    @Test
    fun testRestore_diagramAssetRestoration_andContainment() = runBlocking {
        val uid = "user_diagram_restore"
        val testId = "test_with_diagram_res"

        val testEntity = TestEntity(
            id = testId,
            title = "Diagram Test",
            description = "Desc",
            institute = "Private Vault",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = uid
        )

        val qEntity = QuestionEntity(
            id = "q_diag_1",
            testId = testId,
            questionText = "Question with diagram",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "B", explanation = "Exp", subject = "Physics", chapter = "Optics",
            needsImage = true,
            imageUrl = "private_tests/$testId/diagrams/lens.png"
        )

        val diagramBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val testsArr = JSONArray().put(AccountDataBackupAdapter.testEntityToJson(testEntity))
        val qArr = JSONArray().put(AccountDataBackupAdapter.questionEntityToJson(qEntity))
        val attemptsArr = JSONArray()
        val bookmarksObj = JSONObject().apply {
            put("userId", uid)
            put("testBookmarks", JSONArray())
            put("questionBookmarks", JSONArray())
        }
        val progressObj = JSONObject().apply {
            put("userId", uid)
            put("inProgressSessions", JSONObject())
        }
        val syllabusObj = JSONObject().apply {
            put("userId", uid)
            put("completedChaptersByExam", JSONObject())
        }
        val profileObj = JSONObject().apply {
            put("userId", uid)
        }

        val entries = mutableMapOf<String, ByteArray>()
        entries["data/tests.json"] = testsArr.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/questions.json"] = qArr.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/attempts.json"] = attemptsArr.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/bookmarks.json"] = bookmarksObj.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/progress.json"] = progressObj.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/syllabus.json"] = syllabusObj.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/profile.json"] = profileObj.toString(2).toByteArray(Charsets.UTF_8)
        entries["assets/diagrams/$testId/lens.png"] = diagramBytes

        val checksums = mutableMapOf<String, String>()
        for ((path, bytes) in entries) {
            checksums[path] = BackupArchiveEngine.computeSha256(bytes)
        }

        val manifest = BackupManifest(
            formatVersion = 1,
            databaseSchemaVersion = 19,
            completeness = BackupCompleteness.COMPLETE_USER_DATA,
            owner = BackupOwner(uid = uid),
            stats = BackupStats(
                privateTestsCount = 1,
                questionsCount = 1,
                attemptsCount = 0,
                diagramsCount = 1,
                entryCount = entries.size + 1
            ),
            checksums = checksums
        )

        val out = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(out)
        zipOut.putNextEntry(ZipEntry(BackupArchiveEngine.MANIFEST_ENTRY_NAME))
        zipOut.write(manifest.toJsonString().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
        for ((path, bytes) in entries) {
            zipOut.putNextEntry(ZipEntry(path))
            zipOut.write(bytes)
            zipOut.closeEntry()
        }
        zipOut.finish()

        val result = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(out.toByteArray())
        )

        assertTrue("Restore with diagram asset must succeed", result is RestoreResult.Success)
        val success = result as RestoreResult.Success
        assertEquals(1, success.restoredDiagramsCount)

        // Verify diagram file was placed in context.filesDir/private_tests/{testId}/diagrams/lens.png
        val restoredDiagramFile = File(context.filesDir, "private_tests/$testId/diagrams/lens.png")
        assertTrue("Restored diagram file must exist", restoredDiagramFile.exists())
        assertArrayEquals(diagramBytes, restoredDiagramFile.readBytes())
    }

    @Test
    fun testStageAndValidate_stagesWithoutModifyingLiveRoomData() = runBlocking {
        val uid = "user_staging_only"
        val testId = "test_staging_cuj"
        val archiveBytes = createValidArchiveBytes(uid = uid, testId = testId)

        val stageResult = AccountDataRestoreEngine.stageAndValidateAccountBackup(
            context = context,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )

        assertTrue("Staging and validation must succeed", stageResult is StageValidationResult.Success)
        val success = stageResult as StageValidationResult.Success
        val staged = success.stagedData

        // Verify entities were parsed into staging container
        assertEquals(1, staged.tests.size)
        assertEquals(testId, staged.tests[0].id)
        assertEquals(1, staged.questions.size)
        assertEquals("q_res_1", staged.questions[0].id)
        assertEquals(1, staged.attempts.size)
        assertEquals(1, staged.bookmarks.testBookmarks.size)
        assertEquals(1, staged.progress.inProgressSessions.size)
        assertEquals(1, staged.syllabus.completedChaptersByExam.size)

        // CRITICAL CHECK: Live Room tables must remain 100% UNTOUCHED
        val testsInLiveDb = db.testDao().getAllTestsList()
        assertTrue("Live tests table must be completely empty during staging", testsInLiveDb.isEmpty())

        val questionsInLiveDb = db.questionDao().getQuestionsForTestList(testId)
        assertTrue("Live questions table must be completely empty during staging", questionsInLiveDb.isEmpty())

        val attemptsInLiveDb = db.attemptDao().getAllAttempts().first()
        assertTrue("Live attempts table must be completely empty during staging", attemptsInLiveDb.isEmpty())

        // Live preferences must remain untouched
        val bookmarksPrefs = context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
        assertTrue("Live bookmarks preferences must be empty", bookmarksPrefs.all.isEmpty())

        staged.cleanUp()
    }

    @Test
    fun testStageAndValidate_reprocessingSameArchiveDoesNotDuplicate() = runBlocking {
        val uid = "user_reprocess_cuj"
        val archiveBytes = createValidArchiveBytes(uid = uid)

        val run1 = AccountDataRestoreEngine.stageAndValidateAccountBackup(
            context = context,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )
        assertTrue("Run 1 must succeed", run1 is StageValidationResult.Success)
        val staged1 = (run1 as StageValidationResult.Success).stagedData
        assertEquals(1, staged1.tests.size)
        staged1.cleanUp()

        // Reprocessing the identical archive
        val run2 = AccountDataRestoreEngine.stageAndValidateAccountBackup(
            context = context,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )
        assertTrue("Run 2 must succeed identically", run2 is StageValidationResult.Success)
        val staged2 = (run2 as StageValidationResult.Success).stagedData
        assertEquals(1, staged2.tests.size)
        assertEquals(staged1.tests[0].id, staged2.tests[0].id)
        staged2.cleanUp()

        // Verify live Room database is still 100% untouched
        assertTrue("Live DB must still have 0 tests", db.testDao().getAllTestsList().isEmpty())
    }

    @Test
    fun testStageAndValidate_rejectsDuplicateTestIds() = runBlocking {
        val uid = "user_dup_test"
        val testEntity = TestEntity(
            id = "test_dup_1",
            title = "Test 1",
            description = "Desc",
            institute = "Private Vault",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = uid
        )

        // Put duplicate test ID into testsArray
        val testsArr = JSONArray().put(AccountDataBackupAdapter.testEntityToJson(testEntity))
            .put(AccountDataBackupAdapter.testEntityToJson(testEntity))

        val entries = mutableMapOf<String, ByteArray>()
        entries["data/tests.json"] = testsArr.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/questions.json"] = JSONArray().toString(2).toByteArray(Charsets.UTF_8)
        entries["data/attempts.json"] = JSONArray().toString(2).toByteArray(Charsets.UTF_8)
        entries["data/bookmarks.json"] = JSONObject().apply {
            put("userId", uid)
            put("testBookmarks", JSONArray())
            put("questionBookmarks", JSONArray())
        }.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/progress.json"] = JSONObject().apply {
            put("userId", uid)
            put("inProgressSessions", JSONObject())
        }.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/syllabus.json"] = JSONObject().apply {
            put("userId", uid)
            put("completedChaptersByExam", JSONObject())
        }.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/profile.json"] = JSONObject().apply {
            put("userId", uid)
        }.toString(2).toByteArray(Charsets.UTF_8)

        val checksums = entries.mapValues { BackupArchiveEngine.computeSha256(it.value) }
        val manifest = BackupManifest(
            formatVersion = 1,
            databaseSchemaVersion = 19,
            completeness = BackupCompleteness.COMPLETE_USER_DATA,
            owner = BackupOwner(uid = uid),
            stats = BackupStats(
                privateTestsCount = 2,
                questionsCount = 0,
                attemptsCount = 0,
                diagramsCount = 0,
                entryCount = entries.size + 1
            ),
            checksums = checksums
        )

        val out = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(out)
        zipOut.putNextEntry(ZipEntry(BackupArchiveEngine.MANIFEST_ENTRY_NAME))
        zipOut.write(manifest.toJsonString().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
        for ((p, b) in entries) {
            zipOut.putNextEntry(ZipEntry(p))
            zipOut.write(b)
            zipOut.closeEntry()
        }
        zipOut.finish()

        val result = AccountDataRestoreEngine.stageAndValidateAccountBackup(
            context = context,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(out.toByteArray())
        )

        assertTrue("Must fail due to duplicate test ID", result is StageValidationResult.Failure)
        val failure = result as StageValidationResult.Failure
        assertTrue(failure.reason.contains("Duplicate test ID", ignoreCase = true))
    }

    @Test
    fun testStageAndValidate_rejectsDuplicateQuestionIds() = runBlocking {
        val uid = "user_dup_q"
        val testEntity = TestEntity(
            id = "test_1",
            title = "Test 1",
            description = "Desc",
            institute = "Private Vault",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 2,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = uid
        )

        val qEntity = QuestionEntity(
            id = "q_duplicate_id",
            testId = "test_1",
            questionText = "Q Text",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "A", explanation = "Exp", subject = "Physics", chapter = "Mechanics"
        )

        val testsArr = JSONArray().put(AccountDataBackupAdapter.testEntityToJson(testEntity))
        val qArr = JSONArray().put(AccountDataBackupAdapter.questionEntityToJson(qEntity))
            .put(AccountDataBackupAdapter.questionEntityToJson(qEntity))

        val entries = mutableMapOf<String, ByteArray>()
        entries["data/tests.json"] = testsArr.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/questions.json"] = qArr.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/attempts.json"] = JSONArray().toString(2).toByteArray(Charsets.UTF_8)
        entries["data/bookmarks.json"] = JSONObject().apply {
            put("userId", uid)
            put("testBookmarks", JSONArray())
            put("questionBookmarks", JSONArray())
        }.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/progress.json"] = JSONObject().apply {
            put("userId", uid)
            put("inProgressSessions", JSONObject())
        }.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/syllabus.json"] = JSONObject().apply {
            put("userId", uid)
            put("completedChaptersByExam", JSONObject())
        }.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/profile.json"] = JSONObject().apply {
            put("userId", uid)
        }.toString(2).toByteArray(Charsets.UTF_8)

        val checksums = entries.mapValues { BackupArchiveEngine.computeSha256(it.value) }
        val manifest = BackupManifest(
            formatVersion = 1,
            databaseSchemaVersion = 19,
            completeness = BackupCompleteness.COMPLETE_USER_DATA,
            owner = BackupOwner(uid = uid),
            stats = BackupStats(
                privateTestsCount = 1,
                questionsCount = 2,
                attemptsCount = 0,
                diagramsCount = 0,
                entryCount = entries.size + 1
            ),
            checksums = checksums
        )

        val out = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(out)
        zipOut.putNextEntry(ZipEntry(BackupArchiveEngine.MANIFEST_ENTRY_NAME))
        zipOut.write(manifest.toJsonString().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
        for ((p, b) in entries) {
            zipOut.putNextEntry(ZipEntry(p))
            zipOut.write(b)
            zipOut.closeEntry()
        }
        zipOut.finish()

        val result = AccountDataRestoreEngine.stageAndValidateAccountBackup(
            context = context,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(out.toByteArray())
        )

        assertTrue("Must fail due to duplicate question ID", result is StageValidationResult.Failure)
        val failure = result as StageValidationResult.Failure
        assertTrue(failure.reason.contains("Duplicate question ID", ignoreCase = true))
    }

    @Test
    fun testStageAndValidate_rejectsDuplicateAttemptIds() = runBlocking {
        val uid = "user_dup_att"
        val attemptEntity = AttemptEntity(
            id = 55,
            userId = uid,
            testId = "test_1",
            testTitle = "Test 1",
            score = 10.0, maxScore = 10.0, percentile = 90.0, accuracy = 90.0,
            correctCount = 1, incorrectCount = 0, skippedCount = 0, timeSpentSeconds = 60L,
            answersJson = "{}"
        )

        val attemptsArr = JSONArray().put(AccountDataBackupAdapter.attemptEntityToJson(attemptEntity))
            .put(AccountDataBackupAdapter.attemptEntityToJson(attemptEntity))

        val entries = mutableMapOf<String, ByteArray>()
        entries["data/tests.json"] = JSONArray().toString(2).toByteArray(Charsets.UTF_8)
        entries["data/questions.json"] = JSONArray().toString(2).toByteArray(Charsets.UTF_8)
        entries["data/attempts.json"] = attemptsArr.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/bookmarks.json"] = JSONObject().apply {
            put("userId", uid)
            put("testBookmarks", JSONArray())
            put("questionBookmarks", JSONArray())
        }.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/progress.json"] = JSONObject().apply {
            put("userId", uid)
            put("inProgressSessions", JSONObject())
        }.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/syllabus.json"] = JSONObject().apply {
            put("userId", uid)
            put("completedChaptersByExam", JSONObject())
        }.toString(2).toByteArray(Charsets.UTF_8)
        entries["data/profile.json"] = JSONObject().apply {
            put("userId", uid)
        }.toString(2).toByteArray(Charsets.UTF_8)

        val checksums = entries.mapValues { BackupArchiveEngine.computeSha256(it.value) }
        val manifest = BackupManifest(
            formatVersion = 1,
            databaseSchemaVersion = 19,
            completeness = BackupCompleteness.COMPLETE_USER_DATA,
            owner = BackupOwner(uid = uid),
            stats = BackupStats(
                privateTestsCount = 0,
                questionsCount = 0,
                attemptsCount = 2,
                diagramsCount = 0,
                entryCount = entries.size + 1
            ),
            checksums = checksums
        )

        val out = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(out)
        zipOut.putNextEntry(ZipEntry(BackupArchiveEngine.MANIFEST_ENTRY_NAME))
        zipOut.write(manifest.toJsonString().toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
        for ((p, b) in entries) {
            zipOut.putNextEntry(ZipEntry(p))
            zipOut.write(b)
            zipOut.closeEntry()
        }
        zipOut.finish()

        val result = AccountDataRestoreEngine.stageAndValidateAccountBackup(
            context = context,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(out.toByteArray())
        )

        assertTrue("Must fail due to duplicate attempt ID", result is StageValidationResult.Failure)
        val failure = result as StageValidationResult.Failure
        assertTrue(failure.reason.contains("Duplicate attempt ID", ignoreCase = true))
    }
}
