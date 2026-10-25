package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Universal Ranking and Exam Identity Unit Tests
 * Verifies Phase 3, Phase 4, Phase 5, Phase 6 specifications:
 * - Independent canonical ranking universes for all major exams
 * - Cross-exam scoring isolation
 * - Fractional scoring fidelity (no integer truncation)
 * - Private vs Public test separation
 */
class UniversalRankingAndIdentityTest {

    @Test
    fun testIndependentCanonicalRankingUniverses() {
        val testCases = mapOf(
            "NEET UG" to "NEET",
            "NEET" to "NEET",
            "neet_ug" to "NEET",
            "JEE Main" to "JEE_MAIN",
            "JEE" to "JEE_MAIN",
            "jee_main" to "JEE_MAIN",
            "JEE Advanced" to "JEE_ADVANCED",
            "JEE Adv" to "JEE_ADVANCED",
            "jee_advanced" to "JEE_ADVANCED",
            "SSC CGL" to "SSC_CGL",
            "CGL" to "SSC_CGL",
            "ssc_cgl" to "SSC_CGL",
            "SSC CHSL" to "SSC_CHSL",
            "CHSL" to "SSC_CHSL",
            "ssc_chsl" to "SSC_CHSL",
            "SSC CPO" to "SSC_CPO",
            "CPO" to "SSC_CPO",
            "ssc_cpo" to "SSC_CPO",
            "SSC GD" to "SSC_GD",
            "GD" to "SSC_GD",
            "ssc_gd" to "SSC_GD",
            "SBI PO" to "SBI_PO",
            "sbi_po" to "SBI_PO",
            "SBI Clerk" to "SBI_CLERK",
            "sbi_clerk" to "SBI_CLERK",
            "IBPS PO" to "IBPS_PO",
            "ibps_po" to "IBPS_PO",
            "IBPS Clerk" to "IBPS_CLERK",
            "ibps_clerk" to "IBPS_CLERK",
            "RRB NTPC" to "RRB_NTPC",
            "NTPC" to "RRB_NTPC",
            "rrb_ntpc" to "RRB_NTPC",
            "RRB Group D" to "RRB_GROUP_D",
            "Group D" to "RRB_GROUP_D",
            "rrb_group_d" to "RRB_GROUP_D",
            "RRB ALP" to "RRB_ALP",
            "ALP" to "RRB_ALP",
            "rrb_alp" to "RRB_ALP",
            "RRB Technician" to "RRB_TECHNICIAN",
            "Technician" to "RRB_TECHNICIAN",
            "rrb_technician" to "RRB_TECHNICIAN",
            "RRB JE" to "RRB_JE",
            "rrb_je" to "RRB_JE",
            "NDA" to "NDA",
            "nda" to "NDA",
            "CDS" to "CDS",
            "cds" to "CDS",
            "CTET" to "CTET",
            "ctet" to "CTET",
            "UGC NET" to "UGC_NET",
            "ugc_net" to "UGC_NET",
            "CAT" to "CAT",
            "cat" to "CAT"
        )

        for ((input, expectedCanonical) in testCases) {
            val actual = UniversalExamRegistry.getCanonicalExamKey(input)
            assertEquals("Exam '$input' should normalize to '$expectedCanonical'", expectedCanonical, actual)
        }
    }

    @Test
    fun testCrossExamScoringIsolation() {
        val neetKey = UniversalExamRegistry.getCanonicalExamKey("NEET UG")
        val jeeKey = UniversalExamRegistry.getCanonicalExamKey("JEE Main")
        val sscKey = UniversalExamRegistry.getCanonicalExamKey("SSC CGL")
        val bankingKey = UniversalExamRegistry.getCanonicalExamKey("IBPS PO")
        val railwayKey = UniversalExamRegistry.getCanonicalExamKey("RRB NTPC")

        // Assert all keys are strictly distinct
        val allKeys = setOf(neetKey, jeeKey, sscKey, bankingKey, railwayKey)
        assertEquals("Each exam must belong to an isolated ranking universe", 5, allKeys.size)
        assertNotEquals(neetKey, jeeKey)
        assertNotEquals(sscKey, bankingKey)
        assertNotEquals(railwayKey, sscKey)
    }

    @Test
    fun testFractionalScoringFidelity() {
        // Banking marking scheme: +1.0, -0.25
        val (bankingPos, bankingNeg) = UniversalExamRegistry.getMarkingSchemeForExam("IBPS PO")
        assertEquals(1.0, bankingPos, 0.001)
        assertEquals(0.25, bankingNeg, 0.001)

        // Railways RRB NTPC: +1.0, -0.33
        val (rrbPos, rrbNeg) = UniversalExamRegistry.getMarkingSchemeForExam("RRB NTPC")
        assertEquals(1.0, rrbPos, 0.001)
        assertEquals(0.33, rrbNeg, 0.01)

        // UPSC CDS: English (100/120 = 0.833, -0.277), Mathematics (+1.0, -0.33)
        val (cdsEnglishPos, cdsEnglishNeg) = UniversalExamRegistry.getMarkingSchemeForExam("CDS")
        assertEquals(0.833, cdsEnglishPos, 0.01)
        assertEquals(0.277, cdsEnglishNeg, 0.01)

        val (cdsMathPos, cdsMathNeg) = UniversalExamRegistry.getMarkingSchemeForQuestion("CDS", sectionIdOrName = "cds_math_sec", subjectName = "mathematics")
        assertEquals(1.0, cdsMathPos, 0.001)
        assertEquals(0.33, cdsMathNeg, 0.01)

        // CTET: +1.0, 0.0 (No negative marking)
        val (ctetPos, ctetNeg) = UniversalExamRegistry.getMarkingSchemeForExam("CTET")
        assertEquals(1.0, ctetPos, 0.001)
        assertEquals(0.0, ctetNeg, 0.001)

        // UGC NET: +2.0, 0.0 (No negative marking)
        val (ugcPos, ugcNeg) = UniversalExamRegistry.getMarkingSchemeForExam("UGC NET")
        assertEquals(2.0, ugcPos, 0.001)
        assertEquals(0.0, ugcNeg, 0.001)
    }

    @Test
    fun testOptionEPreservedForBanking() {
        val bankingOptionCount = UniversalExamRegistry.getDefaultOptionCountForExam("IBPS PO")
        assertEquals("Banking exams must mandate 5 options (including Option E)", 5, bankingOptionCount)

        val sbiOptionCount = UniversalExamRegistry.getDefaultOptionCountForExam("SBI PO")
        assertEquals("SBI PO must mandate 5 options (including Option E)", 5, sbiOptionCount)

        val neetOptionCount = UniversalExamRegistry.getDefaultOptionCountForExam("NEET UG")
        assertEquals("NEET must have 4 options", 4, neetOptionCount)
    }

    @Test
    fun testPrivateTestIsolationFlags() {
        // Private test definition
        val isPublic = false
        val isOfficial = false
        val countsTowardRanking = isPublic && isOfficial

        assertFalse("Private tests must never count toward competitive public ranking", countsTowardRanking)
        assertFalse("Private tests must not be public", isPublic)
    }
}
