package Com.ak.testseries.data.backup

import android.content.Context
import androidx.room.withTransaction
import Com.ak.testseries.data.AppDatabase
import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Result contract for the safe account restore engine.
 */
sealed class RestoreResult {
    data class Success(
        val manifest: BackupManifest,
        val restoredTestsCount: Int,
        val restoredQuestionsCount: Int,
        val restoredAttemptsCount: Int,
        val restoredDiagramsCount: Int,
        val mergedBookmarksCount: Int,
        val restoredProgressCount: Int,
        val restoredSyllabusCount: Int,
        val skippedDuplicateAttemptsCount: Int = 0,
        val preservedNewerTestsCount: Int = 0
    ) : RestoreResult()

    data class Failure(
        val reason: String,
        val details: String? = null,
        val cause: Throwable? = null
    ) : RestoreResult()
}

/**
 * Validated in-memory staging data container for an account backup archive.
 * Staged data is verified and ready for inspection without writing to live Room tables or files.
 */
data class StagedBackupData(
    val manifest: BackupManifest,
    val tests: List<TestEntity>,
    val questions: List<QuestionEntity>,
    val attempts: List<AttemptEntity>,
    val bookmarks: BookmarksBackupPayload,
    val progress: ProgressBackupPayload,
    val syllabus: SyllabusBackupPayload,
    val profile: ProfileBackupPayload,
    val stagedDiagramFiles: List<File>,
    val stagedAvatarFile: File?,
    val stagingDir: File
) {
    fun cleanUp() {
        if (stagingDir.exists()) {
            stagingDir.deleteRecursively()
        }
    }
}

/**
 * Result contract for the staging and validation phase of the backup archive pipeline.
 */
sealed class StageValidationResult {
    data class Success(
        val stagedData: StagedBackupData
    ) : StageValidationResult()

    data class Failure(
        val reason: String,
        val details: String? = null,
        val cause: Throwable? = null
    ) : StageValidationResult()
}

/**
 * Transactional and validation-driven restore engine for AK TEST SERIES `.aktsbak` archives.
 *
 * Guarantees:
 * 1. Strict Validation: Validates container, manifest, checksums, schema compatibility, and entry counts before touching live data.
 * 2. Account Ownership Containment: Prohibits restore if authenticated UID is blank or does not match backup manifest owner UID.
 * 3. Staging-First: Unpacks and inspects all contents in an isolated staging cache directory.
 * 4. Zero Mutation on Validation Failure: Never mutates Room, preferences, or files if validation fails.
 * 5. Idempotent & Deterministic: Skips duplicate attempts, preserves newer live tests over older backups.
 * 6. Rollback-Capable: Room database changes commit via `withTransaction`. Preference and file changes maintain rollback snapshots.
 * 7. Security: Auth tokens, session cookies, passwords, API keys, and admin secrets are strictly excluded from restore.
 */
object AccountDataRestoreEngine {

    const val MANIFEST_FILE_NAME = BackupArchiveEngine.MANIFEST_ENTRY_NAME

    val MANDATORY_DATA_CATEGORIES = listOf(
        "data/tests.json",
        "data/questions.json",
        "data/attempts.json",
        "data/bookmarks.json",
        "data/progress.json",
        "data/syllabus.json",
        "data/profile.json"
    )

    /**
     * Validates and stages an account backup archive into an isolated staging directory.
     * CRITICAL SAFEGUARD: This method validates all format versions, checksums, schema compatibility,
     * entry limits, duplicate IDs, and account ownership, and parses all payloads into memory / staging,
     * WITHOUT modifying live Room database tables, SharedPreferences, or live app files.
     */
    fun stageAndValidateAccountBackup(
        context: Context,
        authenticatedUid: String,
        archiveInputStream: InputStream,
        limits: ArchiveLimits = ArchiveLimits(),
        overrideStagingDir: File? = null
    ): StageValidationResult {
        val safeUid = authenticatedUid.trim()
        if (safeUid.isBlank()) {
            return StageValidationResult.Failure(
                reason = "Unauthenticated restore rejected.",
                details = "An authenticated user UID is required to restore an account backup. Unauthenticated guests cannot restore data."
            )
        }

        val stagingDir = overrideStagingDir ?: File(
            context.cacheDir,
            "backup_staging_${safeUid}_${System.currentTimeMillis()}"
        ).apply { mkdirs() }

        try {
            // STEP 1: Unpack and validate archive structure into staging directory
            val unpackError = unpackAndVerifyChecksums(archiveInputStream, stagingDir, limits)
            if (unpackError != null) {
                stagingDir.deleteRecursively()
                return unpackError
            }

            // STEP 2: Parse and validate manifest
            val manifestFile = File(stagingDir, MANIFEST_FILE_NAME)
            if (!manifestFile.exists()) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Missing mandatory root manifest: $MANIFEST_FILE_NAME"
                )
            }

            val manifest = try {
                BackupManifest.fromJsonString(manifestFile.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Malformed manifest JSON in archive.",
                    details = e.message,
                    cause = e
                )
            }

            // Version checks
            if (manifest.formatVersion > BackupArchiveEngine.CURRENT_FORMAT_VERSION) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Unsupported future format version (${manifest.formatVersion}). Current supported version is ${BackupArchiveEngine.CURRENT_FORMAT_VERSION}."
                )
            }
            if (manifest.formatVersion < 1) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Invalid format version (${manifest.formatVersion})."
                )
            }
            if (manifest.databaseSchemaVersion > BackupArchiveEngine.CURRENT_SCHEMA_VERSION) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Unsupported future database schema version (${manifest.databaseSchemaVersion}). Current supported version is ${BackupArchiveEngine.CURRENT_SCHEMA_VERSION}."
                )
            }
            if (manifest.databaseSchemaVersion < manifest.compatibility.minSupportedSchemaVersion) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Unsupported legacy database schema version (${manifest.databaseSchemaVersion})."
                )
            }

            // Completeness check
            if (manifest.completeness != BackupCompleteness.COMPLETE_USER_DATA) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Incomplete backup archive rejected.",
                    details = "Archive completeness is '${manifest.completeness.name}', but full account restore requires '${BackupCompleteness.COMPLETE_USER_DATA.name}'."
                )
            }

            // Account ownership check: strict match
            if (manifest.owner.uid != safeUid) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Account ownership mismatch rejected.",
                    details = "Backup owner UID ('${manifest.owner.uid}') does not match currently authenticated user ('$safeUid'). Cross-account restoration is prohibited."
                )
            }

            // Check mandatory categories
            for (category in MANDATORY_DATA_CATEGORIES) {
                val categoryFile = File(stagingDir, category)
                if (!categoryFile.exists() || !categoryFile.isFile) {
                    stagingDir.deleteRecursively()
                    return StageValidationResult.Failure(
                        reason = "Missing mandatory backup category: $category"
                    )
                }
            }

            // STEP 3: Parse and validate all staged JSON data payloads
            val parsedTests = try {
                val testsFile = File(stagingDir, "data/tests.json")
                val testsArray = JSONArray(testsFile.readText(Charsets.UTF_8))
                if (testsArray.length() != manifest.stats.privateTestsCount) {
                    stagingDir.deleteRecursively()
                    return StageValidationResult.Failure(
                        reason = "Inconsistent manifest stats: Expected ${manifest.stats.privateTestsCount} tests, found ${testsArray.length()} in data/tests.json."
                    )
                }
                val list = (0 until testsArray.length()).map { i ->
                    val entity = AccountDataBackupAdapter.jsonToTestEntity(testsArray.getJSONObject(i))
                    if (entity.uploaderId != safeUid) {
                        stagingDir.deleteRecursively()
                        return StageValidationResult.Failure(
                            reason = "Foreign account test detected in archive payload.",
                            details = "Test '${entity.id}' belongs to '${entity.uploaderId}', expected '$safeUid'."
                        )
                    }
                    entity
                }
                // Duplicate Test ID check
                val seenTestIds = mutableSetOf<String>()
                for (entity in list) {
                    if (!seenTestIds.add(entity.id)) {
                        stagingDir.deleteRecursively()
                        return StageValidationResult.Failure(
                            reason = "Duplicate test ID detected in archive: ${entity.id}"
                        )
                    }
                }
                list
            } catch (e: JSONException) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Invalid JSON structure in data/tests.json: ${e.message}",
                    cause = e
                )
            }

            val validTestIds = parsedTests.map { it.id }.toSet()

            val parsedQuestions = try {
                val questionsFile = File(stagingDir, "data/questions.json")
                val qArray = JSONArray(questionsFile.readText(Charsets.UTF_8))
                if (qArray.length() != manifest.stats.questionsCount) {
                    stagingDir.deleteRecursively()
                    return StageValidationResult.Failure(
                        reason = "Inconsistent manifest stats: Expected ${manifest.stats.questionsCount} questions, found ${qArray.length()} in data/questions.json."
                    )
                }
                val list = (0 until qArray.length()).map { i ->
                    val q = AccountDataBackupAdapter.jsonToQuestionEntity(qArray.getJSONObject(i))
                    if (!validTestIds.contains(q.testId)) {
                        stagingDir.deleteRecursively()
                        return StageValidationResult.Failure(
                            reason = "Orphaned question in backup payload.",
                            details = "Question '${q.id}' references unknown testId '${q.testId}'."
                        )
                    }
                    q
                }
                // Duplicate Question ID check
                val seenQuestionIds = mutableSetOf<String>()
                for (q in list) {
                    if (!seenQuestionIds.add(q.id)) {
                        stagingDir.deleteRecursively()
                        return StageValidationResult.Failure(
                            reason = "Duplicate question ID detected in archive: ${q.id}"
                        )
                    }
                }
                list
            } catch (e: JSONException) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Invalid JSON structure in data/questions.json: ${e.message}",
                    cause = e
                )
            }

            val parsedAttempts = try {
                val attemptsFile = File(stagingDir, "data/attempts.json")
                val attemptsArray = JSONArray(attemptsFile.readText(Charsets.UTF_8))
                if (attemptsArray.length() != manifest.stats.attemptsCount) {
                    stagingDir.deleteRecursively()
                    return StageValidationResult.Failure(
                        reason = "Inconsistent manifest stats: Expected ${manifest.stats.attemptsCount} attempts, found ${attemptsArray.length()} in data/attempts.json."
                    )
                }
                val list = (0 until attemptsArray.length()).map { i ->
                    val attempt = AccountDataBackupAdapter.jsonToAttemptEntity(attemptsArray.getJSONObject(i))
                    if (attempt.userId != safeUid) {
                        stagingDir.deleteRecursively()
                        return StageValidationResult.Failure(
                            reason = "Foreign account attempt detected in archive payload.",
                            details = "Attempt '${attempt.id}' belongs to '${attempt.userId}', expected '$safeUid'."
                        )
                    }
                    attempt
                }
                // Duplicate Attempt ID and Session Token check
                val seenAttemptIds = mutableSetOf<Int>()
                val seenAttemptSessionTokens = mutableSetOf<String>()
                for (attempt in list) {
                    if (attempt.id > 0 && !seenAttemptIds.add(attempt.id)) {
                        stagingDir.deleteRecursively()
                        return StageValidationResult.Failure(
                            reason = "Duplicate attempt ID detected in archive: ${attempt.id}"
                        )
                    }
                    if (attempt.attemptSessionToken.isNotBlank() && !seenAttemptSessionTokens.add(attempt.attemptSessionToken)) {
                        stagingDir.deleteRecursively()
                        return StageValidationResult.Failure(
                            reason = "Duplicate attempt session token detected in archive: ${attempt.attemptSessionToken}"
                        )
                    }
                }
                list
            } catch (e: JSONException) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Invalid JSON structure in data/attempts.json: ${e.message}",
                    cause = e
                )
            }

            val parsedBookmarks = try {
                val bookmarksFile = File(stagingDir, "data/bookmarks.json")
                val payload = BookmarksBackupPayload.fromJsonObject(JSONObject(bookmarksFile.readText(Charsets.UTF_8)))
                if (payload.userId != safeUid) {
                    stagingDir.deleteRecursively()
                    return StageValidationResult.Failure(
                        reason = "Foreign account bookmarks detected: ${payload.userId}"
                    )
                }
                payload
            } catch (e: JSONException) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Invalid JSON in data/bookmarks.json: ${e.message}",
                    cause = e
                )
            }

            val parsedProgress = try {
                val progressFile = File(stagingDir, "data/progress.json")
                val payload = ProgressBackupPayload.fromJsonObject(JSONObject(progressFile.readText(Charsets.UTF_8)))
                if (payload.userId != safeUid) {
                    stagingDir.deleteRecursively()
                    return StageValidationResult.Failure(
                        reason = "Foreign account progress detected: ${payload.userId}"
                    )
                }
                payload
            } catch (e: JSONException) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Invalid JSON in data/progress.json: ${e.message}",
                    cause = e
                )
            }

            val parsedSyllabus = try {
                val syllabusFile = File(stagingDir, "data/syllabus.json")
                val payload = SyllabusBackupPayload.fromJsonObject(JSONObject(syllabusFile.readText(Charsets.UTF_8)))
                if (payload.userId != safeUid) {
                    stagingDir.deleteRecursively()
                    return StageValidationResult.Failure(
                        reason = "Foreign account syllabus detected: ${payload.userId}"
                    )
                }
                payload
            } catch (e: JSONException) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Invalid JSON in data/syllabus.json: ${e.message}",
                    cause = e
                )
            }

            val parsedProfile = try {
                val profileFile = File(stagingDir, "data/profile.json")
                val payload = ProfileBackupPayload.fromJsonObject(JSONObject(profileFile.readText(Charsets.UTF_8)))
                if (payload.userId != safeUid) {
                    stagingDir.deleteRecursively()
                    return StageValidationResult.Failure(
                        reason = "Foreign account profile detected: ${payload.userId}"
                    )
                }
                payload
            } catch (e: JSONException) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Invalid JSON in data/profile.json: ${e.message}",
                    cause = e
                )
            }

            // Check diagram assets in staging
            val diagramsDir = File(stagingDir, "assets/diagrams")
            val stagedDiagramFiles = mutableListOf<File>()
            if (diagramsDir.exists() && diagramsDir.isDirectory) {
                val stagingCanonical = stagingDir.canonicalFile.path
                diagramsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val fileCanonical = file.canonicalFile.path
                    if (!fileCanonical.startsWith(stagingCanonical)) {
                        stagingDir.deleteRecursively()
                        return StageValidationResult.Failure(
                            reason = "Path traversal detected in diagram asset: ${file.path}"
                        )
                    }
                    stagedDiagramFiles.add(file)
                }
            }
            if (stagedDiagramFiles.size != manifest.stats.diagramsCount) {
                stagingDir.deleteRecursively()
                return StageValidationResult.Failure(
                    reason = "Inconsistent manifest stats: Expected ${manifest.stats.diagramsCount} diagrams, found ${stagedDiagramFiles.size}."
                )
            }

            val stagedAvatar = File(stagingDir, "assets/profile/avatar.jpg")
            val stagedAvatarFile = if (stagedAvatar.exists() && stagedAvatar.isFile) stagedAvatar else null

            return StageValidationResult.Success(
                stagedData = StagedBackupData(
                    manifest = manifest,
                    tests = parsedTests,
                    questions = parsedQuestions,
                    attempts = parsedAttempts,
                    bookmarks = parsedBookmarks,
                    progress = parsedProgress,
                    syllabus = parsedSyllabus,
                    profile = parsedProfile,
                    stagedDiagramFiles = stagedDiagramFiles,
                    stagedAvatarFile = stagedAvatarFile,
                    stagingDir = stagingDir
                )
            )
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            return StageValidationResult.Failure(
                reason = "Unexpected error during backup staging and validation: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Restores an account backup from [archiveInputStream] for the currently authenticated user [authenticatedUid].
     */
    suspend fun restoreAccountBackup(
        context: Context,
        db: AppDatabase,
        authenticatedUid: String,
        archiveInputStream: InputStream,
        limits: ArchiveLimits = ArchiveLimits(),
        overrideStagingDir: File? = null
    ): RestoreResult {
        val stageResult = stageAndValidateAccountBackup(
            context = context,
            authenticatedUid = authenticatedUid,
            archiveInputStream = archiveInputStream,
            limits = limits,
            overrideStagingDir = overrideStagingDir
        )

        if (stageResult is StageValidationResult.Failure) {
            return RestoreResult.Failure(
                reason = stageResult.reason,
                details = stageResult.details,
                cause = stageResult.cause
            )
        }

        val stagedData = (stageResult as StageValidationResult.Success).stagedData
        val stagingDir = stagedData.stagingDir
        val safeUid = authenticatedUid.trim()

        try {
            val manifest = stagedData.manifest
            val parsedTests = stagedData.tests
            val parsedQuestions = stagedData.questions
            val parsedAttempts = stagedData.attempts
            val parsedBookmarks = stagedData.bookmarks
            val parsedProgress = stagedData.progress
            val parsedSyllabus = stagedData.syllabus
            val parsedProfile = stagedData.profile
            val stagedDiagramFiles = stagedData.stagedDiagramFiles

            // STEP 4: Conflict Resolution and Entity Preparation
            val testsToInsert = mutableListOf<TestEntity>()
            val questionsToInsert = mutableListOf<QuestionEntity>()
            var preservedNewerTestsCount = 0

            val questionsByTestId = parsedQuestions.groupBy { it.testId }

            for (backupTest in parsedTests) {
                val existing = db.testDao().getTestById(backupTest.id)
                if (existing != null && existing.timestamp > backupTest.timestamp) {
                    // Local live test is strictly newer than the backup copy: preserve local
                    preservedNewerTestsCount++
                } else {
                    testsToInsert.add(backupTest)
                    questionsByTestId[backupTest.id]?.let { questionsToInsert.addAll(it) }
                }
            }

            // Attempts deduplication
            val existingUserAttempts = db.attemptDao().getAttemptsForUser(safeUid).first()
            val allAttemptsInDb = db.attemptDao().getAllAttempts().first()
            val takenIds = allAttemptsInDb.map { it.id }.toSet()

            val attemptsToInsert = mutableListOf<AttemptEntity>()
            var skippedDuplicateAttemptsCount = 0

            for (backupAttempt in parsedAttempts) {
                val isDuplicate = existingUserAttempts.any { existing ->
                    (backupAttempt.attemptSessionToken.isNotBlank() && existing.attemptSessionToken == backupAttempt.attemptSessionToken) ||
                    (existing.testId == backupAttempt.testId && existing.dateTaken == backupAttempt.dateTaken && existing.timeSpentSeconds == backupAttempt.timeSpentSeconds)
                }

                if (isDuplicate) {
                    skippedDuplicateAttemptsCount++
                } else {
                    // If ID collides with an existing row, reset id to 0 to let SQLite autoincrement cleanly
                    val safeAttempt = if (takenIds.contains(backupAttempt.id)) {
                        backupAttempt.copy(id = 0)
                    } else {
                        backupAttempt
                    }
                    attemptsToInsert.add(safeAttempt)
                }
            }

            // STEP 5: Prepare Rollback Snapshots for Files and Preferences
            val filesDirCanonical = context.filesDir.canonicalFile
            val createdFiles = mutableListOf<File>()
            val overwrittenFilesBackup = mutableMapOf<File, ByteArray>()

            val bookmarksPrefs = context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
            val previousBookmarks = bookmarksPrefs.getStringSet("bookmarks_$safeUid", null)

            val progressPrefs = context.getSharedPreferences("test_progress", Context.MODE_PRIVATE)
            val previousProgress = progressPrefs.all.filterKeys { it.startsWith("progress_${safeUid}_") }

            val syllabusPrefs = context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE)
            val previousSyllabus = syllabusPrefs.all.filterKeys { it.startsWith("completed_chapters_${safeUid}_") }

            val profilePrefs = context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
            val previousProfile = mapOf(
                "target_exam" to profilePrefs.getString("target_exam", null),
                "class_level" to profilePrefs.getString("class_level", null),
                "selected_language" to profilePrefs.getString("selected_language", null),
                "test_folders" to profilePrefs.getString("test_folders", null),
                "test_folder_mappings" to profilePrefs.getString("test_folder_mappings", null)
            )

            // STEP 6: Execute Commit Strategy
            try {
                // 6A: Commit Room database changes transactionally
                db.withTransaction {
                    if (testsToInsert.isNotEmpty()) {
                        db.testDao().insertTests(testsToInsert)
                        for (test in testsToInsert) {
                            db.questionDao().deleteQuestionsForTest(test.id)
                        }
                    }
                    if (questionsToInsert.isNotEmpty()) {
                        db.questionDao().insertQuestions(questionsToInsert)
                    }
                    for (attempt in attemptsToInsert) {
                        db.attemptDao().insertAttempt(attempt)
                    }
                }

                // 6B: Commit diagram files to filesDir
                for (stagedFile in stagedDiagramFiles) {
                    // Entry relative path under stagingDir: e.g. "assets/diagrams/test_1/img.png"
                    val relativePath = stagedFile.relativeTo(stagingDir).path.replace('\\', '/')
                    val segments = relativePath.split('/')
                    if (segments.size >= 4 && segments[0] == "assets" && segments[1] == "diagrams") {
                        val testId = segments[2]
                        val fileName = segments.drop(3).joinToString("/")
                        val destDir = File(context.filesDir, "private_tests/$testId/diagrams")
                        if (!destDir.exists()) destDir.mkdirs()
                        val destFile = File(destDir, fileName)

                        if (!destFile.canonicalPath.startsWith(filesDirCanonical.path)) {
                            throw SecurityException("Diagram target path escapes filesDir: ${destFile.path}")
                        }

                        if (destFile.exists()) {
                            overwrittenFilesBackup[destFile] = destFile.readBytes()
                        } else {
                            createdFiles.add(destFile)
                        }

                        stagedFile.copyTo(destFile, overwrite = true)
                    }
                }

                // Restore profile avatar if present in backup archive
                val stagedAvatar = File(stagingDir, "assets/profile/avatar.jpg")
                if (stagedAvatar.exists() && stagedAvatar.isFile) {
                    val destAvatar = File(context.filesDir, "profile_pic_${System.currentTimeMillis()}.jpg")
                    if (!destAvatar.canonicalPath.startsWith(filesDirCanonical.path)) {
                        throw SecurityException("Avatar target path escapes filesDir: ${destAvatar.path}")
                    }
                    stagedAvatar.copyTo(destAvatar, overwrite = true)
                    createdFiles.add(destAvatar)
                    profilePrefs.edit().putString("profileImageUri", destAvatar.absolutePath).commit()
                }

                // Rebuild derived JSON files in secure_ai_tests if present
                for (test in testsToInsert) {
                    if (!test.questionsJson.isNullOrBlank()) {
                        val secureDir = File(context.filesDir, "secure_ai_tests").apply { if (!exists()) mkdirs() }
                        val jsonFile = File(secureDir, "${test.id}.json")
                        if (jsonFile.exists()) {
                            overwrittenFilesBackup[jsonFile] = jsonFile.readBytes()
                        } else {
                            createdFiles.add(jsonFile)
                        }
                        jsonFile.writeText(test.questionsJson)
                    }
                }

                // 6C: Commit preferences
                val currentBookmarks = bookmarksPrefs.getStringSet("bookmarks_$safeUid", emptySet()) ?: emptySet()
                val mergedBookmarks = (currentBookmarks + parsedBookmarks.testBookmarks).toSet()
                bookmarksPrefs.edit().putStringSet("bookmarks_$safeUid", mergedBookmarks).commit()

                val progressEdit = progressPrefs.edit()
                for ((testId, session) in parsedProgress.inProgressSessions) {
                    val key = "progress_${safeUid}_$testId"
                    if (!progressPrefs.contains(key)) {
                        progressEdit.putString(key, session)
                    }
                }
                progressEdit.commit()

                val syllabusEdit = syllabusPrefs.edit()
                for ((exam, chapters) in parsedSyllabus.completedChaptersByExam) {
                    val key = "completed_chapters_${safeUid}_$exam"
                    val existing = syllabusPrefs.getStringSet(key, emptySet()) ?: emptySet()
                    val merged = (existing + chapters).toSet()
                    syllabusEdit.putStringSet(key, merged)
                }
                syllabusEdit.commit()

                val profileEdit = profilePrefs.edit()
                parsedProfile.targetExam.let { profileEdit.putString("target_exam", it) }
                parsedProfile.classLevel.let { profileEdit.putString("class_level", it) }
                parsedProfile.selectedLanguage.let { profileEdit.putString("selected_language", it) }
                parsedProfile.testFolders?.let { profileEdit.putString("test_folders", it) }
                parsedProfile.testFolderMappings?.let { profileEdit.putString("test_folder_mappings", it) }
                profileEdit.commit()

            } catch (commitEx: Exception) {
                // Rollback Filesystem Changes
                for (created in createdFiles) {
                    if (created.exists()) created.delete()
                }
                for ((overwritten, originalBytes) in overwrittenFilesBackup) {
                    overwritten.writeBytes(originalBytes)
                }

                // Rollback SharedPreferences
                val bEdit = bookmarksPrefs.edit()
                if (previousBookmarks == null) bEdit.remove("bookmarks_$safeUid") else bEdit.putStringSet("bookmarks_$safeUid", previousBookmarks)
                bEdit.commit()

                val pEdit = progressPrefs.edit()
                for (key in parsedProgress.inProgressSessions.keys.map { "progress_${safeUid}_$it" }) {
                    if (!previousProgress.containsKey(key)) {
                        pEdit.remove(key)
                    }
                }
                for ((key, value) in previousProgress) {
                    if (value is String) pEdit.putString(key, value)
                }
                pEdit.commit()

                val sEdit = syllabusPrefs.edit()
                for (key in parsedSyllabus.completedChaptersByExam.keys.map { "completed_chapters_${safeUid}_$it" }) {
                    if (!previousSyllabus.containsKey(key)) {
                        sEdit.remove(key)
                    }
                }
                for ((key, value) in previousSyllabus) {
                    if (value is Set<*>) {
                        @Suppress("UNCHECKED_CAST")
                        sEdit.putStringSet(key, value as Set<String>)
                    }
                }
                sEdit.commit()

                val profEdit = profilePrefs.edit()
                for ((k, v) in previousProfile) {
                    if (v == null) profEdit.remove(k) else profEdit.putString(k, v)
                }
                profEdit.commit()

                // Rollback Room records inserted during this attempt
                try {
                    db.withTransaction {
                        for (test in testsToInsert) {
                            db.questionDao().deleteQuestionsForTest(test.id)
                            db.testDao().deleteTest(test)
                        }
                        for (attempt in attemptsToInsert) {
                            // If it was inserted with ID > 0 or session token
                            if (attempt.attemptSessionToken.isNotBlank()) {
                                val found = db.attemptDao().getAttemptBySessionToken(safeUid, attempt.attemptSessionToken)
                                if (found != null) {
                                    db.attemptDao().clearAttemptsForUser(safeUid)
                                    // Re-insert existing attempts that were not part of this restore
                                    for (orig in existingUserAttempts) {
                                        db.attemptDao().insertAttempt(orig)
                                    }
                                }
                            }
                        }
                    }
                } catch (dbRollbackEx: Exception) {
                    // Log or record secondary rollback error
                }

                return RestoreResult.Failure(
                    reason = "Commit operation failed. System rolled back to previous state: ${commitEx.message}",
                    cause = commitEx
                )
            }

            return RestoreResult.Success(
                manifest = manifest,
                restoredTestsCount = testsToInsert.size,
                restoredQuestionsCount = questionsToInsert.size,
                restoredAttemptsCount = attemptsToInsert.size,
                restoredDiagramsCount = stagedDiagramFiles.size,
                mergedBookmarksCount = parsedBookmarks.testBookmarks.size,
                restoredProgressCount = parsedProgress.inProgressSessions.size,
                restoredSyllabusCount = parsedSyllabus.completedChaptersByExam.size,
                skippedDuplicateAttemptsCount = skippedDuplicateAttemptsCount,
                preservedNewerTestsCount = preservedNewerTestsCount
            )
        } catch (e: Exception) {
            return RestoreResult.Failure(
                reason = "Unexpected error during restore: ${e.message}",
                cause = e
            )
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    /**
     * Unpacks the archive stream into [stagingDir] with ZipSlip protection, duplicate detection,
     * and SHA-256 verification against the manifest.
     */
    private fun unpackAndVerifyChecksums(
        inputStream: InputStream,
        stagingDir: File,
        limits: ArchiveLimits
    ): StageValidationResult.Failure? {
        val zipIn = ZipInputStream(BufferedInputStream(inputStream))
        val seenEntryNames = mutableSetOf<String>()
        val computedChecksums = mutableMapOf<String, String>()
        var totalEntries = 0
        var totalUncompressedBytes = 0L

        try {
            var zipEntry = zipIn.nextEntry
            while (zipEntry != null) {
                totalEntries++
                if (totalEntries > limits.maxEntries) {
                    return StageValidationResult.Failure(
                        reason = "Archive exceeds maximum allowed entry count (${limits.maxEntries})."
                    )
                }

                val rawName = zipEntry.name
                val pathValidation = BackupArchiveEngine.sanitizeEntryPath(rawName)
                if (pathValidation.isFailure) {
                    return StageValidationResult.Failure(
                        reason = "Path traversal or illegal entry path in archive: $rawName",
                        details = pathValidation.exceptionOrNull()?.message
                    )
                }
                val cleanPath = pathValidation.getOrThrow()

                if (!seenEntryNames.add(cleanPath)) {
                    return StageValidationResult.Failure(
                        reason = "Duplicate entry detected in archive: $cleanPath"
                    )
                }

                val targetFile = File(stagingDir, cleanPath)
                val canonicalTarget = targetFile.canonicalFile
                if (!canonicalTarget.path.startsWith(stagingDir.canonicalFile.path)) {
                    return StageValidationResult.Failure(
                        reason = "ZipSlip detected: Entry path '$rawName' resolves outside staging directory."
                    )
                }

                canonicalTarget.parentFile?.let { if (!it.exists()) it.mkdirs() }

                val entryBaos = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var entryBytesRead = 0L
                var read: Int
                while (zipIn.read(buffer).also { read = it } != -1) {
                    entryBytesRead += read
                    if (entryBytesRead > limits.maxSingleEntryBytes) {
                        return StageValidationResult.Failure(
                            reason = "Archive entry '$cleanPath' exceeds single entry limit (${limits.maxSingleEntryBytes} bytes)."
                        )
                    }
                    totalUncompressedBytes += read
                    if (totalUncompressedBytes > limits.maxTotalUncompressedBytes) {
                        return StageValidationResult.Failure(
                            reason = "Archive total uncompressed bytes exceeds limit (${limits.maxTotalUncompressedBytes} bytes)."
                        )
                    }
                    entryBaos.write(buffer, 0, read)
                }
                zipIn.closeEntry()

                val entryBytes = entryBaos.toByteArray()
                FileOutputStream(canonicalTarget).use { it.write(entryBytes) }

                if (cleanPath != MANIFEST_FILE_NAME) {
                    computedChecksums[cleanPath] = BackupArchiveEngine.computeSha256(entryBytes)
                }

                zipEntry = zipIn.nextEntry
            }
        } catch (e: Exception) {
            return StageValidationResult.Failure(
                reason = "Archive container is corrupted or not a valid ZIP: ${e.message}",
                cause = e
            )
        }

        // Validate manifest entry presence
        val manifestFile = File(stagingDir, MANIFEST_FILE_NAME)
        if (!manifestFile.exists()) {
            return StageValidationResult.Failure(
                reason = "Missing mandatory root manifest: $MANIFEST_FILE_NAME"
            )
        }

        val manifest = try {
            BackupManifest.fromJsonString(manifestFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            return StageValidationResult.Failure(
                reason = "Malformed manifest JSON in archive: ${e.message}",
                cause = e
            )
        }

        // Validate checksums against manifest
        for ((expectedPath, expectedHash) in manifest.checksums) {
            val foundHash = computedChecksums[expectedPath]
            if (foundHash == null) {
                return StageValidationResult.Failure(
                    reason = "Manifest declares file '$expectedPath', but it was not found in the archive."
                )
            }
            if (!foundHash.equals(expectedHash, ignoreCase = true)) {
                return StageValidationResult.Failure(
                    reason = "Integrity check failed: Checksum mismatch for '$expectedPath'.",
                    details = "Expected: $expectedHash, Found: $foundHash"
                )
            }
        }

        // Check for unmanifested extraneous files
        for (foundPath in computedChecksums.keys) {
            if (!manifest.checksums.containsKey(foundPath)) {
                return StageValidationResult.Failure(
                    reason = "Archive contains unmanifested extra file: '$foundPath'."
                )
            }
        }

        return null // Success
    }
}
