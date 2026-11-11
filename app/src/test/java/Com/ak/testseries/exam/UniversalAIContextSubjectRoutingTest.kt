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

        val configuredPhysicsSections = neet.stages
            .flatMap { it.papers }
            .flatMap { it.subjects }
            .single { it.subjectId.equals("physics", ignoreCase = true) }
            .sections

        assertTrue(sections.isNotEmpty())
        assertTrue(sections == configuredPhysicsSections)
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
