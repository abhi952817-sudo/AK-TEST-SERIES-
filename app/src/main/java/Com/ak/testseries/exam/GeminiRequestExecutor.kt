package Com.ak.testseries.exam

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shared transport for Gemini requests.
 * Prompt construction stays in the Universal* prompt contracts; this class only selects
 * the credential route and forwards the exact same prompt/system instruction.
 */
internal object GeminiRequestExecutor {
    data class Result(
        val responseText: String,
        val modelName: String,
        val modelVersion: String,
        val credentialMode: String
    )

    suspend fun execute(
        prompt: String,
        systemInstruction: String?,
        userApiKey: String? = null,
        modelName: String = "gemini-2.5-flash",
        taskType: String = "question_generation"
    ): Result {
        require(prompt.isNotBlank()) { "Gemini prompt cannot be blank." }
        val cleanUserKey = userApiKey?.trim()?.takeIf { it.isNotEmpty() }
        val credentialMode = if (cleanUserKey == null) "OWNER_MANAGED" else "USER_BYOK"
        val payload = hashMapOf<String, Any>(
            "credentialMode" to credentialMode,
            "modelName" to modelName,
            "taskType" to taskType,
            "prompt" to prompt
        )
        if (!systemInstruction.isNullOrBlank()) payload["systemInstruction"] = systemInstruction
        if (cleanUserKey != null) payload["apiKey"] = cleanUserKey

        val task = FirebaseFunctions.getInstance()
            .getHttpsCallable("generateGeminiContent")
            .call(payload)

        return suspendCancellableCoroutine { continuation ->
            task.addOnSuccessListener { httpsResult ->
                val data = httpsResult.data as? Map<*, *>
                val responseText = data?.get("responseText") as? String
                if (responseText.isNullOrBlank()) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Gemini returned an empty response."))
                    }
                } else if (continuation.isActive) {
                    continuation.resume(
                        Result(
                            responseText = responseText,
                            modelName = data["modelName"] as? String ?: modelName,
                            modelVersion = data["modelVersion"] as? String ?: "UNAVAILABLE",
                            credentialMode = data["credentialMode"] as? String ?: credentialMode
                        )
                    )
                }
            }.addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            continuation.invokeOnCancellation {
                task.cancel()
            }
        }
    }
}
