package Com.ak.testseries.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class DatabaseRepository(private val db: AppDatabase) {

    val allTests: Flow<List<TestEntity>> = db.testDao().getAllTests()
    val allAttempts: Flow<List<AttemptEntity>> = db.attemptDao().getAllAttempts()

    fun getAttemptsForUser(userId: String): Flow<List<AttemptEntity>> {
        return db.attemptDao().getAttemptsForUser(userId)
    }
    val syllabus: Flow<List<SyllabusEntity>> = db.syllabusDao().getSyllabus()
    val recentSearches: Flow<List<RecentSearchEntity>> = db.recentSearchDao().getRecentSearches()

    suspend fun insertRecentSearch(query: String) {
        if (query.isNotBlank()) {
            db.recentSearchDao().insertRecentSearch(RecentSearchEntity(query.trim()))
        }
    }

    suspend fun deleteRecentSearch(query: String) {
        db.recentSearchDao().deleteRecentSearch(query)
    }

    suspend fun clearAllRecentSearches() {
        db.recentSearchDao().clearAllRecentSearches()
    }

    fun searchTestsDeep(
        query: String,
        classLevel: String,
        batchName: String,
        subject: String,
        chapter: String,
        topic: String,
        difficulty: String,
        testType: String
    ): Flow<List<TestEntity>> {
        return db.testDao().searchTestsDeep(query, classLevel, batchName, subject, chapter, topic, difficulty, testType)
    }

    suspend fun getTestById(id: String): TestEntity? {
        return db.testDao().getTestById(id)
    }

    suspend fun insertTest(test: TestEntity) {
        val existing = db.testDao().getTestById(test.id)
        if (existing != null) {
            db.testDao().insertTest(
                test.copy(
                    isBookmarked = existing.isBookmarked || test.isBookmarked,
                    isDownloaded = existing.isDownloaded || test.isDownloaded,
                    downloadsCount = maxOf(test.downloadsCount, existing.downloadsCount),
                    attemptsCount = maxOf(test.attemptsCount, existing.attemptsCount)
                )
            )
        } else {
            db.testDao().insertTest(test)
        }
    }

    suspend fun insertTests(tests: List<TestEntity>) {
        if (tests.isEmpty()) return
        val existingMap = db.testDao().getAllTestsList().associateBy { it.id }
        val merged = tests.map { test ->
            val existing = existingMap[test.id]
            if (existing != null) {
                test.copy(
                    isBookmarked = existing.isBookmarked || test.isBookmarked,
                    isDownloaded = existing.isDownloaded || test.isDownloaded,
                    downloadsCount = maxOf(test.downloadsCount, existing.downloadsCount),
                    attemptsCount = maxOf(test.attemptsCount, existing.attemptsCount)
                )
            } else {
                test
            }
        }
        db.testDao().insertTests(merged)
    }

    suspend fun updateTest(test: TestEntity) {
        db.testDao().updateTest(test)
    }

    suspend fun toggleBookmark(id: String, isBookmarked: Boolean) {
        db.testDao().updateBookmark(id, isBookmarked)
    }

    suspend fun downloadTest(id: String) {
        db.testDao().updateDownloadStatus(id, true)
    }

    suspend fun saveTestWithQuestions(test: TestEntity, questions: List<QuestionEntity>) {
        db.withTransaction {
            insertTest(test)
            if (questions.isNotEmpty()) {
                db.questionDao().deleteQuestionsForTest(test.id)
                db.questionDao().insertQuestions(questions)
            }
        }
    }

    suspend fun deleteTest(test: TestEntity) {
        db.withTransaction {
            db.questionDao().deleteQuestionsForTest(test.id)
            db.testDao().deleteTest(test)
        }
    }

    // --- Questions ---
    fun getQuestionsForTest(testId: String): Flow<List<QuestionEntity>> {
        return db.questionDao().getQuestionsForTest(testId)
    }

    suspend fun getQuestionsForTestList(testId: String): List<QuestionEntity> {
        return db.questionDao().getQuestionsForTestList(testId)
    }

    suspend fun insertQuestions(questions: List<QuestionEntity>) {
        db.questionDao().insertQuestions(questions)
    }

    suspend fun updateQuestion(question: QuestionEntity) {
        db.questionDao().insertQuestions(listOf(question))
    }

    suspend fun toggleQuestionBookmark(id: String, isBookmarked: Boolean) {
        db.questionDao().updateQuestionBookmark(id, isBookmarked)
    }

    fun getBookmarkedQuestions(): Flow<List<QuestionEntity>> {
        return db.questionDao().getBookmarkedQuestions()
    }

    // --- Attempts ---
        suspend fun submitTestRating(testId: String, rating: Int) {
        val test = db.testDao().getTestById(testId)
        if (test != null) {
            val updatedTest = when (rating) {
                1 -> test.copy(rating1Count = test.rating1Count + 1)
                2 -> test.copy(rating2Count = test.rating2Count + 1)
                3 -> test.copy(rating3Count = test.rating3Count + 1)
                4 -> test.copy(rating4Count = test.rating4Count + 1)
                5 -> test.copy(rating5Count = test.rating5Count + 1)
                else -> test
            }
            val totalRatings = updatedTest.rating1Count + updatedTest.rating2Count + updatedTest.rating3Count + updatedTest.rating4Count + updatedTest.rating5Count
            val sumRatings = updatedTest.rating1Count * 1 + updatedTest.rating2Count * 2 + updatedTest.rating3Count * 3 + updatedTest.rating4Count * 4 + updatedTest.rating5Count * 5
            val newRating = if (totalRatings > 0) sumRatings.toFloat() / totalRatings else 0f
            db.testDao().updateTest(updatedTest.copy(rating = newRating))
        }
    }

    suspend fun getAttemptBySessionToken(userId: String, sessionToken: String): AttemptEntity? {
        if (sessionToken.isBlank()) return null
        return db.attemptDao().getAttemptBySessionToken(userId, sessionToken)
    }

    suspend fun getPendingAttemptsForUser(userId: String): List<AttemptEntity> {
        if (userId.isBlank()) return emptyList()
        return db.attemptDao().getPendingAttemptsForUser(userId)
    }

    suspend fun insertAttempt(attempt: AttemptEntity) {
        if (attempt.attemptSessionToken.isNotBlank()) {
            val existing = db.attemptDao().getAttemptBySessionToken(attempt.userId, attempt.attemptSessionToken)
            if (existing != null) {
                // Exact session already recorded: update in-place without duplicate count increment
                db.attemptDao().insertAttempt(attempt.copy(id = existing.id))
                return
            }
        }
        db.attemptDao().insertAttempt(attempt)
        db.testDao().incrementAttemptsCount(attempt.testId)
    }

    suspend fun clearAllAttempts() {
        db.attemptDao().clearAllAttempts()
    }

    suspend fun clearAttemptsForUser(userId: String) {
        if (userId.isNotBlank()) {
            db.attemptDao().clearAttemptsForUser(userId)
        }
    }

    // --- Syllabus ---
    suspend fun insertSyllabus(syllabusList: List<SyllabusEntity>) {
        db.syllabusDao().insertSyllabus(syllabusList)
    }

    suspend fun syncNcertSyllabusFromRemote() {
        val catalogData = Com.ak.testseries.exam.SyllabusCatalog.getAllSyllabusEntities()
        insertSyllabus(catalogData)
    }

    suspend fun syncSyllabusForExam(targetExam: String) {
        val examData = Com.ak.testseries.exam.SyllabusCatalog.getSyllabusEntitiesForExam(targetExam)
        insertSyllabus(examData)
    }
}
