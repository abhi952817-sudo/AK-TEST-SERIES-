package Com.ak.testseries.ai

import Com.ak.testseries.utils.StudentAIKeyStore
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StudentAIProviderClientTest {

    @Test
    fun everyProviderSendsKeyInHeaderAndNeverInUrl() {
        StudentAIKeyStore.Provider.values().forEach { provider ->
            val request = build(provider)
            assertFalse(request.url.toString().contains("test-secret-key"))
            val expectedHeader = when (provider) {
                StudentAIKeyStore.Provider.GEMINI -> "x-goog-api-key"
                StudentAIKeyStore.Provider.ANTHROPIC -> "x-api-key"
                else -> "Authorization"
            }
            val expectedValue = if (expectedHeader == "Authorization") {
                "Bearer test-secret-key"
            } else {
                "test-secret-key"
            }
            assertEquals(expectedValue, request.header(expectedHeader))
        }
    }

    @Test
    fun modelCatalogueRequestsKeepKeysInHeaders() {
        StudentAIKeyStore.Provider.values().forEach { provider ->
            val request = StudentAIProviderClient.buildModelListRequest(provider, "catalogue-secret")
            assertFalse(request.url.toString().contains("catalogue-secret"))
            val header = if (provider == StudentAIKeyStore.Provider.GEMINI) "x-goog-api-key"
                else if (provider == StudentAIKeyStore.Provider.ANTHROPIC) "x-api-key"
                else "Authorization"
            assertTrue(request.header(header).orEmpty().contains("catalogue-secret"))
            assertEquals("GET", request.method)
        }
    }

    @Test
    fun geminiRequestUsesNativeGenerateContentContract() {
        val request = build(StudentAIKeyStore.Provider.GEMINI)
        val body = JSONObject(bodyString(request))
        assertTrue(request.url.toString().contains("/models/gemini-test:generateContent"))
        assertEquals("Explain cell division", body.getJSONArray("contents")
            .getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))
        assertEquals("Be concise", body.getJSONObject("systemInstruction")
            .getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test
    fun anthropicRequestIncludesModelAndRequiredVersionHeader() {
        val request = build(StudentAIKeyStore.Provider.ANTHROPIC)
        val body = JSONObject(bodyString(request))
        assertEquals("claude-test", body.getString("model"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
        assertEquals(512, body.getInt("max_tokens"))
    }

    @Test
    fun openAiCompatibleProvidersUseTheirOwnEndpointsAndModel() {
        val expectedUrls = mapOf(
            StudentAIKeyStore.Provider.OPENAI to "https://api.openai.com/v1/chat/completions",
            StudentAIKeyStore.Provider.GROQ to "https://api.groq.com/openai/v1/chat/completions",
            StudentAIKeyStore.Provider.CEREBRAS to "https://api.cerebras.ai/v1/chat/completions"
        )
        expectedUrls.forEach { (provider, url) ->
            val request = build(provider)
            assertEquals(url, request.url.toString())
            assertEquals("model-test", JSONObject(bodyString(request)).getString("model"))
        }
    }

    @Test
    fun modelCatalogueParsingFiltersGeminiModelsWithoutGenerationSupport() {
        val models = StudentAIProviderClient.parseModelList(
            StudentAIKeyStore.Provider.GEMINI,
            """{"models":[{"name":"models/gemini-text","supportedGenerationMethods":["generateContent"]},{"name":"models/gemini-embed","supportedGenerationMethods":["embedContent"]},{"name":"models/gemini-legacy"}]}"""
        )
        assertEquals(listOf("gemini-legacy", "gemini-text"), models)
    }

    @Test
    fun modelCatalogueParsingReadsOpenAiCompatibleIds() {
        val models = StudentAIProviderClient.parseModelList(
            StudentAIKeyStore.Provider.CEREBRAS,
            """{"data":[{"id":"model-z"},{"id":"model-a"},{"id":"model-z"}]}"""
        )
        assertEquals(listOf("model-a", "model-z"), models)
    }

    @Test
    fun parsesGeminiTextAndActualUsageMetadata() {
        val result = StudentAIProviderClient.parseResponse(
            StudentAIKeyStore.Provider.GEMINI,
            "gemini-test",
            """{"candidates":[{"content":{"parts":[{"text":"Generated answer"}]}}],"usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":7,"totalTokenCount":19}}"""
        )
        assertEquals("Generated answer", result.text)
        assertEquals(12L, result.usage.inputTokens)
        assertEquals(7L, result.usage.outputTokens)
        assertEquals(19L, result.usage.totalTokens)
    }

    @Test
    fun parsesAnthropicAndOpenAiCompatibleResponses() {
        val anthropic = StudentAIProviderClient.parseResponse(
            StudentAIKeyStore.Provider.ANTHROPIC,
            "claude-test",
            """{"content":[{"type":"text","text":"Claude answer"}],"usage":{"input_tokens":8,"output_tokens":5}}"""
        )
        assertEquals("Claude answer", anthropic.text)
        assertEquals(8L, anthropic.usage.inputTokens)

        val openAi = StudentAIProviderClient.parseResponse(
            StudentAIKeyStore.Provider.GROQ,
            "llama-test",
            """{"choices":[{"message":{"content":"Groq answer"}}],"usage":{"prompt_tokens":9,"completion_tokens":4,"total_tokens":13}}"""
        )
        assertEquals("Groq answer", openAi.text)
        assertEquals(13L, openAi.usage.totalTokens)
    }

    private fun build(provider: StudentAIKeyStore.Provider): Request =
        StudentAIProviderClient.buildHttpRequest(
            provider = provider,
            apiKey = "test-secret-key",
            model = when (provider) {
                StudentAIKeyStore.Provider.ANTHROPIC -> "claude-test"
                StudentAIKeyStore.Provider.GEMINI -> "gemini-test"
                else -> "model-test"
            },
            prompt = "Explain cell division",
            systemInstruction = "Be concise",
            maxOutputTokens = 512
        )

    private fun bodyString(request: Request): String {
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        return buffer.readUtf8()
    }
}
