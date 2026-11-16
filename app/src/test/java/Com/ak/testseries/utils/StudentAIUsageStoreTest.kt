package Com.ak.testseries.utils

import Com.ak.testseries.ai.StudentAITextResponse
import Com.ak.testseries.ai.StudentAIUsage
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StudentAIUsageStoreTest {

    @Test
    fun recordsOnlyProviderReportedUsageForTheMatchingAccountAndProvider() {
        val context = RuntimeEnvironment.getApplication<Application>()
        StudentAIUsageStore.record(
            context,
            "student-a",
            StudentAITextResponse(
                StudentAIKeyStore.Provider.GROQ,
                "model-a",
                "answer",
                StudentAIUsage(inputTokens = 11, outputTokens = 5, totalTokens = 16)
            )
        )

        val snapshot = StudentAIUsageStore.snapshot(context, "student-a", StudentAIKeyStore.Provider.GROQ)
        assertEquals(1L, snapshot.successfulRequests)
        assertEquals(11L, snapshot.lastInputTokens)
        assertEquals(5L, snapshot.lastOutputTokens)
        assertEquals(16L, snapshot.lastTotalTokens)
        assertEquals(0L, StudentAIUsageStore.snapshot(context, "student-b", StudentAIKeyStore.Provider.GROQ).successfulRequests)
        assertEquals(0L, StudentAIUsageStore.snapshot(context, "student-a", StudentAIKeyStore.Provider.OPENAI).successfulRequests)
    }

    @Test
    fun missingProviderTokenMetadataIsNotInvented() {
        val context = RuntimeEnvironment.getApplication<Application>()
        StudentAIUsageStore.record(
            context,
            "student-a",
            StudentAITextResponse(
                StudentAIKeyStore.Provider.ANTHROPIC,
                "claude-model",
                "answer",
                StudentAIUsage(inputTokens = 20, outputTokens = 8, totalTokens = null)
            )
        )

        val snapshot = StudentAIUsageStore.snapshot(context, "student-a", StudentAIKeyStore.Provider.ANTHROPIC)
        assertEquals(20L, snapshot.lastInputTokens)
        assertEquals(8L, snapshot.lastOutputTokens)
        assertNull(snapshot.lastTotalTokens)
    }

    @Test
    fun clearAllRemovesOnlyTheSelectedUsersUsage() {
        val context = RuntimeEnvironment.getApplication<Application>()
        val response = StudentAITextResponse(
            StudentAIKeyStore.Provider.GEMINI,
            "gemini-model",
            "answer",
            StudentAIUsage(totalTokens = 9)
        )
        StudentAIUsageStore.record(context, "student-a", response)
        StudentAIUsageStore.record(context, "student-b", response)

        StudentAIUsageStore.clearAll(context, "student-a")

        assertEquals(0L, StudentAIUsageStore.snapshot(context, "student-a", StudentAIKeyStore.Provider.GEMINI).successfulRequests)
        assertEquals(1L, StudentAIUsageStore.snapshot(context, "student-b", StudentAIKeyStore.Provider.GEMINI).successfulRequests)
    }
}
