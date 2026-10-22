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
}
