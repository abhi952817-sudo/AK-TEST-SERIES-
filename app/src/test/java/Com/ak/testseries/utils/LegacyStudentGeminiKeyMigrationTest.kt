package Com.ak.testseries.utils

import android.app.Application
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LegacyStudentGeminiKeyMigrationTest {

    @Test
    fun invalidLegacyCiphertextIsRemovedWithoutCreatingAReplacementKey() {
        val context = RuntimeEnvironment.getApplication() as Application
        val uid = "legacy-student-account"
        val legacyPreferenceKey = "student_key_" + digest(uid).take(32)
        val preferences = context.getSharedPreferences("student_gemini_keys_v1", Context.MODE_PRIVATE)
        preferences.edit().putString(legacyPreferenceKey, "not-valid-base64%%%").commit()

        val migrated = LegacyStudentGeminiKeyMigration.migrate(context, uid)

        assertFalse(migrated)
        assertNull(preferences.getString(legacyPreferenceKey, null))
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
