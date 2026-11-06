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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class AppDatabaseMigration20To21Test {

    @Test
    fun migrationAddsUngradedCountWithoutLosingExistingAttemptData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = object : SupportSQLiteOpenHelper.Callback(20) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE attempts (
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
                db.execSQL("""
                    INSERT INTO attempts (
                        id, userId, testId, testTitle, score, maxScore, percentile, accuracy,
                        correctCount, incorrectCount, skippedCount, timeSpentSeconds, dateTaken,
                        answersJson, attemptSessionToken, verifiedServerSide, submissionStatus
                    ) VALUES (
                        7, 'user_7', 'neet_full_1', 'NEET Full Test', 1.34, 6.0, 0.0, 50.0,
                        1, 1, 1, 90, 1700000000, '{}', 'session_7', 1, 'CONFIRMED'
                    )
                """.trimIndent())
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("attempt_ungraded_migration_20_21")
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_20_21.migrate(db)

        val cursor = db.query(
            "SELECT score, maxScore, correctCount, incorrectCount, skippedCount, ungradedCount FROM attempts WHERE id = 7"
        )
        assertTrue("Existing attempt must survive migration", cursor.moveToFirst())
        assertEquals(1.34, cursor.getDouble(0), 0.001)
        assertEquals(6.0, cursor.getDouble(1), 0.001)
        assertEquals(1, cursor.getInt(2))
        assertEquals(1, cursor.getInt(3))
        assertEquals(1, cursor.getInt(4))
        assertEquals("Existing attempts must default to zero ungraded answers", 0, cursor.getInt(5))
        cursor.close()

        db.execSQL("UPDATE attempts SET ungradedCount = 2 WHERE id = 7")
        val updated = db.query("SELECT ungradedCount FROM attempts WHERE id = 7")
        assertTrue(updated.moveToFirst())
        assertEquals(2, updated.getInt(0))
        updated.close()

        helper.close()
    }
}
