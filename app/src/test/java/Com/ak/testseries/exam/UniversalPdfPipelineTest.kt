package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Test

class UniversalPdfPipelineTest {
    @Test
    fun mcqMultipleIsNotMisclassifiedAsMultiStatement() {
        val exam = UniversalExamRegistry.getExamById("jee_advanced")
            ?: error("JEE Advanced registry entry is required")

        val parsed = UniversalPdfPipeline.parseGeminiResponse(
            jsonString = """
                [
                  {
                    "questionText": "Which of the following statements are correct about this physical system?",
                    "optionA": "Statement A",
                    "optionB": "Statement B",
                    "optionC": "Statement C",
                    "optionD": "Statement D",
                    "correctAnswer": "A, B",
                    "explanation": "Both statements A and B satisfy the stated physical conditions.",
                    "questionType": "MCQ_MULTIPLE"
                  }
                ]
            """.trimIndent(),
            examConfig = exam,
            stageId = "jee_adv_paper_1",
            subjectId = "physics",
            batchIndex = 0,
            existingHashes = emptyMap(),
            actualModel = "unit-test",
            actualSource = "unit-test"
        )

        assertEquals(1, parsed.size)
        assertEquals(QuestionType.MCQ_MULTIPLE, parsed.single().questionType)
    }

    @Test
    fun multipleCorrectQuestionKeysPassStructuralAnswerValidation() {
        val result = QuestionValidationEngine.validateQuestion(
            questionText = "Which of the following statements about the given physical system are correct?",
            options = listOf("Statement A", "Statement B", "Statement C", "Statement D"),
            correctAnswer = "A, B",
            explanation = "Statements A and B satisfy the stated conditions and are therefore correct.",
            sourceAnswer = "A, B",
            questionType = QuestionType.MCQ_MULTIPLE,
            expectedOptionCount = 4
        )

        assertEquals(ValidationStatus.APPROVED, result.status)
    }

    @Test
    fun questionTypeAliasesRemainDistinctAcrossExamFormats() {
        assertEquals(QuestionType.MCQ_MULTIPLE, UniversalPdfPipeline.resolveQuestionType("MSQ", QuestionType.DIRECT_MCQ))
        assertEquals(QuestionType.MCQ_MULTIPLE, UniversalPdfPipeline.resolveQuestionType("MULTI_SELECT", QuestionType.DIRECT_MCQ))
        assertEquals(QuestionType.MULTI_STATEMENT, UniversalPdfPipeline.resolveQuestionType("MULTI_STATEMENT", QuestionType.DIRECT_MCQ))
        assertEquals(QuestionType.TABLE_BASED, UniversalPdfPipeline.resolveQuestionType("TABLE_BASED", QuestionType.DIRECT_MCQ))
        assertEquals(QuestionType.PASSAGE_BASED, UniversalPdfPipeline.resolveQuestionType("PASSAGE_BASED", QuestionType.DIRECT_MCQ))
        assertEquals(QuestionType.CASE_BASED, UniversalPdfPipeline.resolveQuestionType("CASE_BASED", QuestionType.DIRECT_MCQ))
        assertEquals(QuestionType.TITA, UniversalPdfPipeline.resolveQuestionType("TITA", QuestionType.DIRECT_MCQ))
        assertEquals(QuestionType.STATEMENT_BASED, UniversalPdfPipeline.resolveQuestionType("STATEMENT_BASED", QuestionType.DIRECT_MCQ))
        assertEquals(QuestionType.DIRECT_MCQ, UniversalPdfPipeline.resolveQuestionType("MCQ", QuestionType.DIRECT_MCQ))
    }

    @Test
    fun missingIndependentSourceAnswerRequiresReview() {
        val result = QuestionValidationEngine.validateQuestion(
            questionText = "Which statement correctly describes the physical principle?",
            options = listOf("Option A", "Option B", "Option C", "Option D"),
            correctAnswer = "A",
            explanation = "Option A is the answer according to the explanation supplied.",
            questionType = QuestionType.MCQ_SINGLE
        )

        assertEquals(0, result.qualityMetrics.sourceConfidence)
        assertEquals(ValidationStatus.NEEDS_REVIEW, result.status)
    }
}
