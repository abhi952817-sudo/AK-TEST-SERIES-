package Com.ak.testseries.exam

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GeminiResponseValidatorTest {

    private fun validMcq(answer: String = "B", type: String = "MCQ_SINGLE"): JSONObject =
        JSONObject()
            .put("questionTextEn", "Which law explains inertia in a moving body?")
            .put("optionAEn", "Newton's first law")
            .put("optionBEn", "Newton's second law")
            .put("optionCEn", "Newton's third law")
            .put("optionDEn", "Law of gravitation")
            .put("correctAnswer", answer)
            .put("answerStatus", "INDEPENDENTLY_VERIFIED")
            .put("answerConfidence", 96)
            .put("verificationSummary", "The selected option matches the governing law.")
            .put("needsReview", false)
            .put("provenanceType", "GENERATED_PRACTICE")
            .put("sectionId", "PHYSICS_A")
            .put("sectionName", "Physics")
            .put("positiveMarks", 4.0)
            .put("negativeMarks", 1.0)
            .put("questionType", type)

    @Test
    fun acceptsCompleteVerifiedResponse() {
        val root = JSONObject()
            .put("status", "COMPLETE")
            .put("questions", JSONArray().put(validMcq()))
            .put("errors", JSONArray())

        val report = GeminiResponseValidator.parseResponse(root.toString())

        assertTrue(report.isUsable)
        assertEquals(1, report.questions.length())
        assertEquals(0, report.rejectedCount)
    }

    @Test
    fun rejectsLegacyArrayWithoutTaskStatus() {
        val report = GeminiResponseValidator.parseResponse(JSONArray().put(validMcq()).toString())
        assertFalse(report.isUsable)
        assertTrue(report.errors.isNotEmpty())
    }

    @Test
    fun rejectsUnverifiedOrReviewFlaggedQuestion() {
        val item = validMcq()
            .put("answerStatus", "UNVERIFIED")
            .put("needsReview", true)
        val report = GeminiResponseValidator.parseResponse(
            JSONObject().put("status", "COMPLETE").put("questions", JSONArray().put(item)).toString()
        )
        assertFalse(report.isUsable)
        assertEquals(1, report.rejectedCount)
    }

    @Test
    fun rejectsMissingProvenanceAndPlaceholderOptions() {
        val item = validMcq()
            .put("provenanceType", "")
            .put("optionDEn", "Option D")
        val report = GeminiResponseValidator.parseResponse(
            JSONObject().put("status", "COMPLETE").put("questions", JSONArray().put(item)).toString()
        )
        assertFalse(report.isUsable)
        assertTrue(report.errors.first().contains("provenance"))
    }

    @Test
    fun acceptsMultipleCorrectLabelsOnlyForMultipleChoiceType() {
        val multiple = validMcq(answer = "A,C", type = "MCQ_MULTIPLE")
        assertNull(GeminiResponseValidator.validateQuestion(multiple))

        val single = validMcq(answer = "A,C", type = "MCQ_SINGLE")
        assertNotNull(GeminiResponseValidator.validateQuestion(single))
    }

    @Test
    fun acceptsNumericalAnswerWithoutOptions() {
        val numerical = JSONObject()
            .put("questionTextEn", "Calculate the acceleration of a body from the given force and mass.")
            .put("correctAnswer", "-2.5")
            .put("answerStatus", "INDEPENDENTLY_VERIFIED")
            .put("answerConfidence", 92)
            .put("verificationSummary", "The force-to-mass calculation gives the stated value.")
            .put("needsReview", false)
            .put("provenanceType", "SOURCE_EXTRACTED")
            .put("sectionId", "NUMERICAL_A")
            .put("sectionName", "Numerical")
            .put("positiveMarks", 3.0)
            .put("negativeMarks", 0.0)
            .put("questionType", "NAT")
        assertNull(GeminiResponseValidator.validateQuestion(numerical))
    }

    @Test
    fun rejectsErrorOrNeedsReviewTaskEnvelope() {
        for (status in listOf("ERROR", "NEEDS_REVIEW", "")) {
            val report = GeminiResponseValidator.parseResponse(
                JSONObject().put("status", status).put("questions", JSONArray().put(validMcq())).toString()
            )
            assertFalse(report.isUsable)
        }
    }
}
