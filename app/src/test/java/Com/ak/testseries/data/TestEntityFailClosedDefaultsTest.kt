package Com.ak.testseries.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TestEntityFailClosedDefaultsTest {

    @Test
    fun newTestDoesNotBecomePublicOfficialOrRankingEligibleByDefault() {
        val test = TestEntity(
            id = "draft_test",
            title = "Private Draft",
            description = "",
            institute = "",
            batchName = "",
            facultyName = "",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Part Test",
            questionsCount = 0,
            isOfficial = false,
            isVerifiedCommunity = false
        )

        assertFalse(test.isPublic)
        assertFalse(test.countsTowardRanking)
        assertEquals("Draft", test.status)
        assertEquals(0.0f, test.rating, 0.0f)
        assertEquals(0, test.aiConfidence)
        assertEquals("", test.targetExam)
        assertEquals("", test.uploaderId)
    }
}
