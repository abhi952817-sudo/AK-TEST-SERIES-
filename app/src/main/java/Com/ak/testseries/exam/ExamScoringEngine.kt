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
        return clean in VALID_ANSWER_OPTIONS || clean.toDoubleOrNull() != null
    }

    data class ScoreBreakdown(
        val totalQuestions: Int,
        val attemptedCount: Int,
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
        val negativeMarksPerQuestion: Double
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

        var positiveMarksEarned = 0.0
        var negativeMarksPenalty = 0.0

        val isMultiScheme = UniversalExamRegistry.hasMultipleMarkingSchemes(targetExam)
        val isCat = targetExam.equals("CAT", ignoreCase = true) || targetExam.contains("CAT", ignoreCase = true)
        var maxPossible = 0.0

        questions.forEach { question ->
            val userOpt = userAnswers[question.id]?.trim()?.uppercase()
            val correctOpt = question.correctAnswer.trim().uppercase()
            val isKeyVerified = isAnswerKeyVerified(correctOpt)

            val (qPosMark, qNegMark) = if (isMultiScheme) {
                UniversalExamRegistry.getMarkingSchemeForQuestion(targetExam, question.section, question.subject, question.questionType)
            } else {
                Pair(posMark, negMark)
            }
            maxPossible += qPosMark

            val isTitaOrNumerical = question.questionType.equals("TITA", ignoreCase = true) ||
                    question.questionType.equals("NUMERICAL", ignoreCase = true) ||
                    question.questionType.equals("NUMERICAL_VALUE", ignoreCase = true)
            val effectiveNegMark = if (isCat && isTitaOrNumerical) 0.0 else qNegMark

            if (userOpt.isNullOrEmpty()) {
                unanswered++
            } else if (!isKeyVerified) {
                // Unknown/unverified answer key: student choice cannot be scored; no penalty or accuracy skew
            } else if (userOpt == correctOpt || (userOpt.toDoubleOrNull() != null && userOpt.toDoubleOrNull() == correctOpt.toDoubleOrNull())) {
                correct++
                positiveMarksEarned += qPosMark
            } else {
                wrong++
                negativeMarksPenalty += effectiveNegMark
            }
        }

        if (!isMultiScheme) {
            val maxAllowedQs = UniversalExamRegistry.getMaximumAttemptQuestionsCountForExam(targetExam)
            val effectiveCount = if (maxAllowedQs != null && questions.size > maxAllowedQs) {
                maxAllowedQs
            } else {
                questions.size
            }
            maxPossible = effectiveCount * posMark
        }
        val rawScore = positiveMarksEarned - negativeMarksPenalty
        val finalScore = rawScore.coerceAtLeast(0.0) // Non-negative final display boundary
        val attempted = correct + wrong
        val accuracy = if (attempted > 0) (correct.toDouble() / attempted.toDouble()) * 100.0 else 0.0

        return ScoreBreakdown(
            totalQuestions = questions.size,
            attemptedCount = attempted,
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
            negativeMarksPerQuestion = negMark
        )
    }

    fun calculateScore(
        examConfig: ExamConfig,
        stageId: String,
        paperId: String,
        totalQuestions: Int,
        userAnswers: Map<String, String>, // questionId -> selectedOption ("A", "B", "C", "D")
        correctAnswers: Map<String, String>, // questionId -> actualCorrectOption
        questionSections: Map<String, String>, // questionId -> sectionId
        timeSpentSeconds: Long
    ): ScoreBreakdown {
        var correct = 0
        var wrong = 0
        var unanswered = 0
        
        var totalPositiveEarned = 0.0
        var totalNegativePenalty = 0.0
        var totalMaxPossible = 0.0

        val paperConfig = examConfig.stages.find { it.stageId == stageId }?.papers?.find { it.paperId == paperId }
        val sectionConfigMap = paperConfig?.subjects?.flatMap { it.sections }?.associateBy { it.sectionId } ?: emptyMap()

        val defaultPos = examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.sections?.firstOrNull()?.positiveMarks ?: 4.0
        val defaultNeg = examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.sections?.firstOrNull()?.negativeMarks ?: 1.0

        correctAnswers.forEach { (qid, correctOpt) ->
            val userOpt = userAnswers[qid]?.trim()?.uppercase()
            val cleanCorrect = correctOpt.trim().uppercase()
            val isKeyVerified = isAnswerKeyVerified(cleanCorrect)
            val sectionId = questionSections[qid] ?: ""
            val posMarks = sectionConfigMap[sectionId]?.positiveMarks ?: defaultPos
            val negMarks = sectionConfigMap[sectionId]?.negativeMarks ?: defaultNeg
            
            totalMaxPossible += posMarks

            if (userOpt.isNullOrEmpty()) {
                unanswered++
            } else if (!isKeyVerified) {
                // Unknown/unverified answer key
            } else if (userOpt == cleanCorrect || (userOpt.toDoubleOrNull() != null && userOpt.toDoubleOrNull() == cleanCorrect.toDoubleOrNull())) {
                correct++
                totalPositiveEarned += posMarks
            } else {
                wrong++
                totalNegativePenalty += negMarks
            }
        }

        val finalScore = (totalPositiveEarned - totalNegativePenalty).coerceAtLeast(0.0)
        val attempted = correct + wrong
        val accuracy = if (attempted > 0) (correct.toDouble() / attempted.toDouble()) * 100.0 else 0.0

        return ScoreBreakdown(
            totalQuestions = totalQuestions,
            attemptedCount = attempted,
            correctCount = correct,
            wrongCount = wrong,
            unansweredCount = unanswered,
            positiveMarksEarned = totalPositiveEarned,
            negativeMarksPenalty = totalNegativePenalty,
            finalScore = finalScore,
            maxScore = totalMaxPossible,
            accuracyPercent = Math.round(accuracy * 100.0) / 100.0,
            timeSpentSeconds = timeSpentSeconds,
            correctMarksPerQuestion = defaultPos,
            negativeMarksPerQuestion = defaultNeg
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
            } else if (!isKeyVerified) {
                // Unverified key
            } else if (userOpt == correctOpt || (userOpt.toDoubleOrNull() != null && userOpt.toDoubleOrNull() == correctOpt.toDoubleOrNull())) {
                stat.correct++
                stat.score += qPosMark
            } else {
                stat.incorrect++
                stat.score -= effectiveNegMark
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
