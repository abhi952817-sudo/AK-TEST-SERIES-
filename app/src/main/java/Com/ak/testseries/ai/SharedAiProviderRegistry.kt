package Com.ak.testseries.ai

import Com.ak.testseries.data.remote.FirestoreHelper
import android.content.Context
import kotlinx.coroutines.tasks.await

/**
 * Single Shared Registry of the 5 Free AI Providers available to both Admin and Student panels.
 *
 * Rules:
 * - Same 5 providers, same Universal Prompt IDs, same Output JSON Schema.
 * - Never stores plain API keys here or in Firestore.
 * - Admin keys live in Google Secret Manager; Student keys live in Android Keystore + Encrypted Local Storage.
 */
object SharedAiProviderRegistry {

    val providers: List<AiProviderSpec> = listOf(
        AiProviderSpec(
            connectorId = AiConnectorId.C01_GEMINI,
            displayName = "Google Gemini API",
            adminSecretName = "ADMIN_GEMINI_API_KEY",
            requiresAccountId = false,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            docsUrl = "https://aistudio.google.com/docs/rate-limits",
            freeQuotaType = FreeQuotaType.DAILY_REQUESTS_AND_TPM,
            freeQuotaSummary = "Free Tier on select Gemini Flash models (RPM/TPM/RPD limits apply)",
            supportsJsonMode = true,
            supportsVisionOrPdf = true,
            defaultModelId = "gemini-2.5-flash",
            availableModels = listOf(
                AiModelSpec(
                    modelId = "gemini-2.5-flash",
                    displayName = "Gemini 2.5 Flash",
                    contextWindowTokens = 1_000_000,
                    maxOutputTokens = 8_192,
                    supportsJsonMode = true,
                    supportsMultimodal = true
                ),
                AiModelSpec(
                    modelId = "gemini-2.0-flash",
                    displayName = "Gemini 2.0 Flash",
                    contextWindowTokens = 1_000_000,
                    maxOutputTokens = 8_192,
                    supportsJsonMode = true,
                    supportsMultimodal = true
                )
            )
        ),
        AiProviderSpec(
            connectorId = AiConnectorId.C02_GROQ,
            displayName = "GroqCloud",
            adminSecretName = "ADMIN_GROQ_API_KEY",
            requiresAccountId = false,
            baseUrl = "https://api.groq.com/openai/v1",
            docsUrl = "https://console.groq.com/docs/rate-limits",
            freeQuotaType = FreeQuotaType.DAILY_REQUESTS_AND_TPM,
            freeQuotaSummary = "Free tier with per-model RPM, TPM, and RPD rate limits",
            supportsJsonMode = true,
            supportsVisionOrPdf = false,
            defaultModelId = "llama-3.3-70b-versatile",
            availableModels = listOf(
                AiModelSpec(
                    modelId = "llama-3.3-70b-versatile",
                    displayName = "Llama 3.3 70B Versatile",
                    contextWindowTokens = 128_000,
                    maxOutputTokens = 8_192,
                    supportsJsonMode = true,
                    supportsMultimodal = false
                ),
                AiModelSpec(
                    modelId = "llama-3.1-8b-instant",
                    displayName = "Llama 3.1 8B Instant",
                    contextWindowTokens = 128_000,
                    maxOutputTokens = 8_192,
                    supportsJsonMode = true,
                    supportsMultimodal = false
                )
            )
        ),
        AiProviderSpec(
            connectorId = AiConnectorId.C03_MISTRAL,
            displayName = "Mistral API",
            adminSecretName = "ADMIN_MISTRAL_API_KEY",
            requiresAccountId = false,
            baseUrl = "https://api.mistral.ai/v1",
            docsUrl = "https://docs.mistral.ai/admin/billing-usage/usage-limits",
            freeQuotaType = FreeQuotaType.EXPERIMENTATION_RATE_LIMIT,
            freeQuotaSummary = "Free Experimentation tier with rate and token limits",
            supportsJsonMode = true,
            supportsVisionOrPdf = false,
            defaultModelId = "mistral-small-latest",
            availableModels = listOf(
                AiModelSpec(
                    modelId = "mistral-small-latest",
                    displayName = "Mistral Small",
                    contextWindowTokens = 32_000,
                    maxOutputTokens = 8_192,
                    supportsJsonMode = true,
                    supportsMultimodal = false
                ),
                AiModelSpec(
                    modelId = "open-mistral-nemo",
                    displayName = "Mistral Nemo",
                    contextWindowTokens = 128_000,
                    maxOutputTokens = 8_192,
                    supportsJsonMode = true,
                    supportsMultimodal = false
                )
            )
        ),
        AiProviderSpec(
            connectorId = AiConnectorId.C04_CLOUDFLARE,
            displayName = "Cloudflare Workers AI",
            adminSecretName = "ADMIN_CLOUDFLARE_API_TOKEN",
            requiresAccountId = true,
            baseUrl = "https://api.cloudflare.com/client/v4/accounts",
            docsUrl = "https://developers.cloudflare.com/workers-ai/platform/pricing/",
            freeQuotaType = FreeQuotaType.DAILY_NEURONS,
            freeQuotaSummary = "10,000 Neurons per day free allocation",
            supportsJsonMode = true,
            supportsVisionOrPdf = false,
            defaultModelId = "@cf/meta/llama-3.1-8b-instruct",
            availableModels = listOf(
                AiModelSpec(
                    modelId = "@cf/meta/llama-3.1-8b-instruct",
                    displayName = "Llama 3.1 8B Instruct (CF)",
                    contextWindowTokens = 32_000,
                    maxOutputTokens = 4_096,
                    supportsJsonMode = true,
                    supportsMultimodal = false
                ),
                AiModelSpec(
                    modelId = "@cf/mistral/mistral-7b-instruct-v0.2",
                    displayName = "Mistral 7B Instruct (CF)",
                    contextWindowTokens = 32_000,
                    maxOutputTokens = 4_096,
                    supportsJsonMode = false,
                    supportsMultimodal = false
                )
            )
        ),
        AiProviderSpec(
            connectorId = AiConnectorId.C05_HUGGINGFACE,
            displayName = "Hugging Face Inference Providers",
            adminSecretName = "ADMIN_HUGGINGFACE_TOKEN",
            requiresAccountId = false,
            baseUrl = "https://router.huggingface.co/v1",
            docsUrl = "https://huggingface.co/docs/inference-providers/en/pricing",
            freeQuotaType = FreeQuotaType.MONTHLY_CREDITS,
            freeQuotaSummary = "Free account with limited monthly included inference credits",
            supportsJsonMode = true,
            supportsVisionOrPdf = false,
            defaultModelId = "meta-llama/Llama-3.1-8B-Instruct",
            availableModels = listOf(
                AiModelSpec(
                    modelId = "meta-llama/Llama-3.1-8B-Instruct",
                    displayName = "Llama 3.1 8B Instruct (HF)",
                    contextWindowTokens = 32_000,
                    maxOutputTokens = 4_096,
                    supportsJsonMode = true,
                    supportsMultimodal = false
                ),
                AiModelSpec(
                    modelId = "Qwen/Qwen2.5-7B-Instruct",
                    displayName = "Qwen 2.5 7B Instruct (HF)",
                    contextWindowTokens = 32_000,
                    maxOutputTokens = 4_096,
                    supportsJsonMode = true,
                    supportsMultimodal = false
                )
            )
        )
    )

    fun getById(id: AiConnectorId): AiProviderSpec =
        providers.first { it.connectorId == id }

    fun getByCode(code: String): AiProviderSpec? =
        AiConnectorId.fromCode(code)?.let { getById(it) }

    /**
     * Publishes non-sensitive provider registry metadata (NO API keys) to the single
     * real-time Firestore database under ConnectorMetadata/ProviderRegistry.
     */
    suspend fun syncProviderRegistryMetadata(context: Context) {
        val db = FirestoreHelper.getInstance(context)
        val batch = db.batch()
        providers.forEach { spec ->
            val docRef = db.collection("ConnectorMetadata")
                .document("ProviderRegistry")
                .collection("Providers")
                .document(spec.connectorId.code)
            val payload = mapOf(
                "connectorId" to spec.connectorId.code,
                "displayName" to spec.displayName,
                "adminSecretName" to spec.adminSecretName,
                "requiresAccountId" to spec.requiresAccountId,
                "freeQuotaType" to spec.freeQuotaType.name,
                "freeQuotaSummary" to spec.freeQuotaSummary,
                "supportsJsonMode" to spec.supportsJsonMode,
                "supportsVisionOrPdf" to spec.supportsVisionOrPdf,
                "defaultModelId" to spec.defaultModelId,
                "docsUrl" to spec.docsUrl,
                "updatedAtMs" to System.currentTimeMillis()
            )
            batch.set(docRef, payload)
        }
        batch.commit().await()
    }
}
