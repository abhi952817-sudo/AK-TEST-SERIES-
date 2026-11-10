package Com.ak.testseries.data.remote

import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.domain.source.AppConfig
import Com.ak.testseries.domain.source.RemoteDataSource
import Com.ak.testseries.domain.source.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRemoteDataSourceImpl : RemoteDataSource {

    private fun hasVerifiedQuestionPayload(rawJson: String?, expectedCount: Int): Boolean {
        if (rawJson.isNullOrBlank() || expectedCount <= 0) return false
        val questions = try {
            org.json.JSONArray(rawJson)
        } catch (_: Exception) {
            return false
        }
        if (questions.length() != expectedCount) return false
        val seenStems = mutableSetOf<String>()

        for (index in 0 until questions.length()) {
            val question = questions.optJSONObject(index) ?: return false
            val text = question.optString("questionText").ifBlank { question.optString("text") }.trim()
            val type = question.optString("questionType").uppercase()
            val section = question.optString("sectionId").ifBlank { question.optString("section") }.trim()
            val model = question.optString("generationModel")
            val sourceVerified = question.optBoolean("sourceVerified", false)
            if (text.length < 10 || type.isBlank() || section.isBlank()) return false
            if (!model.contains("gemini", ignoreCase = true) || !sourceVerified) return false
            val normalizedStem = text.lowercase().replace(Regex("\\s+"), " ").trim()
            if (!seenStems.add(normalizedStem)) return false

            val numeric = type.contains("NUMERICAL") || type == "NAT" || type == "TITA"
            val optionsArray = question.optJSONArray("options")
            val options = if (optionsArray != null) {
                (0 until optionsArray.length()).map { optionsArray.optString(it).trim() }
            } else {
                listOf("A", "B", "C", "D", "E").map { letter ->
                    question.optString("option" + letter)
                        .ifBlank { question.optString("option" + letter + "En") }
                        .trim()
                }.filter { it.isNotBlank() }
            }
            if (numeric) {
                if (options.isNotEmpty()) return false
            } else {
                if (options.size !in 4..5 || options.any { it.isBlank() }) return false
                if (options.map { it.lowercase() }.distinct().size != options.size) return false
                if (options.any { it.matches(Regex("(option|choice|distractor option)\\s+[A-E]", RegexOption.IGNORE_CASE)) }) return false
            }
        }
        return true
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirestoreHelper.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    override fun observeTests(): Flow<List<TestEntity>> = callbackFlow {
        if (firestore == null) {
            close()
            return@callbackFlow
        }
        
        val testsList = mutableListOf<TestEntity>()
        
        // Canonical listener for Tests_Data collection
        val testsListener = firestore!!.collection("Tests_Data").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val newTests = snapshot.documents.mapNotNull { doc ->
                    try {
                        val questionsCount = doc.getLong("questionsCount")?.toInt() ?: 0
                        val questionsJson = doc.getString("questionsJson")
                        if (!hasVerifiedQuestionPayload(questionsJson, questionsCount)) return@mapNotNull null
                        val title = doc.getString("title").orEmpty()
                        val subject = doc.getString("subject").orEmpty()
                        val targetExam = doc.getString("targetExam") ?: doc.getString("examName").orEmpty()
                        if (title.isBlank() || subject.isBlank() || targetExam.isBlank()) return@mapNotNull null
                        val isOfficialVal = doc.getBoolean("isOfficial") ?: false
                        val statusVal = doc.getString("status") ?: if (isOfficialVal) "Published" else "Draft"
                        val isPublicVal = doc.getBoolean("isPublic") ?: (isOfficialVal || statusVal == "Published")
                        TestEntity(
                            id = doc.id,
                            title = title,
                            description = doc.getString("description") ?: "",
                            institute = doc.getString("institute") ?: "Unknown Institute",
                            batchName = doc.getString("batchName") ?: "Unknown Batch",
                            facultyName = doc.getString("facultyName") ?: "Unknown Faculty",
                            language = doc.getString("language") ?: "English",
                            subject = subject,
                            difficulty = doc.getString("difficulty") ?: "Medium",
                            testType = doc.getString("testType") ?: "Mock Test",
                            questionsCount = questionsCount,
                            isOfficial = isOfficialVal,
                            isVerifiedCommunity = doc.getBoolean("isVerifiedCommunity") ?: false,
                            isPublic = isPublicVal,
                            status = statusVal,
                            uploaderId = doc.getString("uploaderId") ?: doc.getString("creatorId") ?: "",
                            countsTowardRanking = doc.getBoolean("countsTowardRanking") ?: false,
                            isPublic = doc.getBoolean("isPublic") ?: false,
                            status = publicationStatus,
                            uploaderId = doc.getString("uploaderId").orEmpty(),
                            targetExam = targetExam,
                            durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0,
                            classLevel = doc.getString("classLevel").orEmpty(),
                            chapter = doc.getString("chapter") ?: "",
                            topic = doc.getString("topic") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            imageUrl = doc.getString("imageUrl"),
                            questionsJson = questionsJson,
                            examId = doc.getString("examId"),
                            stageId = doc.getString("stageId"),
                            paperId = doc.getString("paperId"),
                            scoringVersion = doc.getLong("scoringVersion")?.toInt()
                        )
                    } catch (e: Exception) { null }
                }
                
                // Merge and emit
                testsList.removeAll { it.testType != "Live" }
                testsList.addAll(newTests)
                trySend(testsList.toList())
            }
        }
        
        // Listener for LiveTests collection
        val liveTestsListener = firestore!!.collection("LiveTests").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val newLiveTests = snapshot.documents.mapNotNull { doc ->
                    try {
                        val processingStatus = doc.getString("processingStatus").orEmpty()
                        val questionsCount = doc.getLong("questionsCount")?.toInt() ?: 0
                        val questionsJson = doc.getString("questionsJson")
                        val publicationStatus = doc.getString("status").orEmpty()
                        if (processingStatus != "Ready" || publicationStatus != "Published" ||
                            !hasVerifiedQuestionPayload(questionsJson, questionsCount)
                        ) return@mapNotNull null
                        val title = doc.getString("title").orEmpty()
                        val subject = doc.getString("subject").orEmpty()
                        val targetExam = doc.getString("targetExam").orEmpty()
                        if (title.isBlank() || subject.isBlank() || targetExam.isBlank()) return@mapNotNull null
                        TestEntity(
                            id = doc.id,
                            title = title,
                            description = "Live Scheduled Test on " + (doc.getString("scheduledTime") ?: ""),
                            institute = "Admin Live",
                            batchName = doc.getString("classLevel").orEmpty(),
                            facultyName = "Admin",
                            language = "English",
                            subject = subject,
                            difficulty = "Medium",
                            testType = "Live",
                            questionsCount = questionsCount,
                            isOfficial = doc.getBoolean("isOfficial") ?: false,
                            isVerifiedCommunity = doc.getBoolean("isVerifiedCommunity") ?: false,
                            countsTowardRanking = doc.getBoolean("countsTowardRanking") ?: false,
                            classLevel = doc.getString("classLevel") ?: "11th",
                            chapter = doc.getString("chapter") ?: "",
                            topic = "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            imageUrl = null,
                            questionsJson = questionsJson,
                            targetExam = targetExam,
                            examId = doc.getString("examId"),
                            stageId = doc.getString("stageId"),
                            paperId = doc.getString("paperId"),
                            scoringVersion = doc.getLong("scoringVersion")?.toInt()
                        )
                    } catch (e: Exception) { null }
                }
                
                // Merge and emit
                testsList.removeAll { it.testType == "Live" }
                testsList.addAll(newLiveTests)
                trySend(testsList.toList())
            }
        }
        
        awaitClose {
            testsListener.remove()
            liveTestsListener.remove()
        }
    }

    override suspend fun getUserProfile(userId: String): UserProfile {
        return try {
            if (firestore == null) {
                return UserProfile(id = userId, name = "Student Profile", email = "")
            }
            val snapshot = firestore!!.collection("Users_Data").document(userId).get().await()
            if (snapshot.exists()) {
                UserProfile(
                    id = snapshot.id,
                    name = snapshot.getString("name") ?: "Student Profile",
                    email = snapshot.getString("email") ?: "",
                    isPremium = snapshot.getBoolean("isPremium") ?: false
                )
            } else {
                UserProfile(id = userId, name = "Student Profile", email = "")
            }
        } catch (e: Exception) {
            UserProfile(id = userId, name = "Student Profile", email = "")
        }
    }

    override suspend fun getAppConfig(): AppConfig {
        return try {
            if (firestore == null) {
                return AppConfig(latestVersion = "1.0.0", maintenanceMode = false)
            }
            val snapshot = firestore!!.collection("Config").document("app_config").get().await()
            if (snapshot.exists()) {
                AppConfig(
                    latestVersion = snapshot.getString("latestVersion") ?: "1.0.0",
                    maintenanceMode = snapshot.getBoolean("maintenanceMode") ?: false,
                    forceUpdate = snapshot.getBoolean("forceUpdate") ?: false
                )
            } else {
                AppConfig(latestVersion = "1.0.0", maintenanceMode = false)
            }
        } catch (e: Exception) {
            AppConfig(latestVersion = "1.0.0", maintenanceMode = false)
        }
    }
}
