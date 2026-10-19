package Com.ak.testseries.exam

import Com.ak.testseries.data.QuestionEntity
import org.junit.Assert.*
import org.junit.Test

class ExamScoringEngineTest {

    private fun createQuestion(
        id: String,
        correctAnswer: String,
        subject: String = "Physics",
        section: String = "SECTION_A",
        questionType: String = "MCQ"
    ): QuestionEntity {
        return QuestionEntity(
            id = id,
            testId = "test_unit",
            questionText = "Question $id",
            optionA = "Choice A",
            optionB = "Choice B",
            optionC = "Choice C",
            optionD = "Choice D",
            correctAnswer = correctAnswer,
            explanation = "Explanation for $id",
            subject = subject,
            chapter = "Chapter 1",
            section = section,
            questionType = questionType
        )
    }

    @Test
    fun testNeetScoringStandardPlusMinus() {
        val q1 = createQuestion("q1", "A") // Correct (+4)
        val q2 = createQuestion("q2", "B") // Wrong (-1)
        val q3 = createQuestion("q3", "C") // Skipped (0)
        val q4 = createQuestion("q4", "D") // Correct (+4)

        val userAnswers = mapOf(
            "q1" to "A",
            "q2" to "C", // Wrong
            "q4" to "D"
        )

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "NEET UG",
            questions = listOf(q1, q2, q3, q4),
            userAnswers = userAnswers,
            timeSpentSeconds = 120
        )

        assertEquals(4, breakdown.totalQuestions)
        assertEquals(3, breakdown.attemptedCount)
        assertEquals(2, breakdown.correctCount)
        assertEquals(1, breakdown.wrongCount)
        assertEquals(1, breakdown.unansweredCount)
        assertEquals(8.0, breakdown.positiveMarksEarned, 0.001)
        assertEquals(1.0, breakdown.negativeMarksPenalty, 0.001)
        assertEquals(7.0, breakdown.finalScore, 0.001)
        assertEquals(7.0, breakdown.rawScore, 0.001)
        assertEquals(16.0, breakdown.maxScore, 0.001) // 4 * 4 = 16
        assertEquals(66.67, breakdown.accuracyPercent, 0.01)
    }

    @Test
    fun testZeroNegativeMarkingExams() {
        val q1 = createQuestion("ctet_1", "A") // Correct (+1)
        val q2 = createQuestion("ctet_2", "B") // Wrong (0 penalty)
        val q3 = createQuestion("ctet_3", "C") // Skipped (0)

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "CTET",
            questions = listOf(q1, q2, q3),
            userAnswers = mapOf("ctet_1" to "A", "ctet_2" to "D"),
            timeSpentSeconds = 60
        )

        assertEquals(1, breakdown.correctCount)
        assertEquals(1, breakdown.wrongCount)
        assertEquals(1, breakdown.unansweredCount)
        assertEquals(1.0, breakdown.positiveMarksEarned, 0.001)
        assertEquals(0.0, breakdown.negativeMarksPenalty, 0.001) // No negative marking in CTET
        assertEquals(1.0, breakdown.finalScore, 0.001)
        assertEquals(3.0, breakdown.maxScore, 0.001)
    }

    @Test
    fun testUgcNetScoringTwoMarksZeroNegative() {
        val q1 = createQuestion("net_1", "A") // Correct (+2)
        val q2 = createQuestion("net_2", "B") // Wrong (0 penalty)

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "UGC NET",
            questions = listOf(q1, q2),
            userAnswers = mapOf("net_1" to "A", "net_2" to "C"),
            timeSpentSeconds = 60
        )

        assertEquals(2.0, breakdown.positiveMarksEarned, 0.001)
        assertEquals(0.0, breakdown.negativeMarksPenalty, 0.001)
        assertEquals(2.0, breakdown.finalScore, 0.001)
        assertEquals(4.0, breakdown.maxScore, 0.001)
    }

    @Test
    fun testCatMcqVsTitaScoring() {
        val qMcq1 = createQuestion("cat_mcq", "A", questionType = "MCQ") // Correct (+3)
        val qMcq2 = createQuestion("cat_mcq_wrong", "B", questionType = "MCQ") // Wrong (-1)
        val qTita1 = createQuestion("cat_tita_right", "42", questionType = "TITA") // Correct (+3)
        val qTita2 = createQuestion("cat_tita_wrong", "15", questionType = "TITA") // Wrong (0 penalty for TITA)

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "CAT",
            questions = listOf(qMcq1, qMcq2, qTita1, qTita2),
            userAnswers = mapOf(
                "cat_mcq" to "A",
                "cat_mcq_wrong" to "C", // Wrong MCQ (-1)
                "cat_tita_right" to "42", // Correct TITA (+3)
                "cat_tita_wrong" to "99" // Wrong TITA (0 penalty)
            ),
            timeSpentSeconds = 180
        )

        assertEquals(2, breakdown.correctCount)
        assertEquals(2, breakdown.wrongCount)
        assertEquals(6.0, breakdown.positiveMarksEarned, 0.001) // 3 + 3
        assertEquals(1.0, breakdown.negativeMarksPenalty, 0.001) // Only MCQ penalized (-1), TITA not penalized
        assertEquals(5.0, breakdown.finalScore, 0.001)
        assertEquals(12.0, breakdown.maxScore, 0.001)
    }

    @Test
    fun testUpscCivilServicesNegativeMarking() {
        val q1 = createQuestion("upsc_1", "A") // Correct (+2.0)
        val q2 = createQuestion("upsc_2", "B") // Wrong (-0.66)
        val q3 = createQuestion("upsc_3", "C") // Skipped (0)

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "UPSC CSE",
            questions = listOf(q1, q2, q3),
            userAnswers = mapOf("upsc_1" to "A", "upsc_2" to "D"),
            timeSpentSeconds = 90
        )

        assertEquals(1, breakdown.correctCount)
        assertEquals(1, breakdown.wrongCount)
        assertEquals(1, breakdown.unansweredCount)
        assertEquals(2.0, breakdown.positiveMarksEarned, 0.001)
        assertEquals(0.66, breakdown.negativeMarksPenalty, 0.001)
        assertEquals(1.34, breakdown.finalScore, 0.01)
        assertEquals(6.0, breakdown.maxScore, 0.001)
    }

    @Test
    fun testSscCglAndChslNegativeMarking() {
        val q1 = createQuestion("cgl_1", "A") // Correct (+2.0)
        val q2 = createQuestion("cgl_2", "B") // Wrong (-0.50)

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "SSC CGL",
            questions = listOf(q1, q2),
            userAnswers = mapOf("cgl_1" to "A", "cgl_2" to "C"),
            timeSpentSeconds = 60
        )

        assertEquals(2.0, breakdown.positiveMarksEarned, 0.001)
        assertEquals(0.50, breakdown.negativeMarksPenalty, 0.001)
        assertEquals(1.50, breakdown.finalScore, 0.001)
        assertEquals(4.0, breakdown.maxScore, 0.001)
    }

    @Test
    fun testSscGdConstableNegativeMarking() {
        val q1 = createQuestion("gd_1", "A") // Correct (+2.0)
        val q2 = createQuestion("gd_2", "B") // Wrong (-0.25)

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "SSC GD Constable",
            questions = listOf(q1, q2),
            userAnswers = mapOf("gd_1" to "A", "gd_2" to "C"),
            timeSpentSeconds = 60
        )

        assertEquals(2.0, breakdown.positiveMarksEarned, 0.001)
        assertEquals(0.25, breakdown.negativeMarksPenalty, 0.001)
        assertEquals(1.75, breakdown.finalScore, 0.001)
        assertEquals(4.0, breakdown.maxScore, 0.001)
    }

    @Test
    fun testBankingExamsNegativeMarking() {
        val q1 = createQuestion("ibps_1", "A") // Correct (+1.0)
        val q2 = createQuestion("ibps_2", "B") // Wrong (-0.25)

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "IBPS PO",
            questions = listOf(q1, q2),
            userAnswers = mapOf("ibps_1" to "A", "ibps_2" to "E"),
            timeSpentSeconds = 60
        )

        assertEquals(1.0, breakdown.positiveMarksEarned, 0.001)
        assertEquals(0.25, breakdown.negativeMarksPenalty, 0.001)
        assertEquals(0.75, breakdown.finalScore, 0.001)
        assertEquals(2.0, breakdown.maxScore, 0.001)
    }

    @Test
    fun testNumericOptionEquivalence() {
        // Option "1" should match "A", "2" should match "B"
        assertTrue(ExamScoringEngine.areOptionsEquivalent("1", "A"))
        assertTrue(ExamScoringEngine.areOptionsEquivalent("A", "1"))
        assertTrue(ExamScoringEngine.areOptionsEquivalent("2", "B"))
        assertTrue(ExamScoringEngine.areOptionsEquivalent("4", "D"))
        assertTrue(ExamScoringEngine.areOptionsEquivalent("5", "E"))
        assertFalse(ExamScoringEngine.areOptionsEquivalent("1", "B"))

        // Float / decimal equivalence
        assertTrue(ExamScoringEngine.areOptionsEquivalent("12.25", "12.250"))
        assertTrue(ExamScoringEngine.areOptionsEquivalent("0.5", ".5"))
        assertFalse(ExamScoringEngine.areOptionsEquivalent("12.25", "12.26"))

        // Test in evaluateTestAttempt
        val q1 = createQuestion("q_num_1", "A") // student answers "1"
        val q2 = createQuestion("q_num_2", "12.25", questionType = "NUMERICAL") // student answers "12.250"

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "NEET UG",
            questions = listOf(q1, q2),
            userAnswers = mapOf("q_num_1" to "1", "q_num_2" to "12.250"),
            timeSpentSeconds = 60
        )

        assertEquals(2, breakdown.correctCount)
        assertEquals(0, breakdown.wrongCount)
        assertEquals(8.0, breakdown.positiveMarksEarned, 0.001)
    }

    @Test
    fun testMultipleCorrectAndCommaSeparatedKeys() {
        // Either A or B accepted
        assertTrue(ExamScoringEngine.isUserAnswerCorrect("A", "A, B"))
        assertTrue(ExamScoringEngine.isUserAnswerCorrect("B", "A, B"))
        assertFalse(ExamScoringEngine.isUserAnswerCorrect("C", "A, B"))

        // Full multi-correct answer matches
        assertTrue(ExamScoringEngine.isUserAnswerCorrect("A, B", "A, B"))
        assertTrue(ExamScoringEngine.isUserAnswerCorrect("B, A", "A, B"))
    }

    @Test
    fun testNonNegativeScoreBoundaryAndRawScorePreservation() {
        // If student gets 0 right and 4 wrong in NEET (-4 raw score)
        val q1 = createQuestion("w1", "A")
        val q2 = createQuestion("w2", "A")
        val q3 = createQuestion("w3", "A")
        val q4 = createQuestion("w4", "A")

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "NEET UG",
            questions = listOf(q1, q2, q3, q4),
            userAnswers = mapOf("w1" to "B", "w2" to "B", "w3" to "B", "w4" to "B"),
            timeSpentSeconds = 60
        )

        assertEquals(0, breakdown.correctCount)
        assertEquals(4, breakdown.wrongCount)
        assertEquals(0.0, breakdown.positiveMarksEarned, 0.001)
        assertEquals(4.0, breakdown.negativeMarksPenalty, 0.001)
        assertEquals(-4.0, breakdown.rawScore, 0.001) // Raw uncoerced score preserved
        assertEquals(0.0, breakdown.finalScore, 0.001) // Coerced to 0.0 display boundary
    }

    @Test
    fun testSectionBOptionalAttemptCapping() {
        // 12 questions in Physics Section B for NEET UG (only first 10 should be evaluated)
        val questions = (1..12).map { i ->
            createQuestion(
                id = "phy_b_$i",
                correctAnswer = "A",
                subject = "Physics",
                section = "SECTION_B"
            )
        }

        // Student answers all 12 questions (first 10 correct, 11 and 12 wrong)
        val userAnswers = mutableMapOf<String, String>()
        for (i in 1..10) {
            userAnswers["phy_b_$i"] = "A" // Correct
        }
        userAnswers["phy_b_11"] = "B" // Surplus wrong (should be ignored)
        userAnswers["phy_b_12"] = "B" // Surplus wrong (should be ignored)

        val breakdown = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "NEET UG",
            questions = questions,
            userAnswers = userAnswers,
            timeSpentSeconds = 300
        )

        assertEquals("Only first 10 attempts in Section B should be counted", 10, breakdown.attemptedCount)
        assertEquals("First 10 were correct", 10, breakdown.correctCount)
        assertEquals("Surplus attempts 11 & 12 should not count as wrong", 0, breakdown.wrongCount)
        assertEquals(40.0, breakdown.positiveMarksEarned, 0.001)
        assertEquals(0.0, breakdown.negativeMarksPenalty, 0.001)
    }

    @Test
    fun testFormatMarkHelper() {
        assertEquals("4", ExamScoringEngine.formatMark(4.0))
        assertEquals("0", ExamScoringEngine.formatMark(0.0))
        assertEquals("2.5", ExamScoringEngine.formatMark(2.5))
        assertEquals("0.66", ExamScoringEngine.formatMark(0.66))
        assertEquals("0.33", ExamScoringEngine.formatMark(0.33))
        assertEquals("0.25", ExamScoringEngine.formatMark(0.25))
    }

    @Test
    fun testSubjectBreakdownAccuracy() {
        val p1 = createQuestion("p1", "A", subject = "Physics") // Correct (+4)
        val p2 = createQuestion("p2", "B", subject = "Physics") // Wrong (-1)
        val c1 = createQuestion("c1", "A", subject = "Chemistry") // Correct (+4)
        val c2 = createQuestion("c2", "A", subject = "Chemistry") // Skipped

        val userAnswers = mapOf("p1" to "A", "p2" to "D", "c1" to "A")

        val breakdown = ExamScoringEngine.evaluateSubjectBreakdown(
            targetExam = "NEET UG",
            questions = listOf(p1, p2, c1, c2),
            userAnswers = userAnswers
        )

        assertEquals(2, breakdown.size)
        val phyStat = breakdown.find { it.subject == "Physics" }!!
        val chemStat = breakdown.find { it.subject == "Chemistry" }!!

        assertEquals(2, phyStat.total)
        assertEquals(1, phyStat.correct)
        assertEquals(1, phyStat.incorrect)
        assertEquals(3.0, phyStat.score, 0.001) // 4 - 1 = 3
        assertEquals(8.0, phyStat.maxScore, 0.001)
        assertEquals(50f, phyStat.accuracy, 0.01f)

        assertEquals(2, chemStat.total)
        assertEquals(1, chemStat.correct)
        assertEquals(0, chemStat.incorrect)
        assertEquals(4.0, chemStat.score, 0.001)
        assertEquals(8.0, chemStat.maxScore, 0.001)
        assertEquals(100f, chemStat.accuracy, 0.01f)
    }
}
