package Com.ak.testseries.exam

/**
 * Versioned, question-specific scoring strategies. This API is intentionally independent
 * of QuestionEntity so legacy NEET/JEE Main records need no Room migration.
 */
object QuestionLevelScoringEngine {
    enum class Outcome { CORRECT, PARTIAL, WRONG }

    data class RubricRule(
        val response: String,
        val awardedMarks: Double,
        val outcome: Outcome
    )

    data class Rubric(
        val schemaVersion: Int,
        val rules: List<RubricRule>,
        val unmatchedMarks: Double,
        val unansweredMarks: Double = 0.0,
        val tolerance: Double? = null
    )

    data class Question(
        val id: String,
        val strategyId: String,
        val maxMarks: Double,
        val positiveMarks: Double? = null,
        val negativeMarks: Double? = null,
        val correctAnswer: String? = null,
        val rubric: Rubric? = null
    )

    data class Result(
        val totalQuestions: Int,
        val attemptedCount: Int,
        val correctCount: Int,
        val partialCount: Int,
        val wrongCount: Int,
        val unansweredCount: Int,
        val positiveMarksEarned: Double,
        val negativeMarksPenalty: Double,
        val rawScore: Double,
        val finalScore: Double,
        val maxScore: Double
    )

    private val strictNumberPattern = Regex("^[+-]?(?:\\\\d+(?:\\\\.\\\\d+)?|\\\\.\\\\d+)$")

    private fun strictFiniteNumber(value: String?): Double? =
        if (value != null && strictNumberPattern.matches(value.trim())) value.trim().toDoubleOrNull()?.takeIf { it.isFinite() } else null

    private val supported = setOf(
        "BINARY_MARKS",
        "EXACT_RESPONSE_TABLE",
        "MULTI_SELECT_PARTIAL_TABLE",
        "NUMERIC_TOLERANCE"
    )

    private fun requireFinite(value: Double?, label: String): Double {
        require(value != null && value.isFinite()) { "$label must be a finite number." }
        return value
    }

    private fun canonicalResponse(value: String, strategyId: String): String {
        val clean = value.trim().uppercase()
        require(clean.isNotEmpty()) { "Answered response cannot be empty." }
        if (strategyId != "MULTI_SELECT_PARTIAL_TABLE") return clean
        val parts = clean.split(",").map { it.trim() }
        require(parts.all { it.isNotEmpty() } && parts.distinct().size == parts.size) {
            "Multi-select responses must contain unique, non-empty selections."
        }
        return parts.sorted().joinToString(",")
    }

    private fun validate(question: Question): Question {
        require(question.id.isNotBlank()) { "Question ID is required." }
        val strategy = question.strategyId.trim().uppercase()
        require(strategy in supported) { "Unsupported or unverified scoring strategy: $strategy." }
        val max = requireFinite(question.maxMarks, "maxMarks")
        require(max > 0.0) { "maxMarks must be greater than zero." }

        if (strategy == "BINARY_MARKS" || strategy == "NUMERIC_TOLERANCE") {
            val positive = requireFinite(question.positiveMarks, "positiveMarks")
            val negative = requireFinite(question.negativeMarks, "negativeMarks")
            require(positive > 0.0 && positive <= max && negative >= 0.0 && negative <= max) {
                "Binary marks must fit within maxMarks."
            }
            require(!question.correctAnswer.isNullOrBlank()) { "A verified correctAnswer is required." }
            if (strategy == "NUMERIC_TOLERANCE") {
                val tolerance = requireFinite(question.rubric?.tolerance, "rubric.tolerance")
                require(tolerance >= 0.0) { "Numeric tolerance cannot be negative." }
                require(strictFiniteNumber(question.correctAnswer) != null) {
                    "Numeric correctAnswer must be finite."
                }
            }
            return question.copy(strategyId = strategy)
        }

        val rubric = question.rubric ?: throw IllegalArgumentException("Table strategy requires a rubric.")
        require(rubric.schemaVersion == 1 && rubric.rules.isNotEmpty()) {
            "Table strategy requires rubric schemaVersion 1 and at least one rule."
        }
        require(rubric.unmatchedMarks.isFinite() && rubric.unansweredMarks.isFinite()) {
            "Rubric fallback marks must be finite."
        }
        require(rubric.unmatchedMarks in -max..max && rubric.unansweredMarks in -max..max) {
            "Rubric fallback marks exceed maxMarks bounds."
        }
        val normalized = rubric.rules.map { rule ->
            val response = canonicalResponse(rule.response, strategy)
            require(rule.awardedMarks.isFinite() && rule.awardedMarks in -max..max) {
                "Rubric awarded marks exceed maxMarks bounds."
            }
            response to rule
        }
        require(normalized.map { it.first }.distinct().size == normalized.size) {
            "Rubric contains duplicate normalized response rules."
        }
        return question.copy(
            strategyId = strategy,
            rubric = rubric.copy(rules = normalized.map { (response, rule) -> rule.copy(response = response) })
        )
    }

    fun evaluate(questions: List<Question>, userAnswers: Map<String, String>): Result {
        require(questions.isNotEmpty() && questions.size <= 450) {
            "Scoring requires between 1 and 450 questions."
        }
        val seenIds = mutableSetOf<String>()
        var positive = 0.0
        var negative = 0.0
        var raw = 0.0
        var max = 0.0
        var correct = 0
        var partial = 0
        var wrong = 0
        var unanswered = 0

        questions.forEach { rawQuestion ->
            val question = validate(rawQuestion)
            require(seenIds.add(question.id)) { "Duplicate question ID: ${question.id}." }
            max += question.maxMarks
            val answer = userAnswers[question.id]
            if (answer.isNullOrBlank() || answer.trim().equals("SKIPPED", ignoreCase = true)) {
                unanswered++
                val marks = if (question.strategyId == "BINARY_MARKS" || question.strategyId == "NUMERIC_TOLERANCE") {
                    0.0
                } else question.rubric!!.unansweredMarks
                raw += marks
                if (marks > 0) positive += marks
                if (marks < 0) negative += -marks
                return@forEach
            }

            val marks: Double
            val outcome: Outcome
            when (question.strategyId) {
                "BINARY_MARKS" -> {
                    val isCorrect = answer.trim().equals(question.correctAnswer!!.trim(), ignoreCase = true)
                    marks = if (isCorrect) question.positiveMarks!! else -question.negativeMarks!!
                    outcome = if (isCorrect) Outcome.CORRECT else Outcome.WRONG
                }
                "NUMERIC_TOLERANCE" -> {
                    val entered = strictFiniteNumber(answer)
                    require(entered != null && entered.isFinite()) { "Numeric response must be finite." }
                    val expected = strictFiniteNumber(question.correctAnswer)!!
                    val isCorrect = kotlin.math.abs(entered - expected) <= question.rubric!!.tolerance!!
                    marks = if (isCorrect) question.positiveMarks!! else -question.negativeMarks!!
                    outcome = if (isCorrect) Outcome.CORRECT else Outcome.WRONG
                }
                else -> {
                    val normalized = canonicalResponse(answer, question.strategyId)
                    val rule = question.rubric!!.rules.firstOrNull { it.response == normalized }
                    marks = rule?.awardedMarks ?: question.rubric.unmatchedMarks
                    outcome = rule?.outcome ?: Outcome.WRONG
                }
            }
            raw += marks
            if (marks > 0) positive += marks
            if (marks < 0) negative += -marks
            when (outcome) {
                Outcome.CORRECT -> correct++
                Outcome.PARTIAL -> partial++
                Outcome.WRONG -> wrong++
            }
        }

        fun rounded(value: Double) = kotlin.math.floor(value * 100.0 + 0.5) / 100.0
        val attempted = correct + partial + wrong
        return Result(
            totalQuestions = questions.size,
            attemptedCount = attempted,
            correctCount = correct,
            partialCount = partial,
            wrongCount = wrong,
            unansweredCount = unanswered,
            positiveMarksEarned = rounded(positive),
            negativeMarksPenalty = rounded(negative),
            rawScore = rounded(raw),
            finalScore = rounded(raw.coerceAtLeast(0.0)),
            maxScore = rounded(max)
        )
    }
}
