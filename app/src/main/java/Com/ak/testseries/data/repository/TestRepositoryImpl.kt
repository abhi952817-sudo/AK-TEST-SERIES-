package Com.ak.testseries.data.repository

import Com.ak.testseries.data.DatabaseRepository
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.data.analytics.AnalyticsManager
import Com.ak.testseries.data.remote.FirebaseRemoteDataSourceImpl
import Com.ak.testseries.domain.repository.TestRepository
import Com.ak.testseries.domain.source.AppConfig
import Com.ak.testseries.domain.source.RemoteDataSource
import Com.ak.testseries.domain.source.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.catch

class TestRepositoryImpl(
    private val localDataSource: DatabaseRepository,
    private val remoteDataSource: RemoteDataSource = FirebaseRemoteDataSourceImpl(),
    private val analyticsManager: AnalyticsManager = AnalyticsManager()
) : TestRepository {

    override fun getAllTests(): Flow<List<TestEntity>> {
        // The local DB acts as the Single Source of Truth
        return localDataSource.allTests
    }

    override suspend fun syncTests() {
        try {
            remoteDataSource.observeTests()
                .catch { error ->
                    android.util.Log.e("TestRepositoryImpl", "Live verified-test sync failed.", error)
                    throw error
                }
                .collect { remoteTests ->
                    persistRemoteTests(remoteTests)
                }
        } catch (error: Exception) {
            android.util.Log.e("TestRepositoryImpl", "Unable to keep verified tests synchronized.", error)
        }
    }

    /**
     * Performs a one-shot server/cache snapshot refresh for an explicit user action.
     * The live sync flow above remains active independently.
     */
    suspend fun refreshTestsOnce(): Int {
        val remoteTests = remoteDataSource.observeTests().firstOrNull().orEmpty()
        persistRemoteTests(remoteTests)
        return remoteTests.size
    }

    private suspend fun persistRemoteTests(remoteTests: List<TestEntity>) {
        // The local database remains the single source of truth for the UI.
        localDataSource.insertTests(remoteTests)

        for (test in remoteTests) {
            val rawJson = test.questionsJson?.takeIf { it.isNotBlank() } ?: continue
            try {
                val jsonArray = org.json.JSONArray(rawJson)
                val questions = mutableListOf<Com.ak.testseries.data.QuestionEntity>()
                for (index in 0 until jsonArray.length()) {
                    val question = jsonArray.optJSONObject(index) ?: continue
                    val options = question.optJSONArray("options")
                    fun option(letter: String, optionIndex: Int): String =
                        question.optString("option" + letter)
                            .ifBlank { question.optString("option" + letter + "En") }
                            .ifBlank { options?.optString(optionIndex).orEmpty() }

                    questions.add(
                        Com.ak.testseries.data.QuestionEntity(
                            id = question.optString("id").ifBlank { "${test.id}_q$index" },
                            testId = test.id,
                            questionText = question.optString("questionText")
                                .ifBlank { question.optString("questionTextEn") }
                                .ifBlank { question.optString("text") },
                            optionA = option("A", 0),
                            optionB = option("B", 1),
                            optionC = option("C", 2),
                            optionD = option("D", 3),
                            optionE = option("E", 4).takeIf { it.isNotBlank() },
                            correctAnswer = question.optString("correctAnswer", ""),
                            explanation = question.optString("explanation")
                                .ifBlank { question.optString("explanationEn") },
                            subject = question.optString("subject").ifBlank { test.subject },
                            chapter = question.optString("chapter").ifBlank { test.chapter },
                            topic = question.optString("topic").ifBlank { test.topic },
                            difficulty = question.optString("difficulty", "Medium"),
                            isBookmarked = question.optBoolean("isBookmarked", false),
                            questionTextHi = question.optString("questionTextHi").takeIf { it.isNotBlank() },
                            optionAHi = question.optString("optionAHi").takeIf { it.isNotBlank() },
                            optionBHi = question.optString("optionBHi").takeIf { it.isNotBlank() },
                            optionCHi = question.optString("optionCHi").takeIf { it.isNotBlank() },
                            optionDHi = question.optString("optionDHi").takeIf { it.isNotBlank() },
                            optionEHi = question.optString("optionEHi").takeIf { it.isNotBlank() },
                            explanationHi = question.optString("explanationHi").takeIf { it.isNotBlank() },
                            questionType = question.optString("questionType"),
                            section = question.optString("sectionId").ifBlank { question.optString("section") },
                            needsImage = question.optBoolean("needsImage", false),
                            imageUrl = question.optString("imageUrl").takeIf { it.isNotBlank() }
                        )
                    )
                }
                if (questions.isNotEmpty()) {
                    localDataSource.insertQuestions(questions)
                }
            } catch (error: Exception) {
                android.util.Log.e(
                    "TestRepositoryImpl",
                    "Failed to persist question payload for verified test ${test.id}.",
                    error
                )
            }
        }
    }

    override suspend fun getUserProfile(userId: String): UserProfile {
        return remoteDataSource.getUserProfile(userId)
    }

    override suspend fun getAppConfig(): AppConfig {
        return remoteDataSource.getAppConfig()
    }
}
