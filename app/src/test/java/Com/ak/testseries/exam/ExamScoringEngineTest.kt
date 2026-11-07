package Com.ak.testseries.exam

import Com.ak.testseries.data.QuestionEntity
import org.junit.Assert.*
import org.junit.Test

class ExamScoringEngineTest {

    private fun createQuestion(
        id: String,
        correctAnswer: String,
        subject: String = "Physics",
        section: String = "phy_all",
        questionType: String = "MCQ_SINGLE"
    ): QuestionEntity = QuestionEntity(
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
        chapter = "Verified Chapter",
        section = section,
        questionType = questionType
    )

    @Test
    fun neet2026ScoresOnlyExactCompulsorySubjectSections() {
        val questions = listOf(
            createQuestion("q1", "A", section = "phy_all"),
            createQuestion("q2", "B", section = "chem_all", subject = "Chemistry"),
            createQuestion("q3", "C", section = "bot_all", subject = "Botany"),
            createQuestion("q4", "D", section = "zoo_all", subject = "Zoology")
        )
        val result = ExamScoringEngine.evaluateTestAttempt(
            "NEET UG", questions,
            mapOf("q1" to "A", "q2" to "C", "q4" to "D"),
            120
        )
        assertEquals(4, result.totalQuestions)
        assertEquals(3, result.attemptedCount)
        assertEquals(2, result.correctCount)
        assertEquals(1, result.wrongCount)
        assertEquals(1, result.unansweredCount)
        assertEquals(0, result.ungradedCount)
        assertEquals(8.0, result.positiveMarksEarned, 0.001)
        assertEquals(1.0, result.negativeMarksPenalty, 0.001)
        assertEquals(7.0, result.finalScore, 0.001)
        assertEquals(16.0, result.maxScore, 0.001)
    }

    @Test
    fun jeeMain2026ScoresCompulsoryMcqAndNumericalSections() {
        val questions = listOf(
            createQuestion("mcq", "A", section = "phy_sec_a"),
            createQuestion(
                "numeric_zero", "0", subject = "Mathematics",
                section = "math_sec_b", questionType = "NUMERICAL_VALUE"
            )
        )
        val result = ExamScoringEngine.evaluateTestAttempt(
            "JEE Main P1", questions,
            mapOf("mcq" to "A", "numeric_zero" to "0"),
            90
        )
        assertEquals(2, result.correctCount)
        assertEquals(0, result.wrongCount)
        assertEquals(0, result.ungradedCount)
        assertEquals(8.0, result.positiveMarksEarned, 0.001)
        assertEquals(8.0, result.maxScore, 0.001)
    }

    @Test
    fun jeeMainNumericalAnswerKeysMustBeIntegers() {
        val valid = ExamScoringEngine.evaluateTestAttempt(
            "JEE Main P1",
            listOf(
                createQuestion("integer", "12", "Mathematics", "math_sec_b", "NUMERICAL_VALUE"),
                createQuestion("integer_decimal_format", "12.0", "Physics", "phy_sec_b", "NUMERICAL_VALUE")
            ),
            mapOf("integer" to "12.0", "integer_decimal_format" to "12"),
            30
        )
        assertEquals(2, valid.correctCount)
        assertEquals(0, valid.ungradedCount)
        assertEquals(8.0, valid.finalScore, 0.001)

        val invalid = ExamScoringEngine.evaluateTestAttempt(
            "JEE Main P1",
            listOf(createQuestion("decimal", "12.5", "Mathematics", "math_sec_b", "NUMERICAL_VALUE")),
            mapOf("decimal" to "12.5"),
            30
        )
        assertEquals(0, invalid.correctCount)
        assertEquals(1, invalid.ungradedCount)
        assertEquals(0.0, invalid.finalScore, 0.001)
        assertEquals(4.0, invalid.maxScore, 0.001)
    }

    @Test
    fun directScoringRejectsMissingOrDuplicateQuestionIds() {
        val duplicate = listOf(
            createQuestion("same", "A"),
            createQuestion("same", "B")
        )
        try {
            ExamScoringEngine.evaluateTestAttempt("NEET UG", duplicate, emptyMap(), 10)
            fail("Duplicate question IDs must be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("unique"))
        }
        try {
            ExamScoringEngine.evaluateTestAttempt(
                "NEET UG",
                listOf(createQuestion("", "A")),
                emptyMap(),
                10
            )
            fail("Missing question IDs must be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("unique"))
        }
    }

    @Test
    fun explicitSkippedAnswerIsUnansweredNotWrong() {
        val result = ExamScoringEngine.evaluateTestAttempt(
            "NEET UG",
            listOf(createQuestion("skip", "A")),
            mapOf("skip" to "SKIPPED"),
            10
        )
        assertEquals(0, result.correctCount)
        assertEquals(0, result.wrongCount)
        assertEquals(1, result.unansweredCount)
        assertEquals(0, result.ungradedCount)
        assertEquals(0.0, result.finalScore, 0.001)
    }

    @Test
    fun explicitPaperScoringAndSubjectBreakdownApplyIntegerAnswerPolicy() {
        val config = UniversalExamRegistry.getExamById("jee_main")!!
        val explicit = ExamScoringEngine.calculateScore(
            examConfig = config,
            stageId = "jee_paper_1",
            paperId = "jee_main_p1",
            totalQuestions = 1,
            userAnswers = mapOf("decimal" to "12.5"),
            correctAnswers = mapOf("decimal" to "12.5"),
            questionSections = mapOf("decimal" to "math_sec_b"),
            timeSpentSeconds = 10
        )
        assertEquals(1, explicit.ungradedCount)
        assertEquals(0, explicit.correctCount)
        assertEquals(0.0, explicit.maxScore, 0.001)

        val breakdown = ExamScoringEngine.evaluateSubjectBreakdown(
            "JEE Main P1",
            listOf(createQuestion("decimal", "12.5", "Mathematics", "math_sec_b", "NUMERICAL_VALUE")),
            mapOf("decimal" to "12.5")
        )
        assertEquals(1, breakdown.single().ungraded)
        assertEquals(0, breakdown.single().correct)
        assertEquals(0.0, breakdown.single().score, 0.001)
    }

    @Test
    fun legacyOrAmbiguousSectionsAreUngradedAndNeverDefaultToSectionA() {
        val result = ExamScoringEngine.evaluateTestAttempt(
            "NEET UG",
            listOf(createQuestion("old", "A", section = "SECTION_A")),
            mapOf("old" to "A"),
            30
        )
        assertEquals(0, result.correctCount)
        assertEquals(1, result.ungradedCount)
        assertEquals(0.0, result.finalScore, 0.001)
        assertEquals(0.0, result.maxScore, 0.001)
    }

    @Test
    fun ambiguousBiologySubjectDoesNotCrossAssignBotanyOrZoology() {
        val result = ExamScoringEngine.evaluateTestAttempt(
            "NEET UG",
            listOf(createQuestion("bio", "A", subject = "Biology", section = "bot_all")),
            mapOf("bio" to "A"),
            30
        )
        assertEquals(1, result.ungradedCount)
        assertEquals(0, result.correctCount)
    }

    @Test
    fun unverifiedExamPatternsCannotBeScoredWithGuessedMarks() {
        for (exam in listOf("CTET", "UPSC Civil Services", "CAT", "JEE Advanced", "NEET PG", "RRB NTPC")) {
            try {
                ExamScoringEngine.evaluateTestAttempt(
                    exam,
                    listOf(createQuestion("q", "A")),
                    mapOf("q" to "A"),
                    30
                )
                fail("Expected scoring to be blocked for $exam")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("source-verified", ignoreCase = true))
            }
        }
    }

    @Test
    fun numericalZeroIsAValidJeeMainAnswer() {
        val result = ExamScoringEngine.evaluateTestAttempt(
            "JEE Main P1",
            listOf(createQuestion("zero", "0", "Mathematics", "math_sec_b", "NUMERICAL_VALUE")),
            mapOf("zero" to "0"),
            5
        )
        assertEquals(1, result.correctCount)
        assertEquals(0, result.unansweredCount)
        assertEquals(4.0, result.finalScore, 0.001)
    }

    @Test
    fun answerKeysWithInvalidNumericalOrMcqFormatAreUngraded() {
        val result = ExamScoringEngine.evaluateTestAttempt(
            "NEET UG",
            listOf(createQuestion("bad", "12abc")),
            mapOf("bad" to "12"),
            5
        )
        assertEquals(1, result.ungradedCount)
        assertEquals(0, result.correctCount)
    }

    @Test
    fun explicitStagePaperCalculationHasNoGenericMarksFallback() {
        val config = UniversalExamRegistry.getExam("neet_ug")!!
        val result = ExamScoringEngine.calculateScore(
            examConfig = config,
            stageId = "neet_main",
            paperId = "neet_paper_1",
            totalQuestions = 2,
            userAnswers = mapOf("q1" to "A", "q2" to "C"),
            correctAnswers = mapOf("q1" to "A", "q2" to "B"),
            questionSections = mapOf("q1" to "phy_all", "q2" to "phy_all"),
            timeSpentSeconds = 60
        )
        assertEquals(1, result.correctCount)
        assertEquals(1, result.wrongCount)
        assertEquals(3.0, result.finalScore, 0.001)
        assertEquals(8.0, result.maxScore, 0.001)
        assertEquals(0, result.ungradedCount)
    }

    @Test
    fun subjectBreakdownReportsInvalidQuestionsAsUngraded() {
        val result = ExamScoringEngine.evaluateSubjectBreakdown(
            "NEET UG",
            listOf(
                createQuestion("p1", "A"),
                createQuestion("p2", "B"),
                createQuestion("bad", "A", subject = "Biology", section = "bot_all")
            ),
            mapOf("p1" to "A", "p2" to "C", "bad" to "A")
        )
        val physics = result.single { it.subject == "Physics" }
        val biology = result.single { it.subject == "Biology" }
        assertEquals(1, physics.correct)
        assertEquals(1, physics.incorrect)
        assertEquals(3.0, physics.score, 0.001)
        assertEquals(8.0, physics.maxScore, 0.001)
        assertEquals(1, biology.ungraded)
        assertEquals(0.0, biology.score, 0.001)
    }

    @Test
    fun optionEquivalenceAndMarkFormattingRemainStable() {
        assertTrue(ExamScoringEngine.areOptionsEquivalent("1", "A"))
        assertTrue(ExamScoringEngine.areOptionsEquivalent("12.25", "12.250", true))
        assertFalse(ExamScoringEngine.areOptionsEquivalent("12.25", "12.26", true))
        assertEquals("4", ExamScoringEngine.formatMark(4.0))
        assertEquals("2.5", ExamScoringEngine.formatMark(2.5))
    }
}
