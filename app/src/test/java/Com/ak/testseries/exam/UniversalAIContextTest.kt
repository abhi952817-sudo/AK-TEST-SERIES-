package Com.ak.testseries.exam

import org.junit.Assert.*
import org.junit.Test

class UniversalAIContextTest {

    private val neet = requireNotNull(UniversalExamRegistry.getExamById("neet_ug"))

    @Test
    fun systemInstructionContainsAccuracyAndExamSpecificRules() {
        val prompt = UniversalAIContext.buildSystemInstruction(neet)

        assertTrue(prompt.contains("AKTS_UNIVERSAL_PROMPT_V2"))
        assertTrue(prompt.contains("SOURCE-OF-TRUTH ORDER"))
        assertTrue(prompt.contains("Never invent an answer key"))
        assertTrue(prompt.contains("CONFLICT"))
        assertTrue(prompt.contains("negative marks"))
        assertTrue(prompt.contains("Bilingual"))
        assertTrue(prompt.contains(neet.examId))
        assertTrue(prompt.contains("Do not output private chain-of-thought"))
    }

    @Test
    fun extractionPromptForbidsFabricationAndAllowsFewerRealQuestions() {
        val prompt = UniversalAIContext.buildDynamicGenerationPrompt(
            examConfig = neet,
            subject = "Physics",
            testTitle = "NEET Physics Source Paper",
            compactMarkdownPayload = "PAGE 1\nQ1. A body moves with constant velocity.",
            currentChunkSize = 5,
            startQ = 1,
            endQ = 5,
            totalQuestions = 20,
            isExtractionMode = true
        )

        assertTrue(prompt.contains("SOURCE QUESTION EXTRACTION"))
        assertTrue(prompt.contains("Never create a replacement question"))
        assertTrue(prompt.contains("Return fewer than 5 items"))
        assertTrue(prompt.contains("SOURCE_EXTRACTED"))
        assertTrue(prompt.contains("sourcePageStart"))
        assertFalse(prompt.contains("Generate exactly 5 realistic"))
    }

    @Test
    fun generationPromptRequiresVerifiedAnswersAndConfiguredMarks() {
        val prompt = UniversalAIContext.buildDynamicGenerationPrompt(
            examConfig = neet,
            subject = "Physics",
            testTitle = "NEET Physics Practice",
            compactMarkdownPayload = "PAGE 1\nNewton's laws and applications.",
            currentChunkSize = 4,
            startQ = 1,
            endQ = 4,
            totalQuestions = 20,
            isExtractionMode = false
        )

        assertTrue(prompt.contains("NEW EXAM-ALIGNED PRACTICE QUESTION GENERATION"))
        assertTrue(prompt.contains("exactly 4"))
        assertTrue(prompt.contains("GENERATED_PRACTICE"))
        assertTrue(prompt.contains("answerStatus"))
        assertTrue(prompt.contains("positiveMarks"))
        assertTrue(prompt.contains("negativeMarks"))
        assertTrue(prompt.contains("Never set isOfficialPYQ=true"))
    }

    @Test
    fun missingSourceDoesNotPretendToBeOfficialSyllabus() {
        val prompt = UniversalAIContext.buildDynamicGenerationPrompt(
            examConfig = neet,
            subject = "Physics",
            testTitle = "No source test",
            compactMarkdownPayload = " ",
            currentChunkSize = 2,
            startQ = 1,
            endQ = 2,
            totalQuestions = 2
        )

        assertTrue(prompt.contains("[NO SOURCE PAYLOAD PROVIDED"))
        assertTrue(prompt.contains("explicit error/needsReview"))
        assertFalse(prompt.contains("Standard Official Syllabus"))
    }

    @Test
    fun unknownSubjectDoesNotBorrowAnotherSubjectsSections() {
        val sections = UniversalAIContext.getApplicableSections(neet, "not-a-real-subject")
        assertTrue("Unknown subject must not silently inherit the first subject's scoring rules", sections.isEmpty())
    }

    @Test
    fun metadataPromptDoesNotSilentlyDefaultUnknownExam() {
        val prompt = UniversalAIContext.buildExamRecognitionPrompt(
            supportedExamNames = listOf("NEET UG", "JEE Main"),
            selectedExamName = "NEET UG",
            sourceText = "A generic science question paper"
        )

        assertTrue(prompt.contains("Do not default to the selected exam, NEET"))
        assertTrue(prompt.contains("targetExamId=null"))
        assertTrue(prompt.contains("needsUserConfirmation=true"))
    }

    @Test
    fun difficultyRegenerationUsesSameUniversalPolicy() {
        val prompt = UniversalAIContext.buildDifficultyRegenerationPrompt(
            examConfig = neet,
            subjectId = "Physics",
            neededCount = 2,
            difficulty = DifficultyLevel.HARD,
            sourceText = "Work, energy and power"
        )

        assertTrue(prompt.contains("AKTS_UNIVERSAL_PROMPT_V2"))
        assertTrue(prompt.contains("exactly HARD difficulty"))
        assertTrue(prompt.contains("Never use filler"))
    }
}
