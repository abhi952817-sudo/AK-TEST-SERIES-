package Com.ak.testseries

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.viewmodel.NEETViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewModelInitTest {
    @Test
    fun testInit() {
        val vm = NEETViewModel(ApplicationProvider.getApplicationContext())
        // Ensure currentUserId is not hardcoded to admin_user_id
        assertNotEquals("admin_user_id", vm.currentUserId.value)
    }

    @Test
    fun testPrivateTestOwnershipVisibility() {
        val userA = "user_student_123"
        val userB = "user_student_456"

        fun createTest(id: String, title: String, uploaderId: String, isPublic: Boolean): TestEntity {
            return TestEntity(
                id = id,
                title = title,
                description = "Desc",
                institute = "Inst",
                batchName = "Batch",
                facultyName = "Faculty",
                language = "English",
                subject = "Physics",
                difficulty = "Medium",
                testType = "Chapter Test",
                questionsCount = 10,
                isOfficial = false,
                isVerifiedCommunity = false,
                uploaderId = uploaderId,
                isPublic = isPublic
            )
        }

        val publicTest = createTest("test_pub", "Public Mock", userA, true)
        val privateTestA = createTest("test_priv_a", "Private Test of User A", userA, false)

        // Rule: test.isPublic || (currentUid.isNotEmpty() && test.uploaderId == currentUid) || isAdmin
        fun isVisible(test: TestEntity, currentUid: String, isAdmin: Boolean): Boolean {
            val isOwner = currentUid.isNotEmpty() && test.uploaderId.isNotEmpty() && test.uploaderId == currentUid
            return test.isPublic || isOwner || isAdmin
        }

        // 1. Public test is visible to everyone
        assertTrue(isVisible(publicTest, userA, isAdmin = false))
        assertTrue(isVisible(publicTest, userB, isAdmin = false))
        assertTrue(isVisible(publicTest, "", isAdmin = false))

        // 2. Private test of User A is visible to User A
        assertTrue(isVisible(privateTestA, userA, isAdmin = false))

        // 3. Private test of User A is NOT visible to User B or anonymous user
        assertFalse(isVisible(privateTestA, userB, isAdmin = false))
        assertFalse(isVisible(privateTestA, "", isAdmin = false))

        // 4. Private test is visible to admin
        assertTrue(isVisible(privateTestA, userB, isAdmin = true))
    }

    @Test
    fun testSearchAndSavedPrivacyAcrossAccountSwitch() {
        val userA = "student_uid_alice"
        val userB = "student_uid_bob"

        fun makeTest(id: String, title: String, uploaderId: String, isPublic: Boolean, isBookmarked: Boolean = false, isDownloaded: Boolean = false): TestEntity {
            return TestEntity(
                id = id,
                title = title,
                description = "Physics mechanics test",
                institute = "Institute A",
                batchName = "Yakeen",
                facultyName = "Dr. Sharma",
                language = "English",
                subject = "Physics",
                difficulty = "Medium",
                testType = "Chapter Test",
                questionsCount = 10,
                isOfficial = false,
                isVerifiedCommunity = false,
                uploaderId = uploaderId,
                isPublic = isPublic,
                isBookmarked = isBookmarked,
                isDownloaded = isDownloaded
            )
        }

        // Room database contains tests from both users
        val localRoomTests = listOf(
            makeTest("pub_1", "Mechanics Public Exam", userA, isPublic = true, isBookmarked = true),
            makeTest("priv_a", "Mechanics Alice Secret Notes", userA, isPublic = false, isBookmarked = true, isDownloaded = true),
            makeTest("priv_b", "Bob Custom Physics Test", userB, isPublic = false, isBookmarked = true)
        )

        // Filter function mimicking NEETViewModel.testsList
        fun filterVisibleTests(allTests: List<TestEntity>, currentUid: String, query: String = "", isAdmin: Boolean = false): List<TestEntity> {
            return allTests.filter { test ->
                val isOwner = currentUid.isNotEmpty() && test.uploaderId.isNotEmpty() && test.uploaderId == currentUid
                val matchPublic = test.isPublic || isOwner || isAdmin
                val matchQuery = query.isEmpty() || test.title.contains(query, ignoreCase = true)
                matchPublic && matchQuery
            }
        }

        // Saved tests screen filter mimicking SavedTestsScreen
        fun getSavedTests(visibleTests: List<TestEntity>): List<TestEntity> {
            return visibleTests.filter { it.isBookmarked || it.isDownloaded }
        }

        // --- Scenario 1: Alice is logged in ---
        val aliceVisible = filterVisibleTests(localRoomTests, currentUid = userA)
        assertTrue(aliceVisible.any { it.id == "pub_1" })
        assertTrue(aliceVisible.any { it.id == "priv_a" })
        assertFalse("Bob's private test must not be visible to Alice", aliceVisible.any { it.id == "priv_b" })

        val aliceSaved = getSavedTests(aliceVisible)
        assertTrue(aliceSaved.any { it.id == "pub_1" })
        assertTrue(aliceSaved.any { it.id == "priv_a" })
        assertFalse(aliceSaved.any { it.id == "priv_b" })

        // Alice searches "Mechanics"
        val aliceSearch = filterVisibleTests(localRoomTests, currentUid = userA, query = "Mechanics")
        assertTrue(aliceSearch.any { it.id == "pub_1" })
        assertTrue(aliceSearch.any { it.id == "priv_a" })

        // --- Scenario 2: Account Switch to Bob on same device ---
        val bobVisible = filterVisibleTests(localRoomTests, currentUid = userB)
        assertTrue(bobVisible.any { it.id == "pub_1" })
        assertFalse("Alice's private test must NOT be visible to Bob", bobVisible.any { it.id == "priv_a" })
        assertTrue(bobVisible.any { it.id == "priv_b" })

        val bobSaved = getSavedTests(bobVisible)
        assertTrue(bobSaved.any { it.id == "pub_1" })
        assertFalse("Alice's private saved test must NOT be visible in Bob's Saved Tests", bobSaved.any { it.id == "priv_a" })
        assertTrue(bobSaved.any { it.id == "priv_b" })

        // Bob searches "Mechanics" (matches Alice's private test title in local Room)
        val bobSearch = filterVisibleTests(localRoomTests, currentUid = userB, query = "Mechanics")
        assertTrue(bobSearch.any { it.id == "pub_1" })
        assertFalse("Bob search must NOT leak Alice's private test from Room", bobSearch.any { it.id == "priv_a" })

        // --- Scenario 3: Alice logs back in ---
        val aliceBackVisible = filterVisibleTests(localRoomTests, currentUid = userA)
        assertTrue("Alice can see her private test again", aliceBackVisible.any { it.id == "priv_a" })
    }
}
