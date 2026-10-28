package Com.ak.testseries.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import Com.ak.testseries.data.AppDatabase
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

/**
 * Focused unit test suite for Phase 4B-2: Question & Diagram Backup Adapters.
 *
 * Verifies:
 * 1. Questions exported strictly for eligible, authenticated-user-owned tests.
 * 2. Cross-user test questions and public community test questions are excluded.
 * 3. 100% field preservation across all 26 fields of QuestionEntity.
 * 4. Rejection of unauthenticated or blank UIDs.
 * 5. Inclusion of referenced diagrams from filesDir/private_tests/{testId}/diagrams/ and question imageUrls.
 * 6. Exclusion of unreferenced diagrams and other users' diagrams.
 * 7. Controlled failure on missing referenced diagrams under strict asset integrity.
 * 8. Rejection of path traversal attempts (../ or paths escaping filesDir) with SecurityException.
 * 9. Rejection of media exceeding single-entry size limits.
 * 10. Archive completeness declared strictly as BackupCompleteness.STRUCTURAL_ONLY.
 * 11. Controlled failure on malformed question JSON arrays.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class QuestionAndDiagramBackupAdapterTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Clean up filesDir test files
        val privateTestsDir = File(context.filesDir, "private_tests")
        if (privateTestsDir.exists()) {
            privateTestsDir.deleteRecursively()
        }
    }

    @After
    fun tearDown() {
        db.close()
        val privateTestsDir = File(context.filesDir, "private_tests")
        if (privateTestsDir.exists()) {
            privateTestsDir.deleteRecursively()
        }
    }

    @Test
    fun testQuestionEntitySerialization_all26FieldsPreserved() {
        val original = QuestionEntity(
            id = "q_full_26_fields",
            testId = "test_owner_101",
            questionText = "What is the frequency of radiation emitted during n=3 to n=2 transition?",
            optionA = "4.56 x 10^14 Hz",
            optionB = "6.12 x 10^14 Hz",
            optionC = "3.28 x 10^15 Hz",
            optionD = "1.89 x 10^15 Hz",
            correctAnswer = "A",
            explanation = "Using Rydberg formula: 1/lambda = R*(1/2^2 - 1/3^2), nu = c/lambda",
            subject = "Physics",
            chapter = "Modern Physics",
            topic = "Bohr Model",
            difficulty = "Hard",
            isBookmarked = true,
            questionTextHi = "n=3 से n=2 संक्रमण के दौरान उत्सर्जित विकिरण की आवृत्ति क्या है?",
            optionAHi = "4.56 x 10^14 हर्ट्ज़",
            optionBHi = "6.12 x 10^14 हर्ट्ज़",
            optionCHi = "3.28 x 10^15 हर्ट्ज़",
            optionDHi = "1.89 x 10^15 हर्ट्ज़",
            explanationHi = "रिडबर्ग सूत्र का उपयोग करके आवृत्ति ज्ञात करें",
            questionType = "MCQ",
            section = "SECTION_B",
            needsImage = true,
            imageUrl = "private_tests/test_owner_101/diagrams/bohr_levels.png",
            optionE = "None of the above",
            optionEHi = "उपरोक्त में से कोई नहीं"
        )

        val json = AccountDataBackupAdapter.questionEntityToJson(original)
        val reconstructed = AccountDataBackupAdapter.jsonToQuestionEntity(json)

        assertEquals("id must match", original.id, reconstructed.id)
        assertEquals("testId must match", original.testId, reconstructed.testId)
        assertEquals("questionText must match", original.questionText, reconstructed.questionText)
        assertEquals("optionA must match", original.optionA, reconstructed.optionA)
        assertEquals("optionB must match", original.optionB, reconstructed.optionB)
        assertEquals("optionC must match", original.optionC, reconstructed.optionC)
        assertEquals("optionD must match", original.optionD, reconstructed.optionD)
        assertEquals("correctAnswer must match", original.correctAnswer, reconstructed.correctAnswer)
        assertEquals("explanation must match", original.explanation, reconstructed.explanation)
        assertEquals("subject must match", original.subject, reconstructed.subject)
        assertEquals("chapter must match", original.chapter, reconstructed.chapter)
        assertEquals("topic must match", original.topic, reconstructed.topic)
        assertEquals("difficulty must match", original.difficulty, reconstructed.difficulty)
        assertEquals("isBookmarked must match", original.isBookmarked, reconstructed.isBookmarked)
        assertEquals("questionTextHi must match", original.questionTextHi, reconstructed.questionTextHi)
        assertEquals("optionAHi must match", original.optionAHi, reconstructed.optionAHi)
        assertEquals("optionBHi must match", original.optionBHi, reconstructed.optionBHi)
        assertEquals("optionCHi must match", original.optionCHi, reconstructed.optionCHi)
        assertEquals("optionDHi must match", original.optionDHi, reconstructed.optionDHi)
        assertEquals("explanationHi must match", original.explanationHi, reconstructed.explanationHi)
        assertEquals("questionType must match", original.questionType, reconstructed.questionType)
        assertEquals("section must match", original.section, reconstructed.section)
        assertEquals("needsImage must match", original.needsImage, reconstructed.needsImage)
        assertEquals("imageUrl must match", original.imageUrl, reconstructed.imageUrl)
        assertEquals("optionE must match", original.optionE, reconstructed.optionE)
        assertEquals("optionEHi must match", original.optionEHi, reconstructed.optionEHi)
    }

    @Test
    fun testQuestionEntitySerialization_nullableFieldsHandledSafely() {
        val originalWithNulls = QuestionEntity(
            id = "q_nulls_only",
            testId = "test_nulls",
            questionText = "Simple question",
            optionA = "1",
            optionB = "2",
            optionC = "3",
            optionD = "4",
            correctAnswer = "B",
            explanation = "Explanation text",
            subject = "Chemistry",
            chapter = "Thermodynamics",
            topic = "Enthalpy",
            difficulty = "Easy",
            isBookmarked = false,
            questionTextHi = null,
            optionAHi = null,
            optionBHi = null,
            optionCHi = null,
            optionDHi = null,
            explanationHi = null,
            questionType = "MCQ",
            section = "SECTION_A",
            needsImage = false,
            imageUrl = null,
            optionE = null,
            optionEHi = null
        )

        val json = AccountDataBackupAdapter.questionEntityToJson(originalWithNulls)
        val reconstructed = AccountDataBackupAdapter.jsonToQuestionEntity(json)

        assertEquals(originalWithNulls.id, reconstructed.id)
        assertNull("questionTextHi must be null", reconstructed.questionTextHi)
        assertNull("optionAHi must be null", reconstructed.optionAHi)
        assertNull("optionBHi must be null", reconstructed.optionBHi)
        assertNull("optionCHi must be null", reconstructed.optionCHi)
        assertNull("optionDHi must be null", reconstructed.optionDHi)
        assertNull("explanationHi must be null", reconstructed.explanationHi)
        assertNull("imageUrl must be null", reconstructed.imageUrl)
        assertNull("optionE must be null", reconstructed.optionE)
        assertNull("optionEHi must be null", reconstructed.optionEHi)
        assertFalse(reconstructed.needsImage)
        assertFalse(reconstructed.isBookmarked)
    }

    @Test
    fun testSerializeAndDeserializeQuestionsListRoundTrip() {
        val q1 = QuestionEntity(
            id = "q_list_1",
            testId = "test_1",
            questionText = "Q1",
            optionA = "A1",
            optionB = "B1",
            optionC = "C1",
            optionD = "D1",
            correctAnswer = "A",
            explanation = "Exp 1",
            subject = "Physics",
            chapter = "Optics"
        )
        val q2 = QuestionEntity(
            id = "q_list_2",
            testId = "test_1",
            questionText = "Q2",
            optionA = "A2",
            optionB = "B2",
            optionC = "C2",
            optionD = "D2",
            correctAnswer = "C",
            explanation = "Exp 2",
            subject = "Biology",
            chapter = "Genetics",
            optionE = "E2"
        )

        val array = AccountDataBackupAdapter.serializeQuestionsToJsonArray(listOf(q1, q2))
        assertEquals(2, array.length())

        val deserializedResult = AccountDataBackupAdapter.deserializeQuestionsFromJsonArray(array)
        assertTrue(deserializedResult.isSuccess)
        val deserialized = deserializedResult.getOrThrow()
        assertEquals(2, deserialized.size)
        assertEquals("q_list_1", deserialized[0].id)
        assertEquals("q_list_2", deserialized[1].id)
        assertEquals("E2", deserialized[1].optionE)
    }

    @Test
    fun testDeserializeQuestions_malformedDataRejection() {
        // Missing id
        val invalid1 = JSONArray().apply {
            put(JSONObject().apply {
                put("testId", "test_1")
                put("questionText", "No ID")
            })
        }
        val result1 = AccountDataBackupAdapter.deserializeQuestionsFromJsonArray(invalid1)
        assertTrue("Missing ID must fail", result1.isFailure)
        assertTrue(result1.exceptionOrNull() is IllegalArgumentException)

        // Missing testId
        val invalid2 = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "q_valid")
                put("questionText", "No testId")
            })
        }
        val result2 = AccountDataBackupAdapter.deserializeQuestionsFromJsonArray(invalid2)
        assertTrue("Missing testId must fail", result2.isFailure)
        assertTrue(result2.exceptionOrNull() is IllegalArgumentException)

        // Non-object item
        val invalid3 = JSONArray().apply {
            put("invalid_string_item")
        }
        val result3 = AccountDataBackupAdapter.deserializeQuestionsFromJsonArray(invalid3)
        assertTrue("Non-object item must fail", result3.isFailure)
        assertTrue(result3.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testExportRoomQuestionsAndDiagramsBackup_rejectsBlankUid() = runBlocking {
        val out = ByteArrayOutputStream()
        val resultBlank = AccountDataBackupAdapter.exportRoomQuestionsAndDiagramsBackup(
            context = context,
            db = db,
            uid = "",
            outputStream = out
        )
        assertTrue("Blank UID must fail", resultBlank.isFailure)
        assertTrue(resultBlank.exceptionOrNull() is IllegalArgumentException)

        val resultWhitespace = AccountDataBackupAdapter.exportRoomQuestionsAndDiagramsBackup(
            context = context,
            db = db,
            uid = "   ",
            outputStream = out
        )
        assertTrue("Whitespace UID must fail", resultWhitespace.isFailure)
        assertTrue(resultWhitespace.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testExportRoomQuestionsAndDiagramsBackup_ownershipIsolation_onlyExportsEligibleQuestions() = runBlocking {
        val authUid = "user_me_123"
        val otherUid = "user_other_456"

        // 1. Owned private test
        val myPrivateTest = TestEntity(
            id = "test_my_private",
            title = "My Private Test",
            description = "Private questions",
            institute = "Self Study",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter",
            questionsCount = 2,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = authUid
        )

        // 2. Owned public test (not a private vault test)
        val myPublicTest = TestEntity(
            id = "test_my_public",
            title = "My Public Test",
            description = "Shared publicly",
            institute = "Public Coaching",
            batchName = "2026",
            facultyName = "Teacher",
            language = "English",
            subject = "Chemistry",
            difficulty = "Easy",
            testType = "Full",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = true,
            uploaderId = authUid
        )

        // 3. Other user's private test
        val otherPrivateTest = TestEntity(
            id = "test_other_user",
            title = "Other User Test",
            description = "Other private",
            institute = "Private Vault",
            batchName = "2026",
            facultyName = "Other",
            language = "English",
            subject = "Biology",
            difficulty = "Hard",
            testType = "Chapter",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = otherUid
        )

        db.testDao().insertTests(listOf(myPrivateTest, myPublicTest, otherPrivateTest))

        // Questions for each test
        val qMyPriv1 = QuestionEntity(
            id = "q_my_priv_1",
            testId = myPrivateTest.id,
            questionText = "My Private Q1",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "A", explanation = "Exp", subject = "Physics", chapter = "Kinematics"
        )
        val qMyPriv2 = QuestionEntity(
            id = "q_my_priv_2",
            testId = myPrivateTest.id,
            questionText = "My Private Q2",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "B", explanation = "Exp", subject = "Physics", chapter = "Dynamics"
        )
        val qMyPub = QuestionEntity(
            id = "q_my_pub_1",
            testId = myPublicTest.id,
            questionText = "Public Q",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "C", explanation = "Exp", subject = "Chemistry", chapter = "Atomic"
        )
        val qOther = QuestionEntity(
            id = "q_other_1",
            testId = otherPrivateTest.id,
            questionText = "Other Q",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "D", explanation = "Exp", subject = "Biology", chapter = "Cells"
        )

        db.questionDao().insertQuestions(listOf(qMyPriv1, qMyPriv2, qMyPub, qOther))

        val out = ByteArrayOutputStream()
        val exportResult = AccountDataBackupAdapter.exportRoomQuestionsAndDiagramsBackup(
            context = context,
            db = db,
            uid = authUid,
            outputStream = out
        )

        assertTrue("Export must succeed: ${exportResult.exceptionOrNull()?.message}", exportResult.isSuccess)
        val manifest = exportResult.getOrThrow()

        // Verify manifest completeness and statistics
        assertEquals("Completeness must be STRUCTURAL_ONLY", BackupCompleteness.STRUCTURAL_ONLY, manifest.completeness)
        assertEquals("privateTestsCount must be 1", 1, manifest.stats.privateTestsCount)
        assertEquals("questionsCount must be 2", 2, manifest.stats.questionsCount)
        assertEquals("diagramsCount must be 0", 0, manifest.stats.diagramsCount)
        assertEquals("attemptsCount must be 0", 0, manifest.stats.attemptsCount)

        // Read zip entries
        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries[entry.name] = zis.readBytes()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue("Must contain data/tests.json", zipEntries.containsKey("data/tests.json"))
        assertTrue("Must contain data/questions.json", zipEntries.containsKey("data/questions.json"))
        assertFalse("Must not contain attempts", zipEntries.containsKey("data/attempts.json"))
        assertFalse("Must not contain bookmarks", zipEntries.containsKey("data/bookmarks.json"))

        val questionsJson = JSONArray(String(zipEntries["data/questions.json"]!!, Charsets.UTF_8))
        assertEquals("Only 2 questions belonging to my private test should be exported", 2, questionsJson.length())

        val exportedQuestionIds = (0 until questionsJson.length()).map { questionsJson.getJSONObject(it).getString("id") }
        assertTrue("Must contain q_my_priv_1", exportedQuestionIds.contains("q_my_priv_1"))
        assertTrue("Must contain q_my_priv_2", exportedQuestionIds.contains("q_my_priv_2"))
        assertFalse("Must NOT contain public test question", exportedQuestionIds.contains("q_my_pub_1"))
        assertFalse("Must NOT contain other user's question", exportedQuestionIds.contains("q_other_1"))
    }

    @Test
    fun testExportRoomQuestionsAndDiagramsBackup_diagramInclusionAndUnreferencedExclusion() = runBlocking {
        val authUid = "user_diagram_test"

        val myTest = TestEntity(
            id = "test_diagram_1",
            title = "Test With Diagram",
            description = "Diagram test",
            institute = "Private Vault",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = authUid
        )
        db.testDao().insertTest(myTest)

        // Create diagram in filesDir/private_tests/{testId}/diagrams/
        val testDiagramsDir = File(context.filesDir, "private_tests/${myTest.id}/diagrams").apply { mkdirs() }
        val referencedDiagramFile = File(testDiagramsDir, "circuit_diagram.png")
        referencedDiagramFile.writeBytes("PNG_TEST_DIAGRAM_CONTENT".toByteArray(Charsets.UTF_8))

        // Create an unreferenced diagram in another test directory
        val otherTestDir = File(context.filesDir, "private_tests/unowned_test_999/diagrams").apply { mkdirs() }
        val unreferencedFile = File(otherTestDir, "unreferenced_diagram.png")
        unreferencedFile.writeBytes("UNREFERENCED_CONTENT".toByteArray(Charsets.UTF_8))

        val q = QuestionEntity(
            id = "q_diag_1",
            testId = myTest.id,
            questionText = "Analyze circuit",
            optionA = "1A", optionB = "2A", optionC = "3A", optionD = "4A",
            correctAnswer = "B", explanation = "Kirchhoff rule", subject = "Physics", chapter = "Circuits",
            needsImage = true,
            imageUrl = "private_tests/${myTest.id}/diagrams/circuit_diagram.png"
        )
        db.questionDao().insertQuestions(listOf(q))

        val out = ByteArrayOutputStream()
        val exportResult = AccountDataBackupAdapter.exportRoomQuestionsAndDiagramsBackup(
            context = context,
            db = db,
            uid = authUid,
            outputStream = out
        )

        assertTrue("Export must succeed: ${exportResult.exceptionOrNull()?.message}", exportResult.isSuccess)
        val manifest = exportResult.getOrThrow()
        assertEquals(1, manifest.stats.diagramsCount)

        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries[entry.name] = zis.readBytes()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val expectedDiagramEntryPath = "assets/diagrams/${myTest.id}/circuit_diagram.png"
        assertTrue("Archive must include referenced diagram", zipEntries.containsKey(expectedDiagramEntryPath))
        assertEquals("PNG_TEST_DIAGRAM_CONTENT", String(zipEntries[expectedDiagramEntryPath]!!, Charsets.UTF_8))

        assertFalse("Archive must NOT include unreferenced other diagram",
            zipEntries.keys.any { it.contains("unreferenced_diagram.png") })
    }

    @Test
    fun testExportRoomQuestionsAndDiagramsBackup_missingReferencedDiagram_strictModeFailsControlled() = runBlocking {
        val authUid = "user_missing_diag"

        val myTest = TestEntity(
            id = "test_missing_diag",
            title = "Missing Diagram Test",
            description = "Test missing diagram",
            institute = "Private Vault",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = authUid
        )
        db.testDao().insertTest(myTest)

        // Question references diagram file that does not exist
        val q = QuestionEntity(
            id = "q_missing_1",
            testId = myTest.id,
            questionText = "Missing diagram question",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "A", explanation = "Exp", subject = "Physics", chapter = "Optics",
            needsImage = true,
            imageUrl = "private_tests/${myTest.id}/diagrams/non_existent.png"
        )
        db.questionDao().insertQuestions(listOf(q))

        val out = ByteArrayOutputStream()
        val exportResultStrict = AccountDataBackupAdapter.exportRoomQuestionsAndDiagramsBackup(
            context = context,
            db = db,
            uid = authUid,
            outputStream = out,
            strictAssetIntegrity = true
        )

        assertTrue("Strict mode must fail on missing referenced diagram", exportResultStrict.isFailure)
        val ex = exportResultStrict.exceptionOrNull()
        assertTrue("Exception must be IllegalStateException", ex is IllegalStateException)
        assertTrue("Error message must report missing asset", ex!!.message!!.contains("non_existent.png"))
    }

    @Test
    fun testExportRoomQuestionsAndDiagramsBackup_pathTraversalAttempt_rejectedWithSecurityException() = runBlocking {
        val authUid = "user_traversal"

        val myTest = TestEntity(
            id = "test_traversal",
            title = "Traversal Test",
            description = "Traversal test",
            institute = "Private Vault",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = authUid
        )
        db.testDao().insertTest(myTest)

        // Malicious question with path traversal in imageUrl
        val qTraversal = QuestionEntity(
            id = "q_traversal_1",
            testId = myTest.id,
            questionText = "Malicious question",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "A", explanation = "Exp", subject = "Physics", chapter = "Optics",
            needsImage = true,
            imageUrl = "../../system/etc/hosts"
        )
        db.questionDao().insertQuestions(listOf(qTraversal))

        val out = ByteArrayOutputStream()
        val exportResult = AccountDataBackupAdapter.exportRoomQuestionsAndDiagramsBackup(
            context = context,
            db = db,
            uid = authUid,
            outputStream = out
        )

        assertTrue("Path traversal attempt must fail", exportResult.isFailure)
        val ex = exportResult.exceptionOrNull()
        assertTrue("Exception must be SecurityException", ex is SecurityException)
        assertTrue("Error message must indicate boundary escape", ex!!.message!!.contains("escapes filesDir"))
    }

    @Test
    fun testExportRoomQuestionsAndDiagramsBackup_oversizedMedia_rejected() = runBlocking {
        val authUid = "user_oversized"

        val myTest = TestEntity(
            id = "test_oversized",
            title = "Oversized Media Test",
            description = "Oversized test",
            institute = "Private Vault",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = authUid
        )
        db.testDao().insertTest(myTest)

        val testDiagramsDir = File(context.filesDir, "private_tests/${myTest.id}/diagrams").apply { mkdirs() }
        val diagramFile = File(testDiagramsDir, "huge_diagram.png")
        // Write 1000 bytes
        diagramFile.writeBytes(ByteArray(1000))

        val q = QuestionEntity(
            id = "q_huge",
            testId = myTest.id,
            questionText = "Huge diagram question",
            optionA = "A", optionB = "B", optionC = "C", optionD = "D",
            correctAnswer = "A", explanation = "Exp", subject = "Physics", chapter = "Optics"
        )
        db.questionDao().insertQuestions(listOf(q))

        // Set limits with maxSingleEntryBytes = 500 (less than 1000 bytes)
        val limits = ArchiveLimits(maxSingleEntryBytes = 500)
        val out = ByteArrayOutputStream()
        val exportResult = AccountDataBackupAdapter.exportRoomQuestionsAndDiagramsBackup(
            context = context,
            db = db,
            uid = authUid,
            outputStream = out,
            limits = limits
        )

        assertTrue("Oversized media must fail", exportResult.isFailure)
        val ex = exportResult.exceptionOrNull()
        assertTrue("Exception must be IllegalStateException", ex is IllegalStateException)
        assertTrue("Error must mention exceeding single-entry limit", ex!!.message!!.contains("exceeds max single-entry limit"))
    }
}
