package Com.ak.testseries.data.backup

import android.content.Context
import Com.ak.testseries.data.AppDatabase
import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream

/**
 * Account-isolated data container for user bookmarks.
 */
data class BookmarksBackupPayload(
    val userId: String,
    val testBookmarks: List<String>,
    val questionBookmarks: List<String>
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("userId", userId)
        put("testBookmarks", JSONArray(testBookmarks))
        put("questionBookmarks", JSONArray(questionBookmarks))
    }

    companion object {
        fun fromJsonObject(json: JSONObject): BookmarksBackupPayload {
            val testArray = json.optJSONArray("testBookmarks") ?: JSONArray()
            val tests = (0 until testArray.length()).map { testArray.getString(it) }
            val qArray = json.optJSONArray("questionBookmarks") ?: JSONArray()
            val questions = (0 until qArray.length()).map { qArray.getString(it) }
            return BookmarksBackupPayload(
                userId = json.optString("userId", ""),
                testBookmarks = tests,
                questionBookmarks = questions
            )
        }
    }
}

/**
 * Account-isolated data container for in-progress test attempts.
 */
data class ProgressBackupPayload(
    val userId: String,
    val inProgressSessions: Map<String, String> // testId -> serialized session JSON
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("userId", userId)
        val sessionsObj = JSONObject()
        inProgressSessions.forEach { (testId, state) ->
            try {
                sessionsObj.put(testId, JSONObject(state))
            } catch (e: Exception) {
                sessionsObj.put(testId, state)
            }
        }
        put("inProgressSessions", sessionsObj)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): ProgressBackupPayload {
            val sessionsObj = json.optJSONObject("inProgressSessions") ?: JSONObject()
            val sessions = mutableMapOf<String, String>()
            val keys = sessionsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                sessions[key] = sessionsObj.get(key).toString()
            }
            return ProgressBackupPayload(
                userId = json.optString("userId", ""),
                inProgressSessions = sessions
            )
        }
    }
}

/**
 * Account-isolated data container for syllabus chapter completion.
 */
data class SyllabusBackupPayload(
    val userId: String,
    val completedChaptersByExam: Map<String, List<String>> // exam -> sorted chapter list
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("userId", userId)
        val examsObj = JSONObject()
        completedChaptersByExam.forEach { (exam, chapters) ->
            examsObj.put(exam, JSONArray(chapters))
        }
        put("completedChaptersByExam", examsObj)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): SyllabusBackupPayload {
            val examsObj = json.optJSONObject("completedChaptersByExam") ?: JSONObject()
            val map = mutableMapOf<String, List<String>>()
            val keys = examsObj.keys()
            while (keys.hasNext()) {
                val exam = keys.next()
                val arr = examsObj.optJSONArray(exam) ?: JSONArray()
                map[exam] = (0 until arr.length()).map { arr.getString(it) }
            }
            return SyllabusBackupPayload(
                userId = json.optString("userId", ""),
                completedChaptersByExam = map
            )
        }
    }
}

/**
 * Safe user profile preferences container.
 * STRICT SECURITY RULE: Passwords, auth tokens, session cookies, and API keys are strictly excluded.
 */
data class ProfileBackupPayload(
    val userId: String,
    val name: String? = null,
    val email: String? = null,
    val targetExam: String = "NEET",
    val classLevel: String = "All",
    val phoneNumber: String? = null,
    val selectedLanguage: String = "HINDI",
    val testFolders: String? = null,
    val testFolderMappings: String? = null,
    val profileImageUri: String? = null,
    val hasAvatar: Boolean = false
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("userId", userId)
        put("name", name ?: JSONObject.NULL)
        put("email", email ?: JSONObject.NULL)
        put("targetExam", targetExam)
        put("classLevel", classLevel)
        put("phoneNumber", phoneNumber ?: JSONObject.NULL)
        put("selectedLanguage", selectedLanguage)
        put("testFolders", testFolders ?: JSONObject.NULL)
        put("testFolderMappings", testFolderMappings ?: JSONObject.NULL)
        put("profileImageUri", profileImageUri ?: JSONObject.NULL)
        put("hasAvatar", hasAvatar)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): ProfileBackupPayload {
            return ProfileBackupPayload(
                userId = json.optString("userId", ""),
                name = if (json.has("name") && !json.isNull("name")) json.getString("name") else null,
                email = if (json.has("email") && !json.isNull("email")) json.getString("email") else null,
                targetExam = json.optString("targetExam", "NEET"),
                classLevel = json.optString("classLevel", "All"),
                phoneNumber = if (json.has("phoneNumber") && !json.isNull("phoneNumber")) json.getString("phoneNumber") else null,
                selectedLanguage = json.optString("selectedLanguage", "HINDI"),
                testFolders = if (json.has("testFolders") && !json.isNull("testFolders")) json.getString("testFolders") else null,
                testFolderMappings = if (json.has("testFolderMappings") && !json.isNull("testFolderMappings")) json.getString("testFolderMappings") else null,
                profileImageUri = if (json.has("profileImageUri") && !json.isNull("profileImageUri")) json.getString("profileImageUri") else null,
                hasAvatar = json.optBoolean("hasAvatar", false)
            )
        }
    }
}

/**
 * Read-only backup adapter connecting live application data to the Phase 4A backup archive engine.
 *
 * Guarantees:
 * - Read-only: Never mutates, deletes, or inserts data into Room, SharedPreferences, or filesDir.
 * - Account-isolated: Only queries and exports data owned by the authenticated UID.
 * - Controlled failure: Returns Result.failure on unauthenticated or blank UID without creating invalid archives.
 * - Integrity: All data categories are validated and sealed with SHA-256 in backup_manifest.json.
 * - Zero AI / Gemini calls.
 */
object AccountDataBackupAdapter {

    // --- Entity Serializers (100% field preservation) ---

    fun testEntityToJson(entity: TestEntity): JSONObject {
        return JSONObject().apply {
            put("id", entity.id)
            put("title", entity.title)
            put("description", entity.description)
            put("institute", entity.institute)
            put("batchName", entity.batchName)
            put("facultyName", entity.facultyName)
            put("language", entity.language)
            put("subject", entity.subject)
            put("difficulty", entity.difficulty)
            put("testType", entity.testType)
            put("questionsCount", entity.questionsCount)
            put("isOfficial", entity.isOfficial)
            put("isVerifiedCommunity", entity.isVerifiedCommunity)
            put("isPublic", entity.isPublic)
            put("countsTowardRanking", entity.countsTowardRanking)
            put("rating", entity.rating.toDouble())
            put("rating1Count", entity.rating1Count)
            put("rating2Count", entity.rating2Count)
            put("rating3Count", entity.rating3Count)
            put("rating4Count", entity.rating4Count)
            put("rating5Count", entity.rating5Count)
            put("attemptsCount", entity.attemptsCount)
            put("downloadsCount", entity.downloadsCount)
            put("isDownloaded", entity.isDownloaded)
            put("isBookmarked", entity.isBookmarked)
            put("timestamp", entity.timestamp)
            put("status", entity.status)
            put("aiConfidence", entity.aiConfidence)
            put("uploaderId", entity.uploaderId)
            put("targetExam", entity.targetExam)
            put("durationMinutes", entity.durationMinutes)
            put("classLevel", entity.classLevel)
            put("chapter", entity.chapter)
            put("topic", entity.topic)
            put("imageUrl", entity.imageUrl ?: JSONObject.NULL)
            put("questionsJson", entity.questionsJson ?: JSONObject.NULL)
            put("examId", entity.examId ?: JSONObject.NULL)
            put("stageId", entity.stageId ?: JSONObject.NULL)
            put("paperId", entity.paperId ?: JSONObject.NULL)
            put("scoringVersion", entity.scoringVersion ?: JSONObject.NULL)
        }
    }

    fun jsonToTestEntity(json: JSONObject): TestEntity {
        return TestEntity(
            id = json.getString("id"),
            title = json.optString("title", ""),
            description = json.optString("description", ""),
            institute = json.optString("institute", ""),
            batchName = json.optString("batchName", ""),
            facultyName = json.optString("facultyName", ""),
            language = json.optString("language", "English"),
            subject = json.optString("subject", "General"),
            difficulty = json.optString("difficulty", "Medium"),
            testType = json.optString("testType", "Full Syllabus"),
            questionsCount = json.optInt("questionsCount", 0),
            isOfficial = json.optBoolean("isOfficial", false),
            isVerifiedCommunity = json.optBoolean("isVerifiedCommunity", false),
            isPublic = json.optBoolean("isPublic", false),
            countsTowardRanking = json.optBoolean("countsTowardRanking", false),
            rating = json.optDouble("rating", 5.0).toFloat(),
            rating1Count = json.optInt("rating1Count", 0),
            rating2Count = json.optInt("rating2Count", 0),
            rating3Count = json.optInt("rating3Count", 0),
            rating4Count = json.optInt("rating4Count", 0),
            rating5Count = json.optInt("rating5Count", 0),
            attemptsCount = json.optInt("attemptsCount", 0),
            downloadsCount = json.optInt("downloadsCount", 0),
            isDownloaded = json.optBoolean("isDownloaded", false),
            isBookmarked = json.optBoolean("isBookmarked", false),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            status = json.optString("status", "Published"),
            aiConfidence = json.optInt("aiConfidence", 100),
            uploaderId = json.optString("uploaderId", ""),
            targetExam = json.optString("targetExam", "NEET"),
            durationMinutes = json.optInt("durationMinutes", 0),
            classLevel = json.optString("classLevel", "All"),
            chapter = json.optString("chapter", "All"),
            topic = json.optString("topic", "All"),
            imageUrl = if (json.has("imageUrl") && !json.isNull("imageUrl")) json.getString("imageUrl") else null,
            questionsJson = if (json.has("questionsJson") && !json.isNull("questionsJson")) json.getString("questionsJson") else null,
            examId = if (json.has("examId") && !json.isNull("examId")) json.getString("examId") else null,
            stageId = if (json.has("stageId") && !json.isNull("stageId")) json.getString("stageId") else null,
            paperId = if (json.has("paperId") && !json.isNull("paperId")) json.getString("paperId") else null,
            scoringVersion = if (json.has("scoringVersion") && !json.isNull("scoringVersion")) json.getInt("scoringVersion") else null
        )
    }

    fun questionEntityToJson(entity: QuestionEntity): JSONObject {
        return JSONObject().apply {
            put("id", entity.id)
            put("testId", entity.testId)
            put("questionText", entity.questionText)
            put("optionA", entity.optionA)
            put("optionB", entity.optionB)
            put("optionC", entity.optionC)
            put("optionD", entity.optionD)
            put("correctAnswer", entity.correctAnswer)
            put("explanation", entity.explanation)
            put("subject", entity.subject)
            put("chapter", entity.chapter)
            put("topic", entity.topic)
            put("difficulty", entity.difficulty)
            put("isBookmarked", entity.isBookmarked)
            put("questionTextHi", entity.questionTextHi ?: JSONObject.NULL)
            put("optionAHi", entity.optionAHi ?: JSONObject.NULL)
            put("optionBHi", entity.optionBHi ?: JSONObject.NULL)
            put("optionCHi", entity.optionCHi ?: JSONObject.NULL)
            put("optionDHi", entity.optionDHi ?: JSONObject.NULL)
            put("explanationHi", entity.explanationHi ?: JSONObject.NULL)
            put("questionType", entity.questionType)
            put("section", entity.section)
            put("needsImage", entity.needsImage)
            put("imageUrl", entity.imageUrl ?: JSONObject.NULL)
            put("optionE", entity.optionE ?: JSONObject.NULL)
            put("optionEHi", entity.optionEHi ?: JSONObject.NULL)
        }
    }

    fun jsonToQuestionEntity(json: JSONObject): QuestionEntity {
        return QuestionEntity(
            id = json.getString("id"),
            testId = json.getString("testId"),
            questionText = json.optString("questionText", ""),
            optionA = json.optString("optionA", ""),
            optionB = json.optString("optionB", ""),
            optionC = json.optString("optionC", ""),
            optionD = json.optString("optionD", ""),
            correctAnswer = json.optString("correctAnswer", ""),
            explanation = json.optString("explanation", ""),
            subject = json.optString("subject", ""),
            chapter = json.optString("chapter", ""),
            topic = json.optString("topic", ""),
            difficulty = json.optString("difficulty", "Medium"),
            isBookmarked = json.optBoolean("isBookmarked", false),
            questionTextHi = if (json.has("questionTextHi") && !json.isNull("questionTextHi")) json.getString("questionTextHi") else null,
            optionAHi = if (json.has("optionAHi") && !json.isNull("optionAHi")) json.getString("optionAHi") else null,
            optionBHi = if (json.has("optionBHi") && !json.isNull("optionBHi")) json.getString("optionBHi") else null,
            optionCHi = if (json.has("optionCHi") && !json.isNull("optionCHi")) json.getString("optionCHi") else null,
            optionDHi = if (json.has("optionDHi") && !json.isNull("optionDHi")) json.getString("optionDHi") else null,
            explanationHi = if (json.has("explanationHi") && !json.isNull("explanationHi")) json.getString("explanationHi") else null,
            questionType = json.optString("questionType", "MCQ"),
            section = json.optString("section", "SECTION_A"),
            needsImage = json.optBoolean("needsImage", false),
            imageUrl = if (json.has("imageUrl") && !json.isNull("imageUrl")) json.getString("imageUrl") else null,
            optionE = if (json.has("optionE") && !json.isNull("optionE")) json.getString("optionE") else null,
            optionEHi = if (json.has("optionEHi") && !json.isNull("optionEHi")) json.getString("optionEHi") else null
        )
    }

    fun attemptEntityToJson(entity: AttemptEntity): JSONObject {
        return JSONObject().apply {
            put("id", entity.id)
            put("userId", entity.userId)
            put("testId", entity.testId)
            put("testTitle", entity.testTitle)
            put("score", entity.score)
            put("maxScore", entity.maxScore)
            put("percentile", entity.percentile)
            put("accuracy", entity.accuracy)
            put("correctCount", entity.correctCount)
            put("incorrectCount", entity.incorrectCount)
            put("skippedCount", entity.skippedCount)
            put("timeSpentSeconds", entity.timeSpentSeconds)
            put("dateTaken", entity.dateTaken)
            put("answersJson", entity.answersJson)
            put("attemptSessionToken", entity.attemptSessionToken)
            put("verifiedServerSide", entity.verifiedServerSide)
            put("submissionStatus", entity.submissionStatus)
        }
    }

    fun jsonToAttemptEntity(json: JSONObject): AttemptEntity {
        return AttemptEntity(
            id = json.optInt("id", 0),
            userId = json.optString("userId", ""),
            testId = json.getString("testId"),
            testTitle = json.optString("testTitle", ""),
            score = json.optDouble("score", 0.0),
            maxScore = json.optDouble("maxScore", 0.0),
            percentile = json.optDouble("percentile", 0.0),
            accuracy = json.optDouble("accuracy", 0.0),
            correctCount = json.optInt("correctCount", 0),
            incorrectCount = json.optInt("incorrectCount", 0),
            skippedCount = json.optInt("skippedCount", 0),
            timeSpentSeconds = json.optLong("timeSpentSeconds", 0L),
            dateTaken = json.optLong("dateTaken", System.currentTimeMillis()),
            answersJson = json.optString("answersJson", "{}"),
            attemptSessionToken = json.optString("attemptSessionToken", ""),
            verifiedServerSide = json.optBoolean("verifiedServerSide", false),
            submissionStatus = json.optString("submissionStatus", "CONFIRMED")
        )
    }

    // --- Diagram & Asset Collectors ---

    data class DiagramCollectionResult(
        val entries: Map<String, ByteArray>,
        val missingReferencedAssets: List<String> = emptyList()
    )

    /**
     * Safely reads and packages the user's local profile avatar if available.
     * Enforces path containment to prevent path traversal outside filesDir.
     */
    fun collectProfileAvatar(
        context: Context,
        limits: ArchiveLimits = ArchiveLimits()
    ): Result<Pair<String, ByteArray>?> {
        try {
            val profilePrefs = context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
            val uriStr = profilePrefs.getString("profileImageUri", null) ?: return Result.success(null)
            if (uriStr.isBlank() || uriStr.startsWith("http://") || uriStr.startsWith("https://") || uriStr.startsWith("content://")) {
                return Result.success(null)
            }

            val localFile = if (File(uriStr).isAbsolute) File(uriStr) else File(context.filesDir, uriStr)
            if (!localFile.exists() || !localFile.isFile) {
                return Result.success(null) // Not present locally, omit gracefully
            }

            val canonical = localFile.canonicalFile
            val filesDirCanonical = context.filesDir.canonicalFile
            if (!canonical.path.startsWith(filesDirCanonical.path)) {
                return Result.failure(SecurityException("Profile avatar path escapes filesDir boundary: ${localFile.path}"))
            }

            if (localFile.length() > limits.maxSingleEntryBytes) {
                return Result.failure(IllegalStateException("Profile avatar exceeds single-entry limit (${localFile.length()} bytes)"))
            }

            val entryPath = "assets/profile/avatar.jpg"
            return Result.success(Pair(entryPath, localFile.readBytes()))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Safely locates and reads diagram files associated with owned tests.
     * Enforces path containment to prevent path traversal outside filesDir.
     */
    fun collectDiagramAssets(
        context: Context,
        ownedTests: List<TestEntity>,
        questions: List<QuestionEntity>,
        limits: ArchiveLimits = ArchiveLimits()
    ): Result<Map<String, ByteArray>> {
        val diagramEntries = mutableMapOf<String, ByteArray>()
        val missingReferencedAssets = mutableListOf<String>()
        val filesDirCanonical = context.filesDir.canonicalFile

        try {
            for (test in ownedTests) {
                val testDiagramsDir = File(context.filesDir, "private_tests/${test.id}/diagrams")
                if (testDiagramsDir.exists() && testDiagramsDir.isDirectory) {
                    val files = testDiagramsDir.listFiles() ?: emptyArray()
                    for (file in files) {
                        if (file.isFile) {
                            val canonical = file.canonicalFile
                            if (!canonical.path.startsWith(filesDirCanonical.path)) {
                                return Result.failure(
                                    SecurityException("Diagram file path escapes filesDir boundary: ${file.path}")
                                )
                            }
                            if (file.length() > limits.maxSingleEntryBytes) {
                                return Result.failure(
                                    IllegalStateException("Diagram file ${file.name} exceeds max single-entry limit (${file.length()} bytes)")
                                )
                            }
                            val entryPath = "assets/diagrams/${test.id}/${file.name}"
                            diagramEntries[entryPath] = file.readBytes()
                        }
                    }
                }
            }

            // Also check individual question imageUrls if pointing to local files in filesDir
            for (question in questions) {
                val imgUrl = question.imageUrl
                if (!imgUrl.isNullOrBlank() && !imgUrl.startsWith("http://") && !imgUrl.startsWith("https://")) {
                    val localFile = File(imgUrl).let {
                        if (it.isAbsolute) it else File(context.filesDir, imgUrl)
                    }
                    if (localFile.exists() && localFile.isFile) {
                        val canonical = localFile.canonicalFile
                        if (canonical.path.startsWith(filesDirCanonical.path)) {
                            val entryPath = "assets/diagrams/${question.testId}/${canonical.name}"
                            if (!diagramEntries.containsKey(entryPath)) {
                                if (localFile.length() > limits.maxSingleEntryBytes) {
                                    return Result.failure(
                                        IllegalStateException("Question diagram ${localFile.name} exceeds max single-entry limit")
                                    )
                                }
                                diagramEntries[entryPath] = localFile.readBytes()
                            }
                        }
                    } else {
                        missingReferencedAssets.add("Question '${question.id}' in test '${question.testId}' references missing diagram: $imgUrl")
                    }
                }
            }

            return Result.success(diagramEntries)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Locates and reads diagram files, explicitly reporting missing assets in a DiagramCollectionResult.
     */
    fun collectDiagramAssetsWithMissingDetection(
        context: Context,
        ownedTests: List<TestEntity>,
        questions: List<QuestionEntity>,
        limits: ArchiveLimits = ArchiveLimits()
    ): Result<DiagramCollectionResult> {
        val diagramEntries = mutableMapOf<String, ByteArray>()
        val missingReferencedAssets = mutableListOf<String>()
        val filesDirCanonical = context.filesDir.canonicalFile

        try {
            for (test in ownedTests) {
                val testDiagramsDir = File(context.filesDir, "private_tests/${test.id}/diagrams")
                if (testDiagramsDir.exists() && testDiagramsDir.isDirectory) {
                    val files = testDiagramsDir.listFiles() ?: emptyArray()
                    for (file in files) {
                        if (file.isFile) {
                            val canonical = file.canonicalFile
                            if (!canonical.path.startsWith(filesDirCanonical.path)) {
                                return Result.failure(
                                    SecurityException("Diagram file path escapes filesDir boundary: ${file.path}")
                                )
                            }
                            if (file.length() > limits.maxSingleEntryBytes) {
                                return Result.failure(
                                    IllegalStateException("Diagram file ${file.name} exceeds max single-entry limit (${file.length()} bytes)")
                                )
                            }
                            val entryPath = "assets/diagrams/${test.id}/${file.name}"
                            diagramEntries[entryPath] = file.readBytes()
                        }
                    }
                }
            }

            for (question in questions) {
                val imgUrl = question.imageUrl
                if (!imgUrl.isNullOrBlank() && !imgUrl.startsWith("http://") && !imgUrl.startsWith("https://") && !imgUrl.startsWith("content://")) {
                    val localFile = File(imgUrl).let {
                        if (it.isAbsolute) it else File(context.filesDir, imgUrl)
                    }
                    if (localFile.exists() && localFile.isFile) {
                        val canonical = localFile.canonicalFile
                        if (!canonical.path.startsWith(filesDirCanonical.path)) {
                            return Result.failure(
                                SecurityException("Question diagram path escapes filesDir: ${localFile.path}")
                            )
                        }
                        val entryPath = "assets/diagrams/${question.testId}/${canonical.name}"
                        if (!diagramEntries.containsKey(entryPath)) {
                            if (localFile.length() > limits.maxSingleEntryBytes) {
                                return Result.failure(
                                    IllegalStateException("Question diagram ${localFile.name} exceeds max single-entry limit")
                                )
                            }
                            diagramEntries[entryPath] = localFile.readBytes()
                        }
                    } else {
                        missingReferencedAssets.add("Question '${question.id}' in test '${question.testId}' references missing diagram: $imgUrl")
                    }
                }
            }

            return Result.success(DiagramCollectionResult(diagramEntries, missingReferencedAssets))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // --- Main Export Orchestrator ---

    /**
     * Reads and packages an authenticated user's private data into a verifiable .aktsbak archive.
     *
     * @param context Application context for accessing SharedPreferences and filesDir.
     * @param db Room database instance.
     * @param uid Authenticated account owner UID (must not be blank).
     * @param email Optional user email for manifest metadata.
     * @param displayName Optional user display name for manifest metadata.
     * @param appVersionCode Target application version code.
     * @param appVersionName Target application version name.
     * @param outputStream Destination stream for the `.aktsbak` ZIP archive.
     * @param limits Configurable safety thresholds.
     * @return Result containing finalized BackupManifest with SHA-256 checksums and statistics.
     */
    suspend fun exportAccountBackup(
        context: Context,
        db: AppDatabase,
        uid: String,
        email: String? = null,
        displayName: String? = null,
        appVersionCode: Int = 1,
        appVersionName: String = "1.0",
        outputStream: OutputStream,
        limits: ArchiveLimits = ArchiveLimits(),
        strictAssetIntegrity: Boolean = true
    ): Result<BackupManifest> {
        val safeUid = uid.trim()
        if (safeUid.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Authenticated UID is required for account backup. Unauthenticated or blank UID cannot create an account backup.")
            )
        }

        try {
            // 1. Fetch owned tests (strictly private / user-authored vault tests)
            val allTests = db.testDao().getAllTestsList()
            val ownedTests = allTests.filter { test ->
                test.uploaderId == safeUid && (!test.isPublic || test.institute.contains("Private", ignoreCase = true))
            }.sortedBy { it.id }

            // 2. Fetch questions for owned tests from canonical Room source
            val allQuestions = mutableListOf<QuestionEntity>()
            for (test in ownedTests) {
                val questionsForTest = db.questionDao().getQuestionsForTestList(test.id)
                allQuestions.addAll(questionsForTest)
            }
            allQuestions.sortBy { it.id }

            // 3. Fetch attempts authored by this user
            val attempts = db.attemptDao().getAttemptsForUser(safeUid).first().sortedBy { it.id }

            // 4. Extract bookmarks
            val bookmarksPrefs = context.getSharedPreferences("user_bookmarks_store", Context.MODE_PRIVATE)
            val testBookmarkSet = bookmarksPrefs.getStringSet("bookmarks_$safeUid", emptySet()) ?: emptySet()
            val additionalTestBookmarks = ownedTests.filter { it.isBookmarked }.map { it.id }.toSet()
            val finalTestBookmarks = (testBookmarkSet + additionalTestBookmarks).sorted()
            val questionBookmarks = allQuestions.filter { it.isBookmarked }.map { it.id }.sorted()
            val bookmarksPayload = BookmarksBackupPayload(
                userId = safeUid,
                testBookmarks = finalTestBookmarks,
                questionBookmarks = questionBookmarks
            )

            // 5. Extract in-progress test sessions
            val progressPrefs = context.getSharedPreferences("test_progress", Context.MODE_PRIVATE)
            val inProgressSessions = mutableMapOf<String, String>()
            val progressPrefix = "progress_${safeUid}_"
            for ((key, value) in progressPrefs.all) {
                if (key.startsWith(progressPrefix) && value is String) {
                    val testId = key.removePrefix(progressPrefix)
                    inProgressSessions[testId] = value
                }
            }
            val progressPayload = ProgressBackupPayload(
                userId = safeUid,
                inProgressSessions = inProgressSessions
            )

            // 6. Extract syllabus completion progress
            val syllabusPrefs = context.getSharedPreferences("SyllabusProgress", Context.MODE_PRIVATE)
            val syllabusPrefix = "completed_chapters_${safeUid}_"
            val completedChaptersByExam = mutableMapOf<String, List<String>>()
            for ((key, value) in syllabusPrefs.all) {
                if (key.startsWith(syllabusPrefix) && value is Set<*>) {
                    val exam = key.removePrefix(syllabusPrefix)
                    @Suppress("UNCHECKED_CAST")
                    val chapters = (value as Set<String>).toList().sorted()
                    completedChaptersByExam[exam] = chapters
                }
            }
            val syllabusPayload = SyllabusBackupPayload(
                userId = safeUid,
                completedChaptersByExam = completedChaptersByExam
            )

            // 7. Collect diagram assets safely with missing referenced asset detection
            val diagramsResult = collectDiagramAssetsWithMissingDetection(context, ownedTests, allQuestions, limits)
            if (diagramsResult.isFailure) {
                return Result.failure(diagramsResult.exceptionOrNull()!!)
            }
            val diagramCollection = diagramsResult.getOrThrow()
            val diagramEntries = diagramCollection.entries
            val missingAssets = diagramCollection.missingReferencedAssets

            if (missingAssets.isNotEmpty() && strictAssetIntegrity) {
                return Result.failure(
                    IllegalStateException(
                        "Missing ${missingAssets.size} referenced diagram asset(s): ${missingAssets.joinToString("; ")}. A complete user data backup cannot be produced when referenced assets are missing."
                    )
                )
            }

            // 8. Collect profile avatar if locally available
            val avatarResult = collectProfileAvatar(context, limits)
            if (avatarResult.isFailure) {
                return Result.failure(avatarResult.exceptionOrNull()!!)
            }
            val avatarPair = avatarResult.getOrThrow()

            // 9. Extract safe profile metadata (non-sensitive only)
            val profilePrefs = context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)
            val profilePayload = ProfileBackupPayload(
                userId = safeUid,
                name = profilePrefs.getString("name", null) ?: displayName,
                email = profilePrefs.getString("email", null) ?: email,
                targetExam = profilePrefs.getString("target_exam", "NEET") ?: "NEET",
                classLevel = profilePrefs.getString("class_level", "All") ?: "All",
                phoneNumber = profilePrefs.getString("phoneNumber", null),
                selectedLanguage = profilePrefs.getString("selected_language", "HINDI") ?: "HINDI",
                testFolders = profilePrefs.getString("test_folders", null),
                testFolderMappings = profilePrefs.getString("test_folder_mappings", null),
                profileImageUri = if (avatarPair != null) "assets/profile/avatar.jpg" else profilePrefs.getString("profileImageUri", null),
                hasAvatar = avatarPair != null
            )

            // 10. Serialize JSON payloads into deterministic byte arrays
            val testsJsonArray = JSONArray()
            ownedTests.forEach { testsJsonArray.put(testEntityToJson(it)) }

            val questionsJsonArray = JSONArray()
            allQuestions.forEach { questionsJsonArray.put(questionEntityToJson(it)) }

            val attemptsJsonArray = JSONArray()
            attempts.forEach { attemptsJsonArray.put(attemptEntityToJson(it)) }

            val entries = mutableMapOf<String, ByteArray>()
            entries["data/tests.json"] = testsJsonArray.toString(2).toByteArray(Charsets.UTF_8)
            entries["data/questions.json"] = questionsJsonArray.toString(2).toByteArray(Charsets.UTF_8)
            entries["data/attempts.json"] = attemptsJsonArray.toString(2).toByteArray(Charsets.UTF_8)
            entries["data/bookmarks.json"] = bookmarksPayload.toJsonObject().toString(2).toByteArray(Charsets.UTF_8)
            entries["data/progress.json"] = progressPayload.toJsonObject().toString(2).toByteArray(Charsets.UTF_8)
            entries["data/syllabus.json"] = syllabusPayload.toJsonObject().toString(2).toByteArray(Charsets.UTF_8)
            entries["data/profile.json"] = profilePayload.toJsonObject().toString(2).toByteArray(Charsets.UTF_8)

            // Include collected diagram entries
            diagramEntries.forEach { (path, bytes) ->
                entries[path] = bytes
            }

            // Include avatar entry if collected
            if (avatarPair != null) {
                entries[avatarPair.first] = avatarPair.second
            }

            // 11. Determine completeness status
            val completenessStatus = if (missingAssets.isEmpty()) {
                BackupCompleteness.COMPLETE_USER_DATA
            } else {
                BackupCompleteness.STRUCTURAL_ONLY
            }

            // 12. Construct account manifest
            val manifestDraft = BackupArchiveEngine.createAccountOwnedManifest(
                uid = safeUid,
                email = profilePayload.email,
                displayName = profilePayload.name,
                appVersionCode = appVersionCode,
                appVersionName = appVersionName,
                completeness = completenessStatus
            ).getOrThrow()

            val stats = BackupStats(
                privateTestsCount = ownedTests.size,
                questionsCount = allQuestions.size,
                attemptsCount = attempts.size,
                inProgressCount = inProgressSessions.size,
                bookmarkedCount = finalTestBookmarks.size + questionBookmarks.size,
                diagramsCount = diagramEntries.size
            )

            val manifestWithStats = manifestDraft.copy(stats = stats)

            // 13. Stream build archive through bounded engine
            return BackupArchiveEngine.buildArchive(outputStream, manifestWithStats, entries, limits)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
