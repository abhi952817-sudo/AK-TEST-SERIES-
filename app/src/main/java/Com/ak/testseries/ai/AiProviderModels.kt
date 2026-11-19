package Com.ak.testseries.ai

/**
 * Fixed 5 Shared Free AI Connector slots used by BOTH Admin (Server-side) and Student (Device-side).
 * Paid-only or limited-trial providers (OpenAI, Claude, Cerebras) are intentionally excluded.
 */
enum class AiConnectorId(val code: String) {
    C01_GEMINI("C-01"),
    C02_GROQ("C-02"),
    C03_MISTRAL("C-03"),
    C04_CLOUDFLARE("C-04"),
    C05_HUGGINGFACE("C-05");

    companion object {
        fun fromCode(code: String): AiConnectorId? =
            entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
    }
}

/**
 * Strict separation of credential ownership and execution environment.
 */
enum class CredentialPath {
    /** Admin: GitHub Secrets -> GitHub Actions -> Google Secret Manager -> Cloud Functions AI Router */
    ADMIN_SERVER_SECRET_MANAGER,

    /** Student: Student UI -> Android Keystore + AES-GCM Local Storage -> On-Device AI Router */
    STUDENT_LOCAL_KEYSTORE
}

/**
 * Verification state of a provider's free tier.
 * A provider only receives FREE_VERIFIED after passing a live API connection & schema test.
 */
enum class FreeTierStatus {
    UNVERIFIED,
    FREE_VERIFIED,
    QUOTA_EXHAUSTED,
    AUTH_FAILED,
    DISABLED
}

enum class FreeQuotaType {
    DAILY_REQUESTS_AND_TPM,
    EXPERIMENTATION_RATE_LIMIT,
    DAILY_NEURONS,
    MONTHLY_CREDITS
}

data class AiModelSpec(
    val modelId: String,
    val displayName: String,
    val contextWindowTokens: Int,
    val maxOutputTokens: Int,
    val supportsJsonMode: Boolean,
    val supportsMultimodal: Boolean
)

data class AiProviderSpec(
    val connectorId: AiConnectorId,
    val displayName: String,
    val adminSecretName: String,
    val requiresAccountId: Boolean,
    val baseUrl: String,
    val docsUrl: String,
    val freeQuotaType: FreeQuotaType,
    val freeQuotaSummary: String,
    val supportsJsonMode: Boolean,
    val supportsVisionOrPdf: Boolean,
    val defaultModelId: String,
    val availableModels: List<AiModelSpec>
)

/**
 * Runtime connection & quota status for a connector (never stores raw API keys).
 */
data class ConnectorHealthSnapshot(
    val connectorId: AiConnectorId,
    val credentialPath: CredentialPath,
    val status: FreeTierStatus = FreeTierStatus.UNVERIFIED,
    val hasConfiguredKey: Boolean = false,
    val activeModelId: String = "",
    val lastVerifiedAtMs: Long = 0L,
    val lastErrorMessage: String? = null
)
