package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamFormatStandardizerSafetyTest {

    private fun config(): ExamConfig = ExamConfig(
        examId = "safety_test",
        examName = "Safety Test",
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
                                    SectionConfig(
                                        sectionId = "PHYSICS_A",
                                        sectionName = "Physics Section",
                                        totalQuestions = 2,
                                        compulsoryQuestions = 2,
                                        positiveMarks = 4.0,
                                        negativeMarks = 1.0,
                                        questionType = QuestionType.MCQ_SINGLE,
                                        optionsCount = 4
                                    )
                                )
                            )
                        ),
                        durationMinutes = 60,
                        totalMarks = 8.0
                    )
                )
            )
        )
    )

    private fun question(
        sectionId: String = "PHYSICS_A",
        options: List<String> = listOf("One", "Two", "Three", "Four")
    ) = UniversalQuestionModel(
        questionId = "q1",
        examId = "safety_test",
        stageId = "stage_1",
        paperId = "paper_1",
        sectionId = sectionId,
        sectionName = "Physics Section",
        subjectId = "physics",
        questionText = "Which option is the correct physical principle?",
        options = options,
        correctAnswer = "A",
        questionType = QuestionType.MCQ_SINGLE,
        positiveMarks = 4.0,
        negativeMarks = 1.0
    )

    @Test
    fun unknownSubjectDoesNotCreateSyntheticSection() {
        val paper = ExamFormatStandardizer.standardizeExamOutput(
            examConfig = config(),
            subjectId = "chemistry",
            questions = listOf(question())
        )

        assertTrue(paper.sectionGroups.isEmpty())
        assertEquals(0, paper.totalQuestions)
        assertEquals(0.0, paper.totalMarks, 0.0)
    }

    @Test
    fun unknownSectionIsNotSilentlyReassigned() {
        val paper = ExamFormatStandardizer.standardizeExamOutput(
            examConfig = config(),
            subjectId = "physics",
            questions = listOf(question(sectionId = "SECTION_A"))
        )

        assertTrue(paper.sectionGroups.isEmpty())
        assertEquals(0, paper.totalQuestions)
    }

    @Test
    fun missingOptionsAreNotFilledWithPlaceholderText() {
        val paper = ExamFormatStandardizer.standardizeExamOutput(
            examConfig = config(),
            subjectId = "physics",
            questions = listOf(question(options = listOf("One", "Two", "Three")))
        )

        assertTrue(paper.sectionGroups.isEmpty())
        assertEquals(0, paper.totalQuestions)
    }

    @Test
    fun validConfiguredQuestionIsPreservedWithoutInventedOptions() {
        val original = question()
        val paper = ExamFormatStandardizer.standardizeExamOutput(
            examConfig = config(),
            subjectId = "physics",
            questions = listOf(original),
            stageId = "stage_1"
        )

        assertEquals(1, paper.totalQuestions)
        assertEquals(listOf("One", "Two", "Three", "Four"), paper.sectionGroups.single().questions.single().options)
        assertEquals("PHYSICS_A", paper.sectionGroups.single().sectionId)
    }
}
