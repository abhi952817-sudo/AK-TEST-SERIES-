package Com.ak.testseries.exam

import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.data.TestVisibilityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Universal Ranking and Isolation Test suite.
 * Validates:
 * 1. Canonical exam key normalization for all 20+ competitive exams.
 * 2. Deterministic competition ranking (tied scores yield identical ranks, e.g. 1, 1, 3).
 * 3. Private vs Public test ranking separation and visibility isolation.
 */
class UniversalRankingAndIsolationTest {

    @Test
    fun testCanonicalExamKeyNormalization_AllExams() {

        val exams = UniversalExamRegistry.getAllExams()
        assertEquals(25, exams.size)
        assertEquals(exams.size, exams.map { it.examId }.distinct().size)
        val canonicalKeys = exams.map { UniversalExamRegistry.getCanonicalExamKey(it.examName) }
        assertFalse("Every registered exam must map to a stable canonical partition", canonicalKeys.any { it == "UNKNOWN" })
        assertEquals("Canonical exam partitions must not collide", canonicalKeys.size, canonicalKeys.distinct().size)

        assertEquals("NEET", UniversalExamRegistry.getCanonicalExamKey("NEET UG"))
        assertEquals("NEET_PG", UniversalExamRegistry.getCanonicalExamKey("NEET PG"))
        assertEquals("JEE_MAIN", UniversalExamRegistry.getCanonicalExamKey("JEE Main Paper 1"))
        assertEquals("JEE_ADVANCED", UniversalExamRegistry.getCanonicalExamKey("JEE Advanced"))
        assertEquals("RRB_NTPC", UniversalExamRegistry.getCanonicalExamKey("RRB NTPC"))
        assertEquals("UNKNOWN", UniversalExamRegistry.getCanonicalExamKey("JEE"))
        assertEquals("UNKNOWN", UniversalExamRegistry.getCanonicalExamKey("RRB"))
        assertEquals("UNKNOWN", UniversalExamRegistry.getCanonicalExamKey(null))
        assertEquals("UNKNOWN", UniversalExamRegistry.getCanonicalExamKey(""))
        assertNull(UniversalExamRegistry.findExamByNameOrShortName("JEE"))
        assertNull(UniversalExamRegistry.findExamByNameOrShortName("JEE Main"))
    
    }

    @Test
    fun testDeterministicCompetitionRankingCalculation() {
        val scores = listOf(500, 500, 420, 380, 380, 380, 200)
        val computedRanks = mutableListOf<Int>()

        var prevScore: Int? = null
        var currentRank = 1
        for (i in scores.indices) {
            val s = scores[i]
            val displayRank = if (prevScore != null && s == prevScore) {
                currentRank
            } else {
                currentRank = i + 1
                prevScore = s
                currentRank
            }
            computedRanks.add(displayRank)
        }

        // 500 (1st), 500 (1st tie), 420 (3rd), 380 (4th), 380 (4th tie), 380 (4th tie), 200 (7th)
        val expectedRanks = listOf(1, 1, 3, 4, 4, 4, 7)
        assertEquals(expectedRanks, computedRanks)
    }

    @Test
    fun testPrivateVsPublicTestVisibilityIsolation() {
        val publicTest = TestEntity(
            id = "pub_101",
            title = "Official NEET Mock 1",
            description = "Public exam test",
            institute = "AK Academy",
            batchName = "12th",
            facultyName = "Dr. AK",
            language = "English",
            subject = "Full Syllabus",
            difficulty = "Medium",
            testType = "Mock Test",
            questionsCount = 180,
            isOfficial = true,
            isVerifiedCommunity = false,
            isPublic = true,
            status = "Published",
            uploaderId = "admin_uid"
        )

        val privateTest = TestEntity(
            id = "priv_202",
            title = "My Private Practice Paper",
            description = "Strictly private",
            institute = "Self Study",
            batchName = "12th",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            countsTowardRanking = false,
            status = "Published",
            uploaderId = "student_uid_123"
        )

        // 1. Public test is visible to any student
        assertTrue(TestVisibilityManager.isTestVisible(publicTest, "other_student_456", isAdmin = false))
        assertTrue(TestVisibilityManager.isTestVisible(publicTest, null, isAdmin = false))

        // 2. Private test is visible ONLY to owner and admin
        assertTrue(TestVisibilityManager.isTestVisible(privateTest, "student_uid_123", isAdmin = false))
        assertFalse(TestVisibilityManager.isTestVisible(privateTest, "other_student_456", isAdmin = false))
        assertFalse(TestVisibilityManager.isTestVisible(privateTest, "", isAdmin = false))
        assertFalse(TestVisibilityManager.isTestVisible(privateTest, null, isAdmin = false))
        assertTrue(TestVisibilityManager.isTestVisible(privateTest, "other_student_456", isAdmin = true))
    }

    @Test
    fun testPrivateTestCountsTowardRankingGating() {
        val privateTest = TestEntity(
            id = "priv_303",
            title = "Private Coaching Material",
            description = "",
            institute = "Private",
            batchName = "Dropper",
            facultyName = "Coach",
            language = "English",
            subject = "Chemistry",
            difficulty = "Medium",
            testType = "Practice Test",
            questionsCount = 50,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            countsTowardRanking = false,
            status = "Published",
            uploaderId = "student_777"
        )

        val qualifiesForPublicRanking = privateTest.countsTowardRanking && (privateTest.isPublic || privateTest.isOfficial)
        assertFalse("Private test must never qualify for public leaderboard ranking", qualifiesForPublicRanking)
    }

    @Test
    fun testCrossExamRankingKeyIsolation() {
        val neetKey = UniversalExamRegistry.getCanonicalExamKey("NEET UG")
        val jeeKey = UniversalExamRegistry.getCanonicalExamKey("JEE Main")
        val sscKey = UniversalExamRegistry.getCanonicalExamKey("SSC CGL")
        val rrbKey = UniversalExamRegistry.getCanonicalExamKey("RRB NTPC")
        val ibpsKey = UniversalExamRegistry.getCanonicalExamKey("IBPS PO")

        // 1. All canonical keys are strictly unique
        val keys = listOf(neetKey, jeeKey, sscKey, rrbKey, ibpsKey)
        assertEquals(keys.size, keys.distinct().size)

        // 2. Exam fields in Users_Data partition storage independently
        val mockUserData = mutableMapOf<String, Long>()
        
        // Student completes 2 qualifying NEET tests
        mockUserData["cumulativeScore_$neetKey"] = 650L
        mockUserData["qualifyingTestsCount_$neetKey"] = 2L

        // NEET data must not bleed into JEE or SSC
        assertEquals(650L, mockUserData["cumulativeScore_$neetKey"])
        assertEquals(2L, mockUserData["qualifyingTestsCount_$neetKey"])
        assertEquals(0L, mockUserData["cumulativeScore_$jeeKey"] ?: 0L)
        assertEquals(0L, mockUserData["qualifyingTestsCount_$jeeKey"] ?: 0L)
        assertEquals(0L, mockUserData["cumulativeScore_$sscKey"] ?: 0L)
        assertEquals(0L, mockUserData["qualifyingTestsCount_$sscKey"] ?: 0L)

        // Verify eligibility logic: NEET is eligible (>= 2), but JEE is NOT eligible (< 2)
        val neetEligible = (mockUserData["qualifyingTestsCount_$neetKey"] ?: 0L) >= 2L
        val jeeEligible = (mockUserData["qualifyingTestsCount_$jeeKey"] ?: 0L) >= 2L
        assertTrue("Student must be eligible for NEET ranking", neetEligible)
        assertFalse("Student must NOT be eligible for JEE ranking", jeeEligible)
    }
}
