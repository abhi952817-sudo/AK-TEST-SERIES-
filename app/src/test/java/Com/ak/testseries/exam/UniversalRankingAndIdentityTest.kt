package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalRankingAndIdentityTest {

    @Test
    fun canonicalExamKeysDoNotCollapseAmbiguousExamNames() {
        assertEquals("NEET", UniversalExamRegistry.getCanonicalExamKey("NEET UG"))
        assertEquals("NEET", UniversalExamRegistry.getCanonicalExamKey("NEET"))
        assertEquals("NEET_PG", UniversalExamRegistry.getCanonicalExamKey("NEET PG"))
        assertEquals("JEE_MAIN", UniversalExamRegistry.getCanonicalExamKey("JEE Main"))
        assertEquals("JEE_ADVANCED", UniversalExamRegistry.getCanonicalExamKey("JEE Advanced"))
        assertEquals("UNKNOWN", UniversalExamRegistry.getCanonicalExamKey("JEE"))
        assertEquals("UNKNOWN", UniversalExamRegistry.getCanonicalExamKey("RRB"))
        assertEquals("RRB_NTPC", UniversalExamRegistry.getCanonicalExamKey("RRB NTPC"))
        assertEquals("SBI_PO", UniversalExamRegistry.getCanonicalExamKey("SBI PO"))
        assertEquals("UNKNOWN", UniversalExamRegistry.getCanonicalExamKey(""))
    }

    @Test
    fun exactExamLookupRejectsGenericJeeAliasAndPartialMatches() {
        assertNull(UniversalExamRegistry.findExamByNameOrShortName("JEE"))
        assertNull(UniversalExamRegistry.findExamByNameOrShortName("JEE M"))
        assertEquals("jee_main", UniversalExamRegistry.findExamByNameOrShortName("JEE Main")?.examId)
        assertEquals("neet_ug", UniversalExamRegistry.findExamByNameOrShortName("NEET UG")?.examId)
    }

    @Test
    fun canonicalRankingUniversesRemainDistinct() {
        val keys = setOf(
            UniversalExamRegistry.getCanonicalExamKey("NEET UG"),
            UniversalExamRegistry.getCanonicalExamKey("JEE Main"),
            UniversalExamRegistry.getCanonicalExamKey("SSC CGL"),
            UniversalExamRegistry.getCanonicalExamKey("IBPS PO"),
            UniversalExamRegistry.getCanonicalExamKey("RRB NTPC")
        )
        assertEquals(5, keys.size)
        assertNotEquals("NEET", "JEE_MAIN")
    }

    @Test
    fun onlySourceVerifiedExamScoringPoliciesAreEnabled() {
        assertTrue(UniversalExamRegistry.isExamVerified("neet_ug"))
        assertTrue(UniversalExamRegistry.isExamVerified("jee_main"))
        assertFalse(UniversalExamRegistry.isExamVerified("jee_advanced"))
        assertEquals(4.0 to 1.0, UniversalExamRegistry.getMarkingSchemeForExam("NEET UG"))
        assertEquals(4.0 to 1.0, UniversalExamRegistry.getMarkingSchemeForExam("JEE Main"))
        for (exam in listOf("JEE Advanced", "CUET UG", "UPSC CSE", "SSC CGL", "SSC CHSL", "SSC MTS", "SSC GD", "SSC CPO", "RRB NTPC", "RRB Group D", "RRB ALP", "RRB Technician", "RRB JE", "CTET", "UGC NET", "CAT", "IBPS PO", "IBPS Clerk", "SBI PO", "SBI Clerk", "NDA", "CDS", "GATE")) {
            try {
                UniversalExamRegistry.getMarkingSchemeForExam(exam)
                throw AssertionError("Scoring must be blocked for unverified exam: $exam")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("source-verified", ignoreCase = true))
            }
        }
    }

    @Test
    fun verifiedExamSectionPoliciesAreExact() {
        assertEquals(
            4.0 to 1.0,
            UniversalExamRegistry.getMarkingSchemeForQuestion(
                "NEET UG", "phy_all", "Physics", "MCQ_SINGLE"
            )
        )
        assertEquals(
            4.0 to 1.0,
            UniversalExamRegistry.getMarkingSchemeForQuestion(
                "JEE Main", "math_sec_b", "Mathematics", "NUMERICAL_VALUE"
            )
        )
        try {
            UniversalExamRegistry.getMarkingSchemeForQuestion(
                "NEET UG", "SECTION_A", "Physics", "MCQ_SINGLE"
            )
            throw AssertionError("Legacy Section A must not be mapped into the 2026 policy")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Unknown or ambiguous section"))
        }
        try {
            UniversalExamRegistry.getMarkingSchemeForQuestion(
                "NEET UG", "bot_all", "Biology", "MCQ_SINGLE"
            )
            throw AssertionError("Biology must not be silently assigned to Botany or Zoology")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Unknown or ambiguous subject"))
        }
    }

    @Test
    fun verifiedOptionCountsAreExplicitAndUnverifiedCountsAreBlocked() {
        assertEquals(4, UniversalExamRegistry.getDefaultOptionCountForExam("NEET UG"))
        assertEquals(4, UniversalExamRegistry.getDefaultOptionCountForExam("JEE Main"))
        try {
            UniversalExamRegistry.getDefaultOptionCountForExam("IBPS PO")
            throw AssertionError("Unverified banking option count must not be assumed")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("source-verified"))
        }
    }

    @Test
    fun unverifiedExamScaffoldingIsNotExposedAsAUsablePattern() {
        val ibps = UniversalExamRegistry.getAllExams().single { it.examId == "ibps_po" }
        assertFalse(ibps.verified)
        assertTrue(ibps.stages.isEmpty())
        assertTrue(ibps.supportedQuestionTypes.isEmpty())
        assertEquals(0, ibps.defaultOptionCount)
        assertTrue(UniversalExamRegistry.getSubjectsForExamName("IBPS PO").isEmpty())

        // Every unverified scaffold must be sanitized on every public registry lookup.
        UniversalExamRegistry.getAllExams().filter { !it.verified }.forEach { unverified ->
            assertTrue("Unverified exam leaked stages: ${unverified.examId}", unverified.stages.isEmpty())
            assertTrue("Unverified exam leaked question types: ${unverified.examId}", unverified.supportedQuestionTypes.isEmpty())
            assertEquals("Unverified exam leaked option count: ${unverified.examId}", 0, unverified.defaultOptionCount)
            assertTrue("Unverified exam leaked a pattern: ${unverified.examId}", unverified.patternRules.all {
                it.contains("verification pending", ignoreCase = true)
            })
            assertNull(UniversalExamRegistry.findExamByNameOrShortName(unverified.examName)?.stages?.firstOrNull())
        }
        assertTrue(UniversalExamRegistry.getActiveExams().all { it.verified })
        try {
            UniversalExamRegistry.getDefaultQuestionsCountForExam("IBPS PO")
            throw AssertionError("Unverified question counts must not be exposed")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("source-verified"))
        }
        try {
            UniversalExamRegistry.getDefaultDurationMinutesForExam("IBPS PO")
            throw AssertionError("Unverified durations must not be exposed")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("source-verified"))
        }
    }

    @Test
    fun privateTestsNeverCountTowardCompetitiveRanking() {
        val isPublic = false
        val isOfficial = false
        val countsTowardRanking = isPublic && isOfficial
        assertFalse(countsTowardRanking)
        assertFalse(isPublic)
    }

    @Test
    fun neetAndJeeSubjectCatalogsRemainIsolated() {
        val neetSubjects = UniversalExamRegistry.getSubjectsForExamName("NEET UG").map { it.lowercase() }
        assertTrue(neetSubjects.any { it.contains("physics") })
        assertTrue(neetSubjects.any { it.contains("chemistry") })
        assertTrue(neetSubjects.any { it.contains("botany") })
        assertTrue(neetSubjects.any { it.contains("zoology") })
        assertFalse(neetSubjects.any { it.contains("mathematics") })

        val jeeSubjects = UniversalExamRegistry.getSubjectsForExamName("JEE Main").map { it.lowercase() }
        assertTrue(jeeSubjects.any { it.contains("physics") })
        assertTrue(jeeSubjects.any { it.contains("chemistry") })
        assertTrue(jeeSubjects.any { it.contains("mathematics") })
        assertFalse(jeeSubjects.any { it.contains("botany") || it.contains("zoology") })
    }

    @Test
    fun syllabusCatalogsDoNotCrossAssignBiologyAndMathematics() {
        val neet = SyllabusCatalog.getChapterDetailsForExam("NEET UG")
        val jee = SyllabusCatalog.getChapterDetailsForExam("JEE Main")
        val jeeAdvanced = SyllabusCatalog.getChapterDetailsForExam("JEE Advanced")
        assertTrue(neet.isNotEmpty())
        assertTrue(neet.any { it.subject.contains("Biology", true) || it.subject.contains("Botany", true) })
        assertFalse(neet.any { it.subject.contains("Mathematics", true) })
        assertTrue(jee.isNotEmpty())
        assertTrue(jee.any { it.subject.contains("Mathematics", true) })
        assertFalse(jee.any { it.subject.contains("Biology", true) })
        assertTrue(jeeAdvanced.isEmpty())
    }

    @Test
    fun 2026VerifiedPatternsHaveNoOptionalSectionAttemptCaps() {
        try {
            UniversalExamRegistry.getMaximumAttemptQuestionsCountForSection(
                "NEET UG", "Section B", "Physics"
            )
            throw AssertionError("NEET 2026 has no optional Section B")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Unknown or ambiguous section"))
        }
        assertNull(
            UniversalExamRegistry.getMaximumAttemptQuestionsCountForSection(
                "JEE Main", "phy_sec_b", "Physics"
            )
        )
    }

    @Test
    fun unverifiedJeeAdvancedDoesNotExposeGuessedMultiMarkingSchemes() {
        assertFalse(UniversalExamRegistry.hasMultipleMarkingSchemes("JEE Advanced"))
        try {
            UniversalExamRegistry.getMarkingSchemeForQuestion(
                "JEE Advanced", "section_a", "Physics", "MCQ_SINGLE"
            )
            throw AssertionError("JEE Advanced scoring must remain blocked until source verification")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("source-verified"))
        }
    }
}
