package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalQuestionGenerationPlannerTest {

    private fun route(name: String): Triple<ExamConfig, StageConfig, PaperConfig> {
        val exam = UniversalExamRegistry.findExamByNameOrShortName(name)
            ?: error("Exam is not registered: " + name)
        val stage = exam.stages.single()
        val paper = stage.papers.single()
        return Triple(exam, stage, paper)
    }

    @Test
    fun neet180QuestionsAreDistributedAcrossAllFourSubjectsAndCompulsorySections() {
        val (exam, stage, paper) = route("NEET UG")
        val plan = UniversalQuestionGenerationPlanner.create(exam, stage, paper, "All Subjects", 180)

        assertEquals(180, plan.totalQuestions)
        assertEquals(mapOf("physics" to 45, "chemistry" to 45, "botany" to 45, "zoology" to 45), plan.subjectCounts)
        assertEquals(180, plan.allocations.sumOf { it.questionCount })
        assertTrue(plan.allocations.all { it.questionCount > 0 })
    }

    @Test
    fun neet200QuestionsUseFullConfiguredSectionCapacity() {
        val (exam, stage, paper) = route("NEET UG")
        val plan = UniversalQuestionGenerationPlanner.create(exam, stage, paper, "All Subjects", 200)

        assertEquals(200, plan.allocations.sumOf { it.questionCount })
        assertEquals(mapOf("physics" to 50, "chemistry" to 50, "botany" to 50, "zoology" to 50), plan.subjectCounts)
        assertTrue(plan.allocations.filter { it.sectionId.endsWith("sec_a") }.all { it.questionCount == 35 })
        assertTrue(plan.allocations.filter { it.sectionId.endsWith("sec_b") }.all { it.questionCount == 15 })
    }

    @Test
    fun jeeMain75QuestionAttemptPatternUsesTwentyMcqAndFiveNumericalPerSubject() {
        val (exam, stage, paper) = route("JEE Main")
        val plan = UniversalQuestionGenerationPlanner.create(exam, stage, paper, "All Subjects", 75)

        assertEquals(75, plan.allocations.sumOf { it.questionCount })
        assertEquals(25, plan.subjectCounts.values.first())
        assertTrue(plan.allocations.filter { it.sectionId.endsWith("sec_a") }.all { it.questionCount == 20 })
        assertTrue(plan.allocations.filter { it.sectionId.endsWith("sec_b") }.all { it.questionCount == 5 })
    }

    @Test
    fun batchRangesPreserveExactGlobalSubjectAndSectionDistribution() {
        val (exam, stage, paper) = route("NEET UG")
        val plan = UniversalQuestionGenerationPlanner.create(exam, stage, paper, "All Subjects", 180)
        val firstBatch = plan.batch(0, 40)
        val secondBatch = plan.batch(40, 40)
        val thirdBatch = plan.batch(80, 40)
        val fourthBatch = plan.batch(120, 60)

        assertEquals(40, firstBatch.sumOf { it.questionCount })
        assertEquals(40, secondBatch.sumOf { it.questionCount })
        assertEquals(40, thirdBatch.sumOf { it.questionCount })
        assertEquals(60, fourthBatch.sumOf { it.questionCount })
        assertTrue(plan.matches(plan.allocations.flatMap { allocation ->
            List(allocation.questionCount) { allocation.subjectId to allocation.sectionId }
        }))
        assertFalse(plan.matches(emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsQuestionCountBeyondRegisteredExamCapacity() {
        val (exam, stage, paper) = route("NEET UG")
        UniversalQuestionGenerationPlanner.create(exam, stage, paper, "All Subjects", 201)
    }
}
