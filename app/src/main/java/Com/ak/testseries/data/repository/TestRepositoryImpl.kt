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
            // 1. Observe real-time data from the remote source (Firebase)
            remoteDataSource.observeTests()
                .catch { e -> 
                    // Handle offline or errors. Firestore uses local cache automatically.
                }
                .collect { remoteTests ->
                    // 2. Insert into the local database
                    localDataSource.insertTests(remoteTests)
                    
                    // 3. Sync questions for each test in real-time
                    for (test in remoteTests) {
                        val qJson = test.questionsJson
                        if (!qJson.isNullOrEmpty()) {
                            try {
                                val jsonArray = org.json.JSONArray(qJson)
                                val questions = mutableListOf<Com.ak.testseries.data.QuestionEntity>()
                                for (i in 0 until jsonArray.length()) {
                                    val qObj = jsonArray.getJSONObject(i)
                                    questions.add(
                                        Com.ak.testseries.data.QuestionEntity(
                                            id = qObj.optString("id", "${test.id}_q${i}"),
                                            testId = test.id,
                                            questionText = qObj.optString("questionText", ""),
                                            optionA = qObj.optString("optionA", ""),
                                            optionB = qObj.optString("optionB", ""),
                                            optionC = qObj.optString("optionC", ""),
                                            optionD = qObj.optString("optionD", ""),
                                            correctAnswer = qObj.optString("correctAnswer", ""),
                                            explanation = qObj.optString("explanation", ""),
                                            subject = qObj.optString("subject", test.subject),
                                            chapter = qObj.optString("chapter", test.chapter),
                                            topic = qObj.optString("topic", test.topic),
                                            difficulty = qObj.optString("difficulty", "Medium"),
                                            isBookmarked = qObj.optBoolean("isBookmarked", false),
                                            questionTextHi = qObj.optString("questionTextHi", "").takeIf { it.isNotEmpty() },
                                            optionAHi = qObj.optString("optionAHi", "").takeIf { it.isNotEmpty() },
                                            optionBHi = qObj.optString("optionBHi", "").takeIf { it.isNotEmpty() },
                                            optionCHi = qObj.optString("optionCHi", "").takeIf { it.isNotEmpty() },
                                            optionDHi = qObj.optString("optionDHi", "").takeIf { it.isNotEmpty() },
                                            explanationHi = qObj.optString("explanationHi", "").takeIf { it.isNotEmpty() },
                                            questionType = qObj.optString("questionType", "MCQ"),
                                            section = qObj.optString("section", "SECTION_A"),
                                            needsImage = qObj.optBoolean("needsImage", false),
                                            imageUrl = qObj.optString("imageUrl", "").takeIf { it.isNotEmpty() }
                                        )
                                    )
                                }
                                if (questions.isNotEmpty()) {
                                    localDataSource.insertQuestions(questions)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            // Handle exceptions appropriately
        }
    }

    override suspend fun getUserProfile(userId: String): UserProfile {
        return remoteDataSource.getUserProfile(userId)
    }

    override suspend fun getAppConfig(): AppConfig {
        return remoteDataSource.getAppConfig()
    }
}
