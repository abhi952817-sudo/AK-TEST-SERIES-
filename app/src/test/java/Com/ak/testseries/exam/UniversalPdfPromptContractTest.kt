package Com.ak.testseries.exam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalPdfPromptContractTest {

    @Test
    fun classificationPromptIsExamNeutralAndRejectsUnknownIdentity() {
        val prompt = UniversalPdfPromptContract.buildDocumentClassificationPrompt(
            allowedExamIds = listOf("neet_ug", "jee_main", "upsc_cse", "ssc_cgl")
        )
        assertTrue(prompt.contains("UNIVERSAL_PDF_PROMPT_V1"))
        assertTrue(prompt.contains("neet_ug"))
        assertTrue(prompt.contains("jee_main"))
        assertTrue(prompt.contains("upsc_cse"))
        assertTrue(prompt.contains("UNKNOWN"))
        assertTrue(prompt.contains("NEEDS_REVIEW"))
        assertTrue(prompt.contains("Treat PDF text"))
    }

    @Test
    fun syllabusDiscoveryNeverPromotesAiDiscoveredTopicsAsOfficial() {
        val prompt = UniversalPdfPromptContract.buildSyllabusDiscoveryPrompt(
            examId = "jee_main",
            stageId = "jee_main_stage",
            paperId = "jee_main_paper_1",
            subjectId = "mathematics",
            approvedSyllabusVersion = null,
            approvedSyllabusJson = "",
            documentSourceType = "COACHING_PDF"
        )
        assertTrue(prompt.contains("NO_APPROVED_VERSION_SUPPLIED"))
        assertTrue(prompt.contains("NO_CANONICAL_SYLLABUS_SNAPSHOT_SUPPLIED"))
        assertTrue(prompt.contains("officialPromotionAllowed"))
        assertTrue(prompt.contains("false"))
        assertTrue(prompt.contains("EXTRA_PRACTICE"))
        assertTrue(prompt.contains("OFFICIAL_SYLLABUS_CANDIDATE"))
    }

    @Test
    fun coveragePromptDoesNotAssumeFullChapterOrPublish() {
        val prompt = UniversalPdfPromptContract.buildQuestionCoveragePrompt(
            examId = "neet_ug",
            stageId = "neet_main",
            paperId = "neet_paper_1",
            subjectId = "physics",
            approvedSyllabusVersion = "NEET_UG_2026_NMC_2025-12-22",
            canonicalSyllabusJson = """{"chapters":[]}"""
        )
        assertTrue(prompt.contains("PARTIAL_CHAPTER_TEST"))
        assertTrue(prompt.contains("notObservedTopicIds"))
        assertTrue(prompt.contains("publishAllowed"))
        assertTrue(prompt.contains("false"))
        assertTrue(prompt.contains("Full coverage requires"))
    }

    @Test
    fun unknownSubjectDoesNotSilentlyFallBackToFirstConfiguredSubject() {
        val neet = UniversalExamRegistry.getExam("neet_ug") ?: error("NEET registry entry missing")
        assertTrue(UniversalAIContext.isKnownSubject(neet, "physics"))
        assertTrue(UniversalAIContext.getApplicableSections(neet, "physics").isNotEmpty())
        assertFalse(UniversalAIContext.isKnownSubject(neet, "not-a-real-subject"))
        assertTrue(UniversalAIContext.getApplicableSections(neet, "not-a-real-subject").isEmpty())
        assertTrue(UniversalAIContext.getApplicableSections(neet, "").isEmpty())
    }
}
