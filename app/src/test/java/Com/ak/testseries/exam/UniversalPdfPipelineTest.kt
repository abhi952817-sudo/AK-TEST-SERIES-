package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
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
}
