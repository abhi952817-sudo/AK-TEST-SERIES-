package Com.ak.testseries.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Real SQLite Migration Test for Room migrations 15 -> 16 -> 17 -> 18.
 * Executes the actual AppDatabase.MIGRATION_* objects against real SQLite database instances,
 * verifying that table schemas evolve correctly, new columns are added with required defaults,
 * and existing candidate attempt records are strictly preserved without data loss.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class AppDatabaseMigrationTest {

    private fun createV15Database(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = object : SupportSQLiteOpenHelper.Callback(15) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Table schema exactly as it was at version 15 (prior to userId addition)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attempts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        testId TEXT NOT NULL,
                        testTitle TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        maxScore INTEGER NOT NULL,
                        percentile REAL NOT NULL,
                        accuracy REAL NOT NULL,
                        correctCount INTEGER NOT NULL,
                        incorrectCount INTEGER NOT NULL,
                        skippedCount INTEGER NOT NULL,
                        timeSpentSeconds INTEGER NOT NULL,
                        dateTaken INTEGER NOT NULL,
                        answersJson TEXT NOT NULL
                    )
                """.trimIndent())
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory SQLite database
            .callback(callback)
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun getTableColumns(db: SupportSQLiteDatabase, tableName: String): List<String> {
        val cursor = db.query("PRAGMA table_info($tableName)")
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        return columns
    }

    @Test
    fun testRealMigration15To16AddsUserIdAndPreservesData() {
        val db = createV15Database()

        // 1. Insert existing candidate attempt in v15 schema
        db.execSQL("""
            INSERT INTO attempts (testId, testTitle, score, maxScore, percentile, accuracy, correctCount, incorrectCount, skippedCount, timeSpentSeconds, dateTaken, answersJson)
            VALUES ('neet_test_1', 'NEET Full Mock 1', 650, 720, 98.5, 92.0, 165, 10, 5, 10800, 1700000000, '{"q1":"A"}')
        """.trimIndent())

        // 2. Execute actual Room Migration object: MIGRATION_15_16
        AppDatabase.MIGRATION_15_16.migrate(db)

        // 3. Verify column addition
        val columns = getTableColumns(db, "attempts")
        assertTrue("Column userId must exist after MIGRATION_15_16", columns.contains("userId"))

        // 4. Verify existing record preserved with default empty userId
        val cursor = db.query("SELECT id, testId, score, userId FROM attempts WHERE testId = 'neet_test_1'")
        assertTrue("Existing attempt row must survive migration", cursor.moveToFirst())
        assertEquals("neet_test_1", cursor.getString(cursor.getColumnIndexOrThrow("testId")))
        assertEquals(650, cursor.getInt(cursor.getColumnIndexOrThrow("score")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("userId")))
        cursor.close()
        db.close()
    }

    @Test
    fun testRealMigration16To17AddsAttemptSessionTokenAndPreservesData() {
        val db = createV15Database()
        AppDatabase.MIGRATION_15_16.migrate(db)

        // Insert row at v16
        db.execSQL("""
            INSERT INTO attempts (testId, testTitle, score, maxScore, percentile, accuracy, correctCount, incorrectCount, skippedCount, timeSpentSeconds, dateTaken, answersJson, userId)
            VALUES ('jee_mock_2', 'JEE Main Physics', 80, 100, 95.0, 85.0, 20, 4, 1, 3600, 1700100000, '{"q1":"B"}', 'usr_alice')
        """.trimIndent())

        // Execute actual Room Migration object: MIGRATION_16_17
        AppDatabase.MIGRATION_16_17.migrate(db)

        val columns = getTableColumns(db, "attempts")
        assertTrue("Column attemptSessionToken must exist after MIGRATION_16_17", columns.contains("attemptSessionToken"))

        val cursor = db.query("SELECT id, testId, userId, attemptSessionToken FROM attempts WHERE testId = 'jee_mock_2'")
        assertTrue(cursor.moveToFirst())
        assertEquals("usr_alice", cursor.getString(cursor.getColumnIndexOrThrow("userId")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("attemptSessionToken")))
        cursor.close()
        db.close()
    }

    @Test
    fun testRealMigration17To18AddsVerificationAndStatusAndPreservesData() {
        val db = createV15Database()
        AppDatabase.MIGRATION_15_16.migrate(db)
        AppDatabase.MIGRATION_16_17.migrate(db)

        // Insert row at v17
        db.execSQL("""
            INSERT INTO attempts (testId, testTitle, score, maxScore, percentile, accuracy, correctCount, incorrectCount, skippedCount, timeSpentSeconds, dateTaken, answersJson, userId, attemptSessionToken)
            VALUES ('cat_mock_3', 'CAT Quantitative', 45, 60, 99.0, 90.0, 15, 2, 3, 2400, 1700200000, '{"q1":"42"}', 'usr_bob', 'token_12345')
        """.trimIndent())

        // Execute actual Room Migration object: MIGRATION_17_18
        AppDatabase.MIGRATION_17_18.migrate(db)

        val columns = getTableColumns(db, "attempts")
        assertTrue("Column verifiedServerSide must exist after MIGRATION_17_18", columns.contains("verifiedServerSide"))
        assertTrue("Column submissionStatus must exist after MIGRATION_17_18", columns.contains("submissionStatus"))

        val cursor = db.query("SELECT id, testId, attemptSessionToken, verifiedServerSide, submissionStatus FROM attempts WHERE testId = 'cat_mock_3'")
        assertTrue(cursor.moveToFirst())
        assertEquals("token_12345", cursor.getString(cursor.getColumnIndexOrThrow("attemptSessionToken")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("verifiedServerSide")))
        assertEquals("CONFIRMED", cursor.getString(cursor.getColumnIndexOrThrow("submissionStatus")))
        cursor.close()
        db.close()
    }

    @Test
    fun testSequentialRealMigrationFrom15To18PreservesIntegrity() {
        val db = createV15Database()

        // Insert pre-migration attempt at version 15
        db.execSQL("""
            INSERT INTO attempts (testId, testTitle, score, maxScore, percentile, accuracy, correctCount, incorrectCount, skippedCount, timeSpentSeconds, dateTaken, answersJson)
            VALUES ('initial_v15_test', 'Initial Test', 500, 720, 80.0, 75.0, 125, 25, 30, 7200, 1699999999, '{"q1":"C"}')
        """.trimIndent())

        // Execute the full migration sequence 15 -> 16 -> 17 -> 18
        AppDatabase.MIGRATION_15_16.migrate(db)
        AppDatabase.MIGRATION_16_17.migrate(db)
        AppDatabase.MIGRATION_17_18.migrate(db)

        val columns = getTableColumns(db, "attempts")
        assertTrue(columns.contains("userId"))
        assertTrue(columns.contains("attemptSessionToken"))
        assertTrue(columns.contains("verifiedServerSide"))
        assertTrue(columns.contains("submissionStatus"))

        // Verify the original row has survived all 3 sequential migrations with all defaults populated
        val cursor = db.query("SELECT * FROM attempts WHERE testId = 'initial_v15_test'")
        assertTrue(cursor.moveToFirst())
        assertEquals(500, cursor.getInt(cursor.getColumnIndexOrThrow("score")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("userId")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("attemptSessionToken")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("verifiedServerSide")))
        assertEquals("CONFIRMED", cursor.getString(cursor.getColumnIndexOrThrow("submissionStatus")))
        cursor.close()

        // Insert new row using complete v18 schema
        db.execSQL("""
            INSERT INTO attempts (testId, testTitle, score, maxScore, percentile, accuracy, correctCount, incorrectCount, skippedCount, timeSpentSeconds, dateTaken, answersJson, userId, attemptSessionToken, verifiedServerSide, submissionStatus)
            VALUES ('v18_test', 'V18 New Test', 680, 720, 99.2, 95.0, 170, 5, 5, 10000, 1700300000, '{"q1":"A"}', 'usr_charlie', 'token_9999', 1, 'CONFIRMED')
        """.trimIndent())

        val countCursor = db.query("SELECT COUNT(*) FROM attempts")
        assertTrue(countCursor.moveToFirst())
        assertEquals(2, countCursor.getInt(0))
        countCursor.close()

        db.close()
    }

    @Test
    fun testRealMigration18To19AddsCanonicalExamOptionEAndRealScores() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = object : SupportSQLiteOpenHelper.Callback(18) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tests (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        institute TEXT NOT NULL,
                        batchName TEXT NOT NULL,
                        facultyName TEXT NOT NULL,
                        language TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        difficulty TEXT NOT NULL,
                        testType TEXT NOT NULL,
                        questionsCount INTEGER NOT NULL,
                        isOfficial INTEGER NOT NULL,
                        isVerifiedCommunity INTEGER NOT NULL,
                        isPublic INTEGER NOT NULL,
                        countsTowardRanking INTEGER NOT NULL,
                        downloadsCount INTEGER NOT NULL,
                        isDownloaded INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        aiConfidence INTEGER NOT NULL,
                        uploaderId TEXT NOT NULL,
                        targetExam TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        classLevel TEXT NOT NULL,
                        chapter TEXT NOT NULL,
                        topic TEXT NOT NULL,
                        questionsJson TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS questions (
                        id TEXT PRIMARY KEY NOT NULL,
                        testId TEXT NOT NULL,
                        questionText TEXT NOT NULL,
                        optionA TEXT NOT NULL,
                        optionB TEXT NOT NULL,
                        optionC TEXT NOT NULL,
                        optionD TEXT NOT NULL,
                        correctAnswer TEXT NOT NULL,
                        explanation TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        chapter TEXT NOT NULL,
                        topic TEXT NOT NULL,
                        difficulty TEXT NOT NULL,
                        isBookmarked INTEGER NOT NULL,
                        questionTextHi TEXT,
                        optionAHi TEXT,
                        optionBHi TEXT,
                        optionCHi TEXT,
                        optionDHi TEXT,
                        explanationHi TEXT,
                        questionType TEXT NOT NULL,
                        section TEXT NOT NULL,
                        needsImage INTEGER NOT NULL,
                        imageUrl TEXT
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attempts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL,
                        testId TEXT NOT NULL,
                        testTitle TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        maxScore INTEGER NOT NULL,
                        percentile REAL NOT NULL,
                        accuracy REAL NOT NULL,
                        correctCount INTEGER NOT NULL,
                        incorrectCount INTEGER NOT NULL,
                        skippedCount INTEGER NOT NULL,
                        timeSpentSeconds INTEGER NOT NULL,
                        dateTaken INTEGER NOT NULL,
                        answersJson TEXT NOT NULL,
                        attemptSessionToken TEXT NOT NULL,
                        verifiedServerSide INTEGER NOT NULL,
                        submissionStatus TEXT NOT NULL
                    )
                """.trimIndent())
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(callback)
            .build()

        val db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase

        // Insert row at v18
        db.execSQL("""
            INSERT INTO tests (id, title, description, institute, batchName, facultyName, language, subject, difficulty, testType, questionsCount, isOfficial, isVerifiedCommunity, isPublic, countsTowardRanking, downloadsCount, isDownloaded, timestamp, status, aiConfidence, uploaderId, targetExam, durationMinutes, classLevel, chapter, topic, questionsJson)
            VALUES ('t_v18', 'Test v18', 'Desc', 'Inst', 'Batch', 'Fac', 'Eng', 'Phy', 'Med', 'Mock', 10, 1, 1, 0, 0, 0, 1, 1700000000, 'Published', 90, 'admin', 'NEET', 60, '12th', 'Ch1', 'Top1', '[]')
        """.trimIndent())

        db.execSQL("""
            INSERT INTO questions (id, testId, questionText, optionA, optionB, optionC, optionD, correctAnswer, explanation, subject, chapter, topic, difficulty, isBookmarked, questionTextHi, optionAHi, optionBHi, optionCHi, optionDHi, explanationHi, questionType, section, needsImage, imageUrl)
            VALUES ('q_v18_1', 't_v18', 'What is velocity?', 'A', 'B', 'C', 'D', 'A', 'Exp', 'Phy', 'Ch1', 'Top1', 'Med', 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MCQ', 'SECTION_A', 0, NULL)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO attempts (testId, testTitle, score, maxScore, percentile, accuracy, correctCount, incorrectCount, skippedCount, timeSpentSeconds, dateTaken, answersJson, userId, attemptSessionToken, verifiedServerSide, submissionStatus)
            VALUES ('t_v18', 'Test v18', 650, 720, 98.0, 90.0, 165, 10, 5, 7200, 1700400000, '{}', 'user_1', 'token_1', 1, 'CONFIRMED')
        """.trimIndent())

        // Execute MIGRATION_18_19
        AppDatabase.MIGRATION_18_19.migrate(db)

        // Verify tests columns
        val testColumns = getTableColumns(db, "tests")
        assertTrue("tests must have examId", testColumns.contains("examId"))
        assertTrue("tests must have stageId", testColumns.contains("stageId"))
        assertTrue("tests must have paperId", testColumns.contains("paperId"))
        assertTrue("tests must have scoringVersion", testColumns.contains("scoringVersion"))

        // Verify questions columns
        val questionColumns = getTableColumns(db, "questions")
        assertTrue("questions must have optionE", questionColumns.contains("optionE"))
        assertTrue("questions must have optionEHi", questionColumns.contains("optionEHi"))

        // Verify attempts columns & data preservation
        val attemptColumns = getTableColumns(db, "attempts")
        assertTrue(attemptColumns.contains("score"))
        assertTrue(attemptColumns.contains("maxScore"))

        val cursor = db.query("SELECT score, maxScore FROM attempts WHERE testId = 't_v18'")
        assertTrue(cursor.moveToFirst())
        assertEquals(650.0, cursor.getDouble(cursor.getColumnIndexOrThrow("score")), 0.001)
        assertEquals(720.0, cursor.getDouble(cursor.getColumnIndexOrThrow("maxScore")), 0.001)
        cursor.close()

        // Insert new attempt with fractional score in v19 schema
        db.execSQL("""
            INSERT INTO attempts (testId, testTitle, score, maxScore, percentile, accuracy, correctCount, incorrectCount, skippedCount, timeSpentSeconds, dateTaken, answersJson, userId, attemptSessionToken, verifiedServerSide, submissionStatus)
            VALUES ('t_v19', 'Banking Test', 68.25, 100.0, 99.1, 92.5, 70, 7, 23, 3600, 1700500000, '{}', 'user_2', 'token_2', 1, 'CONFIRMED')
        """.trimIndent())

        val cursorV19 = db.query("SELECT score, maxScore FROM attempts WHERE testId = 't_v19'")
        assertTrue(cursorV19.moveToFirst())
        assertEquals(68.25, cursorV19.getDouble(cursorV19.getColumnIndexOrThrow("score")), 0.001)
        assertEquals(100.0, cursorV19.getDouble(cursorV19.getColumnIndexOrThrow("maxScore")), 0.001)
        cursorV19.close()

        db.close()
    }

    @Test
    fun testSequentialRealMigrationFrom15To19PreservesAllTablesAndIntegrity() {
        val db = createV15Database()

        // 1. Insert v15 attempt
        db.execSQL("""
            INSERT INTO attempts (testId, testTitle, score, maxScore, percentile, accuracy, correctCount, incorrectCount, skippedCount, timeSpentSeconds, dateTaken, answersJson)
            VALUES ('test_seq_15', 'Sequential Test', 600, 720, 90.0, 85.0, 150, 20, 10, 7200, 1700000000, '{}')
        """.trimIndent())

        // 2. Run migrations 15 -> 16 -> 17 -> 18
        AppDatabase.MIGRATION_15_16.migrate(db)
        AppDatabase.MIGRATION_16_17.migrate(db)
        AppDatabase.MIGRATION_17_18.migrate(db)

        // 3. Create tests and questions tables as of v18
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tests (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                institute TEXT NOT NULL,
                batchName TEXT NOT NULL,
                facultyName TEXT NOT NULL,
                language TEXT NOT NULL,
                subject TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                testType TEXT NOT NULL,
                questionsCount INTEGER NOT NULL,
                isOfficial INTEGER NOT NULL,
                isVerifiedCommunity INTEGER NOT NULL,
                isPublic INTEGER NOT NULL,
                countsTowardRanking INTEGER NOT NULL,
                downloadsCount INTEGER NOT NULL,
                isDownloaded INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                status TEXT NOT NULL,
                aiConfidence INTEGER NOT NULL,
                uploaderId TEXT NOT NULL,
                targetExam TEXT NOT NULL,
                durationMinutes INTEGER NOT NULL,
                classLevel TEXT NOT NULL,
                chapter TEXT NOT NULL,
                topic TEXT NOT NULL,
                questionsJson TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS questions (
                id TEXT PRIMARY KEY NOT NULL,
                testId TEXT NOT NULL,
                questionText TEXT NOT NULL,
                optionA TEXT NOT NULL,
                optionB TEXT NOT NULL,
                optionC TEXT NOT NULL,
                optionD TEXT NOT NULL,
                correctAnswer TEXT NOT NULL,
                explanation TEXT NOT NULL,
                subject TEXT NOT NULL,
                chapter TEXT NOT NULL,
                topic TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                isBookmarked INTEGER NOT NULL,
                questionTextHi TEXT,
                optionAHi TEXT,
                optionBHi TEXT,
                optionCHi TEXT,
                optionDHi TEXT,
                explanationHi TEXT,
                questionType TEXT NOT NULL,
                section TEXT NOT NULL,
                needsImage INTEGER NOT NULL,
                imageUrl TEXT
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO tests (id, title, description, institute, batchName, facultyName, language, subject, difficulty, testType, questionsCount, isOfficial, isVerifiedCommunity, isPublic, countsTowardRanking, downloadsCount, isDownloaded, timestamp, status, aiConfidence, uploaderId, targetExam, durationMinutes, classLevel, chapter, topic, questionsJson)
            VALUES ('test_seq_15', 'Sequential Test', 'Desc', 'Inst', 'Batch', 'Fac', 'Eng', 'Phy', 'Med', 'Mock', 10, 1, 1, 0, 0, 0, 1, 1700000000, 'Published', 90, 'admin', 'NEET', 60, '12th', 'Ch1', 'Top1', '[]')
        """.trimIndent())

        db.execSQL("""
            INSERT INTO questions (id, testId, questionText, optionA, optionB, optionC, optionD, correctAnswer, explanation, subject, chapter, topic, difficulty, isBookmarked, questionType, section, needsImage)
            VALUES ('q_seq_1', 'test_seq_15', 'Q1', 'A', 'B', 'C', 'D', 'A', 'Exp', 'Phy', 'Ch1', 'Top1', 'Med', 0, 'MCQ', 'SECTION_A', 0)
        """.trimIndent())

        // 4. Run migration 18 -> 19
        AppDatabase.MIGRATION_18_19.migrate(db)

        // 5. Verify all tables and fractional scores survived
        val testCols = getTableColumns(db, "tests")
        assertTrue(testCols.contains("examId"))
        assertTrue(testCols.contains("scoringVersion"))

        val qCols = getTableColumns(db, "questions")
        assertTrue(qCols.contains("optionE"))

        val attemptCursor = db.query("SELECT score, maxScore FROM attempts WHERE testId = 'test_seq_15'")
        assertTrue(attemptCursor.moveToFirst())
        assertEquals(600.0, attemptCursor.getDouble(attemptCursor.getColumnIndexOrThrow("score")), 0.001)
        assertEquals(720.0, attemptCursor.getDouble(attemptCursor.getColumnIndexOrThrow("maxScore")), 0.001)
        attemptCursor.close()

        db.close()
    }
}

