package Com.ak.testseries.exam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalAIContextTest {

    @Test
    fun verifiedNeetPromptUsesExactSectionPolicyAndNoPlaceholderOptions() {
        val config = UniversalExamRegistry.getExam("neet_ug")!!
        val prompt = UniversalAIContext.buildDynamicGenerationPrompt(
            examConfig = config,
            subject = "Physics",
            testTitle = "NEET Physics",
            compactMarkdownPayload = "1. A source question about potential difference.",
            currentChunkSize = 1,
            startQ = 1,
            endQ = 1,
            totalQuestions = 45,
            isExtractionMode = true
        )
        assertTrue(prompt.contains("phy_all"))
        assertTrue(prompt.contains("positive=4.0"))
        assertTrue(prompt.contains("negative=1.0"))
        assertFalse(prompt.contains("Option A (English)"))
        assertFalse(prompt.contains("Default Section"))
        assertFalse(prompt.contains("Source: Standard Official Syllabus"))
        assertFalse(prompt.contains("SECTION_A"))
    }

    @Test
    fun jeePromptSeparatesMcqAndNumericalSections() {
        val config = UniversalExamRegistry.getExam("jee_main")!!
        val prompt = UniversalAIContext.buildDynamicGenerationPrompt(
            examConfig = config,
            subject = "Physics",
            testTitle = "JEE Main Physics",
            compactMarkdownPayload = "Source PDF question text.",
            currentChunkSize = 25,
            startQ = 1,
            endQ = 25,
            totalQuestions = 25,
            isExtractionMode = true
        )
        assertTrue(prompt.contains("phy_sec_a"))
        assertTrue(prompt.contains("phy_sec_b"))
        assertTrue(prompt.contains("NUMERICAL_VALUE"))
    }

    @Test
    fun missingSourceAndUnverifiedExamCannotUseSyntheticPromptFallbacks() {
        val neet = UniversalExamRegistry.getExam("neet_ug")!!
        try {
            UniversalAIContext.buildDynamicGenerationPrompt(
                examConfig = neet,
                subject = "Physics",
                testTitle = "No source",
                compactMarkdownPayload = "",
                currentChunkSize = 1,
                startQ = 1,
                endQ = 1,
                totalQuestions = 45
            )
            throw AssertionError("Blank source must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("real source document"))
        }

        val ibps = UniversalExamRegistry.getExam("ibps_po")!!
        try {
            UniversalAIContext.buildSystemInstruction(ibps)
            throw AssertionError("Unverified exam AI instruction must be blocked")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("unverified"))
        }
    }

    @Test
    fun subjectLookupDoesNotFallBackToFirstSubject() {
        val neet = UniversalExamRegistry.getExam("neet_ug")!!
        try {
            UniversalAIContext.getApplicableSections(neet, "Biology")
            throw AssertionError("Ambiguous Biology must not be silently mapped to Botany or Zoology")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Unknown or ambiguous subject"))
        }
    }
}
