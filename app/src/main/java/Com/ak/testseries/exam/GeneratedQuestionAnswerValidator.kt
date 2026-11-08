package Com.ak.testseries.exam

/**
 * Strict, exam-agnostic validation for answers returned by question extraction.
 * Option positions are preserved so a missing option cannot shift later choices.
 */
object GeneratedQuestionAnswerValidator {
    private val numericalTypes = setOf("NUMERICAL", "NUMERICAL_VALUE", "NAT", "TITA", "NVQ")
    private val multipleChoiceTypes = setOf("MCQ_MULTIPLE", "MULTIPLE_CHOICE", "MULTI_SELECT", "MSQ")

    fun isValid(
        questionType: String,
        answer: String,
        options: List<String>,
        expectedOptionCount: Int
    ): Boolean {
        val type = questionType.trim().uppercase().replace(Regex("[\\s-]+"), "_")
        val cleanAnswer = answer.trim()
        if (cleanAnswer.isEmpty()) return false

        if (type in numericalTypes) {
            val numeric = cleanAnswer.toDoubleOrNull()
            return numeric != null && numeric.isFinite() &&
                cleanAnswer.matches(Regex("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$"))
        }

        val requiredCount = expectedOptionCount.coerceIn(4, 5)
        if (options.size < requiredCount || options.take(requiredCount).any { it.isBlank() }) return false

        val optionKeys = listOf("A", "B", "C", "D", "E")
        fun optionExists(key: String): Boolean {
            val index = optionKeys.indexOf(key)
            return index >= 0 && index < requiredCount && options[index].isNotBlank()
        }

        if (type in multipleChoiceTypes) {
            val keys = cleanAnswer.uppercase().split(",").map { it.trim() }
            return keys.size >= 2 &&
                keys.distinct().size == keys.size &&
                keys.all { optionExists(it) }
        }

        return optionExists(cleanAnswer.uppercase())
    }
}
