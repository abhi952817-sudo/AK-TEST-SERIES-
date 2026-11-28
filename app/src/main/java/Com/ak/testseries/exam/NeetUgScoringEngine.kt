package Com.ak.testseries.exam

import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity

/**
 * NEET UG 2026 first-release scoring adapter.
 *
 * This intentionally supports only the verified NEET 2026 paper identity and
 * the NTA single-correct MCQ +4/-1/0 rule. Other exams continue through the
 * universal engine until their own policies are verified and integrated.
 */
object NeetUgScoringEngine {
    private const val EXAM_ID = "neet_ug"
    private const val SCORING_VERSION = 1
    private const val STAGE_ID = "neet_main"
    private const val PAPER_ID = "neet_paper_1"
    private const val POSITIVE_MARKS = 4.0
    private const val NEGATIVE_MARKS = 1.0
    private const val FULL_LENGTH_QUESTIONS = 180

    private val supportedQuestionTypes = setOf(
        "", "MCQ", "MCQ_SINGLE", "DIRECT_MCQ", "STATEMENT_I_II",
        "ASSERTION_REASON", "MATCH_LIST_I_II", "DIAGRAM_LABELING", "GRAPH"
    )

    fun evaluateTestAttempt(
        test: TestEntity,
        questions: List<QuestionEntity>,
        userAnswers: Map<String, String>,
        timeSpentSeconds: Long
    ): ExamScoringEngine.ScoreBreakdown {
        require(test.examId == EXAM_ID) { "NEET scoring requires canonical examId neet_ug." }
        require(test.scoringVersion == SCORING_VERSION) { "Unsupported NEET scoring version. Expected 2026 v1." }
        require(test.stageId == STAGE_ID && test.paperId == PAPER_ID) {
            "NEET stage/paper identity does not match the approved 2026 paper."
        }
        require(questions.isNotEmpty() && questions.size <= FULL_LENGTH_QUESTIONS) {
            "NEET attempt must contain between 1 and 180 questions."
        }
        require(timeSpentSeconds >= 0L) { "NEET time spent cannot be negative." }
        require(test.questionsCount == questions.size) {
            "NEET test question count does not match the question records."
        }

        val fullLength = questions.size == FULL_LENGTH_QUESTIONS ||
            test.testType.contains("full", ignoreCase = true)
        if (fullLength) {
            require(questions.size == FULL_LENGTH_QUESTIONS) {
                "A full-length NEET 2026 paper must contain exactly 180 questions."
            }
            require(test.durationMinutes == 0 || test.durationMinutes == 180) {
                "A full-length NEET 2026 paper must use the official 180-minute duration."
            }
        }

        val seenIds = mutableSetOf<String>()
        val subjectCounts = mutableMapOf("physics" to 0, "chemistry" to 0, "biology" to 0)
        var correct = 0
        var wrong = 0
        var unanswered = 0
        var positiveEarned = 0.0
        var negativePenalty = 0.0

        questions.forEach { question ->
            require(question.id.isNotBlank() && seenIds.add(question.id)) {
                "NEET question IDs must be present and unique."
            }
            val type = question.questionType.trim().uppercase()
            require(type in supportedQuestionTypes) {
                "NEET 2026 accepts only four-option single-correct MCQs; unsupported type: $type"
            }

            val subject = normalizeSubject(question.subject)
            if (subject != null) subjectCounts[subject] = subjectCounts.getValue(subject) + 1

            val key = question.correctAnswer.trim().uppercase()
            val userAnswer = userAnswers[question.id]?.trim()?.uppercase()
            if (userAnswer.isNullOrEmpty()) {
                unanswered++
            } else if (!ExamScoringEngine.isAnswerKeyVerified(key)) {
                // The server owns the protected answer key. An unavailable local key
                // must not invent a wrong answer or a negative penalty.
            } else if (ExamScoringEngine.isUserAnswerCorrect(userAnswer, key, false)) {
                correct++
                positiveEarned += POSITIVE_MARKS
            } else {
                require(userAnswer in setOf("A", "B", "C", "D")) {
                    "NEET answers must be one of A, B, C, or D."
                }
                wrong++
                negativePenalty += NEGATIVE_MARKS
            }
        }

        val unknownAnswerIds = userAnswers.keys.filterNot { it in seenIds }
        require(unknownAnswerIds.isEmpty()) {
            "NEET answers contain unknown question IDs: ${unknownAnswerIds.sorted().joinToString(", ")}."
        }

        if (fullLength) {
            require(subjectCounts["physics"] == 45 &&
                subjectCounts["chemistry"] == 45 &&
                subjectCounts["biology"] == 90
            ) {
                "A full-length NEET 2026 paper requires 45 Physics, 45 Chemistry, and 90 Biology questions."
            }
        }

        val rawScore = positiveEarned - negativePenalty
        val attempted = correct + wrong
        val accuracy = if (attempted > 0) correct.toDouble() / attempted.toDouble() * 100.0 else 0.0

        return ExamScoringEngine.ScoreBreakdown(
            totalQuestions = questions.size,
            attemptedCount = attempted,
            correctCount = correct,
            wrongCount = wrong,
            unansweredCount = unanswered,
            positiveMarksEarned = positiveEarned,
            negativeMarksPenalty = negativePenalty,
            finalScore = rawScore,
            maxScore = questions.size * POSITIVE_MARKS,
            accuracyPercent = kotlin.math.round(accuracy * 100.0) / 100.0,
            timeSpentSeconds = timeSpentSeconds,
            correctMarksPerQuestion = POSITIVE_MARKS,
            negativeMarksPerQuestion = NEGATIVE_MARKS,
            rawScore = rawScore
        )
    }

    private fun normalizeSubject(value: String): String? {
        val subject = value.trim().lowercase()
        return when {
            subject.contains("physics") || subject == "phy" -> "physics"
            subject.contains("chemistry") || subject == "chem" -> "chemistry"
            subject.contains("biology") || subject.contains("botany") ||
                subject.contains("zoology") || subject == "bio" -> "biology"
            else -> null
        }
    }
}
