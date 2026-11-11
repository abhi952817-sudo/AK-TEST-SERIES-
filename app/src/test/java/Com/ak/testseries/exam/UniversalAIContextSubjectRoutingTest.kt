package Com.ak.testseries.exam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalAIContextSubjectRoutingTest {

    private val neet = UniversalExamRegistry.findExamByNameOrShortName("NEET UG")
        ?: error("NEET UG must be registered for subject-routing tests")

    @Test
    fun exactConfiguredSubjectResolvesOnlyItsOwnSections() {
        val sections = UniversalAIContext.getApplicableSections(neet, "physics")

        assertTrue(sections.isNotEmpty())
        assertTrue(sections.all { it.sectionId.contains("physics", ignoreCase = true) })
    }

    @Test
    fun allSubjectsDoesNotResolveToTheFirstConfiguredSubject() {
        assertTrue(UniversalAIContext.getApplicableSections(neet, "All Subjects").isEmpty())
    }

    @Test
    fun unknownOrBlankSubjectDoesNotResolveToAnyConfiguredSubject() {
        assertTrue(UniversalAIContext.getApplicableSections(neet, "not-a-real-subject").isEmpty())
        assertTrue(UniversalAIContext.getApplicableSections(neet, " ").isEmpty())
    }

    @Test
    fun generationPromptRejectsAllSubjectsUntilPlannerIsIntegrated() {
        val error = runCatching {
            UniversalAIContext.buildDynamicGenerationPrompt(
                examConfig = neet,
                subject = "All Subjects",
                testTitle = "Test",
                compactMarkdownPayload = "",
                currentChunkSize = 10,
                startQ = 1,
                endQ = 10,
                totalQuestions = 10
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertFalse(error?.message.isNullOrBlank())
    }
}
