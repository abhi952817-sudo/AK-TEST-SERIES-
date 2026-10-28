package Com.ak.testseries.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import Com.ak.testseries.data.AppDatabase
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
import java.util.zip.ZipInputStream

/**
 * Focused unit test suite for Phase 4B-1: Room Test Data Backup Adapter Only.
 *
 * Verifies:
 * - 100% field preservation across all 40 fields of TestEntity in JSON serialization.
 * - Strict ownership filtering (only authenticated user's private tests).
 * - Rejection of unauthenticated or blank UIDs.
 * - Handling of empty datasets (producing valid STRUCTURAL_ONLY archives).
 * - Safe rejection of malformed or corrupt JSON test records.
 * - Accurate archive completeness reporting (STRUCTURAL_ONLY, never COMPLETE_USER_DATA).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class RoomTestDataBackupAdapterTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testTestEntitySerialization_all40FieldsPreserved() {
        val original = TestEntity(
            id = "test_full_40_fields",
            title = "NEET Comprehensive Full Mock 2026",
            description = "Detailed physics, chemistry, biology benchmark",
            institute = "Allen Kota Private Batch",
            batchName = "Target NEET 2026",
            facultyName = "Dr. H. C. Verma",
            language = "Bilingual",
            subject = "Full Syllabus",
            difficulty = "Hard",
            testType = "Full Syllabus",
            questionsCount = 180,
            isOfficial = true,
            isVerifiedCommunity = true,
            isPublic = false,
            countsTowardRanking = true,
            rating = 4.85f,
            rating1Count = 2,
            rating2Count = 5,
            rating3Count = 10,
            rating4Count = 25,
            rating5Count = 150,
            attemptsCount = 192,
            downloadsCount = 45,
            isDownloaded = true,
            isBookmarked = true,
            timestamp = 1714567890123L,
            status = "Published",
            aiConfidence = 99,
            uploaderId = "auth_user_999",
            targetExam = "NEET-UG",
            durationMinutes = 200,
            classLevel = "12th",
            chapter = "Modern Physics & Genetics",
            topic = "Photoelectric effect & DNA Replication",
            imageUrl = "private_tests/test_full_40_fields/banner.png",
            questionsJson = "[{\"id\":\"q1\"}]",
            examId = "neet_ug_2026",
            stageId = "main_stage",
            paperId = "paper_morning_1",
            scoringVersion = 3
        )

        val json = AccountDataBackupAdapter.testEntityToJson(original)
        val reconstructed = AccountDataBackupAdapter.jsonToTestEntity(json)

        assertEquals("id must match", original.id, reconstructed.id)
        assertEquals("title must match", original.title, reconstructed.title)
        assertEquals("description must match", original.description, reconstructed.description)
        assertEquals("institute must match", original.institute, reconstructed.institute)
        assertEquals("batchName must match", original.batchName, reconstructed.batchName)
        assertEquals("facultyName must match", original.facultyName, reconstructed.facultyName)
        assertEquals("language must match", original.language, reconstructed.language)
        assertEquals("subject must match", original.subject, reconstructed.subject)
        assertEquals("difficulty must match", original.difficulty, reconstructed.difficulty)
        assertEquals("testType must match", original.testType, reconstructed.testType)
        assertEquals("questionsCount must match", original.questionsCount, reconstructed.questionsCount)
        assertEquals("isOfficial must match", original.isOfficial, reconstructed.isOfficial)
        assertEquals("isVerifiedCommunity must match", original.isVerifiedCommunity, reconstructed.isVerifiedCommunity)
        assertEquals("isPublic must match", original.isPublic, reconstructed.isPublic)
        assertEquals("countsTowardRanking must match", original.countsTowardRanking, reconstructed.countsTowardRanking)
        assertEquals("rating must match within delta", original.rating, reconstructed.rating, 0.001f)
        assertEquals("rating1Count must match", original.rating1Count, reconstructed.rating1Count)
        assertEquals("rating2Count must match", original.rating2Count, reconstructed.rating2Count)
        assertEquals("rating3Count must match", original.rating3Count, reconstructed.rating3Count)
        assertEquals("rating4Count must match", original.rating4Count, reconstructed.rating4Count)
        assertEquals("rating5Count must match", original.rating5Count, reconstructed.rating5Count)
        assertEquals("attemptsCount must match", original.attemptsCount, reconstructed.attemptsCount)
        assertEquals("downloadsCount must match", original.downloadsCount, reconstructed.downloadsCount)
        assertEquals("isDownloaded must match", original.isDownloaded, reconstructed.isDownloaded)
        assertEquals("isBookmarked must match", original.isBookmarked, reconstructed.isBookmarked)
        assertEquals("timestamp must match", original.timestamp, reconstructed.timestamp)
        assertEquals("status must match", original.status, reconstructed.status)
        assertEquals("aiConfidence must match", original.aiConfidence, reconstructed.aiConfidence)
        assertEquals("uploaderId must match", original.uploaderId, reconstructed.uploaderId)
        assertEquals("targetExam must match", original.targetExam, reconstructed.targetExam)
        assertEquals("durationMinutes must match", original.durationMinutes, reconstructed.durationMinutes)
        assertEquals("classLevel must match", original.classLevel, reconstructed.classLevel)
        assertEquals("chapter must match", original.chapter, reconstructed.chapter)
        assertEquals("topic must match", original.topic, reconstructed.topic)
        assertEquals("imageUrl must match", original.imageUrl, reconstructed.imageUrl)
        assertEquals("questionsJson must match", original.questionsJson, reconstructed.questionsJson)
        assertEquals("examId must match", original.examId, reconstructed.examId)
        assertEquals("stageId must match", original.stageId, reconstructed.stageId)
        assertEquals("paperId must match", original.paperId, reconstructed.paperId)
        assertEquals("scoringVersion must match", original.scoringVersion, reconstructed.scoringVersion)
    }

    @Test
    fun testTestEntitySerialization_nullOptionalFieldsHandledSafely() {
        val originalWithNulls = TestEntity(
            id = "test_nulls_only",
            title = "Minimal Test",
            description = "Minimal description",
            institute = "Self Study",
            batchName = "Default",
            facultyName = "None",
            language = "English",
            subject = "Physics",
            difficulty = "Easy",
            testType = "Chapter Test",
            questionsCount = 10,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            imageUrl = null,
            questionsJson = null,
            examId = null,
            stageId = null,
            paperId = null,
            scoringVersion = null
        )

        val json = AccountDataBackupAdapter.testEntityToJson(originalWithNulls)
        val reconstructed = AccountDataBackupAdapter.jsonToTestEntity(json)

        assertNull("imageUrl should be null", reconstructed.imageUrl)
        assertNull("questionsJson should be null", reconstructed.questionsJson)
        assertNull("examId should be null", reconstructed.examId)
        assertNull("stageId should be null", reconstructed.stageId)
        assertNull("paperId should be null", reconstructed.paperId)
        assertNull("scoringVersion should be null", reconstructed.scoringVersion)
        assertEquals("id should match", originalWithNulls.id, reconstructed.id)
    }

    @Test
    fun testOwnershipFiltering_exportsOnlyEligiblePrivateTestsForAuthenticatedUser() {
        val authUid = "user_auth_789"
        val otherUid = "user_attacker_001"

        val allTests = listOf(
            TestEntity(
                id = "test_auth_private",
                title = "Auth User Private Test",
                description = "Strictly private",
                institute = "Personal Vault",
                batchName = "Batch A",
                facultyName = "Faculty A",
                language = "English",
                subject = "Biology",
                difficulty = "Medium",
                testType = "Chapter Test",
                questionsCount = 50,
                isOfficial = false,
                isVerifiedCommunity = false,
                isPublic = false,
                uploaderId = authUid
            ),
            TestEntity(
                id = "test_auth_vault_named",
                title = "Auth User Vault Named",
                description = "Private vault test with institute keyword",
                institute = "Private Vault Academy",
                batchName = "Batch A",
                facultyName = "Faculty A",
                language = "English",
                subject = "Chemistry",
                difficulty = "Hard",
                testType = "PYQ",
                questionsCount = 45,
                isOfficial = false,
                isVerifiedCommunity = false,
                isPublic = true, // Public flag set, but institute explicitly matches Private
                uploaderId = authUid
            ),
            TestEntity(
                id = "test_auth_public",
                title = "Auth User Public Test",
                description = "Public community mock",
                institute = "Public Open Coaching",
                batchName = "Batch A",
                facultyName = "Faculty A",
                language = "English",
                subject = "Physics",
                difficulty = "Easy",
                testType = "Full Syllabus",
                questionsCount = 180,
                isOfficial = false,
                isVerifiedCommunity = false,
                isPublic = true,
                uploaderId = authUid
            ),
            TestEntity(
                id = "test_other_private",
                title = "Other User Private Test",
                description = "Should never be visible to authUid",
                institute = "Secret Vault",
                batchName = "Batch X",
                facultyName = "Faculty X",
                language = "Hindi",
                subject = "Physics",
                difficulty = "Hard",
                testType = "Part Test",
                questionsCount = 30,
                isOfficial = false,
                isVerifiedCommunity = false,
                isPublic = false,
                uploaderId = otherUid
            ),
            TestEntity(
                id = "test_anonymous",
                title = "Anonymous Test",
                description = "Unowned test",
                institute = "Private Lab",
                batchName = "None",
                facultyName = "None",
                language = "English",
                subject = "General",
                difficulty = "Easy",
                testType = "Chapter Test",
                questionsCount = 10,
                isOfficial = false,
                isVerifiedCommunity = false,
                isPublic = false,
                uploaderId = "anonymous"
            )
        )

        val filtered = AccountDataBackupAdapter.filterEligiblePrivateTests(allTests, authUid)

        assertEquals("Must return exactly 2 eligible private tests for authUid", 2, filtered.size)
        assertTrue("Must contain test_auth_private", filtered.any { it.id == "test_auth_private" })
        assertTrue("Must contain test_auth_vault_named", filtered.any { it.id == "test_auth_vault_named" })
        assertFalse("Must NOT contain auth user public test", filtered.any { it.id == "test_auth_public" })
        assertFalse("Must NOT contain other user private test", filtered.any { it.id == "test_other_private" })
        assertFalse("Must NOT contain anonymous test", filtered.any { it.id == "test_anonymous" })
    }

    @Test
    fun testOwnershipFiltering_rejectsUnauthenticatedOrBlankUid() {
        val allTests = listOf(
            TestEntity(
                id = "test_priv",
                title = "Private Test",
                description = "Desc",
                institute = "Private Vault",
                batchName = "B",
                facultyName = "F",
                language = "English",
                subject = "Physics",
                difficulty = "Easy",
                testType = "Chapter Test",
                questionsCount = 10,
                isOfficial = false,
                isVerifiedCommunity = false,
                isPublic = false,
                uploaderId = "user_123"
            )
        )

        assertTrue("Empty UID must return empty list", AccountDataBackupAdapter.filterEligiblePrivateTests(allTests, "").isEmpty())
        assertTrue("Whitespace UID must return empty list", AccountDataBackupAdapter.filterEligiblePrivateTests(allTests, "   ").isEmpty())
    }

    @Test
    fun testRoomTestsBackup_emptyData_createsValidStructuralArchive(): Unit = runBlocking {
        // Authenticated user has 0 tests in Room DB
        val out = ByteArrayOutputStream()
        val result = AccountDataBackupAdapter.exportRoomTestsBackup(
            db = db,
            uid = "user_with_zero_tests",
            email = "zero@example.com",
            displayName = "Zero Tests User",
            outputStream = out
        )

        assertTrue("Export must succeed even with zero tests", result.isSuccess)
        val manifest = result.getOrThrow()

        assertEquals("Completeness must be STRUCTURAL_ONLY", BackupCompleteness.STRUCTURAL_ONLY, manifest.completeness)
        assertEquals("Owner UID must match", "user_with_zero_tests", manifest.owner.uid)
        assertEquals("Private tests count must be 0", 0, manifest.stats.privateTestsCount)
        assertTrue("Manifest must declare data/tests.json checksum", manifest.checksums.containsKey("data/tests.json"))

        // Validate ZIP integrity with BackupArchiveEngine
        val archiveBytes = out.toByteArray()
        val validationResult = BackupArchiveEngine.validateArchive(ByteArrayInputStream(archiveBytes))
        assertTrue("Archive must pass engine validation", validationResult is BackupValidationResult.Valid)

        // Read ZIP entry content directly
        val zipIn = ZipInputStream(ByteArrayInputStream(archiveBytes))
        var foundTestsJson = false
        var zipEntry = zipIn.nextEntry
        while (zipEntry != null) {
            if (zipEntry.name == "data/tests.json") {
                foundTestsJson = true
                val content = String(zipIn.readBytes(), Charsets.UTF_8)
                val jsonArray = JSONArray(content)
                assertEquals("tests.json must be an empty JSON array", 0, jsonArray.length())
            }
            zipEntry = zipIn.nextEntry
        }
        assertTrue("data/tests.json must be present in archive", foundTestsJson)
    }

    @Test
    fun testRoomTestsBackup_rejectsLoggedOutOrBlankUid(): Unit = runBlocking {
        val out = ByteArrayOutputStream()
        val blankResult = AccountDataBackupAdapter.exportRoomTestsBackup(
            db = db,
            uid = "",
            outputStream = out
        )
        assertTrue("Blank UID must fail export", blankResult.isFailure)
        assertTrue("Exception must be IllegalArgumentException", blankResult.exceptionOrNull() is IllegalArgumentException)

        val whitespaceResult = AccountDataBackupAdapter.exportRoomTestsBackup(
            db = db,
            uid = "   ",
            outputStream = out
        )
        assertTrue("Whitespace UID must fail export", whitespaceResult.isFailure)
        assertTrue("Exception must be IllegalArgumentException", whitespaceResult.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testMalformedJsonHandling_rejectsNonObjectOrMissingId() {
        // 1. Non-object elements in array
        val primitiveArray = JSONArray().apply {
            put("just a string")
            put(12345)
        }
        val primitiveResult = AccountDataBackupAdapter.deserializeTestsFromJsonArray(primitiveArray)
        assertTrue("Must fail on primitive items", primitiveResult.isFailure)
        assertTrue(primitiveResult.exceptionOrNull()?.message?.contains("expected JSON object") == true)

        // 2. Object with missing id
        val missingIdArray = JSONArray().apply {
            put(JSONObject().apply {
                put("title", "Test without ID")
                put("subject", "Physics")
            })
        }
        val missingIdResult = AccountDataBackupAdapter.deserializeTestsFromJsonArray(missingIdArray)
        assertTrue("Must fail on missing or blank id", missingIdResult.isFailure)
        assertTrue(missingIdResult.exceptionOrNull()?.message?.contains("missing or blank 'id'") == true)

        // 3. Object with blank id
        val blankIdArray = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "   ")
                put("title", "Test with blank ID")
            })
        }
        val blankIdResult = AccountDataBackupAdapter.deserializeTestsFromJsonArray(blankIdArray)
        assertTrue("Must fail on whitespace id", blankIdResult.isFailure)
        assertTrue(blankIdResult.exceptionOrNull()?.message?.contains("missing or blank 'id'") == true)

        // 4. Valid array
        val validArray = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "test_valid_1")
                put("title", "Valid Test 1")
                put("uploaderId", "user_1")
            })
        }
        val validResult = AccountDataBackupAdapter.deserializeTestsFromJsonArray(validArray)
        assertTrue("Must succeed on valid test JSON object", validResult.isSuccess)
        assertEquals(1, validResult.getOrThrow().size)
        assertEquals("test_valid_1", validResult.getOrThrow()[0].id)
    }

    @Test
    fun testArchiveCompleteness_isAlwaysReportedAsStructuralOnly(): Unit = runBlocking {
        val testUid = "user_structural_test"
        val test1 = TestEntity(
            id = "test_s1",
            title = "Structural Test 1",
            description = "Desc 1",
            institute = "Private Vault",
            batchName = "Batch S",
            facultyName = "Faculty S",
            language = "English",
            subject = "Physics",
            difficulty = "Easy",
            testType = "Chapter Test",
            questionsCount = 10,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = testUid
        )
        db.testDao().insertTests(listOf(test1))

        val out = ByteArrayOutputStream()
        val result = AccountDataBackupAdapter.exportRoomTestsBackup(
            db = db,
            uid = testUid,
            outputStream = out
        )

        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()

        // Strict completeness assertion: Room Test data alone MUST NEVER be labeled COMPLETE_USER_DATA
        assertEquals(
            "Room test data only must be labeled STRUCTURAL_ONLY",
            BackupCompleteness.STRUCTURAL_ONLY,
            manifest.completeness
        )
        assertNotEquals(
            "Room test data alone must NOT claim COMPLETE_USER_DATA",
            BackupCompleteness.COMPLETE_USER_DATA,
            manifest.completeness
        )
        assertEquals(1, manifest.stats.privateTestsCount)
    }
}
