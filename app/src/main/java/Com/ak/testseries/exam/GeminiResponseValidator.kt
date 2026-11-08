package Com.ak.testseries.exam

import org.json.JSONArray
import org.json.JSONObject

/**
 * Fail-closed contract for every Gemini question response.
 * This is structural validation, not proof that a model's academic answer is correct.
 */
object GeminiResponseValidator {
    data class Report(
        val questions: JSONArray,
        val rejectedCount: Int,
        val errors: List<String>,
        val taskStatus: String
    ) {
        val isUsable: Boolean get() = questions.length() > 0 && errors.isEmpty()
    }

    fun parseResponse(raw: String, minimumMcqOptions: Int = 4): Report {
        val root = try {
            JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        } catch (_: Exception) {
            return Report(JSONArray(), 0, listOf("Response must be a JSON object; legacy arrays and malformed JSON are rejected."), "ERROR")
        }
        val status = root.optString("status", "").uppercase()
        if (status !in setOf("COMPLETE", "PARTIAL")) {
            return Report(JSONArray(), 0, listOf("Task status is missing or not publishable: $status"), status.ifBlank { "ERROR" })
        }
        val taskErrors = root.optJSONArray("errors")
        if (taskErrors != null && taskErrors.length() > 0) {
            return Report(JSONArray(), taskErrors.length(), listOf("Gemini task reported one or more extraction/generation errors; the batch is not publishable."), status)
        }
        val source = root.optJSONArray("questions")
            ?: return Report(JSONArray(), 0, listOf("Required questions array is missing."), status)
        val accepted = JSONArray()
        val errors = mutableListOf<String>()
        var rejected = 0
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index)
            val reason = item?.let { validateQuestion(it, minimumMcqOptions) } ?: "Question item is not a JSON object."
            if (reason == null) accepted.put(item) else {
                rejected++
                errors.add("Question ${index + 1}: $reason")
            }
        }
        if (accepted.length() == 0 && errors.isEmpty()) errors.add("No questions were returned.")
        return Report(accepted, rejected, errors, status)
    }

    fun parseResponse(raw: String, examConfig: ExamConfig): Report {
        val base = parseResponse(raw, minimumMcqOptions = 2)
        if (base.questions.length() == 0 || base.errors.isNotEmpty()) return base
        val sections = examConfig.stages.flatMap { it.papers }.flatMap { it.subjects }.flatMap { it.sections }
        val configErrors = mutableListOf<String>()
        for (index in 0 until base.questions.length()) {
            val item = base.questions.getJSONObject(index)
            val questionExamId = item.optString("examId", "")
            if (questionExamId != examConfig.examId) {
                configErrors.add("Question ${index + 1}: examId does not match the selected exam configuration.")
                continue
            }
            val sectionId = item.optString("sectionId", "")
            val section = sections.firstOrNull { it.sectionId == sectionId }
            if (section == null) {
                configErrors.add("Question ${index + 1}: sectionId is not present in the selected exam configuration.")
                continue
            }
            if (item.optString("sectionName", "") != section.sectionName) {
                configErrors.add("Question ${index + 1}: sectionName does not match configured section.")
                continue
            }
            if (item.optString("questionType", "") != section.questionType.name) {
                configErrors.add("Question ${index + 1}: questionType does not match configured section type.")
                continue
            }
            val positive = item.optDouble("positiveMarks", Double.NaN)
            val negative = item.optDouble("negativeMarks", Double.NaN)
            if (!positive.isFinite() || !negative.isFinite() ||
                kotlin.math.abs(positive - section.positiveMarks) > 0.000001 ||
                kotlin.math.abs(negative - section.negativeMarks) > 0.000001
            ) {
                configErrors.add("Question ${index + 1}: positive/negative marks do not match configured section scoring.")
                continue
            }
            val questionError = validateQuestion(item, section.optionsCount, requireExactOptionCount = true)
            if (questionError != null) configErrors.add("Question ${index + 1}: $questionError")
        }
        if (configErrors.isEmpty()) return base
        return Report(
            questions = JSONArray(),
            rejectedCount = base.rejectedCount + configErrors.size,
            errors = base.errors + configErrors,
            taskStatus = base.taskStatus
        )
    }

    fun validateQuestion(
        item: JSONObject,
        minimumMcqOptions: Int = 4,
        requireExactOptionCount: Boolean = false
    ): String? {
        val question = item.optString("questionTextEn", "").ifBlank { item.optString("questionText", "") }.trim()
        if (question.isBlank() || question.equals("Question Text", true) || question.length < 10) return "Question text is missing, placeholder, or too short."

        val answerStatus = item.optString("answerStatus", "").uppercase()
        if (answerStatus !in setOf("KEY_EXTRACTED", "INDEPENDENTLY_VERIFIED")) return "Answer is not verified ($answerStatus)."
        if (!item.has("needsReview") || item.optBoolean("needsReview", true)) return "Question is flagged for review or lacks an explicit needsReview=false."
        val provenance = item.optString("provenanceType", "").uppercase()
        if (provenance !in setOf("SOURCE_EXTRACTED", "GENERATED_PRACTICE")) return "Question provenance is missing or invalid."

        val answer = if (item.isNull("correctAnswer")) "" else item.optString("correctAnswer", "").trim()
        if (answer.isBlank() || answer.equals("null", true)) return "Verified answer is missing."

        val type = item.optString("questionType", "").uppercase()
        val allowedTypes = QuestionType.values().map { it.name }.toSet()
        if (type !in allowedTypes) return "Question type is missing or unsupported ($type)."
        val numerical = type in setOf("NAT", "TITA", "NUMERICAL", "NUMERICAL_VALUE")
        val freeResponse = type in setOf("DESCRIPTIVE")
        if (numerical) {
            if (!answer.matches(Regex("^[+-]?\\d+(\\.\\d+)?$"))) return "Numerical answer is not a valid numeric value."
        } else if (freeResponse) {
            if (answer.length < 2) return "Descriptive answer is missing or too short."
        } else {
            val labels = listOf("A", "B", "C", "D", "E")
            val options = labels.map { label ->
                item.optString("option${label}En", "").ifBlank { item.optString("option$label", "") }.trim()
            }
            val present = options.mapIndexedNotNull { i, value ->
                if (value.isNotBlank() && !value.startsWith("Option ", true) && !value.equals("N/A", true)) labels[i] else null
            }
            val expectedCount = minimumMcqOptions.coerceIn(2, 5)
            if (requireExactOptionCount && present != labels.take(expectedCount)) {
                return "Expected configured option labels ${labels.take(expectedCount)} in order; found $present."
            }
            if (!requireExactOptionCount && present.size < expectedCount) return "Required options are missing or contain placeholders."
            val answers = answer.uppercase().split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
            if (answers.isEmpty() || answers.any { it !in present } || answers.distinct().size != answers.size) return "Answer label does not map to the supplied options."
            if (type != "MCQ_MULTIPLE" && answers.size != 1) return "Only MCQ_MULTIPLE may contain multiple correct answer labels."
        }

        val sourceAnswer = if (item.isNull("sourceAnswer")) "" else item.optString("sourceAnswer", "").trim()
        if (answerStatus == "KEY_EXTRACTED" && sourceAnswer.isBlank()) return "KEY_EXTRACTED answer has no preserved sourceAnswer."
        if (sourceAnswer.isNotBlank() && normalizeAnswer(sourceAnswer) != normalizeAnswer(answer)) {
            return "Source answer key conflicts with the verified correctAnswer."
        }

        val confidence = item.optDouble("answerConfidence", -1.0)
        if (!confidence.isFinite() || confidence < 70.0 || confidence > 100.0) return "Answer confidence is below the 70-point acceptance threshold or outside 0-100."
        val verificationSummary = item.optString("verificationSummary", "").trim()
        if (verificationSummary.isBlank()) return "Concise verification summary is missing."
        val sectionId = item.optString("sectionId", "").trim()
        val sectionName = item.optString("sectionName", "").trim()
        if (sectionId.isBlank() || sectionId == "null" || sectionName.isBlank() || sectionName == "null") return "Configured section identity is missing."
        if (!item.has("positiveMarks") || item.isNull("positiveMarks") || !item.has("negativeMarks") || item.isNull("negativeMarks")) return "Configured positive/negative marks are missing."
        val positiveMarks = item.optDouble("positiveMarks", Double.NaN)
        val negativeMarks = item.optDouble("negativeMarks", Double.NaN)
        if (!positiveMarks.isFinite() || !negativeMarks.isFinite() || positiveMarks < 0.0 || negativeMarks < 0.0) return "Configured marks are invalid."
        return null
    }

    private fun normalizeAnswer(value: String): String {
        val trimmed = value.trim()
        val labels = trimmed.uppercase().split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
        if (labels.isNotEmpty() && labels.all { it.matches(Regex("^[A-E]$")) }) {
            return labels.distinct().sorted().joinToString(",")
        }
        val numeric = trimmed.toBigDecimalOrNull()
        if (numeric != null) return numeric.stripTrailingZeros().toPlainString()
        return trimmed.replace(Regex("\\s+"), " ").uppercase()
    }
}
