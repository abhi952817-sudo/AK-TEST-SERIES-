package Com.ak.testseries.exam

import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic boundary for Gemini JSON. Prompts are advisory; this contract is enforced locally.
 * Uncertain or malformed responses must never be treated as publishable question data.
 */
object GeminiResponseContract {
    data class Envelope(
        val status: String,
        val questions: JSONArray,
        val errors: List<String>
    )

    data class QuestionCheck(
        val accepted: Boolean,
        val reasons: List<String>
    )

    fun parseEnvelope(raw: String): Envelope {
        val clean = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        require(clean.startsWith("{")) { "Gemini response must be a JSON object envelope." }
        val root = JSONObject(clean)
        val status = root.optString("status").uppercase()
        require(status in setOf("COMPLETE", "PARTIAL", "NEEDS_REVIEW")) {
            "Gemini task status is missing or not successful: ${status.ifBlank { "MISSING" }}."
        }
        val questions = root.optJSONArray("questions")
            ?: throw IllegalArgumentException("Gemini response is missing the questions array.")
        require(questions.length() > 0) { "Gemini response contains no questions." }
        val errors = mutableListOf<String>()
        root.optJSONArray("errors")?.let { array ->
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotBlank()) errors.add(value)
            }
        }
        return Envelope(status, questions, errors)
    }

    fun validateQuestion(
        item: JSONObject,
        examConfig: ExamConfig,
        subjectId: String,
        questionIndex: Int,
        allowExtractedKey: Boolean
    ): QuestionCheck {
        val reasons = mutableListOf<String>()
        fun requiredText(key: String): String = item.optString(key).trim().takeUnless {
            it.isBlank() || it.equals("null", true)
        } ?: ""

        val questionText = requiredText("questionTextEn").ifBlank { requiredText("questionText") }
        if (questionText.length < 10) reasons.add("Question text is missing or too short.")

        val answerStatus = requiredText("answerStatus").uppercase()
        val allowedAnswerStatuses = if (allowExtractedKey) {
            setOf("KEY_EXTRACTED", "INDEPENDENTLY_VERIFIED")
        } else {
            setOf("INDEPENDENTLY_VERIFIED")
        }
        if (answerStatus !in allowedAnswerStatuses) {
            reasons.add("Answer status is not verified: ${answerStatus.ifBlank { "MISSING" }}.")
        }

        val provenance = requiredText("provenanceType").uppercase()
        val expectedProvenance = if (allowExtractedKey) "SOURCE_EXTRACTED" else "GENERATED_PRACTICE"
        if (provenance != expectedProvenance) reasons.add("Question provenance does not match the requested task.")

        if (item.optBoolean("needsReview", false)) reasons.add("Gemini marked this question for review.")
        if (item.has("correctAnswer") && item.isNull("correctAnswer")) {
            reasons.add("Correct answer is null.")
        }
        val answer = requiredText("correctAnswer")
        if (answer.isBlank()) reasons.add("Correct answer is missing.")

        val rawType = requiredText("questionType").uppercase()
        val questionType = runCatching { QuestionType.valueOf(rawType) }.getOrNull()
        val configuredTypes = examConfig.supportedQuestionTypes
        val typeSupported = questionType != null && configuredTypes.any { configured ->
            configured == questionType ||
                (configured in setOf(QuestionType.DIRECT_MCQ, QuestionType.MCQ_SINGLE) && questionType in setOf(QuestionType.DIRECT_MCQ, QuestionType.MCQ_SINGLE)) ||
                (configured in setOf(QuestionType.NUMERICAL, QuestionType.NUMERICAL_VALUE, QuestionType.TITA) && questionType in setOf(QuestionType.NUMERICAL, QuestionType.NUMERICAL_VALUE, QuestionType.TITA))
        }
        if (!typeSupported) {
            reasons.add("Question type is missing or not supported by this exam.")
        }

        val sections = UniversalAIContext.getApplicableSections(examConfig, subjectId)
        val expectedSection = sections.let { list ->
            var running = 0
            list.firstOrNull { section ->
                running += section.totalQuestions
                questionIndex <= running
            }
        }
        val sectionId = requiredText("sectionId")
        val sectionName = requiredText("sectionName")
        if (expectedSection == null) {
            reasons.add("No verified section configuration exists for this subject/question.")
        } else {
            if (sectionId != expectedSection.sectionId) reasons.add("Section ID does not match the configured section.")
            if (sectionName != expectedSection.sectionName) reasons.add("Section name does not match the configured section.")
            if (!item.has("positiveMarks") || item.optDouble("positiveMarks", Double.NaN) != expectedSection.positiveMarks) {
                reasons.add("Positive marks do not match the configured section.")
            }
            if (!item.has("negativeMarks") || item.optDouble("negativeMarks", Double.NaN) != expectedSection.negativeMarks) {
                reasons.add("Negative marks do not match the configured section.")
            }
            if (questionType != null && questionType != expectedSection.questionType) {
                reasons.add("Question type does not match the configured section.")
            }
            if (questionType != null && questionType !in setOf(QuestionType.NUMERICAL, QuestionType.NUMERICAL_VALUE, QuestionType.TITA, QuestionType.DESCRIPTIVE)) {
                val expectedCount = expectedSection.optionsCount
                val options = listOf("optionAEn", "optionBEn", "optionCEn", "optionDEn", "optionEEn")
                    .take(expectedCount).map { requiredText(it) }
                if (options.size != expectedCount || options.any { it.isBlank() }) {
                    reasons.add("Option count/content does not match the configured section.")
                }
            }
        }

        if (questionType in setOf(QuestionType.NUMERICAL, QuestionType.NUMERICAL_VALUE, QuestionType.TITA) &&
            answer.toDoubleOrNull() == null
        ) {
            reasons.add("Numerical answer is not a valid number.")
        }
        return QuestionCheck(reasons.isEmpty(), reasons)
    }

    fun validateStudentQuestion(item: JSONObject): QuestionCheck {
        val reasons = mutableListOf<String>()
        val text = item.optString("questionTextEn").ifBlank { item.optString("questionText") }.trim()
        if (text.length < 10) reasons.add("Question text is missing or too short.")
        val answerStatus = item.optString("answerStatus").uppercase()
        if (answerStatus != "INDEPENDENTLY_VERIFIED" && answerStatus != "KEY_EXTRACTED") {
            reasons.add("Answer is not verified: ${answerStatus.ifBlank { "MISSING" }}.")
        }
        val provenance = item.optString("provenanceType").uppercase()
        if (provenance !in setOf("SOURCE_EXTRACTED", "GENERATED_PRACTICE")) {
            reasons.add("Question provenance is missing or invalid.")
        }
        if (item.optBoolean("needsReview", false)) reasons.add("Question requires human review.")
        if (item.isNull("correctAnswer") || item.optString("correctAnswer").isBlank()) {
            reasons.add("Correct answer is missing.")
        }
        val explanation = item.optString("explanationEn").ifBlank { item.optString("explanation") }
        if (explanation.isBlank()) reasons.add("Explanation is missing.")
        val type = item.optString("questionType").uppercase()
        if (type.isBlank()) reasons.add("Question type is missing.")
        val numerical = type in setOf("NUMERICAL", "NUMERICAL_VALUE", "TITA", "NAT", "NVQ")
        if (!numerical) {
            val optionCount = if (item.has("optionEEn") && !item.isNull("optionEEn") && item.optString("optionEEn").isNotBlank()) 5 else 4
            val keys = listOf("optionAEn", "optionBEn", "optionCEn", "optionDEn", "optionEEn").take(optionCount)
            if (keys.any { item.optString(it).isBlank() }) reasons.add("One or more required options are missing.")
        }
        return QuestionCheck(reasons.isEmpty(), reasons)
    }
}
