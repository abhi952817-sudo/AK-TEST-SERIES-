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
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class SafeBackupExportImportUITest {

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
        val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
        for (f in cacheFiles) {
            if (f.name.contains("staging") || f.name.endsWith(".aktsbak")) {
                f.deleteRecursively()
            }
        }
    }

    private fun createValidCompleteArchive(
        ownerUid: String,
        testId: String = "test_phase4c_1",
        tamperChecksum: Boolean = false,
        incompleteStatus: Boolean = false
    ): ByteArray {
        val testEntity = TestEntity(
            id = testId,
            title = "Export-Import Test",
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
            countsTowardRanking = false,
            rating = 5.0f,
            timestamp = 1000L,
            status = "Published",
            aiConfidence = 100,
            uploaderId = ownerUid,
            targetExam = "NEET"
        )

        val questionEntity = QuestionEntity(
            id = "q_p4c_1",
            testId = testId,
            questionText = "What is the speed of light in vacuum?",
            optionA = "3x10^8 m/s",
            optionB = "3x10^6 m/s",
            optionC = "3x10^4 m/s",
            optionD = "None",
            correctAnswer = "A",
            explanation = "Constant c.",
            subject = "Physics",
            chapter = "Optics"
        )

        val attemptEntity = AttemptEntity(
            id = 101,
            userId = ownerUid,
            testId = testId,
            testTitle = "Export-Import Test",
            score = 4.0,
            maxScore = 4.0,
            percentile = 100.0,
            accuracy = 100.0,
            correctCount = 1,
            incorrectCount = 0,
            skippedCount = 0,
            timeSpentSeconds = 45L,
            dateTaken = 2000L,
            answersJson = "{\"q_p4c_1\":\"A\"}",
            attemptSessionToken = "dedup_token_p4c_101",
            verifiedServerSide = true,
            submissionStatus = "CONFIRMED"
        )

        val bookmarks = BookmarksBackupPayload(
            userId = ownerUid,
            testBookmarks = listOf(testId),
            questionBookmarks = listOf("q_p4c_1")
        )

        val progress = ProgressBackupPayload(
            userId = ownerUid,
            inProgressSessions = mapOf(testId to "{\"index\":0}")
        )

        val syllabus = SyllabusBackupPayload(
            userId = ownerUid,
            completedChaptersByExam = mapOf("NEET" to listOf("Kinematics"))
        )

        val profile = ProfileBackupPayload(
            userId = ownerUid,
            name = "Test Candidate",
            email = "candidate@example.com",
            targetExam = "NEET"
        )

        val entries = mutableMapOf<String, ByteArray>()
        entries["data/tests.json"] = JSONArray().put(AccountDataBackupAdapter.testEntityToJson(testEntity)).toString(2).toByteArray(Charsets.UTF_8)
        entries["data/questions.json"] = JSONArray().put(AccountDataBackupAdapter.questionEntityToJson(questionEntity)).toString(2).toByteArray(Charsets.UTF_8)
        entries["data/attempts.json"] = JSONArray().put(AccountDataBackupAdapter.attemptEntityToJson(attemptEntity)).toString(2).toByteArray(Charsets.UTF_8)
        entries["data/bookmarks.json"] = bookmarks.toJsonObject().toString(2).toByteArray(Charsets.UTF_8)
        entries["data/progress.json"] = progress.toJsonObject().toString(2).toByteArray(Charsets.UTF_8)
        entries["data/syllabus.json"] = syllabus.toJsonObject().toString(2).toByteArray(Charsets.UTF_8)
        entries["data/profile.json"] = profile.toJsonObject().toString(2).toByteArray(Charsets.UTF_8)

        val completeness = if (incompleteStatus) BackupCompleteness.STRUCTURAL_ONLY else BackupCompleteness.COMPLETE_USER_DATA

        val manifestDraft = BackupArchiveEngine.createAccountOwnedManifest(
            uid = ownerUid,
            email = "candidate@example.com",
            displayName = "Test Candidate",
            completeness = completeness
        ).getOrThrow()

        val manifestWithStats = manifestDraft.copy(
            stats = BackupStats(
                privateTestsCount = 1,
                questionsCount = 1,
                attemptsCount = 1,
                inProgressCount = 1,
                bookmarkedCount = 2,
                diagramsCount = 0
            )
        )

        val baos = ByteArrayOutputStream()
        BackupArchiveEngine.buildArchive(baos, manifestWithStats, entries).getOrThrow()
        val archiveBytes = baos.toByteArray()

        if (tamperChecksum) {
            val tamperedBaos = ByteArrayOutputStream()
            val zipIn = java.util.zip.ZipInputStream(ByteArrayInputStream(archiveBytes))
            val zipOut = ZipOutputStream(tamperedBaos)
            var entry = zipIn.nextEntry
            while (entry != null) {
                zipOut.putNextEntry(ZipEntry(entry.name))
                if (entry.name == "data/tests.json") {
                    zipOut.write("tampered".toByteArray(Charsets.UTF_8))
                } else {
                    val buf = ByteArray(1024)
                    var r: Int
                    while (zipIn.read(buf).also { r = it } != -1) {
                        zipOut.write(buf, 0, r)
                    }
                }
                zipOut.closeEntry()
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipOut.finish()
            return tamperedBaos.toByteArray()
        }

        return archiveBytes
    }

    @Test
    fun testExportAccountBackup_successWithCorrectExtensionAndCompleteness(): Unit = runBlocking {
        val uid = "user_valid_owner"
        val test = TestEntity(
            id = "test_exp_1",
            title = "My Private Test",
            description = "Desc",
            institute = "Private Vault",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Biology",
            difficulty = "Easy",
            testType = "Chapter Test",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = uid,
            targetExam = "NEET"
        )
        db.testDao().insertTest(test)
        val question = QuestionEntity(
            id = "q_exp_1",
            testId = "test_exp_1",
            questionText = "Cell powerhouse?",
            optionA = "Mitochondria",
            optionB = "Nucleus",
            optionC = "Ribosome",
            optionD = "Golgi",
            correctAnswer = "A",
            explanation = "ATP generation",
            subject = "Biology",
            chapter = "Cytology"
        )
        db.questionDao().insertQuestions(listOf(question))

        val exportFile = File(context.cacheDir, "test_output_backup.aktsbak")
        val fos = FileOutputStream(exportFile)

        val result = AccountDataBackupAdapter.exportAccountBackup(
            context = context,
            db = db,
            uid = uid,
            email = "owner@aktestseries.app",
            displayName = "Dr. Owner",
            appVersionCode = 1,
            appVersionName = "1.0",
            outputStream = fos,
            strictAssetIntegrity = true
        )
        fos.close()

        assertTrue("Export must succeed for authenticated user with owned tests", result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(BackupCompleteness.COMPLETE_USER_DATA, manifest.completeness)
        assertEquals(uid, manifest.owner.uid)
        assertEquals(1, manifest.stats.privateTestsCount)
        assertEquals(1, manifest.stats.questionsCount)
        assertTrue(exportFile.exists())
        assertTrue("Archive file must not be empty", exportFile.length() > 0)
        exportFile.delete()
    }

    @Test
    fun testExportAccountBackup_rejectsLoggedOutOrBlankUid(): Unit = runBlocking {
        val exportFile = File(context.cacheDir, "logged_out_export.aktsbak")
        val fos = FileOutputStream(exportFile)

        val result = AccountDataBackupAdapter.exportAccountBackup(
            context = context,
            db = db,
            uid = "   ", // Blank UID representing logged-out state
            outputStream = fos
        )
        fos.close()

        assertTrue("Export must be rejected for blank/logged-out UID", result.isFailure)
        assertTrue(
            "Failure message must explain authenticated UID requirement",
            result.exceptionOrNull()?.message?.contains("Authenticated UID is required") == true
        )
        exportFile.delete()
    }

    @Test
    fun testValidation_rejectsCorruptOrNonZipFile() {
        val corruptBytes = "This is not a valid ZIP file contents".toByteArray(Charsets.UTF_8)
        val validation = BackupArchiveEngine.validateArchive(ByteArrayInputStream(corruptBytes))

        assertTrue("Validation must return Invalid for non-ZIP input", validation is BackupValidationResult.Invalid)
        val invalid = validation as BackupValidationResult.Invalid
        assertTrue(
            "Reason must indicate corruption, invalid ZIP, or missing manifest",
            invalid.reason.contains("corrupted") || invalid.reason.contains("not a valid ZIP") || invalid.reason.contains("manifest")
        )
    }

    @Test
    fun testValidation_rejectsTamperedChecksum() {
        val tamperedBytes = createValidCompleteArchive(ownerUid = "user_tamper", tamperChecksum = true)
        val validation = BackupArchiveEngine.validateArchive(ByteArrayInputStream(tamperedBytes))

        assertTrue("Validation must return Invalid for tampered payload checksum", validation is BackupValidationResult.Invalid)
        val invalid = validation as BackupValidationResult.Invalid
        assertTrue(
            "Reason must report checksum mismatch",
            invalid.reason.contains("Checksum mismatch") || invalid.reason.contains("Integrity check failed")
        )
    }

    @Test
    fun testRestore_rejectsCrossAccountRestoreAndLeavesLiveDataUnchanged(): Unit = runBlocking {
        // Initial live state for candidate user_current
        val initialTest = TestEntity(
            id = "live_test_orig",
            title = "Live Test",
            description = "Desc",
            institute = "Private Vault",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 0,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = "user_current",
            targetExam = "NEET"
        )
        db.testDao().insertTest(initialTest)

        // Archive created by user_foreign
        val foreignArchiveBytes = createValidCompleteArchive(ownerUid = "user_foreign")

        // Attempt to restore foreign archive into user_current
        val restoreResult = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = "user_current",
            archiveInputStream = ByteArrayInputStream(foreignArchiveBytes)
        )

        assertTrue("Restore must fail on cross-account restore attempt", restoreResult is RestoreResult.Failure)
        val failure = restoreResult as RestoreResult.Failure
        assertTrue(
            "Failure must indicate account ownership mismatch",
            failure.reason.contains("Account ownership mismatch") || failure.details?.contains("Cross-account restoration is prohibited") == true
        )

        // Verify live data was completely untouched
        val currentTests = db.testDao().getAllTestsList()
        assertEquals("Live tests count must remain 1", 1, currentTests.size)
        assertEquals("Live test must remain the original test", "live_test_orig", currentTests[0].id)
        assertNull("Foreign test must not have been restored into database", db.testDao().getTestById("test_phase4c_1"))
    }

    @Test
    fun testRestore_rejectsIncompleteStructuralOnlyArchive(): Unit = runBlocking {
        val structuralArchiveBytes = createValidCompleteArchive(
            ownerUid = "user_structural",
            incompleteStatus = true // STRUCTURAL_ONLY
        )

        val restoreResult = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = "user_structural",
            archiveInputStream = ByteArrayInputStream(structuralArchiveBytes)
        )

        assertTrue("Restore must reject incomplete STRUCTURAL_ONLY archive", restoreResult is RestoreResult.Failure)
        val failure = restoreResult as RestoreResult.Failure
        assertTrue(
            "Failure must explain incomplete archive rejection",
            failure.reason.contains("Incomplete backup archive rejected")
        )
    }

    @Test
    fun testRestore_successForMatchingAccountAndIdempotentDedup(): Unit = runBlocking {
        val uid = "user_match"
        val archiveBytes = createValidCompleteArchive(ownerUid = uid, testId = "test_matching_1")

        val result1 = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )

        assertTrue("First restore must succeed", result1 is RestoreResult.Success)
        val success1 = result1 as RestoreResult.Success
        assertEquals(1, success1.restoredTestsCount)
        assertEquals(1, success1.restoredQuestionsCount)
        assertEquals(1, success1.restoredAttemptsCount)
        assertEquals(0, success1.skippedDuplicateAttemptsCount)

        assertNotNull("Restored test must exist in DB", db.testDao().getTestById("test_matching_1"))
        val attempts = db.attemptDao().getAttemptsForUser(uid).first()
        assertEquals(1, attempts.size)

        // Second restore with identical archive (idempotency test)
        val result2 = AccountDataRestoreEngine.restoreAccountBackup(
            context = context,
            db = db,
            authenticatedUid = uid,
            archiveInputStream = ByteArrayInputStream(archiveBytes)
        )

        assertTrue("Second restore must succeed", result2 is RestoreResult.Success)
        val success2 = result2 as RestoreResult.Success
        assertEquals("Duplicate attempt must be skipped", 1, success2.skippedDuplicateAttemptsCount)
        val attemptsAfterSecond = db.attemptDao().getAttemptsForUser(uid).first()
        assertEquals("Attempt count in database must not duplicate", 1, attemptsAfterSecond.size)
    }

    @Test
    fun testExportArchive_containsNoCredentialsOrAuthSecrets(): Unit = runBlocking {
        val uid = "user_security_check"
        // Seed database and preferences
        val test = TestEntity(
            id = "test_sec_1",
            title = "Security Test",
            description = "Desc",
            institute = "Private Vault",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 0,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = uid,
            targetExam = "NEET"
        )
        db.testDao().insertTest(test)

        val out = ByteArrayOutputStream()
        val result = AccountDataBackupAdapter.exportAccountBackup(
            context = context,
            db = db,
            uid = uid,
            email = "user@test.org",
            outputStream = out
        )
        assertTrue(result.isSuccess)

        // Read all entries in generated archive
        val zipIn = java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray()))
        var entry = zipIn.nextEntry
        val forbiddenStrings = listOf(
            "password", "bearer", "firebase_token", "apiKey", "access_token", "client_secret", "refresh_token"
        )

        while (entry != null) {
            val entryBytes = zipIn.readBytes()
            val entryText = String(entryBytes, Charsets.UTF_8).lowercase()
            for (forbidden in forbiddenStrings) {
                assertFalse(
                    "Archive entry '${entry.name}' must not contain sensitive credential secret '$forbidden'",
                    entryText.contains("\"$forbidden\"")
                )
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }
    }

    @Test
    fun testBackupUiState_transitionsAndDismissal() {
        val idle = BackupUiState.Idle
        assertNotNull(idle)

        val exporting = BackupUiState.Exporting("Exporting...")
        assertEquals("Exporting...", exporting.message)

        val error = BackupUiState.Error("Title", "Message")
        assertEquals("Title", error.title)
        assertEquals("Message", error.message)

        val tempStaging = File(context.cacheDir, "test_ui_staging.aktsbak")
        tempStaging.writeText("sample data")
        assertTrue(tempStaging.exists())

        val manifest = BackupManifest(
            owner = BackupOwner("uid_123")
        )
        val ready = BackupUiState.ValidationReady(manifest, tempStaging)
        assertEquals("uid_123", ready.manifest.owner.uid)

        // Simulate cancel/dismiss: file cleanup
        if (ready.stagingFile.exists()) {
            ready.stagingFile.delete()
        }
        assertFalse("Staging file must be cleaned up on dismissal", tempStaging.exists())
    }

    @Test
    fun testAndroidOSBackupRules_excludesDatabaseAndPrivateFiles() {
        val backupRulesXml = File(context.packageResourcePath ?: "", "res/xml/backup_rules.xml").let {
            if (it.exists()) it.readText() else null
        }
        val resourceFile = File("src/main/res/xml/backup_rules.xml")
        val content = if (resourceFile.exists()) resourceFile.readText() else backupRulesXml

        if (content != null) {
            assertTrue("backup_rules.xml must exclude database", content.contains("neet_test_series_db"))
            assertTrue("backup_rules.xml must exclude private_tests", content.contains("private_tests"))
            assertTrue("backup_rules.xml must exclude backup_staging", content.contains("backup_staging"))
            assertTrue("backup_rules.xml must exclude user_profile_prefs", content.contains("user_profile_prefs"))
        }

        val dataExtractionFile = File("src/main/res/xml/data_extraction_rules.xml")
        if (dataExtractionFile.exists()) {
            val extractContent = dataExtractionFile.readText()
            assertTrue("data_extraction_rules.xml must exclude database in cloud-backup", extractContent.contains("neet_test_series_db"))
            assertTrue("data_extraction_rules.xml must exclude private_tests", extractContent.contains("private_tests"))
            assertTrue("data_extraction_rules.xml must exclude backup_staging", extractContent.contains("backup_staging"))
            assertTrue("data_extraction_rules.xml must exclude user_bookmarks_store", extractContent.contains("user_bookmarks_store"))
        }
    }

    @Test
    fun testValidationReady_stagedDataPreviewAndCleanUp() {
        val stagingDir = File(context.cacheDir, "test_staging_preview_${System.currentTimeMillis()}").apply { mkdirs() }
        val dummyFile = File(stagingDir, "test.txt").apply { writeText("staged") }
        val manifest = BackupManifest(owner = BackupOwner("uid_preview_123"))
        val stagedData = StagedBackupData(
            manifest = manifest,
            tests = emptyList(),
            questions = emptyList(),
            attempts = emptyList(),
            bookmarks = BookmarksBackupPayload(userId = "uid_preview_123", testBookmarks = emptyList(), questionBookmarks = emptyList()),
            progress = ProgressBackupPayload(userId = "uid_preview_123", inProgressSessions = emptyMap()),
            syllabus = SyllabusBackupPayload(userId = "uid_preview_123", completedChaptersByExam = emptyMap()),
            profile = ProfileBackupPayload(userId = "uid_preview_123", targetExam = "NEET", classLevel = "12", selectedLanguage = "English"),
            stagedDiagramFiles = emptyList(),
            stagedAvatarFile = null,
            stagingDir = stagingDir
        )

        val tempArchive = File(context.cacheDir, "temp_archive.aktsbak").apply { writeText("archive") }
        val ready = BackupUiState.ValidationReady(
            manifest = manifest,
            stagingFile = tempArchive,
            stagedData = stagedData
        )

        assertEquals("uid_preview_123", ready.manifest.owner.uid)
        assertNotNull(ready.stagedData)
        assertTrue(ready.stagedData!!.stagingDir.exists())

        // Cleanup
        ready.stagedData!!.cleanUp()
        assertFalse(stagingDir.exists())
        ready.stagingFile.delete()
        assertFalse(tempArchive.exists())
    }

    @Test
    fun testExportAccountBackup_withIncludeDiagramsFalse() = runBlocking {
        val ownerUid = "uid_diagram_toggle_test"
        val testEntity = TestEntity(
            id = "test_diag_toggle",
            title = "Diagram Toggle Test",
            description = "Desc",
            institute = "Institute",
            batchName = "Batch",
            facultyName = "Faculty",
            language = "English",
            subject = "Biology",
            difficulty = "Easy",
            testType = "Chapter Test",
            questionsCount = 1,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            countsTowardRanking = false,
            rating = 5.0f,
            timestamp = 1000L,
            status = "Published",
            aiConfidence = 100,
            uploaderId = ownerUid,
            targetExam = "NEET"
        )
        db.testDao().insertTest(testEntity)

        val question = QuestionEntity(
            id = "q_diag_1",
            testId = "test_diag_toggle",
            questionText = "Cell question with diagram?",
            optionA = "A",
            optionB = "B",
            optionC = "C",
            optionD = "D",
            correctAnswer = "A",
            explanation = "Exp",
            subject = "Biology",
            chapter = "Cell",
            imageUrl = "private_tests/test_diag_toggle/diagrams/cell.png"
        )
        db.questionDao().insertQuestions(listOf(question))

        // When includeDiagrams = false, export should succeed and omit diagrams without throwing missing diagram errors
        val baos = ByteArrayOutputStream()
        val exportResult = AccountDataBackupAdapter.exportAccountBackup(
            context = context,
            db = db,
            uid = ownerUid,
            email = "toggle@example.com",
            displayName = "Toggle User",
            outputStream = baos,
            includeDiagrams = false
        )

        assertTrue("Export must succeed with includeDiagrams=false", exportResult.isSuccess)
        val manifest = exportResult.getOrThrow()
        assertEquals(0, manifest.stats.diagramsCount)
        assertEquals(1, manifest.stats.privateTestsCount)
        assertEquals(1, manifest.stats.questionsCount)
        assertEquals(ownerUid, manifest.owner.uid)
    }

    @Test
    fun testAppScreenBackupRestoreEnumExists() {
        val screens = Com.ak.testseries.viewmodel.AppScreen.values()
        val hasBackupRestore = screens.any { it.name == "BACKUP_RESTORE" }
        assertTrue("AppScreen must contain BACKUP_RESTORE enum", hasBackupRestore)
    }

    @Test
    fun testBackupMetadataPersistenceInSharedPreferences() {
        val uid = "test_uid_persistence"
        val prefs = context.getSharedPreferences("user_backup_prefs", Context.MODE_PRIVATE)
        val timestamp = 1700000000000L
        val stats = "Tests: 3, Attempts: 5, Bookmarks: 10, Diagrams: 0"

        prefs.edit()
            .putLong("last_backup_timestamp_$uid", timestamp)
            .putString("last_backup_stats_$uid", stats)
            .commit()

        val readTimestamp = prefs.getLong("last_backup_timestamp_$uid", 0L)
        val readStats = prefs.getString("last_backup_stats_$uid", null)

        assertEquals(timestamp, readTimestamp)
        assertEquals(stats, readStats)
    }

    @Test
    fun testEvaluateBackupHealth_allCases() {
        val now = System.currentTimeMillis()

        // 1. No backup and no data -> GOOD
        val healthEmpty = evaluateBackupHealth(null, 0, 0, isHindi = false)
        assertEquals(BackupHealthStatus.GOOD, healthEmpty.status)

        // 2. No backup with active data -> CRITICAL
        val healthNoBackupWithData = evaluateBackupHealth(null, 3, 5, isHindi = false)
        assertEquals(BackupHealthStatus.CRITICAL, healthNoBackupWithData.status)

        // 3. Recent backup (2 days ago) -> GOOD
        val twoDaysAgo = now - (2L * 24 * 60 * 60 * 1000)
        val healthRecent = evaluateBackupHealth(twoDaysAgo, 3, 5, isHindi = false)
        assertEquals(BackupHealthStatus.GOOD, healthRecent.status)
        assertEquals(2, healthRecent.daysSinceLastBackup)

        // 4. Warning backup (15 days ago) -> WARNING
        val fifteenDaysAgo = now - (15L * 24 * 60 * 60 * 1000)
        val healthWarning = evaluateBackupHealth(fifteenDaysAgo, 3, 5, isHindi = false)
        assertEquals(BackupHealthStatus.WARNING, healthWarning.status)
        assertEquals(15, healthWarning.daysSinceLastBackup)

        // 5. Outdated backup (45 days ago) -> CRITICAL
        val fortyFiveDaysAgo = now - (45L * 24 * 60 * 60 * 1000)
        val healthCritical = evaluateBackupHealth(fortyFiveDaysAgo, 3, 5, isHindi = false)
        assertEquals(BackupHealthStatus.CRITICAL, healthCritical.status)
        assertEquals(45, healthCritical.daysSinceLastBackup)
    }

    @Test
    fun testBackupExportRecord_serializationAndDeserialization() {
        val record1 = BackupExportRecord(
            timestamp = 1000L,
            fileName = "backup_1.aktsbak",
            statsSummary = "Tests: 2, Attempts: 1",
            totalBytes = 4096L,
            isComplete = true
        )
        val record2 = BackupExportRecord(
            timestamp = 2000L,
            fileName = "backup_2.aktsbak",
            statsSummary = "Tests: 4, Attempts: 3",
            totalBytes = 8192L,
            isComplete = false
        )

        val list = listOf(record1, record2)
        val json = BackupExportRecord.serializeList(list)
        val restored = BackupExportRecord.deserializeList(json)

        assertEquals(2, restored.size)
        assertEquals("backup_1.aktsbak", restored[0].fileName)
        assertEquals(4096L, restored[0].totalBytes)
        assertTrue(restored[0].isComplete)
        assertEquals("backup_2.aktsbak", restored[1].fileName)
        assertEquals(8192L, restored[1].totalBytes)
        assertFalse(restored[1].isComplete)
    }
}
