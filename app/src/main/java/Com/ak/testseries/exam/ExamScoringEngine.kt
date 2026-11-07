package Com.ak.testseries.exam

import Com.ak.testseries.data.QuestionEntity

data class SubjectStatData(
    val subject: String,
    var total: Int = 0,
    var correct: Int = 0,
    var incorrect: Int = 0,
    var score: Double = 0.0,
    var maxScore: Double = 0.0
) {
    val accuracy: Float get() = if (total > 0) (correct.toFloat() / (correct + incorrect).coerceAtLeast(1).toFloat()) * 100f else 0f
    val formattedScore: String get() = ExamScoringEngine.formatMark(score)
    val formattedMaxScore: String get() = ExamScoringEngine.formatMark(maxScore)
}

/**
 * Configuration-Driven Universal Exam Scoring Engine
 * Accurately calculates positive marks, negative penalties, and exam-specific adjustments based on section config.
 */
object ExamScoringEngine {

    val VALID_ANSWER_OPTIONS = setOf("A", "B", "C", "D", "E", "1", "2", "3", "4", "5")

    fun isAnswerKeyVerified(key: String): Boolean {
        val clean = key.trim().uppercase()
        if (clean.isEmpty()) return false
        if (clean in VALID_ANSWER_OPTIONS || clean.toDoubleOrNull() != null) return true
        if (clean.contains(",")) {
            val parts = clean.split(",").map { it.trim() }
            if (parts.isNotEmpty() && parts.all { it in VALID_ANSWER_OPTIONS || it.toDoubleOrNull() != null }) {
                return true
            }
        }
        return false
    }

    fun areOptionsEquivalent(ans1: String, ans2: String, isNumerical: Boolean = false): Boolean {
        val a = ans1.trim().uppercase()
        val b = ans2.trim().uppercase()
        if (a == b) return true
        if (!isNumerical) {
            val mapIndex = mapOf("1" to "A", "2" to "B", "3" to "C", "4" to "D", "5" to "E")
            if (mapIndex[a] == b || mapIndex[b] == a) return true
        }
        val num1 = a.toDoubleOrNull()
        val num2 = b.toDoubleOrNull()
        if (num1 != null && num2 != null) {
            return Math.abs(num1 - num2) < 0.0001
        }
        return false
    }

    fun isUserAnswerCorrect(userAns: String, correctAns: String, isNumerical: Boolean = false): Boolean {
        val u = userAns.trim().uppercase()
        val c = correctAns.trim().uppercase()
        if (u.isEmpty() || c.isEmpty()) return false
        if (areOptionsEquivalent(u, c, isNumerical)) return true

        if (c.contains(",")) {
            val acceptedKeys = c.split(",").map { it.trim() }
            val normalizedUserParts = u.split(",").map { it.trim() }.sorted()
            val normalizedCorrectParts = acceptedKeys.sorted()
            if (normalizedUserParts == normalizedCorrectParts) return true

            if (acceptedKeys.any { areOptionsEquivalent(u, it, isNumerical) }) return true
        }
        return false
    }

    data class ScoreBreakdown(
        val totalQuestions: Int,
        val attemptedCount: Int,
        val ungradedCount: Int = 0,
        val correctCount: Int,
        val wrongCount: Int,
        val unansweredCount: Int,
        val positiveMarksEarned: Double,
        val negativeMarksPenalty: Double,
        val finalScore: Double,
        val maxScore: Double,
        val accuracyPercent: Double,
        val timeSpentSeconds: Long,
        val correctMarksPerQuestion: Double,
        val negativeMarksPerQuestion: Double,
        val rawScore: Double = positiveMarksEarned - negativeMarksPenalty
    )

    fun getMarkingScheme(examName: String): Pair<Double, Double> {
        return UniversalExamRegistry.getMarkingSchemeForExam(examName)
    }

    fun formatMark(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }

    fun evaluateTestAttempt(
        targetExam: String,
        questions: List<QuestionEntity>,
        userAnswers: Map<String, String>,
        timeSpentSeconds: Long
    ): ScoreBreakdown {
        val (posMark, negMark) = getMarkingScheme(targetExam)
        var correct = 0
        var wrong = 0
        var unanswered = 0
        var ungraded = 0
        var positiveMarksEarned = 0.0
        var negativeMarksPenalty = 0.0
        var maxPossible = 0.0

        questions.forEach { question ->
            val policy = try {
                UniversalExamRegistry.getMarkingSchemeForQuestion(
                    targetExam, question.section, question.subject, question.questionType
                )
            } catch (_: IllegalArgumentException) {
                ungraded++
                return@forEach
            }
            val qPosMark = policy.first
            val qNegMark = policy.second
            maxPossible += qPosMark

            val userOpt = userAnswers[question.id]?.trim()?.uppercase()
            val correctOpt = question.correctAnswer.trim().uppercase()
            val numerical = question.questionType.equals("NUMERICAL_VALUE", true) ||
                question.questionType.equals("NUMERICAL", true) ||
                question.questionType.equals("TITA", true) ||
                question.questionType.equals("NAT", true)
            val keyVerified = if (numerical) {
                correctOpt.toDoubleOrNull()?.isFinite() == true
            } else {
                correctOpt in setOf("A", "B", "C", "D", "1", "2", "3", "4")
            }
            if (!keyVerified) {
                ungraded++
                return@forEach
            }

            if (userOpt.isNullOrEmpty()) {
                unanswered++
            } else if (isUserAnswerCorrect(userOpt, correctOpt, numerical)) {
                correct++
                positiveMarksEarned += qPosMark
            } else {
                wrong++
                negativeMarksPenalty += qNegMark
            }
        }

        val rawScore = positiveMarksEarned - negativeMarksPenalty
        val finalScore = rawScore.coerceAtLeast(0.0)
        val attempted = correct + wrong + ungraded
        val accuracyDenominator = correct + wrong
        val accuracy = if (accuracyDenominator > 0) {
            (correct.toDouble() / accuracyDenominator.toDouble()) * 100.0
        } else 0.0

        return ScoreBreakdown(
            totalQuestions = questions.size,
            attemptedCount = attempted,
            ungradedCount = ungraded,
            correctCount = correct,
            wrongCount = wrong,
            unansweredCount = unanswered,
            positiveMarksEarned = positiveMarksEarned,
            negativeMarksPenalty = negativeMarksPenalty,
            finalScore = finalScore,
            maxScore = maxPossible,
            accuracyPercent = Math.round(accuracy * 100.0) / 100.0,
            timeSpentSeconds = timeSpentSeconds,
            correctMarksPerQuestion = posMark,
            negativeMarksPerQuestion = negMark,
            rawScore = rawScore
        )
    }

    fun calculateScore(
        examConfig: ExamConfig,
        stageId: String,
        paperId: String,
        totalQuestions: Int,
        userAnswers: Map<String, String>,
        correctAnswers: Map<String, String>,
        questionSections: Map<String, String>,
        timeSpentSeconds: Long
    ): ScoreBreakdown {
        require(examConfig.active && examConfig.verified) {
            "Scoring is blocked for an unverified exam configuration."
        }
        val stage = examConfig.stages.singleOrNull { it.stageId == stageId }
            ?: throw IllegalArgumentException("Exact exam stage is missing or ambiguous.")
        val paper = stage.papers.singleOrNull { it.paperId == paperId }
            ?: throw IllegalArgumentException("Exact exam paper is missing or ambiguous.")
        val sections = paper.subjects.flatMap { it.sections }
        require(sections.map { it.sectionId }.distinct().size == sections.size) {
            "Section IDs must be unique within a paper."
        }
        val sectionConfigMap = sections.associateBy { it.sectionId }
        val defaultSection = sections.firstOrNull()
            ?: throw IllegalArgumentException("Verified paper has no section policy.")

        var correct = 0
        var wrong = 0
        var unanswered = 0
        var ungraded = (totalQuestions - correctAnswers.size).coerceAtLeast(0)
        var totalPositiveEarned = 0.0
        var totalNegativePenalty = 0.0
        var totalMaxPossible = 0.0

        correctAnswers.forEach { (qid, correctOpt) ->
            val sectionId = questionSections[qid]
            val section = sectionId?.let { sectionConfigMap[it] }
            if (section == null) {
                ungraded++
                return@forEach
            }
            val numerical = section.questionType in setOf(QuestionType.NUMERICAL_VALUE, QuestionType.TITA)
            val cleanCorrect = correctOpt.trim().uppercase()
            val keyVerified = if (numerical) cleanCorrect.toDoubleOrNull()?.isFinite() == true
                else cleanCorrect in setOf("A", "B", "C", "D", "1", "2", "3", "4")
            if (!keyVerified) {
                ungraded++
                return@forEach
            }
            totalMaxPossible += section.positiveMarks
            val userOpt = userAnswers[qid]?.trim()?.uppercase()
            if (userOpt.isNullOrEmpty()) {
                unanswered++
            } else if (isUserAnswerCorrect(userOpt, cleanCorrect, numerical)) {
                correct++
                totalPositiveEarned += section.positiveMarks
            } else {
                wrong++
                totalNegativePenalty += section.negativeMarks
            }
        }

        val rawScore = totalPositiveEarned - totalNegativePenalty
        val finalScore = rawScore.coerceAtLeast(0.0)
        val attempted = correct + wrong + ungraded
        val accuracyDenominator = correct + wrong
        val accuracy = if (accuracyDenominator > 0) {
            (correct.toDouble() / accuracyDenominator.toDouble()) * 100.0
        } else 0.0

        return ScoreBreakdown(
            totalQuestions = totalQuestions,
            attemptedCount = attempted,
            ungradedCount = ungraded,
            correctCount = correct,
            wrongCount = wrong,
            unansweredCount = unanswered,
            positiveMarksEarned = totalPositiveEarned,
            negativeMarksPenalty = totalNegativePenalty,
            finalScore = finalScore,
            maxScore = totalMaxPossible,
            accuracyPercent = Math.round(accuracy * 100.0) / 100.0,
            timeSpentSeconds = timeSpentSeconds,
            correctMarksPerQuestion = defaultSection.positiveMarks,
            negativeMarksPerQuestion = defaultSection.negativeMarks,
            rawScore = rawScore
        )
    }

    fun evaluateSubjectBreakdown(
        targetExam: String,
        questions: List<QuestionEntity>,
        userAnswers: Map<String, String>
    ): List<SubjectStatData> {
        val (posMark, negMark) = getMarkingScheme(targetExam)
        val isMultiScheme = UniversalExamRegistry.hasMultipleMarkingSchemes(targetExam)
        val isCat = targetExam.equals("CAT", ignoreCase = true) || targetExam.contains("CAT", ignoreCase = true)

        val statsMap = linkedMapOf<String, SubjectStatData>()
        val sectionAttemptCounts = mutableMapOf<String, Int>()

        questions.forEach { question ->
            val subjectKey = question.subject.ifBlank { "General" }
            val stat = statsMap.getOrPut(subjectKey) {
                SubjectStatData(subject = subjectKey)
            }
            stat.total++

            val (qPosMark, qNegMark) = if (isMultiScheme) {
                UniversalExamRegistry.getMarkingSchemeForQuestion(targetExam, question.section, question.subject, question.questionType)
            } else {
                Pair(posMark, negMark)
            }
            stat.maxScore += qPosMark

            val isTitaOrNumerical = question.questionType.equals("TITA", ignoreCase = true) ||
                    question.questionType.equals("NUMERICAL", ignoreCase = true) ||
                    question.questionType.equals("NUMERICAL_VALUE", ignoreCase = true)
            val effectiveNegMark = if (isCat && isTitaOrNumerical) 0.0 else qNegMark

            val userOpt = userAnswers[question.id]?.trim()?.uppercase()
            val correctOpt = question.correctAnswer.trim().uppercase()
            val isKeyVerified = isAnswerKeyVerified(correctOpt)

            if (userOpt.isNullOrEmpty()) {
                // Unanswered / skipped
            } else {
                val secKey = "${question.subject.lowercase().trim()}_${question.section.lowercase().trim()}"
                val secLimit = UniversalExamRegistry.getMaximumAttemptQuestionsCountForSection(
                    examName = targetExam,
                    sectionNameOrId = question.section,
                    subjectName = question.subject
                )
                val currentAttempts = sectionAttemptCounts.getOrDefault(secKey, 0)
                if (secLimit != null && currentAttempts >= secLimit) {
                    // Surplus optional question beyond limit - ignored
                } else {
                    if (secLimit != null) {
                        sectionAttemptCounts[secKey] = currentAttempts + 1
                    }
                    if (!isKeyVerified) {
                        // Unverified key
                    } else if (isUserAnswerCorrect(userOpt, correctOpt, isTitaOrNumerical)) {
                        stat.correct++
                        stat.score += qPosMark
                    } else {
                        stat.incorrect++
                        stat.score -= effectiveNegMark
                    }
                }
            }
        }

        if (!isMultiScheme) {
            statsMap.forEach { (subKey, stat) ->
                val maxAllowedQs = UniversalExamRegistry.getMaximumAttemptQuestionsCountForSubject(targetExam, subKey)
                if (maxAllowedQs != null && stat.total > maxAllowedQs) {
                    stat.maxScore = maxAllowedQs * posMark
                }
            }
        }

        return statsMap.values.toList()
    }
}
