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

    private fun isFiniteNumericAnswer(value: String): Boolean {
        val number = value.trim().toDoubleOrNull() ?: return false
        return number.isFinite()
    }

    fun isAnswerKeyVerified(key: String): Boolean {
        val clean = key.trim().uppercase()
        if (clean.isEmpty()) return false
        val parts = if (clean.contains(",")) clean.split(",").map { it.trim() } else listOf(clean)
        if (parts.any { it.isEmpty() } || parts.distinct().size != parts.size) return false
        return parts.all { it in VALID_ANSWER_OPTIONS || isFiniteNumericAnswer(it) }
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
        if (isNumerical && (num1 == null || num2 == null || !num1.isFinite() || !num2.isFinite())) {
            return false
        }
        if (num1 != null && num2 != null) {
            return Math.abs(num1 - num2) < 0.0001
        }
        return false
    }

    private fun isMultiSelectQuestionType(questionType: String): Boolean {
        return questionType.trim().uppercase() in setOf(
            "MSQ", "MULTI_SELECT", "MULTIPLE_SELECT", "MULTIPLE_CORRECT", "MCQ_MULTIPLE"
        )
    }

    fun isUserAnswerCorrect(
        userAns: String,
        correctAns: String,
        isNumerical: Boolean = false,
        questionType: String = ""
    ): Boolean {
        val u = userAns.trim().uppercase()
        val c = correctAns.trim().uppercase()
        if (u.isEmpty() || c.isEmpty()) return false

        if (isMultiSelectQuestionType(questionType)) {
            val indexToLetter = mapOf("1" to "A", "2" to "B", "3" to "C", "4" to "D", "5" to "E")
            fun normalizeOption(value: String): String {
                val token = value.trim().uppercase()
                return if (isNumerical) token else (indexToLetter[token] ?: token)
            }
            val userParts = u.split(",").map(::normalizeOption)
            val correctParts = c.split(",").map(::normalizeOption)
            if (userParts.any { it.isEmpty() } || correctParts.any { it.isEmpty() }) return false
            if (userParts.distinct().size != userParts.size || correctParts.distinct().size != correctParts.size) return false
            return userParts.size == correctParts.size && userParts.toSet() == correctParts.toSet()
        }

        if (areOptionsEquivalent(u, c, isNumerical)) return true
        if (c.contains(",")) {
            val acceptedKeys = c.split(",").map { it.trim() }
            return acceptedKeys.any { areOptionsEquivalent(u, it, isNumerical) }
        }
        return false
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

        var positiveMarksEarned = 0.0
        var negativeMarksPenalty = 0.0

        val isMultiScheme = UniversalExamRegistry.hasMultipleMarkingSchemes(targetExam)
        val isCat = targetExam.equals("CAT", ignoreCase = true) || targetExam.contains("CAT", ignoreCase = true)
        var maxPossible = 0.0

        val sectionAttemptCounts = mutableMapOf<String, Int>()

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
            } else {
                val secKey = "${question.subject.lowercase().trim()}_${question.section.lowercase().trim()}"
                val secLimit = UniversalExamRegistry.getMaximumAttemptQuestionsCountForSection(
                    examName = targetExam,
                    sectionNameOrId = question.section,
                    subjectName = question.subject
                )
                val currentAttempts = sectionAttemptCounts.getOrDefault(secKey, 0)
                if (secLimit != null && currentAttempts >= secLimit) {
                    // Surplus question in optional section - ignored per official exam rules
                    unanswered++
                } else {
                    if (secLimit != null) {
                        sectionAttemptCounts[secKey] = currentAttempts + 1
                    }
                    if (!isKeyVerified) {
                        // Unknown/unverified answer key: student choice cannot be scored; no penalty or accuracy skew
                    } else if (isUserAnswerCorrect(userOpt, correctOpt, isTitaOrNumerical, question.questionType)) {
                        correct++
                        positiveMarksEarned += qPosMark
                    } else {
                        wrong++
                        negativeMarksPenalty += effectiveNegMark
                    }
                }
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
            negativeMarksPerQuestion = negMark,
            rawScore = rawScore
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
        timeSpentSeconds: Long,
        questionTypes: Map<String, String> = emptyMap() // questionId -> question type, required for MSQ
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

            val isNumerical = cleanCorrect.toDoubleOrNull() != null

            if (userOpt.isNullOrEmpty()) {
                unanswered++
            } else if (!isKeyVerified) {
                // Unknown/unverified answer key
            } else if (isUserAnswerCorrect(userOpt, cleanCorrect, isNumerical, questionTypes[qid].orEmpty())) {
                correct++
                totalPositiveEarned += posMarks
            } else {
                wrong++
                totalNegativePenalty += negMarks
            }
        }

        val rawScore = totalPositiveEarned - totalNegativePenalty
        val finalScore = rawScore.coerceAtLeast(0.0)
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
            negativeMarksPerQuestion = defaultNeg,
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
                    } else if (isUserAnswerCorrect(userOpt, correctOpt, isTitaOrNumerical, question.questionType)) {
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
