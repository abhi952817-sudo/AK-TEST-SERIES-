package Com.ak.testseries.ai

import Com.ak.testseries.utils.StudentAIKeyStore
import Com.ak.testseries.utils.StudentAIUsageStore
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Student BYOK text-generation transport.
 *
 * Requests go directly from the device to the selected provider. Student API keys
 * are read from Android Keystore-backed local storage and are sent only in headers.
 * This client must not be used for Community Doubts, whose workflow is manual-only.
 */
data class StudentAITextRequest(
    val provider: StudentAIKeyStore.Provider,
    val model: String,
    val prompt: String,
    val systemInstruction: String? = null,
    val maxOutputTokens: Int = 2048
)

data class StudentAIUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null
)

data class StudentAITextResponse(
    val provider: StudentAIKeyStore.Provider,
    val model: String,
    val text: String,
    val usage: StudentAIUsage
)

class StudentAIProviderException(
    val httpStatus: Int? = null,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

object StudentAIProviderClient {
    private const val MAX_PROMPT_CHARS = 100_000
    private const val MAX_SYSTEM_INSTRUCTION_CHARS = 20_000

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Runs a text request using the signed-in student's own provider key.
     * No key is sent to Firebase, Firestore, Cloud Functions, logs or URL parameters.
     */
    suspend fun generate(
        context: Context,
        authenticatedUid: String,
        request: StudentAITextRequest
    ): StudentAITextResponse = withContext(Dispatchers.IO) {
        require(authenticatedUid.isNotBlank()) { "Sign in before using a personal AI provider." }
        val signedInUid = FirebaseAuth.getInstance().currentUser?.uid
        if (signedInUid.isNullOrBlank() || signedInUid != authenticatedUid) {
            throw StudentAIProviderException(message = "The signed-in account changed. Reopen provider settings and try again.")
        }
        require(request.model.isNotBlank()) { "Select a provider model first." }
        require(request.prompt.isNotBlank()) { "Prompt cannot be empty." }
        require(request.prompt.length <= MAX_PROMPT_CHARS) { "Prompt is too large. Split the source into smaller batches." }
        require((request.systemInstruction?.length ?: 0) <= MAX_SYSTEM_INSTRUCTION_CHARS) {
            "System instruction is too large."
        }
        require(request.maxOutputTokens in 1..32768) { "Output token limit is outside the supported range." }

        val apiKey = StudentAIKeyStore.read(context.applicationContext, authenticatedUid, request.provider)
            ?.takeIf { it.isNotBlank() }
            ?: throw StudentAIProviderException(message = "No personal API key is configured for this provider.")

        val httpRequest = buildHttpRequest(
            provider = request.provider,
            apiKey = apiKey,
            model = request.model.trim(),
            prompt = request.prompt,
            systemInstruction = request.systemInstruction,
            maxOutputTokens = request.maxOutputTokens
        )

        val rawBody = try {
            httpClient.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw StudentAIProviderException(
                        httpStatus = response.code,
                        message = providerErrorMessage(response.code)
                    )
                }
                body
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StudentAIProviderException) {
            throw e
        } catch (e: Exception) {
            throw StudentAIProviderException(message = "The AI provider request failed. Check your connection and provider settings.", cause = e)
        }

        if (FirebaseAuth.getInstance().currentUser?.uid != authenticatedUid) {
            throw StudentAIProviderException(message = "The signed-in account changed. The response was discarded.")
        }
        val parsed = parseResponse(request.provider, request.model.trim(), rawBody)
        try {
            StudentAIUsageStore.record(context.applicationContext, authenticatedUid, parsed)
        } catch (_: Exception) {
            // Usage persistence is best-effort; it must not discard a valid provider response.
        }
        parsed
    }

    /** Executes through the student's saved default provider and model. */
    suspend fun generatePreferred(
        context: Context,
        authenticatedUid: String,
        prompt: String,
        systemInstruction: String? = null,
        maxOutputTokens: Int = 2048
    ): StudentAITextResponse {
        val provider = StudentAIKeyStore.preferredProvider(context, authenticatedUid)
            ?: throw StudentAIProviderException(message = "Choose a personal AI provider in Settings first.")
        val model = StudentAIKeyStore.preferredModel(context, authenticatedUid, provider)
            ?.takeIf { it.isNotBlank() }
            ?: throw StudentAIProviderException(message = "Choose a model for your personal AI provider in Settings first.")
        return generate(
            context = context,
            authenticatedUid = authenticatedUid,
            request = StudentAITextRequest(
                provider = provider,
                model = model,
                prompt = prompt,
                systemInstruction = systemInstruction,
                maxOutputTokens = maxOutputTokens
            )
        )
    }

    /** Verifies a personal key by requesting the provider's model catalogue. */
    suspend fun verifyApiKey(
        provider: StudentAIKeyStore.Provider,
        apiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Enter an API key first." }
        val request = buildModelListRequest(provider, apiKey.trim())
        val body = try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw StudentAIProviderException(
                        httpStatus = response.code,
                        message = providerErrorMessage(response.code)
                    )
                }
                responseBody
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StudentAIProviderException) {
            throw e
        } catch (e: Exception) {
            throw StudentAIProviderException(
                message = "Could not verify the provider key. Check your connection and try again.",
                cause = e
            )
        }
        parseModelList(provider, body)
    }

    internal fun parseModelList(provider: StudentAIKeyStore.Provider, body: String): List<String> {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw StudentAIProviderException(message = "The provider returned an unreadable model list.", cause = e)
        }
        val discovered = when (provider) {
            StudentAIKeyStore.Provider.GEMINI -> {
                val items = json.optJSONArray("models") ?: JSONArray()
                val models = ArrayList<String>()
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val methods = item.optJSONArray("supportedGenerationMethods")
                    if (methods != null && (0 until methods.length()).none { methods.optString(it) == "generateContent" }) {
                        continue
                    }
                    val modelId = item.optString("name").removePrefix("models/")
                    if (modelId.isNotBlank()) models.add(modelId)
                }
                models
            }
            else -> {
                val items = json.optJSONArray("data") ?: JSONArray()
                val models = ArrayList<String>()
                for (index in 0 until items.length()) {
                    val modelId = items.optJSONObject(index)?.optString("id").orEmpty()
                    if (modelId.isNotBlank()) models.add(modelId)
                }
                models
            }
        }.distinct().sorted()
        if (discovered.isEmpty()) {
            throw StudentAIProviderException(message = "The key was accepted, but no compatible text models were returned.")
        }
        return discovered
    }

    internal fun buildModelListRequest(
        provider: StudentAIKeyStore.Provider,
        apiKey: String
    ): Request {
        require(apiKey.isNotBlank())
        val url = when (provider) {
            StudentAIKeyStore.Provider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100"
            StudentAIKeyStore.Provider.OPENAI -> "https://api.openai.com/v1/models"
            StudentAIKeyStore.Provider.GROQ -> "https://api.groq.com/openai/v1/models"
            StudentAIKeyStore.Provider.CEREBRAS -> "https://api.cerebras.ai/v1/models"
            StudentAIKeyStore.Provider.ANTHROPIC -> "https://api.anthropic.com/v1/models"
        }
        val builder = Request.Builder().url(url).get().header("Accept", "application/json").header("Cache-Control", "no-store")
        when (provider) {
            StudentAIKeyStore.Provider.GEMINI -> builder.header("x-goog-api-key", apiKey)
            StudentAIKeyStore.Provider.ANTHROPIC -> builder
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
            else -> builder.header("Authorization", "Bearer $apiKey")
        }
        return builder.build()
    }

    internal fun buildHttpRequest(
        provider: StudentAIKeyStore.Provider,
        apiKey: String,
        model: String,
        prompt: String,
        systemInstruction: String?,
        maxOutputTokens: Int
    ): Request {
        require(apiKey.isNotBlank())
        require(model.matches(Regex("[A-Za-z0-9._:-]{1,160}"))) { "Invalid model identifier." }

        val body = when (provider) {
            StudentAIKeyStore.Provider.GEMINI -> geminiBody(prompt, systemInstruction, maxOutputTokens)
            StudentAIKeyStore.Provider.OPENAI,
            StudentAIKeyStore.Provider.GROQ,
            StudentAIKeyStore.Provider.CEREBRAS -> openAiCompatibleBody(provider, model, prompt, systemInstruction, maxOutputTokens)
            StudentAIKeyStore.Provider.ANTHROPIC -> anthropicBody(model, prompt, systemInstruction, maxOutputTokens)
        }

        val url = when (provider) {
            StudentAIKeyStore.Provider.GEMINI ->
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            StudentAIKeyStore.Provider.OPENAI -> "https://api.openai.com/v1/chat/completions"
            StudentAIKeyStore.Provider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
            StudentAIKeyStore.Provider.CEREBRAS -> "https://api.cerebras.ai/v1/chat/completions"
            StudentAIKeyStore.Provider.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
        }

        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")

        when (provider) {
            StudentAIKeyStore.Provider.GEMINI -> builder.header("x-goog-api-key", apiKey)
            StudentAIKeyStore.Provider.ANTHROPIC -> builder
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
            else -> builder.header("Authorization", "Bearer $apiKey")
        }
        return builder.build()
    }

    internal fun parseResponse(
        provider: StudentAIKeyStore.Provider,
        model: String,
        rawBody: String
    ): StudentAITextResponse {
        val json = try {
            JSONObject(rawBody)
        } catch (e: Exception) {
            throw StudentAIProviderException(message = "The AI provider returned an unreadable response.", cause = e)
        }

        val text = when (provider) {
            StudentAIKeyStore.Provider.GEMINI -> {
                val candidates = json.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                collectTextParts(parts)
            }
            StudentAIKeyStore.Provider.ANTHROPIC -> collectTextParts(json.optJSONArray("content"))
            StudentAIKeyStore.Provider.OPENAI,
            StudentAIKeyStore.Provider.GROQ,
            StudentAIKeyStore.Provider.CEREBRAS -> {
                val choice = json.optJSONArray("choices")?.optJSONObject(0)
                val message = choice?.optJSONObject("message")
                when (val content = message?.opt("content")) {
                    is String -> content.takeIf { it.isNotBlank() }
                    is JSONArray -> collectTextParts(content)
                    else -> null
                }
            }
        }?.trim().orEmpty()

        if (text.isBlank()) {
            throw StudentAIProviderException(message = "The AI provider returned no usable text.")
        }

        val usageJson = when (provider) {
            StudentAIKeyStore.Provider.GEMINI -> json.optJSONObject("usageMetadata")
            StudentAIKeyStore.Provider.ANTHROPIC -> json.optJSONObject("usage")
            else -> json.optJSONObject("usage")
        }
        val usage = when (provider) {
            StudentAIKeyStore.Provider.GEMINI -> StudentAIUsage(
                inputTokens = usageJson?.longOrNull("promptTokenCount"),
                outputTokens = usageJson?.longOrNull("candidatesTokenCount"),
                totalTokens = usageJson?.longOrNull("totalTokenCount")
            )
            StudentAIKeyStore.Provider.ANTHROPIC -> StudentAIUsage(
                inputTokens = usageJson?.longOrNull("input_tokens"),
                outputTokens = usageJson?.longOrNull("output_tokens"),
                totalTokens = null
            )
            else -> StudentAIUsage(
                inputTokens = usageJson?.longOrNull("prompt_tokens"),
                outputTokens = usageJson?.longOrNull("completion_tokens"),
                totalTokens = usageJson?.longOrNull("total_tokens")
            )
        }

        return StudentAITextResponse(provider, model, text, usage)
    }

    private fun geminiBody(prompt: String, system: String?, maxTokens: Int): JSONObject =
        JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            if (!system.isNullOrBlank()) {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            }
            put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens))
        }

    private fun openAiCompatibleBody(
        provider: StudentAIKeyStore.Provider,
        model: String,
        prompt: String,
        system: String?,
        maxTokens: Int
    ): JSONObject = JSONObject().apply {
        put("model", model)
        val messages = JSONArray()
        if (!system.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", system))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        put("messages", messages)
        when (provider) {
            StudentAIKeyStore.Provider.OPENAI -> put("max_completion_tokens", maxTokens)
            else -> put("max_tokens", maxTokens)
        }
    }

    private fun anthropicBody(model: String, prompt: String, system: String?, maxTokens: Int): JSONObject =
        JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens)
            if (!system.isNullOrBlank()) put("system", system)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }

    private fun collectTextParts(parts: JSONArray?): String? {
        if (parts == null) return null
        val collected = ArrayList<String>()
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            part.optString("text").takeIf { it.isNotBlank() }?.let(collected::add)
        }
        return collected.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key).takeIf { it >= 0L } else null

    private fun providerErrorMessage(status: Int): String = when (status) {
        400 -> "The provider rejected the request. Check the selected model and request settings."
        401, 403 -> "The provider did not accept this API key or its permissions."
        404 -> "The selected model or provider endpoint was not found."
        408, 429 -> "The provider is busy or its rate limit/quota was reached. Try again later."
        in 500..599 -> "The AI provider is temporarily unavailable."
        else -> "The AI provider request failed (HTTP $status)."
    }
}
