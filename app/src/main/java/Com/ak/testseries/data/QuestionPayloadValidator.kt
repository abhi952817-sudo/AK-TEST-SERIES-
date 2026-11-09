package Com.ak.testseries.data

import org.json.JSONArray

/**
 * Fail-closed validation for public test payloads before they enter the test palette.
 * The expected option count comes from the canonical exam configuration.
 */
object QuestionPayloadValidator {

    private val numericTypes = setOf("NUMERICAL", "NUMERICAL_VALUE", "TITA", "NAT", "INTEGER")
    private val placeholders = setOf(
        "option a", "option b", "option c", "option d", "option e",
        "distractor option a", "distractor option b", "distractor option c",
        "distractor option d", "distractor option e",
        "sample option", "correct option"
    )

    fun isValid(rawJson: String, expectedCount: Int, expectedOptionCount: Int): Boolean {
        if (expectedCount <= 0 || rawJson.isBlank()) return false
        val optionCount = expectedOptionCount.coerceIn(4, 5)
        val questions = try {
            JSONArray(rawJson)
        } catch (_: Exception) {
            return false
        }
        if (questions.length() != expectedCount) return false

        val seenQuestionStems = mutableSetOf<String>()
        for (index in 0 until questions.length()) {
            val question = questions.optJSONObject(index) ?: return false
            val text = question.optString("questionText")
                .ifBlank { question.optString("questionTextEn") }
                .trim()
            if (text.isBlank() || text.equals("Question Text", ignoreCase = true) ||
                text.startsWith("Practice Question", ignoreCase = true)
            ) return false

            val normalizedStem = text.lowercase().replace(Regex("\\s+"), " ").trim()
            if (!seenQuestionStems.add(normalizedStem)) return false

            val answer = question.optString("correctAnswer").trim()
            val explanation = question.optString("explanation")
                .ifBlank { question.optString("explanationEn") }
                .ifBlank { question.optString("reasoning") }
                .trim()
            if (answer.isBlank() || explanation.isBlank()) return false

            val type = question.optString("questionType", "MCQ").uppercase()
            val options = listOf("A", "B", "C", "D", "E").take(optionCount).map { letter ->
                question.optString("option$letter")
                    .ifBlank { question.optString("option${letter}En") }
                    .trim()
            }

            if (type in numericTypes) {
                if (options.any { it.isNotBlank() }) return false
                if (answer.toDoubleOrNull()?.isFinite() != true) return false
            } else {
                if (options.any { it.isBlank() }) return false
                if (options.any { it.lowercase() in placeholders }) return false
                if (options.map { it.lowercase() }.distinct().size != optionCount) return false
                val validAnswers = if (optionCount == 5) {
                    setOf("A", "B", "C", "D", "E", "1", "2", "3", "4", "5")
                } else {
                    setOf("A", "B", "C", "D", "1", "2", "3", "4")
                }
                val answerTokens = answer.uppercase()
                    .replace(Regex("\\bAND\\b"), ",")
                    .split(Regex("[,;|\\s]+"))
                    .filter { it.isNotBlank() }
                val isMultipleAnswer = type.contains("MULTIPLE") || type.contains("MULTI_SELECT")
                val answerValid = if (isMultipleAnswer) {
                    answerTokens.isNotEmpty() && answerTokens.all { it in validAnswers }
                } else {
                    answer.uppercase() in validAnswers
                }
                if (!answerValid) return false
            }
        }
        return true
    }
}
