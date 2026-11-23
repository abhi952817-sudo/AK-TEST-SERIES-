package Com.ak.testseries.exam

import java.net.URI
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Exact, source-backed scoring policy identity. Display labels are never used
 * as resolution keys because they can change between exam years.
 */
data class ScoringPolicyKey(
    val examId: String,
    val examYear: String,
    val scoringVersion: String,
    val stageId: String,
    val paperId: String,
    val subjectId: String,
    val sectionId: String,
    val questionType: String
)

data class ScoringPolicy(
    val examId: String,
    val examYear: String,
    val scoringVersion: String,
    val stageId: String,
    val paperId: String,
    val subjectId: String,
    val subjectName: String,
    val sectionId: String,
    val sectionName: String,
    val questionType: String,
    val positiveMarks: Double,
    val negativeMarks: Double,
    val maxAttemptQuestions: Int? = null,
    val status: String,
    val sourceUrl: String,
    val sourceTitle: String,
    val verifiedAt: String,
    val approvedBy: String
)

/**
 * Fail-closed resolver shared conceptually with backend_functions/scoringPolicyResolver.js.
 * It rejects missing, duplicate, stale, unapproved or source-less policies.
 */
object ScoringPolicyResolver {

    private fun isValidHttpsSourceUrl(value: String): Boolean = try {
        val uri = URI(value.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    } catch (_: Exception) {
        false
    }

    private fun isValidVerifiedAt(value: String): Boolean {
        if (!value.matches(Regex(
                "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?(?:Z|[+-]\\d{2}:\\d{2})$"
            ))) return false

        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
        return formats.any { pattern ->
            val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val position = ParsePosition(0)
            val parsed = parser.parse(value, position)
            parsed != null && position.index == value.length
        }
    }

    fun resolve(
        registrySchemaVersion: Int,
        registryStatus: String,
        policies: List<ScoringPolicy>,
        key: ScoringPolicyKey
    ): ScoringPolicy {
        require(registrySchemaVersion == 1) {
            "The versioned scoring registry schema is unsupported."
        }
        require(registryStatus == "ACTIVE") {
            "The versioned scoring registry is missing or inactive."
        }
        val requiredValues = listOf(
            key.examId, key.examYear, key.scoringVersion, key.stageId,
            key.paperId, key.subjectId, key.sectionId, key.questionType
        )
        require(requiredValues.all { it.isNotBlank() }) {
            "All exam/year/version/stage/paper/subject/section/question-type keys are required."
        }

        val matches = policies.filter {
            it.examId == key.examId &&
                it.examYear == key.examYear &&
                it.scoringVersion == key.scoringVersion &&
                it.stageId == key.stageId &&
                it.paperId == key.paperId &&
                it.subjectId == key.subjectId &&
                it.sectionId == key.sectionId &&
                it.questionType.equals(key.questionType, ignoreCase = true)
        }
        require(matches.size == 1) {
            if (matches.isEmpty()) {
                "No policy matches the exact exam/year/stage/paper/subject/section/question type."
            } else {
                "Duplicate policies exist for the exact scoring key."
            }
        }

        val policy = matches.single()
        require(policy.status == "APPROVED") { "The scoring policy is not approved." }
        require(isValidHttpsSourceUrl(policy.sourceUrl)) {
            "The scoring policy requires a valid HTTPS authoritative source URL."
        }
        require(policy.sourceTitle.isNotBlank() && isValidVerifiedAt(policy.verifiedAt) && policy.approvedBy.isNotBlank()) {
            "The scoring policy is missing valid source provenance or approval metadata."
        }
        require(policy.positiveMarks.isFinite() && policy.positiveMarks > 0.0) {
            "The scoring policy has invalid positive marks."
        }
        require(policy.negativeMarks.isFinite() && policy.negativeMarks >= 0.0) {
            "The scoring policy has invalid negative marks."
        }
        require(policy.maxAttemptQuestions == null || policy.maxAttemptQuestions > 0) {
            "The scoring policy has an invalid section attempt limit."
        }
        return policy
    }
}
