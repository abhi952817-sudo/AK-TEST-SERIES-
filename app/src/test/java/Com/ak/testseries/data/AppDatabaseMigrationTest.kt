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
                // Table schemas exactly as they existed at version 15:
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
                        rating REAL NOT NULL,
                        rating1Count INTEGER NOT NULL,
                        rating2Count INTEGER NOT NULL,
                        rating3Count INTEGER NOT NULL,
                        rating4Count INTEGER NOT NULL,
                        rating5Count INTEGER NOT NULL,
                        attemptsCount INTEGER NOT NULL,
                        downloadsCount INTEGER NOT NULL,
                        isDownloaded INTEGER NOT NULL,
                        isBookmarked INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        aiConfidence INTEGER NOT NULL,
                        uploaderId TEXT NOT NULL,
                        targetExam TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        classLevel TEXT NOT NULL,
                        chapter TEXT NOT NULL,
                        topic TEXT NOT NULL,
                        imageUrl TEXT,
                        questionsJson TEXT
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

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ncert_syllabus (
                        id TEXT PRIMARY KEY NOT NULL,
                        classLevel TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        chapter TEXT NOT NULL,
                        topics TEXT NOT NULL,
                        totalTests INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recent_searches (
                        query TEXT PRIMARY KEY NOT NULL,
                        timestamp INTEGER NOT NULL
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

        // 1. Populate all tables in their historical version-15 schema
        db.execSQL("""
            INSERT INTO tests (
                id, title, description, institute, batchName, facultyName, language, subject, difficulty, testType,
                questionsCount, isOfficial, isVerifiedCommunity, isPublic, countsTowardRanking, rating, rating1Count,
                rating2Count, rating3Count, rating4Count, rating5Count, attemptsCount, downloadsCount, isDownloaded,
                isBookmarked, timestamp, status, aiConfidence, uploaderId, targetExam, durationMinutes, classLevel,
                chapter, topic, imageUrl, questionsJson
            ) VALUES (
                'test_seq_15', 'Sequential Test', 'Desc', 'Inst', 'Batch', 'Fac', 'English', 'Physics', 'Medium', 'Mock',
                10, 1, 1, 1, 1, 4.5, 0, 0, 0, 0, 5, 12, 50, 1,
                0, 1700000000, 'Published', 95, 'admin_user', 'NEET', 60, '12th',
                'Mechanics', 'Kinematics', 'https://example.com/banner.png', '[]'
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO questions (
                id, testId, questionText, optionA, optionB, optionC, optionD, correctAnswer, explanation,
                subject, chapter, topic, difficulty, isBookmarked, questionTextHi, optionAHi, optionBHi,
                optionCHi, optionDHi, explanationHi, questionType, section, needsImage, imageUrl
            ) VALUES (
                'q_seq_1', 'test_seq_15', 'What is acceleration due to gravity?', '9.8 m/s^2', '10 m/s^2', '8.9 m/s^2', '0 m/s^2', 'A', 'Standard gravity value.',
                'Physics', 'Mechanics', 'Kinematics', 'Medium', 0, NULL, NULL, NULL,
                NULL, NULL, NULL, 'MCQ', 'SECTION_A', 0, NULL
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO attempts (
                testId, testTitle, score, maxScore, percentile, accuracy, correctCount,
                incorrectCount, skippedCount, timeSpentSeconds, dateTaken, answersJson
            ) VALUES (
                'test_seq_15', 'Sequential Test', 600, 720, 92.5, 88.0, 150,
                20, 10, 7200, 1700000000, '{"q_seq_1":"A"}'
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO ncert_syllabus (id, classLevel, subject, chapter, topics, totalTests)
            VALUES ('syl_15', 'Class 11', 'Physics', 'Units and Measurements', 'SI Units, Dimensions', 5)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO recent_searches (query, timestamp)
            VALUES ('Mechanics NEET', 1700000000)
        """.trimIndent())

        // 2. Execute full sequential migration chain: 15 -> 16 -> 17 -> 18 -> 19
        AppDatabase.MIGRATION_15_16.migrate(db)
        AppDatabase.MIGRATION_16_17.migrate(db)
        AppDatabase.MIGRATION_17_18.migrate(db)
        AppDatabase.MIGRATION_18_19.migrate(db)
        db.version = 19

        // 3. Verify resulting database opens as version 19
        assertEquals(19, db.version)
        assertTrue("Database must remain open after migration", db.isOpen)

        // 4. Verify tests table row preservation, primary key, and newly added columns
        val testCols = getTableColumns(db, "tests")
        assertTrue("tests must have examId column", testCols.contains("examId"))
        assertTrue("tests must have stageId column", testCols.contains("stageId"))
        assertTrue("tests must have paperId column", testCols.contains("paperId"))
        assertTrue("tests must have scoringVersion column", testCols.contains("scoringVersion"))

        val testCursor = db.query("SELECT id, title, rating, examId, stageId, paperId, scoringVersion FROM tests WHERE id = 'test_seq_15'")
        assertTrue("Existing test row must survive migration", testCursor.moveToFirst())
        assertEquals("test_seq_15", testCursor.getString(testCursor.getColumnIndexOrThrow("id")))
        assertEquals("Sequential Test", testCursor.getString(testCursor.getColumnIndexOrThrow("title")))
        assertEquals(4.5, testCursor.getDouble(testCursor.getColumnIndexOrThrow("rating")), 0.001)
        assertTrue("New examId must be null for pre-migration test", testCursor.isNull(testCursor.getColumnIndexOrThrow("examId")))
        assertTrue("New scoringVersion must be null for pre-migration test", testCursor.isNull(testCursor.getColumnIndexOrThrow("scoringVersion")))
        testCursor.close()

        // 5. Verify questions table row preservation and newly added five-option columns
        val qCols = getTableColumns(db, "questions")
        assertTrue("questions must have optionE column", qCols.contains("optionE"))
        assertTrue("questions must have optionEHi column", qCols.contains("optionEHi"))

        val qCursor = db.query("SELECT id, testId, questionText, optionA, optionE, optionEHi FROM questions WHERE id = 'q_seq_1'")
        assertTrue("Existing question row must survive migration", qCursor.moveToFirst())
        assertEquals("q_seq_1", qCursor.getString(qCursor.getColumnIndexOrThrow("id")))
        assertEquals("test_seq_15", qCursor.getString(qCursor.getColumnIndexOrThrow("testId")))
        assertEquals("What is acceleration due to gravity?", qCursor.getString(qCursor.getColumnIndexOrThrow("questionText")))
        assertEquals("9.8 m/s^2", qCursor.getString(qCursor.getColumnIndexOrThrow("optionA")))
        assertTrue("New optionE must be null for pre-migration question", qCursor.isNull(qCursor.getColumnIndexOrThrow("optionE")))
        assertTrue("New optionEHi must be null for pre-migration question", qCursor.isNull(qCursor.getColumnIndexOrThrow("optionEHi")))
        qCursor.close()

        // 6. Verify attempts table row preservation, defaults, and REAL fractional score conversion
        val attemptCols = getTableColumns(db, "attempts")
        assertTrue(attemptCols.contains("userId"))
        assertTrue(attemptCols.contains("attemptSessionToken"))
        assertTrue(attemptCols.contains("verifiedServerSide"))
        assertTrue(attemptCols.contains("submissionStatus"))
        assertTrue(attemptCols.contains("score"))
        assertTrue(attemptCols.contains("maxScore"))

        val attemptCursor = db.query("SELECT testId, score, maxScore, userId, attemptSessionToken, verifiedServerSide, submissionStatus FROM attempts WHERE testId = 'test_seq_15'")
        assertTrue("Existing attempt row must survive migration", attemptCursor.moveToFirst())
        assertEquals("test_seq_15", attemptCursor.getString(attemptCursor.getColumnIndexOrThrow("testId")))
        assertEquals(600.0, attemptCursor.getDouble(attemptCursor.getColumnIndexOrThrow("score")), 0.001)
        assertEquals(720.0, attemptCursor.getDouble(attemptCursor.getColumnIndexOrThrow("maxScore")), 0.001)
        assertEquals("", attemptCursor.getString(attemptCursor.getColumnIndexOrThrow("userId")))
        assertEquals("", attemptCursor.getString(attemptCursor.getColumnIndexOrThrow("attemptSessionToken")))
        assertEquals(0, attemptCursor.getInt(attemptCursor.getColumnIndexOrThrow("verifiedServerSide")))
        assertEquals("CONFIRMED", attemptCursor.getString(attemptCursor.getColumnIndexOrThrow("submissionStatus")))
        attemptCursor.close()

        // 7. Verify index index_attempts_userId_attemptSessionToken exists on attempts
        val indexCursor = db.query("PRAGMA index_list('attempts')")
        var foundIndex = false
        while (indexCursor.moveToNext()) {
            val indexName = indexCursor.getString(indexCursor.getColumnIndexOrThrow("name"))
            if (indexName == "index_attempts_userId_attemptSessionToken") {
                foundIndex = true
            }
        }
        indexCursor.close()
        assertTrue("Composite index index_attempts_userId_attemptSessionToken must exist on attempts", foundIndex)

        // 8. Verify fractional score precision in v19 schema (e.g. JEE/Banking marks +3.75, -0.25)
        db.execSQL("""
            INSERT INTO attempts (
                testId, testTitle, score, maxScore, percentile, accuracy, correctCount,
                incorrectCount, skippedCount, timeSpentSeconds, dateTaken, answersJson,
                userId, attemptSessionToken, verifiedServerSide, submissionStatus
            ) VALUES (
                'test_v19_fractional', 'JEE Advanced Paper 1', 78.75, 180.0, 99.4, 91.2,
                22, 5, 3, 10800, 1700500000, '{"q1":"B"}',
                'user_v19', 'session_tok_v19', 1, 'CONFIRMED'
            )
        """.trimIndent())

        val fracCursor = db.query("SELECT score, maxScore FROM attempts WHERE testId = 'test_v19_fractional'")
        assertTrue(fracCursor.moveToFirst())
        assertEquals(78.75, fracCursor.getDouble(fracCursor.getColumnIndexOrThrow("score")), 0.001)
        assertEquals(180.0, fracCursor.getDouble(fracCursor.getColumnIndexOrThrow("maxScore")), 0.001)
        fracCursor.close()

        // 9. Verify ncert_syllabus and recent_searches rows preserved
        val sylCursor = db.query("SELECT id, chapter FROM ncert_syllabus WHERE id = 'syl_15'")
        assertTrue(sylCursor.moveToFirst())
        assertEquals("Units and Measurements", sylCursor.getString(sylCursor.getColumnIndexOrThrow("chapter")))
        sylCursor.close()

        val searchCursor = db.query("SELECT query FROM recent_searches WHERE query = 'Mechanics NEET'")
        assertTrue(searchCursor.moveToFirst())
        assertEquals("Mechanics NEET", searchCursor.getString(searchCursor.getColumnIndexOrThrow("query")))
        searchCursor.close()

        db.close()
    }

    @Test
    fun testMigration19To20_createsPdfSessionsTable_andPreservesAllExistingData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = object : SupportSQLiteOpenHelper.Callback(19) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Base tables as in v19
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
                        rating REAL NOT NULL,
                        rating1Count INTEGER NOT NULL,
                        rating2Count INTEGER NOT NULL,
                        rating3Count INTEGER NOT NULL,
                        rating4Count INTEGER NOT NULL,
                        rating5Count INTEGER NOT NULL,
                        attemptsCount INTEGER NOT NULL,
                        downloadsCount INTEGER NOT NULL,
                        isDownloaded INTEGER NOT NULL,
                        isBookmarked INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        aiConfidence INTEGER NOT NULL,
                        uploaderId TEXT NOT NULL,
                        targetExam TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        classLevel TEXT NOT NULL,
                        chapter TEXT NOT NULL,
                        topic TEXT NOT NULL,
                        imageUrl TEXT,
                        questionsJson TEXT,
                        examId TEXT,
                        stageId TEXT,
                        paperId TEXT,
                        scoringVersion INTEGER
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS questions (
                        id TEXT PRIMARY KEY NOT NULL,
                        testId TEXT NOT NULL,
                        questionNumber INTEGER NOT NULL,
                        questionText TEXT NOT NULL,
                        questionTextHi TEXT NOT NULL,
                        optionA TEXT NOT NULL,
                        optionB TEXT NOT NULL,
                        optionC TEXT NOT NULL,
                        optionD TEXT NOT NULL,
                        optionAHi TEXT NOT NULL,
                        optionBHi TEXT NOT NULL,
                        optionCHi TEXT NOT NULL,
                        optionDHi TEXT NOT NULL,
                        correctOption TEXT NOT NULL,
                        explanation TEXT NOT NULL,
                        explanationHi TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        chapter TEXT NOT NULL,
                        topic TEXT NOT NULL,
                        difficulty TEXT NOT NULL,
                        isBookmarked INTEGER NOT NULL,
                        imageUrl TEXT,
                        optionE TEXT,
                        optionEHi TEXT
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attempts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL DEFAULT '',
                        testId TEXT NOT NULL,
                        testTitle TEXT NOT NULL,
                        score REAL NOT NULL,
                        maxScore REAL NOT NULL,
                        percentile REAL NOT NULL,
                        accuracy REAL NOT NULL,
                        correctCount INTEGER NOT NULL,
                        incorrectCount INTEGER NOT NULL,
                        skippedCount INTEGER NOT NULL,
                        timeSpentSeconds INTEGER NOT NULL,
                        dateTaken INTEGER NOT NULL,
                        answersJson TEXT NOT NULL,
                        attemptSessionToken TEXT NOT NULL DEFAULT '',
                        verifiedServerSide INTEGER NOT NULL DEFAULT 0,
                        submissionStatus TEXT NOT NULL DEFAULT 'CONFIRMED'
                    )
                """.trimIndent())
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_db_migration_19_20")
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // 1. Insert pre-existing v19 records
        db.execSQL("""
            INSERT INTO tests (
                id, title, description, institute, batchName, facultyName, language, subject,
                difficulty, testType, questionsCount, isOfficial, isVerifiedCommunity, isPublic,
                countsTowardRanking, rating, rating1Count, rating2Count, rating3Count, rating4Count,
                rating5Count, attemptsCount, downloadsCount, isDownloaded, isBookmarked, timestamp,
                status, aiConfidence, uploaderId, targetExam, durationMinutes, classLevel, chapter, topic,
                examId, stageId, paperId, scoringVersion
            ) VALUES (
                'test_v19', 'NEET Full Test 2024', 'Full syllabus mock', 'Allen', 'Leader', 'Dr. Sharma',
                'English', 'Full Syllabus', 'Medium', 'Full Mock', 200, 1, 1, 1, 1, 4.8, 0, 1, 2, 5, 20,
                100, 50, 1, 0, 1700000000, 'PUBLISHED', 95, 'admin_1', 'NEET UG', 200, '12th', 'All', 'All',
                'neet_ug', 'neet_stage_1', 'paper_1', 1
            )
        """.trimIndent())

        // 2. Execute Room Migration 19 -> 20
        AppDatabase.MIGRATION_19_20.migrate(db)

        // 3. Verify pdf_sessions table was created
        val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='pdf_sessions'")
        assertTrue("pdf_sessions table must exist after migration 19_20", tableCursor.moveToFirst())
        tableCursor.close()

        // 4. Verify columns in pdf_sessions
        val pragmaCursor = db.query("PRAGMA table_info('pdf_sessions')")
        val columns = mutableSetOf<String>()
        while (pragmaCursor.moveToNext()) {
            columns.add(pragmaCursor.getString(pragmaCursor.getColumnIndexOrThrow("name")))
        }
        pragmaCursor.close()

        val requiredColumns = listOf(
            "sessionId", "filePath", "fileName", "fileSize", "fileHash",
            "pageCount", "processedPages", "lastProcessedPage", "status",
            "createdAt", "updatedAt", "errorMessage", "extractedTextPath", "examTarget"
        )
        for (col in requiredColumns) {
            assertTrue("Column '$col' must be present in pdf_sessions", columns.contains(col))
        }

        // 5. Verify index exists on fileHash and status
        val indexCursor = db.query("PRAGMA index_list('pdf_sessions')")
        val indices = mutableSetOf<String>()
        while (indexCursor.moveToNext()) {
            indices.add(indexCursor.getString(indexCursor.getColumnIndexOrThrow("name")))
        }
        indexCursor.close()
        assertTrue(indices.contains("index_pdf_sessions_fileHash"))
        assertTrue(indices.contains("index_pdf_sessions_status"))

        // 6. Verify inserting into pdf_sessions succeeds
        db.execSQL("""
            INSERT INTO pdf_sessions (
                sessionId, filePath, fileName, fileSize, fileHash, pageCount,
                processedPages, lastProcessedPage, status, createdAt, updatedAt,
                errorMessage, extractedTextPath, examTarget
            ) VALUES (
                'session_test_1', '/data/user/0/Com.ak.testseries/files/pdf_sessions/session_test_1/source.pdf',
                'neet_2024.pdf', 1048576, 'abcdef1234567890', 50, 10, 10, 'PARSING',
                1700000000, 1700000100, NULL, NULL, 'NEET UG'
            )
        """.trimIndent())

        val sessionCursor = db.query("SELECT * FROM pdf_sessions WHERE sessionId = 'session_test_1'")
        assertTrue(sessionCursor.moveToFirst())
        assertEquals("neet_2024.pdf", sessionCursor.getString(sessionCursor.getColumnIndexOrThrow("fileName")))
        assertEquals(50, sessionCursor.getInt(sessionCursor.getColumnIndexOrThrow("pageCount")))
        assertEquals(10, sessionCursor.getInt(sessionCursor.getColumnIndexOrThrow("lastProcessedPage")))
        assertEquals("PARSING", sessionCursor.getString(sessionCursor.getColumnIndexOrThrow("status")))
        sessionCursor.close()

        // 7. Verify pre-existing v19 data remains intact
        val testCursor = db.query("SELECT id, title FROM tests WHERE id = 'test_v19'")
        assertTrue(testCursor.moveToFirst())
        assertEquals("NEET Full Test 2024", testCursor.getString(testCursor.getColumnIndexOrThrow("title")))
        testCursor.close()

        db.close()
    }
}

