package Com.ak.testseries.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionPayloadValidatorTest {

    private fun mcq(
        text: String = "Which statement is correct?",
        includeOptionE: Boolean = false,
        optionA: String = "Alpha",
        answer: String = "B"
    ): JSONObject = JSONObject().apply {
        put("questionText", text)
        put("optionA", optionA)
        put("optionB", "Beta")
        put("optionC", "Gamma")
        put("optionD", "Delta")
        if (includeOptionE) put("optionE", "Epsilon")
        put("correctAnswer", answer)
        put("explanation", "The second option is supported by the stated facts.")
        put("questionType", "MCQ_SINGLE")
    }

    @Test
    fun acceptsValidFourOptionQuestionForFourOptionExam() {
        val payload = JSONArray().put(mcq()).toString()
        assertTrue(QuestionPayloadValidator.isValid(payload, expectedCount = 1, expectedOptionCount = 4))
    }

    @Test
    fun acceptsValidFiveOptionQuestionForFiveOptionExam() {
        val payload = JSONArray().put(mcq(includeOptionE = true, answer = "E")).toString()
        assertTrue(QuestionPayloadValidator.isValid(payload, expectedCount = 1, expectedOptionCount = 5))
    }

    @Test
    fun rejectsFiveOptionExamQuestionWhenOptionEIsMissing() {
        val payload = JSONArray().put(mcq()).toString()
        assertFalse(QuestionPayloadValidator.isValid(payload, expectedCount = 1, expectedOptionCount = 5))
    }

    @Test
    fun rejectsPlaceholderOptionsAndInvalidAnswerKeys() {
        val placeholderPayload = JSONArray().put(mcq(optionA = "Option A")).toString()
        assertFalse(QuestionPayloadValidator.isValid(placeholderPayload, 1, 4))

        val invalidAnswerPayload = JSONArray().put(mcq(answer = "Option B")).toString()
        assertFalse(QuestionPayloadValidator.isValid(invalidAnswerPayload, 1, 4))
    }

    @Test
    fun rejectsDuplicateQuestionStems() {
        val payload = JSONArray()
            .put(mcq(text = "Which statement is correct?"))
            .put(mcq(text = "  WHICH   STATEMENT IS CORRECT? "))
            .toString()
        assertFalse(QuestionPayloadValidator.isValid(payload, expectedCount = 2, expectedOptionCount = 4))
    }

    @Test
    fun acceptsNumericalQuestionOnlyWithoutMcqOptionsAndWithNumericAnswer() {
        val valid = JSONObject().apply {
            put("questionText", "Calculate the value of x.")
            put("correctAnswer", "-2.5")
            put("explanation", "Substitution gives negative two point five.")
            put("questionType", "NUMERICAL_VALUE")
        }
        assertTrue(QuestionPayloadValidator.isValid(JSONArray().put(valid).toString(), 1, 4))

        val invalid = JSONObject(valid.toString()).apply { put("optionA", "2") }
        assertFalse(QuestionPayloadValidator.isValid(JSONArray().put(invalid).toString(), 1, 4))
    }

    @Test
    fun rejectsQuestionCountMismatchAndMissingExplanation() {
        val payload = JSONArray().put(mcq()).toString()
        assertFalse(QuestionPayloadValidator.isValid(payload, expectedCount = 2, expectedOptionCount = 4))

        val noExplanation = JSONObject(mcq().toString()).apply { put("explanation", "") }
        assertFalse(QuestionPayloadValidator.isValid(JSONArray().put(noExplanation).toString(), 1, 4))
    }
}
