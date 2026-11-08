package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UniversalPdfPipelineSectionResolutionTest {

    private fun config(): ExamConfig = ExamConfig(
        examId = "section_test",
        examName = "Section Test",
        shortName = "ST",
        category = "Test",
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
                                    SectionConfig("A", "Section A", 2, 2, 4.0, 1.0, QuestionType.MCQ_SINGLE, 4),
                                    SectionConfig("B", "Section B", 3, 3, 3.0, 0.0, QuestionType.NUMERICAL_VALUE, 0)
                                )
                            )
                        ),
                        durationMinutes = 60,
                        totalMarks = 17.0
                    )
                )
            )
        )
    )

    @Test
    fun resolvesOnlyWithinConfiguredSectionQuestionRanges() {
        val exam = config()
        assertEquals("A", UniversalPdfPipeline.resolveSectionForIndex(exam, "physics", 1)?.sectionId)
        assertEquals("A", UniversalPdfPipeline.resolveSectionForIndex(exam, "physics", 2)?.sectionId)
        assertEquals("B", UniversalPdfPipeline.resolveSectionForIndex(exam, "physics", 3)?.sectionId)
        assertEquals("B", UniversalPdfPipeline.resolveSectionForIndex(exam, "physics", 5)?.sectionId)
        assertNull(UniversalPdfPipeline.resolveSectionForIndex(exam, "physics", 6))
        assertNull(UniversalPdfPipeline.resolveSectionForIndex(exam, "physics", 0))
    }

    @Test
    fun unknownSubjectDoesNotBorrowAnotherSubjectsSections() {
        assertNull(UniversalPdfPipeline.resolveSectionForIndex(config(), "chemistry", 1))
    }
}
