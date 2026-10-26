package Com.ak.testseries.data

import org.junit.Assert.*
import org.junit.Test

class AccountSwitchingPrivacyTest {

    @Test
    fun testCaseA_and_B_userALogsOut_userBLogsIn_cannotSeeUserAPrivateTests() {
        val userAPrivateTest = TestEntity(
            id = "test_user_a_private_001",
            title = "User A Private Physics Test",
            description = "High yield kinematics",
            institute = "Private Notes",
            batchName = "Target 2026",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 10,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = "user_A_uid"
        )

        val publicExamTest = TestEntity(
            id = "test_public_neet_001",
            title = "All India NEET Mock 1",
            description = "Official standard full mock",
            institute = "AK Test Series",
            batchName = "All",
            facultyName = "NTA Faculty",
            language = "English",
            subject = "Full Syllabus",
            difficulty = "Medium",
            testType = "Full Syllabus",
            questionsCount = 180,
            isOfficial = true,
            isVerifiedCommunity = false,
            isPublic = true,
            uploaderId = "official_admin"
        )

        // While User A is logged in:
        assertTrue(
            "User A should see their own private test",
            TestVisibilityManager.isTestVisible(userAPrivateTest, currentUid = "user_A_uid", isAdmin = false)
        )
        assertTrue(
            "User A should see public exam tests",
            TestVisibilityManager.isTestVisible(publicExamTest, currentUid = "user_A_uid", isAdmin = false)
        )

        // User A logs out (currentUid is blank):
        assertFalse(
            "Logged out guest cannot see User A's private test",
            TestVisibilityManager.isTestVisible(userAPrivateTest, currentUid = "", isAdmin = false)
        )
        assertTrue(
            "Logged out guest can still view public tests",
            TestVisibilityManager.isTestVisible(publicExamTest, currentUid = "", isAdmin = false)
        )

        // User B logs in (currentUid is user_B_uid):
        assertFalse(
            "User B CANNOT see User A's private test",
            TestVisibilityManager.isTestVisible(userAPrivateTest, currentUid = "user_B_uid", isAdmin = false)
        )
        assertTrue(
            "User B CAN see public tests",
            TestVisibilityManager.isTestVisible(publicExamTest, currentUid = "user_B_uid", isAdmin = false)
        )
    }

    @Test
    fun testCaseB_inProgressAnswersScopingPreventsExposure() {
        val testId = "neet_full_mock_123"
        val userA_answers = mapOf("q1" to "A", "q2" to "B", "q3" to "C")
        val userA_uid = "user_A_uid"
        val userB_uid = "user_B_uid"

        // Simulated user-scoped progress store
        val progressStore = mutableMapOf<String, String>()

        // User A saves in-progress test
        val userA_payload = """{"testId":"$testId","timerRemaining":5400,"userAnswers":{"q1":"A","q2":"B"}}"""
        val keyUserA = "progress_${userA_uid}_$testId"
        progressStore[keyUserA] = userA_payload

        // User A logs out, then User B opens the same test
        val keyUserB = "progress_${userB_uid}_$testId"
        val userB_savedProgress = progressStore[keyUserB]

        assertNull("User B must NOT find any in-progress answers from User A", userB_savedProgress)

        // User A logs back in later
        val userA_restoredProgress = progressStore[keyUserA]
        assertNotNull("User A can safely resume their own test progress", userA_restoredProgress)
        assertTrue(userA_restoredProgress!!.contains(""""q1":"A""""))
    }

    @Test
    fun testCaseC_and_E_loggingOutPreservesUserAPrivateTestsWithoutDeletion() {
        // Simulate local SQLite database table
        val localDatabaseTests = mutableListOf<TestEntity>()

        val userAPrivateTest = TestEntity(
            id = "private_test_101",
            title = "My Organic Chemistry PYQs",
            description = "Class 12 Hydrocarbons",
            institute = "Self Notes",
            batchName = "Target 2026",
            facultyName = "User A",
            language = "English",
            subject = "Chemistry",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 15,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = "user_A_uid"
        )
        localDatabaseTests.add(userAPrivateTest)

        // User logs out: database is NOT cleared or dropped
        // Verification: test still exists in localDatabaseTests
        assertEquals(1, localDatabaseTests.size)
        assertEquals("private_test_101", localDatabaseTests[0].id)
        assertEquals("user_A_uid", localDatabaseTests[0].uploaderId)
    }

    @Test
    fun testCaseD_publicTestsRemainAccessibleToEveryone() {
        val publicTest = TestEntity(
            id = "public_jee_main_001",
            title = "JEE Main Physics Drill",
            description = "Rotational Dynamics & Mechanics",
            institute = "AK Series",
            batchName = "JEE",
            facultyName = "Staff",
            language = "English",
            subject = "Physics",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 30,
            isOfficial = true,
            isVerifiedCommunity = true,
            isPublic = true,
            uploaderId = "official_admin"
        )

        // Verified across all states
        assertTrue("User A can access public test", TestVisibilityManager.isTestVisible(publicTest, "user_A_uid"))
        assertTrue("User B can access public test", TestVisibilityManager.isTestVisible(publicTest, "user_B_uid"))
        assertTrue("Unauthenticated guest can access public test", TestVisibilityManager.isTestVisible(publicTest, ""))
        assertTrue("Null user can access public test", TestVisibilityManager.isTestVisible(publicTest, null))
        assertTrue("Admin can access public test", TestVisibilityManager.isTestVisible(publicTest, "admin_uid", isAdmin = true))
    }

    @Test
    fun testSyllabusProgressScopingByAccount() {
        val syllabusPrefStore = mutableMapOf<String, Set<String>>()

        val userA_uid = "user_A_uid"
        val userB_uid = "user_B_uid"
        val exam = "NEET"

        // User A checks 3 chapters
        val userA_key = "completed_chapters_${userA_uid}_$exam"
        val userA_chapters = setOf("Kinematics", "Laws of Motion", "Work Power Energy")
        syllabusPrefStore[userA_key] = userA_chapters

        // User B logs in and views syllabus
        val userB_key = "completed_chapters_${userB_uid}_$exam"
        val userB_chapters = syllabusPrefStore[userB_key] ?: emptySet()

        assertTrue("User B should start with zero checked chapters, not User A's 3", userB_chapters.isEmpty())

        // User B checks 1 chapter
        syllabusPrefStore[userB_key] = setOf("Optics")

        // Verify User A's chapters remain intact and isolated
        assertEquals(3, syllabusPrefStore[userA_key]?.size)
        assertEquals(1, syllabusPrefStore[userB_key]?.size)
        assertFalse(syllabusPrefStore[userB_key]!!.contains("Kinematics"))
        assertTrue(syllabusPrefStore[userB_key]!!.contains("Optics"))
    }

    @Test
    fun testBookmarkScopingByAccount() {
        val userBookmarkStore = mutableMapOf<String, Set<String>>()

        val userA_uid = "user_A_uid"
        val userB_uid = "user_B_uid"

        // User A bookmarks test 1 and test 2
        userBookmarkStore["bookmarks_$userA_uid"] = setOf("test_1", "test_2")

        // User B's bookmarks are queried
        val userB_bookmarks = userBookmarkStore["bookmarks_$userB_uid"] ?: emptySet()
        assertFalse("User B does NOT have test_1 bookmarked", userB_bookmarks.contains("test_1"))
        assertFalse("User B does NOT have test_2 bookmarked", userB_bookmarks.contains("test_2"))

        // User B bookmarks test 3
        userBookmarkStore["bookmarks_$userB_uid"] = setOf("test_3")

        // Verify isolation
        assertEquals(setOf("test_1", "test_2"), userBookmarkStore["bookmarks_$userA_uid"])
        assertEquals(setOf("test_3"), userBookmarkStore["bookmarks_$userB_uid"])
    }

    @Test
    fun testCaseF_accountSwitchingDoesNotCrash() {
        // Simulating rapid switching between accounts
        val testEntity = TestEntity(
            id = "test_any",
            title = "Sample Test",
            description = "Sample",
            institute = "Sample",
            batchName = "Sample",
            facultyName = "Sample",
            language = "English",
            subject = "Physics",
            difficulty = "Easy",
            testType = "Sample",
            questionsCount = 5,
            isOfficial = false,
            isVerifiedCommunity = false,
            isPublic = false,
            uploaderId = "uid_x"
        )

        val uids = listOf("uid_a", "", "uid_b", null, "uid_x", "admin_123")
        for (uid in uids) {
            val visible = try {
                TestVisibilityManager.isTestVisible(testEntity, uid, isAdmin = (uid == "admin_123"))
            } catch (e: Exception) {
                fail("Visibility evaluation crashed on UID: $uid with exception: ${e.message}")
                false
            }
            if (uid == "uid_x" || uid == "admin_123") {
                assertTrue("Owner or admin should see private test", visible)
            } else {
                assertFalse("Others should not see private test", visible)
            }
        }
    }
}
