package Com.ak.testseries.exam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedQuestionAnswerValidatorTest {
    private val fourOptions = listOf("Option A", "Option B", "Option C", "Option D")

    @Test
    fun acceptsSingleCorrectMcqAnswer() {
        assertTrue(GeneratedQuestionAnswerValidator.isValid("MCQ", "C", fourOptions, 4))
    }

    @Test
    fun acceptsMultipleCorrectKeysOnlyWhenDistinctAndPresent() {
        assertTrue(GeneratedQuestionAnswerValidator.isValid("MCQ_MULTIPLE", "A, C", fourOptions, 4))
        assertFalse(GeneratedQuestionAnswerValidator.isValid("MSQ", "A, A", fourOptions, 4))
        assertFalse(GeneratedQuestionAnswerValidator.isValid("MULTI_SELECT", "A, E", fourOptions, 4))
    }

    @Test
    fun enforcesFiveOptionExamPattern() {
        val fiveOptions = fourOptions + "Option E"
        assertTrue(GeneratedQuestionAnswerValidator.isValid("MCQ_SINGLE", "E", fiveOptions, 5))
        assertTrue(GeneratedQuestionAnswerValidator.isValid("MCQ_MULTIPLE", "B, E", fiveOptions, 5))
        assertFalse(GeneratedQuestionAnswerValidator.isValid("MCQ_SINGLE", "A", fourOptions, 5))
    }

    @Test
    fun acceptsFiniteNumericalAnswersWithoutOptions() {
        assertTrue(GeneratedQuestionAnswerValidator.isValid("NAT", "-12.250", emptyList(), 4))
        assertFalse(GeneratedQuestionAnswerValidator.isValid("NUMERICAL", "Infinity", emptyList(), 4))
        assertFalse(GeneratedQuestionAnswerValidator.isValid("TITA", "not numeric", emptyList(), 4))
    }

    @Test
    fun rejectsMissingOptionsWithoutShiftingOptionPositions() {
        val missingB = listOf("Option A", "", "Option C", "Option D")
        assertFalse(GeneratedQuestionAnswerValidator.isValid("MCQ", "C", missingB, 4))
    }
}
