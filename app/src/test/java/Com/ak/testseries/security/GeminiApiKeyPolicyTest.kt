package Com.ak.testseries.security

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiApiKeyPolicyTest {

    @Test
    fun studentWithoutPersonalKeyCannotFallBackToAdminKeys() {
        assertEquals(
            emptyList<Pair<Int, String>>(),
            GeminiApiKeyPolicy.selectKeys("student-1", null, false, "admin-primary", "admin-secondary")
        )
    }

    @Test
    fun personalKeyIsExclusiveEvenWhenAdminKeysExist() {
        assertEquals(
            listOf(2 to "student-personal"),
            GeminiApiKeyPolicy.selectKeys("student-1", "student-personal", false, "admin-primary", "admin-secondary")
        )
    }

    @Test
    fun verifiedAdminMayUseAdminKeysWhenNoPersonalKeyExists() {
        assertEquals(
            listOf(0 to "admin-primary", 1 to "admin-secondary"),
            GeminiApiKeyPolicy.selectKeys("admin-1", null, true, "admin-primary", "admin-secondary")
        )
    }

    @Test
    fun personalKeyMustBeRotatedAtSevenDayBoundary() {
        val sevenDays = GeminiApiKeyPolicy.KEY_ROTATION_INTERVAL_MILLIS
        assertEquals(false, GeminiApiKeyPolicy.isPersonalKeyFresh(1_000L, 1_000L + sevenDays))
        assertEquals(true, GeminiApiKeyPolicy.isPersonalKeyFresh(1_000L, 1_000L + sevenDays - 1L))
        assertEquals(false, GeminiApiKeyPolicy.isPersonalKeyFresh(0L, 10_000L))
    }

    @Test
    fun signedOutUserCannotUseAnyKey() {
        assertEquals(
            emptyList<Pair<Int, String>>(),
            GeminiApiKeyPolicy.selectKeys("", "personal", true, "admin-primary", "admin-secondary")
        )
    }
}
