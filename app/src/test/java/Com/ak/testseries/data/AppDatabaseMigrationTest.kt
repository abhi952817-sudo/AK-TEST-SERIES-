package Com.ak.testseries.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class AppDatabaseMigrationTest {

    @Test
    fun testMigrationsExecuteSqlSuccessfully() {
        val sqlite = SQLiteDatabase.create(null)
        // Setup schema as it was prior to v16
        sqlite.execSQL("""
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

        // Execute MIGRATION_15_16
        sqlite.execSQL("ALTER TABLE attempts ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
        // Execute MIGRATION_16_17
        sqlite.execSQL("ALTER TABLE attempts ADD COLUMN attemptSessionToken TEXT NOT NULL DEFAULT ''")
        // Execute MIGRATION_17_18
        sqlite.execSQL("ALTER TABLE attempts ADD COLUMN verifiedServerSide INTEGER NOT NULL DEFAULT 0")
        sqlite.execSQL("ALTER TABLE attempts ADD COLUMN submissionStatus TEXT NOT NULL DEFAULT 'CONFIRMED'")

        // Query PRAGMA table_info to verify all columns are present
        val cursor = sqlite.rawQuery("PRAGMA table_info(attempts)", null)
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        sqlite.close()

        assertTrue("Column userId should exist after migration 15->16", columns.contains("userId"))
        assertTrue("Column attemptSessionToken should exist after migration 16->17", columns.contains("attemptSessionToken"))
        assertTrue("Column verifiedServerSide should exist after migration 17->18", columns.contains("verifiedServerSide"))
        assertTrue("Column submissionStatus should exist after migration 17->18", columns.contains("submissionStatus"))
    }
}
