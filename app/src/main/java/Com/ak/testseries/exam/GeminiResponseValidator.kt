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

    fun validateQuestion(item: JSONObject, minimumMcqOptions: Int = 4): String? {
        val question = item.optString("questionTextEn", item.optString("questionText", "")).trim()
        if (question.isBlank() || question.equals("Question Text", true) || question.length < 10) return "Question text is missing, placeholder, or too short."

        val answerStatus = item.optString("answerStatus", "").uppercase()
        if (answerStatus !in setOf("KEY_EXTRACTED", "INDEPENDENTLY_VERIFIED")) return "Answer is not verified ($answerStatus)."
        if (!item.has("needsReview") || item.optBoolean("needsReview", true)) return "Question is flagged for review or lacks an explicit needsReview=false."
        val provenance = item.optString("provenanceType", "").uppercase()
        if (provenance !in setOf("SOURCE_EXTRACTED", "GENERATED_PRACTICE")) return "Question provenance is missing or invalid."

        val answer = if (item.isNull("correctAnswer")) "" else item.optString("correctAnswer", "").trim()
        if (answer.isBlank() || answer.equals("null", true)) return "Verified answer is missing."

        val type = item.optString("questionType", "MCQ_SINGLE").uppercase()
        val numerical = type in setOf("NAT", "TITA", "NUMERICAL", "NUMERICAL_VALUE", "NUMERICAL_VALUE_QUESTION")
        if (numerical) {
            if (!answer.matches(Regex("^[+-]?\\d+(\\.\\d+)?$"))) return "Numerical answer is not a valid numeric value."
        } else {
            val labels = listOf("A", "B", "C", "D", "E")
            val options = labels.map { label ->
                val suffix = if (label == "A") "A" else label
                item.optString("option${suffix}En", item.optString("option$suffix", "")).trim()
            }
            val present = options.mapIndexedNotNull { i, value -> if (value.isNotBlank() && !value.startsWith("Option ", true)) labels[i] else null }
            if (present.size < minimumMcqOptions.coerceIn(2, 5)) return "Required options are missing or contain placeholders."
            val answers = answer.uppercase().split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
            if (answers.isEmpty() || answers.any { it !in present } || answers.distinct().size != answers.size) return "Answer label does not map to the supplied options."
            if (type !in setOf("MCQ_MULTIPLE", "MULTI_STATEMENT") && answers.size != 1) return "Single-correct question has multiple answer labels."
        }

        val confidence = item.optDouble("answerConfidence", -1.0)
        if (confidence < 0.0 || confidence > 100.0) return "Answer confidence is missing or outside 0-100."
        return null
    }
}
