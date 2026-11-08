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
            JSONObject().put("status", "COMPLETE").put("questions", JSONArray().put(item)).put("errors", JSONArray()).toString()
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
            JSONObject().put("status", "COMPLETE").put("questions", JSONArray().put(item)).put("errors", JSONArray()).toString()
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


    private fun testExamConfig(): ExamConfig = ExamConfig(
        examId = "neet_ug",
        examName = "NEET UG",
        shortName = "NEET",
        category = "Medical",
        stages = listOf(
            StageConfig(
                stageId = "stage_1",
                stageName = "Main",
                papers = listOf(
                    PaperConfig(
                        paperId = "paper_1",
                        paperName = "Paper",
                        subjects = listOf(
                            SubjectConfig(
                                subjectId = "physics",
                                subjectName = "Physics",
                                sections = listOf(
                                    SectionConfig(
                                        sectionId = "PHYSICS_A",
                                        sectionName = "Physics",
                                        totalQuestions = 1,
                                        compulsoryQuestions = 1,
                                        positiveMarks = 4.0,
                                        negativeMarks = 1.0,
                                        questionType = QuestionType.MCQ_SINGLE,
                                        optionsCount = 4
                                    )
                                )
                            )
                        ),
                        durationMinutes = 60,
                        totalMarks = 4.0
                    )
                )
            )
        )
    )

    @Test
    fun examConfigRejectsIdentityScoringAndQuestionTypeMismatch() {
        val wrongExam = validMcq().put("examId", "jee_main")
            .put("subjectId", "physics")
        val root = JSONObject().put("status", "COMPLETE").put("questions", JSONArray().put(wrongExam)).put("errors", JSONArray())
        val report = GeminiResponseValidator.parseResponse(root.toString(), testExamConfig())
        assertFalse(report.isUsable)
        assertTrue(report.errors.any { it.contains("examId") })
    }

    @Test
    fun examConfigAcceptsOnlyExactConfiguredSectionAndMarks() {
        val item = validMcq().put("examId", "neet_ug").put("subjectId", "physics")
        val root = JSONObject().put("status", "COMPLETE").put("questions", JSONArray().put(item)).put("errors", JSONArray())
        val report = GeminiResponseValidator.parseResponse(root.toString(), testExamConfig())
        assertTrue(report.isUsable)
        assertEquals(1, report.questions.length())
    }

    @Test
    fun rejectsPartialTaskEvenWhenQuestionArrayLooksValid() {
        val root = JSONObject()
            .put("status", "PARTIAL")
            .put("questions", JSONArray().put(validMcq()))
            .put("errors", JSONArray())
        val report = GeminiResponseValidator.parseResponse(root.toString())
        assertFalse(report.isUsable)
        assertEquals(0, report.questions.length())
        assertTrue(report.errors.any { it.contains("COMPLETE") })
    }

    @Test
    fun rejectsTaskEnvelopeWithReportedErrorsEvenWhenQuestionsExist() {
        val root = JSONObject()
            .put("status", "COMPLETE")
            .put("questions", JSONArray().put(validMcq()))
            .put("errors", JSONArray().put("One source page could not be read."))
        val report = GeminiResponseValidator.parseResponse(root.toString())
        assertFalse(report.isUsable)
        assertEquals(0, report.questions.length())
        assertEquals(1, report.rejectedCount)
    }

    @Test
    fun rejectsSourceAnswerConflictAndMissingExtractedKey() {
        val conflict = validMcq()
            .put("answerStatus", "KEY_EXTRACTED")
            .put("sourceAnswer", "A")
        assertTrue(GeminiResponseValidator.validateQuestion(conflict)!!.contains("conflicts"))

        val missingKey = validMcq().put("answerStatus", "KEY_EXTRACTED")
        assertTrue(GeminiResponseValidator.validateQuestion(missingKey)!!.contains("sourceAnswer"))
    }

    @Test
    fun normalizesOrderForMultipleCorrectSourceKeys() {
        val item = validMcq(answer = "A,C", type = "MCQ_MULTIPLE")
            .put("sourceAnswer", "C,A")
        assertNull(GeminiResponseValidator.validateQuestion(item))
    }

    @Test
    fun enforcesExactConfiguredOptionCountAndOptionOrder() {
        val fourOptions = validMcq()
        assertNotNull(GeminiResponseValidator.validateQuestion(fourOptions, 5, requireExactOptionCount = true))

        val missingA = validMcq()
            .put("optionAEn", "")
            .put("optionEEn", "Friction")
        assertNotNull(GeminiResponseValidator.validateQuestion(missingA, 4, requireExactOptionCount = true))
    }

    @Test
    fun onlyMcqMultipleMayCarryMultipleCorrectLabels() {
        val multiStatement = validMcq(answer = "A,C", type = "MULTI_STATEMENT")
        assertNotNull(GeminiResponseValidator.validateQuestion(multiStatement))
    }

    @Test
    fun validationEngineAcceptsMultipleCorrectAnswersAndOrderIndependentSourceKey() {
        val result = QuestionValidationEngine.validateQuestion(
            questionText = "Which of the following are vector quantities?",
            options = listOf("Velocity", "Speed", "Force", "Mass"),
            correctAnswer = "A,C",
            sourceAnswer = "C,A",
            explanation = "Velocity and force have magnitude and direction.",
            questionType = QuestionType.MCQ_MULTIPLE,
            expectedOptionCount = 4
        )
        assertFalse(result.issues.any { it.contains("Multiple-correct") || it.contains("Answer conflict") })
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
