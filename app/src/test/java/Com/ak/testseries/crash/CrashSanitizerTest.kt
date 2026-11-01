package Com.ak.testseries.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class CrashSanitizerTest {

    @Test
    fun testRedactsGoogleApiKey() {
        val input = "Failed to connect with API key AIzaSyA1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q in request header"
        val sanitized = CrashSanitizer.sanitizeText(input)

        assertFalse("API key should not be present in sanitized output", sanitized.contains("AIzaSyA1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q"))
        assertTrue("Redacted token marker should be present", sanitized.contains("[REDACTED_API_KEY]"))
    }

    @Test
    fun testRedactsBearerAndJwtTokens() {
        val bearerInput = "Authorization failed for Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        val sanitized = CrashSanitizer.sanitizeText(bearerInput)

        assertFalse("JWT token should not be present", sanitized.contains("eyJzdWIiOi"))
        assertTrue("Should contain redaction marker", sanitized.contains("[REDACTED_"))
    }

    @Test
    fun testRedactsEmailAndPhone() {
        val input = "User student_neet_99@testseries.org with phone +91 9876543210 encountered a parsing error"
        val sanitized = CrashSanitizer.sanitizeText(input)

        assertFalse("User email must be redacted", sanitized.contains("student_neet_99@testseries.org"))
        assertTrue("Email redaction marker present", sanitized.contains("[REDACTED_EMAIL]"))
        assertFalse("User phone number must be redacted", sanitized.contains("9876543210"))
    }

    @Test
    fun testRedactsInternalFilePaths() {
        val input = "FileNotFoundException: /data/user/0/Com.ak.testseries/databases/neet_exam.db (Permission denied)"
        val sanitized = CrashSanitizer.sanitizeText(input)

        assertFalse("Internal path should not be visible", sanitized.contains("/data/user/0/Com.ak.testseries"))
        assertTrue("Storage placeholder should be visible", sanitized.contains("[APP_INTERNAL_STORAGE]"))
    }

    @Test
    fun testRedactsSensitiveSqlQueries() {
        val input = "SQLiteException: Error executing SELECT id, password, auth_token FROM users WHERE email='alice@test.com';"
        val sanitized = CrashSanitizer.sanitizeText(input)

        assertFalse("Raw SQL query must not leak", sanitized.contains("SELECT id, password"))
        assertTrue("Sanitized query marker present", sanitized.contains("[REDACTED_DATABASE_OPERATION]"))
    }

    @Test
    fun testRedactsUrlQuerySecrets() {
        val input = "HTTP 403 at https://api.service.com/v1/generate?api_key=secretKey123&token=myTokenVal&type=exam"
        val sanitized = CrashSanitizer.sanitizeText(input)

        assertFalse("api_key param secret should be redacted", sanitized.contains("secretKey123"))
        assertFalse("token param value should be redacted", sanitized.contains("myTokenVal"))
        assertTrue("Sanitized param marker present", sanitized.contains("api_key=[REDACTED]"))
    }

    @Test
    fun testCategorizesOutOfMemoryError() {
        val oom = OutOfMemoryError("Failed to allocate a 104857600 byte allocation with 4194304 free bytes")
        val crash = CrashSanitizer.sanitize(oom)

        assertEquals("Memory Optimization", crash.category)
        assertTrue(crash.safeUserMessage.contains("low on temporary memory", ignoreCase = true))
        assertTrue(crash.incidentId.startsWith("ERR-"))
    }

    @Test
    fun testCategorizesNetworkError() {
        val ex = UnknownHostException("Unable to resolve host aktestseries.app: No address associated with hostname")
        val crash = CrashSanitizer.sanitize(ex)

        assertEquals("Network Connection", crash.category)
        assertTrue(crash.safeUserMessage.contains("network operation", ignoreCase = true))
    }

    @Test
    fun testIncidentIdFormatting() {
        val incidentId = CrashSanitizer.generateIncidentId("NullPointerException", "Attempt to invoke virtual method")
        assertTrue("Incident ID should start with ERR-", incidentId.startsWith("ERR-"))
        assertEquals("Incident ID segments count", 3, incidentId.split("-").size)
    }

    @Test
    fun testSanitizedStackTraceIsBounded() {
        val ex = RuntimeException("Deep chained exception")
        val crash = CrashSanitizer.sanitize(ex)

        assertTrue(crash.sanitizedDetails.isNotBlank())
        assertTrue("Details must be bounded to reasonable size", crash.sanitizedDetails.length <= 2500)
    }
}
