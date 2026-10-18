package Com.ak.testseries.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TestDao {
    @Query("SELECT * FROM tests ORDER BY timestamp DESC")
    fun getAllTests(): Flow<List<TestEntity>>

    @Query("SELECT * FROM tests")
    suspend fun getAllTestsList(): List<TestEntity>

    @Query("SELECT * FROM tests WHERE id = :id LIMIT 1")
    suspend fun getTestById(id: String): TestEntity?

    @Query("""
        SELECT * FROM tests 
        WHERE (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR institute LIKE '%' || :query || '%' OR facultyName LIKE '%' || :query || '%' OR chapter LIKE '%' || :query || '%' OR topic LIKE '%' || :query || '%')
        AND (:classLevel = 'All' OR classLevel = :classLevel)
        AND (:batchName = 'All' OR batchName = :batchName)
        AND (:subject = 'All' OR subject = :subject)
        AND (:chapter = 'All' OR chapter = :chapter)
        AND (:topic = 'All' OR topic = :topic)
        AND (:difficulty = 'All' OR difficulty = :difficulty)
        AND (:testType = 'All' OR testType = :testType)
    """)
    fun searchTestsDeep(
        query: String,
        classLevel: String,
        batchName: String,
        subject: String,
        chapter: String,
        topic: String,
        difficulty: String,
        testType: String
    ): Flow<List<TestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTest(test: TestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTests(tests: List<TestEntity>)

    @Update
    suspend fun updateTest(test: TestEntity)

    @Query("UPDATE tests SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmark(id: String, isBookmarked: Boolean)

    @Query("UPDATE tests SET isDownloaded = :isDownloaded, downloadsCount = downloadsCount + 1 WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, isDownloaded: Boolean)

    @Query("UPDATE tests SET attemptsCount = attemptsCount + 1 WHERE id = :id")
    suspend fun incrementAttemptsCount(id: String)

    @Delete
    suspend fun deleteTest(test: TestEntity)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE testId = :testId")
    fun getQuestionsForTest(testId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE testId = :testId")
    suspend fun getQuestionsForTestList(testId: String): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Query("UPDATE questions SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateQuestionBookmark(id: String, isBookmarked: Boolean)

    @Query("SELECT * FROM questions WHERE isBookmarked = 1")
    fun getBookmarkedQuestions(): Flow<List<QuestionEntity>>

    @Query("DELETE FROM questions WHERE testId = :testId")
    suspend fun deleteQuestionsForTest(testId: String)
}

@Dao
interface AttemptDao {
    @Query("SELECT * FROM attempts ORDER BY dateTaken DESC")
    fun getAllAttempts(): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts WHERE userId = :userId ORDER BY dateTaken DESC")
    fun getAttemptsForUser(userId: String): Flow<List<AttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: AttemptEntity)

    @Query("SELECT * FROM attempts WHERE userId = :userId AND attemptSessionToken = :sessionToken LIMIT 1")
    suspend fun getAttemptBySessionToken(userId: String, sessionToken: String): AttemptEntity?

    @Query("DELETE FROM attempts WHERE userId = :userId")
    suspend fun clearAttemptsForUser(userId: String)

    @Query("DELETE FROM attempts")
    suspend fun clearAllAttempts()
}

@Dao
interface SyllabusDao {
    @Query("SELECT * FROM ncert_syllabus")
    fun getSyllabus(): Flow<List<SyllabusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyllabus(syllabus: List<SyllabusEntity>)
}

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 5")
    fun getRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(recentSearch: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearAllRecentSearches()
}

@Database(
    entities = [TestEntity::class, QuestionEntity::class, AttemptEntity::class, SyllabusEntity::class, RecentSearchEntity::class],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun testDao(): TestDao
    abstract fun questionDao(): QuestionDao
    abstract fun attemptDao(): AttemptDao
    abstract fun syllabusDao(): SyllabusDao
    abstract fun recentSearchDao(): RecentSearchDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN attemptSessionToken TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neet_test_series_db"
                )
                .addMigrations(MIGRATION_15_16, MIGRATION_16_17)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
