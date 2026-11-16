package Com.ak.testseries.utils

import android.content.Context
import Com.ak.testseries.ai.StudentAITextResponse
import java.security.MessageDigest

/** Local, non-secret usage metadata returned by the student's provider. No cost is estimated. */
data class StudentAIUsageSnapshot(
    val successfulRequests: Long,
    val lastInputTokens: Long?,
    val lastOutputTokens: Long?,
    val lastTotalTokens: Long?
)

object StudentAIUsageStore {
    private const val PREFS_NAME = "student_ai_usage_v1"

    @Synchronized
    fun record(context: Context, uid: String, response: StudentAITextResponse) {
        if (uid.isBlank()) return
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefix = prefix(uid, response.provider)
        val editor = preferences.edit()
            .putLong(prefix + "_requests", preferences.getLong(prefix + "_requests", 0L) + 1L)
            .putLong(prefix + "_last_at", System.currentTimeMillis())

        response.usage.inputTokens?.let { editor.putLong(prefix + "_last_input", it) }
            ?: editor.remove(prefix + "_last_input")
        response.usage.outputTokens?.let { editor.putLong(prefix + "_last_output", it) }
            ?: editor.remove(prefix + "_last_output")
        response.usage.totalTokens?.let { editor.putLong(prefix + "_last_total", it) }
            ?: editor.remove(prefix + "_last_total")
        check(editor.commit()) { "Student AI usage metadata could not be saved." }
    }

    fun snapshot(context: Context, uid: String, provider: StudentAIKeyStore.Provider): StudentAIUsageSnapshot {
        if (uid.isBlank()) return StudentAIUsageSnapshot(0L, null, null, null)
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefix = prefix(uid, provider)
        return StudentAIUsageSnapshot(
            successfulRequests = preferences.getLong(prefix + "_requests", 0L),
            lastInputTokens = preferences.getLong(prefix + "_last_input", -1L).takeIf { it >= 0L },
            lastOutputTokens = preferences.getLong(prefix + "_last_output", -1L).takeIf { it >= 0L },
            lastTotalTokens = preferences.getLong(prefix + "_last_total", -1L).takeIf { it >= 0L }
        )
    }

    @Synchronized
    fun clearAll(context: Context, uid: String) {
        if (uid.isBlank()) return
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        StudentAIKeyStore.Provider.values().forEach { provider ->
            val prefix = prefix(uid, provider)
            listOf("_requests", "_last_at", "_last_input", "_last_output", "_last_total")
                .forEach { suffix -> editor.remove(prefix + suffix) }
        }
        editor.commit()
    }

    private fun prefix(uid: String, provider: StudentAIKeyStore.Provider): String =
        "usage_" + MessageDigest.getInstance("SHA-256")
            .digest((uid + "|" + provider.id).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(40)
}
