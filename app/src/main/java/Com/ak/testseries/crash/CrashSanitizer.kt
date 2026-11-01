package Com.ak.testseries.crash

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * Sanitizes crash reports, exceptions, and diagnostics to eliminate sensitive information leakage
 * (CWE-209 / OWASP MASVS Security Standards).
 *
 * Removes:
 * - API keys, Gemini tokens, Bearer tokens, JWTs, passwords, auth headers
 * - Internal file paths (/data/user/0/..., /data/data/..., etc.)
 * - PII (Email addresses, phone numbers)
 * - SQL query syntax / schema dumps
 * - Query parameter secrets in URLs
 */
object CrashSanitizer {

    private const val MAX_SANITIZED_LENGTH = 2048
    private const val MAX_STACK_TRACE_FRAMES = 15

    // Sensitive data regex patterns
    private val GOOGLE_API_KEY_PATTERN = Pattern.compile("AIza[0-9A-Za-z-_]{35}")
    private val BEARER_TOKEN_PATTERN = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9\\-_.~+/]+=*")
    private val GENERIC_SECRET_PATTERN = Pattern.compile("(?i)(api[_-]?key|secret|token|password|auth|credential|access[_-]?token)[\"']?\\s*[:=]\\s*[\"']?([A-Za-z0-9_\\-.]{6,})[\"']?")
    private val JWT_TOKEN_PATTERN = Pattern.compile("ey[A-Za-z0-9-_=]{10,}\\.[A-Za-z0-9-_=]{10,}\\.[A-Za-z0-9-_.+/=]{6,}")
    private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val PHONE_PATTERN = Pattern.compile("\\b(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b")
    private val INTERNAL_STORAGE_PATH_PATTERN = Pattern.compile("/data/(?:user/\\d+|data)/[a-zA-Z0-9._-]+(?:/[a-zA-Z0-9._-]+)*")
    private val EXTERNAL_STORAGE_PATH_PATTERN = Pattern.compile("/storage/emulated/\\d+/[a-zA-Z0-9._-]+(?:/[a-zA-Z0-9._-]+)*")
    private val URL_SECRET_PARAM_PATTERN = Pattern.compile("([?&](?:token|key|api_key|password|secret|auth|access_token)=)[^&\\s]+")
    private val SQL_QUERY_PATTERN = Pattern.compile("(?i)\\b(SELECT\\s+.+?\\s+FROM|INSERT\\s+INTO\\s+.+?VALUES|UPDATE\\s+.+?\\s+SET|DELETE\\s+FROM\\s+).+?(?:;|$)", Pattern.DOTALL)

    data class SanitizedCrash(
        val incidentId: String,
        val category: String,
        val safeUserMessage: String,
        val rootExceptionType: String,
        val sanitizedDetails: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Sanitizes an arbitrary string message, removing all secrets, paths, and PII.
     */
    fun sanitizeText(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var sanitized = input

        // 1. Redact API keys & tokens
        sanitized = GOOGLE_API_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_API_KEY]")
        sanitized = JWT_TOKEN_PATTERN.matcher(sanitized).replaceAll("[REDACTED_JWT_TOKEN]")
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED_TOKEN]")
        sanitized = GENERIC_SECRET_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED_CREDENTIAL]")
        sanitized = URL_SECRET_PARAM_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]")

        // 2. Redact PII
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[REDACTED_EMAIL]")
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[REDACTED_PHONE]")

        // 3. Redact internal file system paths
        sanitized = INTERNAL_STORAGE_PATH_PATTERN.matcher(sanitized).replaceAll("[APP_INTERNAL_STORAGE]")
        sanitized = EXTERNAL_STORAGE_PATH_PATTERN.matcher(sanitized).replaceAll("[APP_EXTERNAL_STORAGE]")

        // 4. Redact raw SQL commands
        sanitized = SQL_QUERY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_DATABASE_OPERATION]")

        return sanitized
    }

    /**
     * Cleans and prepares a production-safe SanitizedCrash object from a Throwable.
     */
    fun sanitize(throwable: Throwable?): SanitizedCrash {
        val rootCause = getRootCause(throwable)
        val rootType = rootCause?.javaClass?.simpleName ?: (throwable?.javaClass?.simpleName ?: "UnknownException")
        val rawMessage = throwable?.message ?: rootCause?.message ?: "An unhandled exception occurred"

        val incidentId = generateIncidentId(rootType, rawMessage)
        val (category, safeMessage) = categorizeError(throwable, rootCause)

        // Generate bounded, sanitized stack trace
        val sanitizedTrace = buildSanitizedStackTrace(throwable)

        return SanitizedCrash(
            incidentId = incidentId,
            category = category,
            safeUserMessage = safeMessage,
            rootExceptionType = rootType,
            sanitizedDetails = sanitizedTrace
        )
    }

    /**
     * Categorizes the exception into high-level user-facing feedback without leaking internal details.
     */
    private fun categorizeError(throwable: Throwable?, rootCause: Throwable?): Pair<String, String> {
        val t = rootCause ?: throwable ?: return Pair("Application Stability", "A safe restart resolved this issue.")
        val typeName = t.javaClass.name.lowercase(Locale.ROOT)
        val message = (t.message ?: "").lowercase(Locale.ROOT)

        return when {
            t is OutOfMemoryError || typeName.contains("outofmemory") -> {
                Pair(
                    "Memory Optimization",
                    "The device was running low on temporary memory. The app has been cleanly reset to safeguard your data."
                )
            }
            typeName.contains("timeout") || typeName.contains("unknownhost") || typeName.contains("connectexception") || message.contains("network") -> {
                Pair(
                    "Network Connection",
                    "A network operation could not complete in time. Please check your internet connectivity."
                )
            }
            typeName.contains("sqlite") || message.contains("database") || message.contains("disk full") -> {
                Pair(
                    "Storage & Cache",
                    "A local database or file storage limit was reached. Your saved tests and progress are securely preserved."
                )
            }
            typeName.contains("security") || message.contains("permission") -> {
                Pair(
                    "Security & Permissions",
                    "An action was prevented due to system permission policies."
                )
            }
            typeName.contains("parse") || typeName.contains("format") || typeName.contains("json") -> {
                Pair(
                    "Data Formatting",
                    "The requested document or question data could not be parsed safely."
                )
            }
            else -> {
                Pair(
                    "Application Stability",
                    "The app encountered an unexpected condition. Restarting will resume normal operations."
                )
            }
        }
    }

    /**
     * Builds a safe, bounded stack trace stripped of confidential details and limited to top frames.
     */
    private fun buildSanitizedStackTrace(throwable: Throwable?): String {
        if (throwable == null) return "No stack trace available."

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val fullTrace = sw.toString()

        val lines = fullTrace.lines()
        val topLines = lines.take(MAX_STACK_TRACE_FRAMES)
        val joined = topLines.joinToString("\n")

        val sanitized = sanitizeText(joined)

        return if (sanitized.length > MAX_SANITIZED_LENGTH) {
            sanitized.take(MAX_SANITIZED_LENGTH) + "\n... [TRUNCATED FOR SECURITY]"
        } else {
            sanitized
        }
    }

    /**
     * Extracts root cause from throwable chain.
     */
    private fun getRootCause(throwable: Throwable?): Throwable? {
        var current = throwable
        while (current?.cause != null && current.cause !== current) {
            current = current.cause
        }
        return current
    }

    /**
     * Generates a non-reversible incident code (e.g., ERR-20261031-149832).
     */
    fun generateIncidentId(errorType: String, message: String): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val seed = (errorType + message + System.currentTimeMillis()).hashCode()
        val suffix = (abs(seed) % 1_000_000).toString().padStart(6, '0')
        return "ERR-$datePart-$suffix"
    }
}
