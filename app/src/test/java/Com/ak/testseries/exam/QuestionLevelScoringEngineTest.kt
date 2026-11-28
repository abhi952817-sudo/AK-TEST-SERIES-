package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuestionLevelScoringEngineTest {
    private fun multiQuestion() = QuestionLevelScoringEngine.Question(
        id = "multi_1",
        strategyId = "MULTI_SELECT_PARTIAL_TABLE",
        maxMarks = 4.0,
        rubric = QuestionLevelScoringEngine.Rubric(
            schemaVersion = 1,
            unmatchedMarks = -2.0,
            rules = listOf(
                QuestionLevelScoringEngine.RubricRule("A,B", 4.0, QuestionLevelScoringEngine.Outcome.CORRECT),
                QuestionLevelScoringEngine.RubricRule("A", 2.0, QuestionLevelScoringEngine.Outcome.PARTIAL),
                QuestionLevelScoringEngine.RubricRule("A,C", 1.0, QuestionLevelScoringEngine.Outcome.PARTIAL)
            )
        )
    )

    @Test fun multiSelectIsOrderIndependentAndCanAwardPartialMarks() {
        val result = QuestionLevelScoringEngine.evaluate(listOf(multiQuestion()), mapOf("multi_1" to "C,A"))
        assertEquals(1, result.partialCount)
        assertEquals(1.0, result.finalScore, 0.001)
    }

    @Test fun binaryStrategyKeepsFullCorrectOrNegativeMarking() {
        val question = QuestionLevelScoringEngine.Question(
            id = "binary", strategyId = "BINARY_MARKS", maxMarks = 4.0,
            positiveMarks = 4.0, negativeMarks = 1.0, correctAnswer = "C"
        )
        val result = QuestionLevelScoringEngine.evaluate(listOf(question), mapOf("binary" to "C"))
        assertEquals(1, result.correctCount)
        assertEquals(4.0, result.finalScore, 0.001)
    }

    @Test fun numericToleranceRequiresAndUsesExplicitTolerance() {
        val question = QuestionLevelScoringEngine.Question(
            id = "numeric", strategyId = "NUMERIC_TOLERANCE", maxMarks = 4.0,
            positiveMarks = 4.0, negativeMarks = 1.0, correctAnswer = "12.5",
            rubric = QuestionLevelScoringEngine.Rubric(1, emptyList(), 0.0, tolerance = 0.01)
        )
        assertEquals(4.0, QuestionLevelScoringEngine.evaluate(listOf(question), mapOf("numeric" to "12.509")).finalScore, 0.001)
        assertEquals(4.0, QuestionLevelScoringEngine.evaluate(listOf(question), mapOf("numeric" to "12.5")).finalScore, 0.001)
        assertThrows(IllegalArgumentException::class.java) {
            QuestionLevelScoringEngine.evaluate(listOf(question), mapOf("numeric" to "NaN"))
        }
    }

    @Test fun missingRubricAndDuplicateQuestionIdsFailClosed() {
        val invalid = multiQuestion().copy(rubric = null)
        assertThrows(IllegalArgumentException::class.java) {
            QuestionLevelScoringEngine.evaluate(listOf(invalid), mapOf("multi_1" to "A"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            QuestionLevelScoringEngine.evaluate(listOf(multiQuestion(), multiQuestion()), mapOf("multi_1" to "A"))
        }
    }

    @Test fun unknownAnswerQuestionIdsFailClosed() {
        val question = multiQuestion()
        assertThrows(IllegalArgumentException::class.java) {
            QuestionLevelScoringEngine.evaluate(listOf(question), mapOf("not_in_test" to "A"))
        }
    }

    @Test fun unansweredUsesExplicitRubricValue() {
        val question = multiQuestion().copy(rubric = multiQuestion().rubric!!.copy(unansweredMarks = 0.5))
        val result = QuestionLevelScoringEngine.evaluate(listOf(question), emptyMap())
        assertEquals(1, result.unansweredCount)
        assertEquals(0.5, result.finalScore, 0.001)
    }
}
