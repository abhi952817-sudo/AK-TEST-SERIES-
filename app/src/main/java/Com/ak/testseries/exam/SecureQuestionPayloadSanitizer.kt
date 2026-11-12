package Com.ak.testseries.exam

import org.json.JSONArray
import org.json.JSONObject

/**
 * Splits public question content from server-only answer keys.
 * Any parse or structural error fails closed; original JSON is never returned as a public fallback.
 */
data class SeparatedQuestionPayload(
    val publicJson: String,
    val answerKeys: List<Map<String, Any>>
)

object SecureQuestionPayloadSanitizer {
    private val secretFields = listOf(
        "correctAnswer", "correctAnswers", "correct_answer", "correct_answers",
        "correctOption", "correct_option", "correctOptionIndex", "correctAnswerIndex", "correctOptions",
        "answer", "answers", "answerKey", "answer_key", "answerKeyData",
        "sourceAnswer", "source_answer",
        "explanation", "explanationEn", "explanationHi", "explanation_en", "explanation_hi",
        "solution", "solutionEn", "solutionHi", "reasoning", "detailedSolution",
        "answerExplanation", "answerExplanationEn", "answerExplanationHi", "rationale"
    )

    fun separate(questionsJson: String): SeparatedQuestionPayload {
        require(questionsJson.isNotBlank()) { "Question payload is empty; refusing to publish." }
        val source = try {
            JSONArray(questionsJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed question JSON; refusing to publish.", e)
        }
        require(source.length() > 0) { "No questions found; refusing to publish." }

        val publicArray = JSONArray()
        val answerKeys = mutableListOf<Map<String, Any>>()
        val seenIds = mutableSetOf<String>()

        for (index in 0 until source.length()) {
            val question = source.optJSONObject(index)
                ?: throw IllegalArgumentException("Question at index $index is not a valid object.")
            val questionId = question.optString("id", "").trim()
            require(questionId.isNotBlank() && seenIds.add(questionId)) {
                "Question IDs must be non-empty and unique before publishing."
            }

            fun readSecretValue(vararg names: String): String {
                for (name in names) {
                    val value = question.opt(name) ?: continue
                    val normalized = when (value) {
                        is JSONArray -> (0 until value.length())
                            .mapNotNull { itemIndex -> value.optString(itemIndex).trim().takeIf { it.isNotBlank() } }
                            .joinToString(",")
                        is JSONObject -> listOf("correctAnswer", "answer", "value", "key")
                            .firstNotNullOfOrNull { nestedName ->
                                value.opt(nestedName)?.toString()?.trim()?.takeIf { it.isNotBlank() }
                            }.orEmpty()
                        is String -> value.trim()
                        is Number -> value.toString()
                        else -> ""
                    }
                    if (normalized.isNotBlank()) return normalized
                }
                return ""
            }

            val correctAnswer = readSecretValue(
                "correctAnswer", "correctAnswers", "correct_answer", "correct_answers",
                "correctOption", "correct_option", "correctOptionIndex", "correctAnswerIndex",
                "correctOptions", "answerKey", "answer_key", "answerKeyData", "sourceAnswer", "source_answer",
                "answer", "answers"
            )
            val explanation = readSecretValue(
                "explanation", "explanationEn", "explanation_en", "reasoning", "solution", "solutionEn",
                "detailedSolution", "answerExplanation", "answerExplanationEn", "rationale"
            )
            val explanationHi = readSecretValue(
                "explanationHi", "explanation_hi", "solutionHi", "answerExplanationHi"
            )

            answerKeys.add(
                mapOf(
                    "id" to questionId,
                    "correctAnswer" to correctAnswer,
                    "explanation" to explanation,
                    "explanationEn" to explanation,
                    "explanationHi" to explanationHi
                )
            )

            val publicQuestion = JSONObject(question.toString())
            secretFields.forEach(publicQuestion::remove)
            publicArray.put(publicQuestion)
        }

        require(answerKeys.size == source.length()) {
            "Private answer-key extraction was incomplete; refusing to publish."
        }
        return SeparatedQuestionPayload(publicArray.toString(), answerKeys)
    }
}
