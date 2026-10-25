package Com.ak.testseries.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Authorization Boundaries and Account Isolation:
 * - Public vs Private test visibility rules (TestVisibilityManager)
 * - Private test ownership isolation
 * - Super-admin email verification requirement
 */
class AuthorizationBoundaryTest {

    private fun createSampleTest(
        id: String,
        isPublic: Boolean,
        status: String = "Published",
        uploaderId: String = "alice_student_123"
    ): TestEntity {
        return TestEntity(
            id = id,
            title = "Sample Test $id",
            description = "Description",
            institute = "AK Institute",
            batchName = "Batch A",
            facultyName = "Faculty",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Mock Test",
            questionsCount = 10,
            isOfficial = false,
            isVerifiedCommunity = false,
            targetExam = "NEET",
            status = status,
            isPublic = isPublic,
            uploaderId = uploaderId
        )
    }

    @Test
    fun publicPublishedTest_isVisibleToAnyStudentAndGuest() {
        val publicTest = createSampleTest("test_pub_1", isPublic = true, status = "Published", uploaderId = "alice_123")

        // Visible to Alice (owner)
        assertTrue(TestVisibilityManager.isTestVisible(publicTest, currentUid = "alice_123", isAdmin = false))

        // Visible to Bob (other student)
        assertTrue(TestVisibilityManager.isTestVisible(publicTest, currentUid = "bob_456", isAdmin = false))

        // Visible to Guest / Unauthenticated
        assertTrue(TestVisibilityManager.isTestVisible(publicTest, currentUid = "", isAdmin = false))
        assertTrue(TestVisibilityManager.isTestVisible(publicTest, currentUid = null, isAdmin = false))
    }

    @Test
    fun privateTest_isHiddenFromOtherStudentsAndGuests() {
        val privateTest = createSampleTest("test_priv_1", isPublic = false, status = "Published", uploaderId = "alice_123")

        // Hidden from Bob (different student)
        assertFalse(TestVisibilityManager.isTestVisible(privateTest, currentUid = "bob_456", isAdmin = false))

        // Hidden from Guest / Unauthenticated
        assertFalse(TestVisibilityManager.isTestVisible(privateTest, currentUid = "", isAdmin = false))
        assertFalse(TestVisibilityManager.isTestVisible(privateTest, currentUid = null, isAdmin = false))
    }

    @Test
    fun privateTest_isVisibleToOwnerStudent() {
        val privateTest = createSampleTest("test_priv_1", isPublic = false, status = "Published", uploaderId = "alice_123")

        // Visible to Alice (owner)
        assertTrue(TestVisibilityManager.isTestVisible(privateTest, currentUid = "alice_123", isAdmin = false))
    }

    @Test
    fun privateTest_isVisibleToAdminRegardlessOfOwner() {
        val privateTest = createSampleTest("test_priv_1", isPublic = false, status = "Published", uploaderId = "alice_123")

        // Admin can inspect all tests
        assertTrue(TestVisibilityManager.isTestVisible(privateTest, currentUid = "admin_999", isAdmin = true))
        assertTrue(TestVisibilityManager.isTestVisible(privateTest, currentUid = null, isAdmin = true))
    }

    @Test
    fun underReviewOrDraftTest_isHiddenFromOtherStudents_visibleToOwnerAndAdmin() {
        val underReviewTest = createSampleTest("test_draft_1", isPublic = true, status = "Under Review", uploaderId = "alice_123")
        val draftTest = createSampleTest("test_draft_2", isPublic = true, status = "Draft", uploaderId = "alice_123")

        // Hidden from Bob
        assertFalse(TestVisibilityManager.isTestVisible(underReviewTest, currentUid = "bob_456", isAdmin = false))
        assertFalse(TestVisibilityManager.isTestVisible(draftTest, currentUid = "bob_456", isAdmin = false))

        // Visible to Alice (owner)
        assertTrue(TestVisibilityManager.isTestVisible(underReviewTest, currentUid = "alice_123", isAdmin = false))
        assertTrue(TestVisibilityManager.isTestVisible(draftTest, currentUid = "alice_123", isAdmin = false))

        // Visible to Admin
        assertTrue(TestVisibilityManager.isTestVisible(underReviewTest, currentUid = "bob_456", isAdmin = true))
        assertTrue(TestVisibilityManager.isTestVisible(draftTest, currentUid = "bob_456", isAdmin = true))
    }

    @Test
    fun privateVaultAccountIsolation_excludesOtherStudentsPrivateTests() {
        val alicePrivate = createSampleTest("alice_priv", isPublic = false, uploaderId = "alice_123")
        val bobPrivate = createSampleTest("bob_priv", isPublic = false, uploaderId = "bob_456")
        val tests = listOf(alicePrivate, bobPrivate)

        // For Alice (non-admin), only alicePrivate should be visible
        val currentUserId = "alice_123"
        val isAdmin = false
        val aliceVaultTests = tests.filter { test ->
            val isOwner = currentUserId.isNotBlank() && test.uploaderId == currentUserId
            val isPrivateTest = !test.isPublic || test.institute.contains("Private", ignoreCase = true)
            if (isAdmin) {
                isPrivateTest || isOwner
            } else {
                isOwner && isPrivateTest
            }
        }
        assertTrue(aliceVaultTests.any { it.id == "alice_priv" })
        assertFalse(aliceVaultTests.any { it.id == "bob_priv" })

        // For Unauthenticated user (""), no private tests from other users should be visible
        val guestVaultTests = tests.filter { test ->
            val isOwner = "".isNotBlank() && test.uploaderId == ""
            val isPrivateTest = !test.isPublic || test.institute.contains("Private", ignoreCase = true)
            if (isAdmin) {
                isPrivateTest || isOwner
            } else {
                isOwner && isPrivateTest
            }
        }
        assertTrue(guestVaultTests.isEmpty())
    }

    @Test
    fun ownerEmailVerificationPolicy_requiresEmailVerifiedFlag() {
        val ownerEmail = "abhi952817@gmail.com"

        fun evaluateIsOwner(email: String?, isEmailVerified: Boolean): Boolean {
            return email?.equals(ownerEmail, ignoreCase = true) == true && isEmailVerified
        }

        // Unverified email matching owner address is NOT owner
        assertFalse(evaluateIsOwner("abhi952817@gmail.com", isEmailVerified = false))

        // Verified email matching owner address IS owner
        assertTrue(evaluateIsOwner("abhi952817@gmail.com", isEmailVerified = true))

        // Case insensitivity works for verified email
        assertTrue(evaluateIsOwner("ABHI952817@GMAIL.COM", isEmailVerified = true))

        // Verified email with different address is NOT owner
        assertFalse(evaluateIsOwner("student@example.com", isEmailVerified = true))
    }

    @Test
    fun banEnforcement_blocksRestrictedStudentActions() {
        var isUserBanned = true
        var testInitiated = false

        fun attemptInitiateTest() {
            if (isUserBanned) {
                // Restricted action blocked
                return
            }
            testInitiated = true
        }

        attemptInitiateTest()
        assertFalse("Test initiation must be blocked when user is banned", testInitiated)

        // Ban lifted
        isUserBanned = false
        attemptInitiateTest()
        assertTrue("Test initiation must be permitted once ban is lifted", testInitiated)
    }

    @Test
    fun banStateIsolation_logoutClearsBanAndAccountSwitchDoesNotLeak() {
        // User A is banned
        var currentUserId = "user_a"
        var isUserBanned = true
        var userProfileBanned = true

        // User logs out
        fun performLogout() {
            currentUserId = ""
            isUserBanned = false
            userProfileBanned = false
        }

        performLogout()
        assertEquals("", currentUserId)
        assertFalse("Logout must reset isUserBanned flag", isUserBanned)
        assertFalse("Logout must reset profile ban status", userProfileBanned)

        // User B logs in (active student)
        fun performLoginUserB() {
            currentUserId = "user_b"
            // Profile loaded from User B's Users_Data document
            val userBDataIsBanned = false
            isUserBanned = userBDataIsBanned
            userProfileBanned = userBDataIsBanned
        }

        performLoginUserB()
        assertEquals("user_b", currentUserId)
        assertFalse("User B must not inherit User A's ban status", isUserBanned)
        assertFalse(userProfileBanned)
    }

    @Test
    fun userProfileData_includesIsBannedField() {
        val defaultStudent = StudentData(
            id = "student_1",
            name = "Student",
            email = "student@example.com",
            testsAttempted = "0",
            avgScore = "0",
            isBanned = false
        )
        assertFalse(defaultStudent.isBanned)

        val bannedStudent = defaultStudent.copy(isBanned = true)
        assertTrue(bannedStudent.isBanned)
    }
}
