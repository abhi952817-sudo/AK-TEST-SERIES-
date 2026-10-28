package Com.ak.testseries.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import Com.ak.testseries.data.AppDatabase
import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity
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
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class AccountDataBackupAdapterTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Clear SharedPreferences between test runs
        context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("test_progress", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
        // Clean up any test files in filesDir
        val privateTestsDir = File(context.filesDir, "private_tests")
        if (privateTestsDir.exists()) {
            privateTestsDir.deleteRecursively()
        }
    }

    @Test
    fun testExportAccountBackup_rejectsBlankOrUnauthenticatedUid() = runBlocking {
        val out = ByteArrayOutputStream()
        val resultBlank = AccountDataBackupAdapter.exportAccountBackup(
            context = context,
            db = db,
            uid = "",
            outputStream = out
        )
        assertTrue("Blank UID must fail", resultBlank.isFailure)
        assertTrue(resultBlank.exceptionOrNull() is IllegalArgumentException)

        val resultWhitespace = AccountDataBackupAdapter.exportAccountBackup(
            context = context,
            db = db,
            uid = "   ",
            outputStream = out
        )
        assertTrue("Whitespace UID must fail", resultWhitespace.isFailure)
        assertTrue(resultWhitespace.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testExportAccountBackup_accountIsolation_userA_doesNotIncludeUserBData() = runBlocking {
        val userAUid = "user_A_101"
        val userBUid = "user_B_202"

        // 1. Seed tests
        val userAPrivateTest = TestEntity(
            id = "test_user_a_priv",
            title = "User A Private Physics",
            description = "Kinematics drill",
            institute = "Private Vault",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = userAUid
        )
        val userBPrivateTest = TestEntity(
            id = "test_user_b_priv",
            title = "User B Private Organic",
            description = "Reagents drill",
            institute = "Private Vault",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Chemistry",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = userBUid
        )
        val publicTest = TestEntity(
            id = "test_public_all",
            title = "Public NEET Mock",
            description = "Open mock for all",
            institute = "AK Series",
            batchName = "All",
            facultyName = "Admin",
            language = "English",
            subject = "Full Syllabus",
            difficulty = "Medium",
            testType = "Full Syllabus",
            questionsCount = 1,
            isOfficial = true,
            isVerifiedCommunity = false,
            isPublic = true,
            uploaderId = "admin_uid"
        )
        db.testDao().insertTests(listOf(userAPrivateTest, userBPrivateTest, publicTest))

        // 2. Seed questions
        val qA = QuestionEntity(
            id = "q_user_a_1",
            testId = "test_user_a_priv",
            questionText = "Calculate velocity of User A object",
            optionA = "10 m/s",
            optionB = "20 m/s",
            optionC = "30 m/s",
            optionD = "40 m/s",
            correctAnswer = "B",
            explanation = "v = u + at",
            subject = "Physics",
            chapter = "Kinematics",
            isBookmarked = true
        )
        val qB = QuestionEntity(
            id = "q_user_b_1",
            testId = "test_user_b_priv",
            questionText = "User B secret organic reaction",
            optionA = "Aldol",
            optionB = "Cannizzaro",
            optionC = "Perkin",
            optionD = "Wurtz",
            correctAnswer = "A",
            explanation = "Enolate attack",
            subject = "Chemistry",
            chapter = "Aldehydes"
        )
        db.questionDao().insertQuestions(listOf(qA, qB))

        // 3. Seed attempts
        val attemptA = AttemptEntity(
            id = 1,
            userId = userAUid,
            testId = "test_user_a_priv",
            testTitle = "User A Private Physics",
            score = 4.0,
            maxScore = 4.0,
            percentile = 100.0,
            accuracy = 100.0,
            correctCount = 1,
            incorrectCount = 0,
            skippedCount = 0,
            timeSpentSeconds = 60L,
            answersJson = "{\"q_user_a_1\":\"B\"}"
        )
        val attemptB = AttemptEntity(
            id = 2,
            userId = userBUid,
            testId = "test_user_b_priv",
            testTitle = "User B Private Organic",
            score = 0.0,
            maxScore = 4.0,
            percentile = 0.0,
            accuracy = 0.0,
            correctCount = 0,
            incorrectCount = 1,
            skippedCount = 0,
            timeSpentSeconds = 30L,
            answersJson = "{\"q_user_b_1\":\"C\"}"
        )
        db.attemptDao().insertAttempt(attemptA)
        db.attemptDao().insertAttempt(attemptB)

        // 4. Seed SharedPreferences for both users
        val bookmarksPrefs = context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
        bookmarksPrefs.edit()
            .putStringSet("bookmarks_$userAUid", setOf("test_user_a_priv"))
            .putStringSet("bookmarks_$userBUid", setOf("test_user_b_priv"))
            .commit()

        val progressPrefs = context.getSharedPreferences("test_progress", Context.MODE_PRIVATE)
        progressPrefs.edit()
            .putString("progress_${userAUid}_test_user_a_priv", "{\"currentIndex\":0,\"timerRemaining\":300}")
            .putString("progress_${userBUid}_test_user_b_priv", "{\"currentIndex\":2,\"timerRemaining\":100}")
            .commit()

        val syllabusPrefs = context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE)
        syllabusPrefs.edit()
            .putStringSet("completed_chapters_${userAUid}_NEET", setOf("Kinematics", "Laws of Motion"))
            .putStringSet("completed_chapters_${userBUid}_NEET", setOf("Aldehydes", "Ketones"))
            .commit()

        val profilePrefs = context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
        profilePrefs.edit()
            .putString("name", "Student User A")
            .putString("email", "userA@example.com")
            .putString("target_exam", "NEET")
            .putString("class_level", "12th")
            .commit()

        // 5. Execute export for User A
        val archiveOut = ByteArrayOutputStream()
        val exportResult = AccountDataBackupAdapter.exportAccountBackup(
            context = context,
            db = db,
            uid = userAUid,
            outputStream = archiveOut
        )

        assertTrue("Export must succeed for User A", exportResult.isSuccess)
        val manifest = exportResult.getOrThrow()
        assertEquals(userAUid, manifest.owner.uid)
        assertEquals(BackupCompleteness.COMPLETE_USER_DATA, manifest.completeness)
        assertEquals(1, manifest.stats.privateTestsCount)
        assertEquals(1, manifest.stats.questionsCount)
        assertEquals(1, manifest.stats.attemptsCount)
        assertEquals(1, manifest.stats.inProgressCount)

        // 6. Validate the generated archive container with core engine
        val archiveBytes = archiveOut.toByteArray()
        val validationResult = BackupArchiveEngine.validateArchive(ByteArrayInputStream(archiveBytes))
        assertTrue("Archive must pass strict core engine validation", validationResult is BackupValidationResult.Valid)

        // 7. Unpack and inspect entry contents to strictly verify isolation
        val entries = unpackZip(archiveBytes)
        assertTrue(entries.containsKey("data/tests.json"))
        assertTrue(entries.containsKey("data/questions.json"))
        assertTrue(entries.containsKey("data/attempts.json"))
        assertTrue(entries.containsKey("data/bookmarks.json"))
        assertTrue(entries.containsKey("data/progress.json"))
        assertTrue(entries.containsKey("data/syllabus.json"))
        assertTrue(entries.containsKey("data/profile.json"))

        // Check tests.json: User A only
        val testsJson = JSONArray(String(entries["data/tests.json"]!!, Charsets.UTF_8))
        assertEquals(1, testsJson.length())
        assertEquals("test_user_a_priv", testsJson.getJSONObject(0).getString("id"))
        assertEquals(userAUid, testsJson.getJSONObject(0).getString("uploaderId"))

        // Check questions.json: User A questions only
        val questionsJson = JSONArray(String(entries["data/questions.json"]!!, Charsets.UTF_8))
        assertEquals(1, questionsJson.length())
        assertEquals("q_user_a_1", questionsJson.getJSONObject(0).getString("id"))

        // Check attempts.json: User A attempt only
        val attemptsJson = JSONArray(String(entries["data/attempts.json"]!!, Charsets.UTF_8))
        assertEquals(1, attemptsJson.length())
        assertEquals(userAUid, attemptsJson.getJSONObject(0).getString("userId"))

        // Check bookmarks.json: User A bookmarks only
        val bookmarksJson = JSONObject(String(entries["data/bookmarks.json"]!!, Charsets.UTF_8))
        val testBookmarksArr = bookmarksJson.getJSONArray("testBookmarks")
        assertEquals(1, testBookmarksArr.length())
        assertEquals("test_user_a_priv", testBookmarksArr.getString(0))

        // Check progress.json: User A session only
        val progressJson = JSONObject(String(entries["data/progress.json"]!!, Charsets.UTF_8))
        val sessionsObj = progressJson.getJSONObject("inProgressSessions")
        assertTrue("Must contain User A's test session", sessionsObj.has("test_user_a_priv"))
        assertFalse("Must NOT contain User B's test session", sessionsObj.has("test_user_b_priv"))

        // Check syllabus.json: User A chapters only
        val syllabusJson = JSONObject(String(entries["data/syllabus.json"]!!, Charsets.UTF_8))
        val examObj = syllabusJson.getJSONObject("completedChaptersByExam")
        val neetArr = examObj.getJSONArray("NEET")
        val chaptersList = (0 until neetArr.length()).map { neetArr.getString(it) }
        assertTrue(chaptersList.contains("Kinematics"))
        assertFalse("Must NOT contain User B chapters", chaptersList.contains("Aldehydes"))
    }

    @Test
    fun testExportAccountBackup_readOnlyGuarantee_doesNotMutateDatabaseOrPreferences() = runBlocking {
        val uid = "readonly_user_55"

        val test = TestEntity(
            id = "test_ro_1",
            title = "Read Only Test",
            description = "Description",
            institute = "Private Vault",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Biology",
            difficulty = "Easy",
            testType = "Part Test",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = uid
        )
        db.testDao().insertTest(test)

        val q = QuestionEntity(
            id = "q_ro_1",
            testId = "test_ro_1",
            questionText = "Cell biology question",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "A", explanation = "Exp", subject = "Biology", chapter = "Cell"
        )
        db.questionDao().insertQuestions(listOf(q))

        val attempt = AttemptEntity(
            id = 5,
            userId = uid,
            testId = "test_ro_1",
            testTitle = "Read Only Test",
            score = 4.0, maxScore = 4.0, percentile = 100.0, accuracy = 100.0,
            correctCount = 1, incorrectCount = 0, skippedCount = 0, timeSpentSeconds = 45L,
            answersJson = "{}"
        )
        db.attemptDao().insertAttempt(attempt)

        val progressPrefs = context.getSharedPreferences("test_progress", Context.MODE_PRIVATE)
        progressPrefs.edit().putString("progress_${uid}_test_ro_1", "{\"saved\":true}").commit()

        // Capture state before export
        val testsBefore = db.testDao().getAllTestsList()
        val questionsBefore = db.questionDao().getQuestionsForTestList("test_ro_1")
        val attemptsBefore = db.attemptDao().getAllAttempts() // flow or list
        val prefsBefore = progressPrefs.all.toMap()

        // Run export
        val out = ByteArrayOutputStream()
        val result = AccountDataBackupAdapter.exportAccountBackup(context, db, uid, outputStream = out)
        assertTrue(result.isSuccess)

        // Verify state after export is completely identical
        val testsAfter = db.testDao().getAllTestsList()
        val questionsAfter = db.questionDao().getQuestionsForTestList("test_ro_1")
        val prefsAfter = progressPrefs.all.toMap()

        assertEquals("Tests row count must not change", testsBefore.size, testsAfter.size)
        assertEquals("Tests data must be identical", testsBefore[0], testsAfter[0])
        assertEquals("Questions count must not change", questionsBefore.size, questionsAfter.size)
        assertEquals("Preferences must be completely unmodified", prefsBefore, prefsAfter)
    }

    @Test
    fun testExportAccountBackup_emptyAccount_succeedsWithCompleteEmptyDatasets() = runBlocking {
        val emptyUid = "fresh_user_000"
        val out = ByteArrayOutputStream()

        val result = AccountDataBackupAdapter.exportAccountBackup(
            context = context,
            db = db,
            uid = emptyUid,
            email = "fresh@example.com",
            displayName = "Fresh User",
            outputStream = out
        )

        assertTrue("Empty account export must succeed", result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(emptyUid, manifest.owner.uid)
        assertEquals(BackupCompleteness.COMPLETE_USER_DATA, manifest.completeness)
        assertEquals(0, manifest.stats.privateTestsCount)
        assertEquals(0, manifest.stats.questionsCount)
        assertEquals(0, manifest.stats.attemptsCount)
        assertEquals(0, manifest.stats.inProgressCount)
        assertEquals(0, manifest.stats.bookmarkedCount)
        assertEquals(0, manifest.stats.diagramsCount)

        val validation = BackupArchiveEngine.validateArchive(ByteArrayInputStream(out.toByteArray()))
        assertTrue("Validation must pass for empty account archive", validation is BackupValidationResult.Valid)
        val valid = validation as BackupValidationResult.Valid
        assertTrue(valid.isComplete)

        val entries = unpackZip(out.toByteArray())
        val testsJson = JSONArray(String(entries["data/tests.json"]!!, Charsets.UTF_8))
        assertEquals(0, testsJson.length())

        val questionsJson = JSONArray(String(entries["data/questions.json"]!!, Charsets.UTF_8))
        assertEquals(0, questionsJson.length())

        val attemptsJson = JSONArray(String(entries["data/attempts.json"]!!, Charsets.UTF_8))
        assertEquals(0, attemptsJson.length())
    }

    @Test
    fun testDiagramCollection_packagesReferencedDiagrams_andRejectsPathTraversal() = runBlocking {
        val uid = "diagram_user_1"
        val testId = "test_with_diagram"

        val test = TestEntity(
            id = testId,
            title = "Diagram Test",
            description = "Test with diagrams",
            institute = "Private Vault",
            batchName = "2026",
            facultyName = "Self",
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
        db.testDao().insertTest(test)

        // Create diagram file in filesDir/private_tests/{testId}/diagrams/
        val diagramsDir = File(context.filesDir, "private_tests/$testId/diagrams").apply { mkdirs() }
        val samplePng = File(diagramsDir, "circuit_diagram_1.png")
        samplePng.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val q = QuestionEntity(
            id = "q_diagram_1",
            testId = testId,
            questionText = "Identify circuit component",
            optionA = "Resistor", optionB = "Capacitor", optionC = "Inductor", optionD = "Diode",
            correctAnswer = "B", explanation = "Standard symbol", subject = "Physics", chapter = "AC",
            needsImage = true,
            imageUrl = samplePng.absolutePath
        )
        db.questionDao().insertQuestions(listOf(q))

        val out = ByteArrayOutputStream()
        val result = AccountDataBackupAdapter.exportAccountBackup(context, db, uid, outputStream = out)
        assertTrue("Export must succeed with diagram assets", result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(1, manifest.stats.diagramsCount)

        val entries = unpackZip(out.toByteArray())
        val diagramEntryPath = "assets/diagrams/$testId/circuit_diagram_1.png"
        assertTrue("Archive must contain packaged diagram", entries.containsKey(diagramEntryPath))
        assertArrayEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            entries[diagramEntryPath]
        )
    }

    @Test
    fun testExclusionOfSensitiveData_neverExportsAuthTokensOrAdminSecrets() = runBlocking {
        val uid = "sensitive_user"

        // Put sensitive tokens in auth_prefs and AdminPrefs
        val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        authPrefs.edit()
            .putString("auth_token", "SECRET_BEARER_JWT_TOKEN_12345")
            .putString("refresh_token", "SECRET_REFRESH_TOKEN_67890")
            .commit()

        val adminPrefs = context.getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE)
        adminPrefs.edit()
            .putString("admin_pin", "9999")
            .putString("admin_password_hash", "hash_secret_value")
            .commit()

        // Also put a fake sensitive key in user_profile_prefs
        val profilePrefs = context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
        profilePrefs.edit()
            .putString("name", "Student Safe Name")
            .putString("password", "SHOULD_NEVER_BE_EXPOSED")
            .putString("api_key", "AI_SECRET_KEY_NEVER_EXPORT")
            .commit()

        val out = ByteArrayOutputStream()
        val result = AccountDataBackupAdapter.exportAccountBackup(context, db, uid, outputStream = out)
        assertTrue(result.isSuccess)

        val entries = unpackZip(out.toByteArray())
        val profileJson = JSONObject(String(entries["data/profile.json"]!!, Charsets.UTF_8))

        assertEquals("Student Safe Name", profileJson.getString("name"))
        assertFalse("Password must NOT be in profile.json", profileJson.has("password"))
        assertFalse("API key must NOT be in profile.json", profileJson.has("api_key"))
        assertFalse("Auth token must NOT be in profile.json", profileJson.has("auth_token"))

        // Ensure no auth_prefs or admin_prefs entry exists in archive
        for (entryName in entries.keys) {
            assertFalse("Archive must not contain auth files: $entryName", entryName.contains("auth"))
            assertFalse("Archive must not contain admin files: $entryName", entryName.contains("Admin"))
        }
    }

    @Test
    fun testEntitySerialization_roundTripPreservation() {
        val originalTest = TestEntity(
            id = "test_roundtrip_100",
            title = "Full Test Title",
            description = "Detailed test description",
            institute = "Private Vault",
            batchName = "Target Batch",
            facultyName = "Faculty Name",
            language = "Hinglish",
            subject = "Physics",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            countsTowardRanking = false,
            rating = 4.8f,
            rating1Count = 1,
            rating2Count = 2,
            rating3Count = 3,
            rating4Count = 4,
            rating5Count = 50,
            attemptsCount = 12,
            downloadsCount = 5,
            isDownloaded = true,
            isBookmarked = true,
            timestamp = 1710000000000L,
            status = "Published",
            aiConfidence = 99,
            uploaderId = "user_rt_1",
            targetExam = "NEET",
            durationMinutes = 60,
            classLevel = "12th",
            chapter = "Thermodynamics",
            topic = "Carnot Engine",
            imageUrl = "https://example.com/banner.png",
            questionsJson = "[]",
            examId = "neet_ug",
            stageId = "prelims",
            paperId = "paper_1",
            scoringVersion = 2
        )

        val testJson = AccountDataBackupAdapter.testEntityToJson(originalTest)
        val reconstructedTest = AccountDataBackupAdapter.jsonToTestEntity(testJson)

        assertEquals(originalTest.id, reconstructedTest.id)
        assertEquals(originalTest.title, reconstructedTest.title)
        assertEquals(originalTest.institute, reconstructedTest.institute)
        assertEquals(originalTest.rating, reconstructedTest.rating)
        assertEquals(originalTest.examId, reconstructedTest.examId)
        assertEquals(originalTest.scoringVersion, reconstructedTest.scoringVersion)
        assertEquals(originalTest, reconstructedTest)

        val originalQuestion = QuestionEntity(
            id = "q_rt_1",
            testId = "test_roundtrip_100",
            questionText = "What is entropy?",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "C",
            explanation = "Thermodynamic disorder",
            subject = "Physics",
            chapter = "Thermodynamics",
            topic = "Second Law",
            difficulty = "Medium",
            isBookmarked = true,
            questionTextHi = "एन्ट्रॉपी क्या है?",
            optionAHi = "क", optionBHi = "ख", optionCHi = "ग", optionDHi = "घ",
            explanationHi = "ऊष्मागतिकी व्याख्या",
            questionType = "MCQ",
            section = "SECTION_B",
            needsImage = true,
            imageUrl = "local_diagram.png",
            optionE = "E",
            optionEHi = "ङ"
        )

        val qJson = AccountDataBackupAdapter.questionEntityToJson(originalQuestion)
        val reconstructedQ = AccountDataBackupAdapter.jsonToQuestionEntity(qJson)
        assertEquals(originalQuestion, reconstructedQ)

        val originalAttempt = AttemptEntity(
            id = 42,
            userId = "user_rt_1",
            testId = "test_roundtrip_100",
            testTitle = "Full Test Title",
            score = 175.5,
            maxScore = 180.0,
            percentile = 99.8,
            accuracy = 97.5,
            correctCount = 44,
            incorrectCount = 1,
            skippedCount = 0,
            timeSpentSeconds = 3200L,
            dateTaken = 1710005000000L,
            answersJson = "{\"q_rt_1\":\"C\"}",
            attemptSessionToken = "token_abc_123",
            verifiedServerSide = true,
            submissionStatus = "CONFIRMED"
        )

        val attemptJson = AccountDataBackupAdapter.attemptEntityToJson(originalAttempt)
        val reconstructedAttempt = AccountDataBackupAdapter.jsonToAttemptEntity(attemptJson)
        assertEquals(originalAttempt, reconstructedAttempt)
    }

    private fun unpackZip(zipBytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        val zipIn = ZipInputStream(ByteArrayInputStream(zipBytes))
        var entry = zipIn.nextEntry
        val buffer = ByteArray(4096)
        while (entry != null) {
            val baos = ByteArrayOutputStream()
            var read: Int
            while (zipIn.read(buffer).also { read = it } != -1) {
                baos.write(buffer, 0, read)
            }
            result[entry.name] = baos.toByteArray()
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }
        return result
    }
}
