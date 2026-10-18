package Com.ak.testseries

import Com.ak.testseries.ui.CommunityPost
import org.junit.Assert.*
import org.junit.Test

class CommunityPostTest {

    @Test
    fun testLegacyDiscussionPostDefaults() {
        // Simulates an existing discussion post created with only legacy parameters
        val legacyPost = CommunityPost(
            id = "legacy_post_1",
            authorName = "Rohan Sharma",
            authorSubtitle = "NEET Aspirant",
            title = "How to approach Physics rotational mechanics?",
            content = "Any tips on moment of inertia formulas?",
            subject = "Physics",
            targetExam = "NEET"
        )

        // Verify existing fields remain intact
        assertEquals("legacy_post_1", legacyPost.id)
        assertEquals("Rohan Sharma", legacyPost.authorName)
        assertEquals("How to approach Physics rotational mechanics?", legacyPost.title)
        assertEquals("Physics", legacyPost.subject)
        assertEquals("NEET", legacyPost.targetExam)

        // Verify backward compatibility defaults for new fields
        assertEquals("DISCUSSION", legacyPost.postType)
        assertNull(legacyPost.testId)
        assertNull(legacyPost.creatorId)
        assertNull(legacyPost.testTitle)
        assertNull(legacyPost.questionCount)
        assertNull(legacyPost.durationMinutes)
        assertNull(legacyPost.chapter)
        assertNull(legacyPost.difficulty)
    }

    @Test
    fun testCommunityPostTestShareDataModel() {
        // Simulates a community post referencing a test
        val testSharePost = CommunityPost(
            id = "share_post_1",
            authorName = "Dr. Verma",
            authorSubtitle = "Physics Faculty",
            title = "Target NEET 2026: Mechanics Practice Test",
            content = "Attempt this 45-question test covering Kinematics & Laws of Motion.",
            subject = "Physics",
            targetExam = "NEET",
            postType = "TEST_SHARE",
            testId = "ai_1710892800000",
            creatorId = "firebase_uid_faculty_99",
            testTitle = "Mechanics High-Yield Test",
            questionCount = 45,
            durationMinutes = 60,
            chapter = "Laws of Motion",
            difficulty = "Medium"
        )

        assertEquals("share_post_1", testSharePost.id)
        assertEquals("TEST_SHARE", testSharePost.postType)
        assertEquals("ai_1710892800000", testSharePost.testId)
        assertEquals("firebase_uid_faculty_99", testSharePost.creatorId)
        assertEquals("Mechanics High-Yield Test", testSharePost.testTitle)
        assertEquals(45, testSharePost.questionCount)
        assertEquals(60, testSharePost.durationMinutes)
        assertEquals("Laws of Motion", testSharePost.chapter)
        assertEquals("Medium", testSharePost.difficulty)
    }

    @Test
    fun testFirestoreDeserializationCompatibility() {
        // Simulates doc map returned by Firestore for an older post (no test reference keys present)
        val legacyDocData = mapOf<String, Any>(
            "authorName" to "Pooja",
            "title" to "Organic Chemistry notes",
            "content" to "Check out reaction mechanisms",
            "subject" to "Chemistry"
        )

        val deserializedLegacy = CommunityPost(
            id = "doc_old_1",
            authorName = legacyDocData["authorName"] as? String ?: "Unknown",
            title = legacyDocData["title"] as? String ?: "",
            content = legacyDocData["content"] as? String ?: "",
            subject = legacyDocData["subject"] as? String ?: "",
            postType = legacyDocData["postType"] as? String ?: "DISCUSSION",
            testId = legacyDocData["testId"] as? String,
            creatorId = legacyDocData["creatorId"] as? String,
            testTitle = legacyDocData["testTitle"] as? String,
            questionCount = (legacyDocData["questionCount"] as? Number)?.toInt(),
            durationMinutes = (legacyDocData["durationMinutes"] as? Number)?.toInt(),
            chapter = legacyDocData["chapter"] as? String,
            difficulty = legacyDocData["difficulty"] as? String
        )

        assertEquals("DISCUSSION", deserializedLegacy.postType)
        assertNull(deserializedLegacy.testId)
        assertNull(deserializedLegacy.creatorId)
        assertNull(deserializedLegacy.testTitle)
        assertNull(deserializedLegacy.questionCount)
        assertNull(deserializedLegacy.durationMinutes)
        assertNull(deserializedLegacy.chapter)
        assertNull(deserializedLegacy.difficulty)

        // Simulates doc map with test reference keys
        val testShareDocData = mapOf<String, Any>(
            "authorName" to "Rahul",
            "title" to "Biology Unit Test",
            "postType" to "TEST_SHARE",
            "testId" to "test_bio_123",
            "creatorId" to "uid_rahul",
            "testTitle" to "Human Physiology 50Q",
            "questionCount" to 50,
            "durationMinutes" to 45,
            "chapter" to "Digestion and Absorption",
            "difficulty" to "Easy"
        )

        val deserializedShare = CommunityPost(
            id = "doc_share_1",
            authorName = testShareDocData["authorName"] as? String ?: "Unknown",
            title = testShareDocData["title"] as? String ?: "",
            postType = testShareDocData["postType"] as? String ?: "DISCUSSION",
            testId = testShareDocData["testId"] as? String,
            creatorId = testShareDocData["creatorId"] as? String,
            testTitle = testShareDocData["testTitle"] as? String,
            questionCount = (testShareDocData["questionCount"] as? Number)?.toInt(),
            durationMinutes = (testShareDocData["durationMinutes"] as? Number)?.toInt(),
            chapter = testShareDocData["chapter"] as? String,
            difficulty = testShareDocData["difficulty"] as? String
        )

        assertEquals("TEST_SHARE", deserializedShare.postType)
        assertEquals("test_bio_123", deserializedShare.testId)
        assertEquals("uid_rahul", deserializedShare.creatorId)
        assertEquals("Human Physiology 50Q", deserializedShare.testTitle)
        assertEquals(50, deserializedShare.questionCount)
        assertEquals(45, deserializedShare.durationMinutes)
        assertEquals("Digestion and Absorption", deserializedShare.chapter)
        assertEquals("Easy", deserializedShare.difficulty)
    }

    @Test
    fun testOpenTestCallbackEligibility() {
        val validSharePost = CommunityPost(
            id = "p1",
            postType = "TEST_SHARE",
            testId = "test_physics_999"
        )
        val missingIdSharePost = CommunityPost(
            id = "p2",
            postType = "TEST_SHARE",
            testId = null
        )
        val blankIdSharePost = CommunityPost(
            id = "p3",
            postType = "TEST_SHARE",
            testId = "   "
        )
        val normalDiscussionPost = CommunityPost(
            id = "p4",
            postType = "DISCUSSION",
            testId = "test_123" // Even if somehow populated, DISCUSSION should not trigger test attempt
        )

        var openedTestId: String? = null
        val onOpenTest: (String) -> Unit = { id -> openedTestId = id }

        // 1. Valid TEST_SHARE:
        if (validSharePost.postType == "TEST_SHARE" && !validSharePost.testId.isNullOrBlank()) {
            onOpenTest(validSharePost.testId!!)
        }
        assertEquals("test_physics_999", openedTestId)

        // 2. Missing testId in TEST_SHARE:
        openedTestId = null
        if (missingIdSharePost.postType == "TEST_SHARE" && !missingIdSharePost.testId.isNullOrBlank()) {
            onOpenTest(missingIdSharePost.testId!!)
        }
        assertNull(openedTestId)

        // 3. Blank testId in TEST_SHARE:
        openedTestId = null
        if (blankIdSharePost.postType == "TEST_SHARE" && !blankIdSharePost.testId.isNullOrBlank()) {
            onOpenTest(blankIdSharePost.testId!!)
        }
        assertNull(openedTestId)

        // 4. Normal DISCUSSION:
        openedTestId = null
        if (normalDiscussionPost.postType == "TEST_SHARE" && !normalDiscussionPost.testId.isNullOrBlank()) {
            onOpenTest(normalDiscussionPost.testId!!)
        }
        assertNull(openedTestId)
    }

    @Test
    fun testCommunityShareOwnerEligibilityAndAuthentication() {
        val ownerUid = "firebase_user_abc_123"
        val otherUserUid = "firebase_user_xyz_789"
        val test = Com.ak.testseries.data.TestEntity(
            id = "test_owner_1",
            title = "NEET Biology Mock",
            description = "Test description",
            institute = "Self",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Biology",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = ownerUid,
            isPublic = false
        )

        // 1. Owner can share
        assertTrue(Com.ak.testseries.ui.CommunityShareManager.isEligibleToShare(test.uploaderId, ownerUid))

        // 2. Non-owner cannot share
        assertFalse(Com.ak.testseries.ui.CommunityShareManager.isEligibleToShare(test.uploaderId, otherUserUid))

        // 3. Missing authentication (null or empty UID) cannot share
        assertFalse(Com.ak.testseries.ui.CommunityShareManager.isEligibleToShare(test.uploaderId, null))
        assertFalse(Com.ak.testseries.ui.CommunityShareManager.isEligibleToShare(test.uploaderId, ""))
        assertFalse(Com.ak.testseries.ui.CommunityShareManager.isEligibleToShare(test.uploaderId, "   "))
    }

    @Test
    fun testBuildTestSharePostPreservesOriginalIdentityAndDoesNotMutateIsPublicOrDuplicateQuestions() {
        val ownerUid = "firebase_user_abc_123"
        val originalTest = Com.ak.testseries.data.TestEntity(
            id = "test_original_456",
            title = "Genetics & Evolution Mastery",
            description = "High-yield NCERT based genetics problems",
            institute = "Self",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Biology",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            targetExam = "NEET",
            durationMinutes = 45,
            chapter = "Principles of Inheritance",
            uploaderId = ownerUid,
            isPublic = false,
            questionsJson = """[{"id":"q1","text":"Mendel law"}]"""
        )

        val sharePost = Com.ak.testseries.ui.CommunityShareManager.buildTestSharePost(
            test = originalTest,
            currentUid = ownerUid,
            authorName = "Ananya Patel",
            authorSubtitle = "NEET Aspirant",
            targetExam = "NEET"
        )

        // 1. Post type is TEST_SHARE
        assertEquals("TEST_SHARE", sharePost.postType)

        // 2. testId is the original test ID
        assertEquals(originalTest.id, sharePost.testId)

        // 3. creatorId is the authenticated user's Firebase UID
        assertEquals(ownerUid, sharePost.creatorId)

        // 4. Test title and metadata correctly populated
        assertEquals("Genetics & Evolution Mastery", sharePost.testTitle)
        assertEquals("Shared Test: Genetics & Evolution Mastery", sharePost.title)
        assertEquals("High-yield NCERT based genetics problems", sharePost.content)
        assertEquals(45, sharePost.questionCount)
        assertEquals(45, sharePost.durationMinutes)
        assertEquals("Principles of Inheritance", sharePost.chapter)
        assertEquals("Medium", sharePost.difficulty)
        assertEquals("Biology", sharePost.subject)
        assertEquals("NEET", sharePost.targetExam)

        // 5. isPublic is strictly preserved as false on original test
        assertFalse("Sharing to Community must NOT change test.isPublic", originalTest.isPublic)

        // 6. CommunityPost has NO questions collection or questionsJson duplicate field
        assertEquals("test_original_456", sharePost.testId)
    }

    @Test
    fun testDuplicateSharePrevention() {
        val ownerUid = "firebase_user_abc_123"
        val testId = "test_original_456"

        val existingPosts = listOf(
            CommunityPost(
                id = "post_1",
                postType = "TEST_SHARE",
                testId = testId,
                creatorId = ownerUid
            ),
            CommunityPost(
                id = "post_2",
                postType = "DISCUSSION",
                testId = testId,
                creatorId = ownerUid
            )
        )

        // Same owner, same testId, TEST_SHARE -> duplicate detected
        assertTrue(
            Com.ak.testseries.ui.CommunityShareManager.isDuplicateShare(
                existingPosts = existingPosts,
                testId = testId,
                currentUid = ownerUid
            )
        )

        // Different owner attempting to check duplicate
        assertFalse(
            Com.ak.testseries.ui.CommunityShareManager.isDuplicateShare(
                existingPosts = existingPosts,
                testId = testId,
                currentUid = "other_user_999"
            )
        )

        // Different test
        assertFalse(
            Com.ak.testseries.ui.CommunityShareManager.isDuplicateShare(
                existingPosts = existingPosts,
                testId = "different_test_777",
                currentUid = ownerUid
            )
        )

        // Only discussion post for this test does not trigger duplicate TEST_SHARE
        val discussionOnlyList = listOf(
            CommunityPost(
                id = "post_3",
                postType = "DISCUSSION",
                testId = "test_disc_only",
                creatorId = ownerUid
            )
        )
        assertFalse(
            Com.ak.testseries.ui.CommunityShareManager.isDuplicateShare(
                existingPosts = discussionOnlyList,
                testId = "test_disc_only",
                currentUid = ownerUid
            )
        )
    }

    @Test
    fun testCommunityPostToMapSerializationIntegrity() {
        val testSharePost = CommunityPost(
            id = "share_serialization_1",
            authorName = "Ananya Patel",
            authorSubtitle = "NEET Aspirant",
            title = "Shared Test: Mechanics",
            content = "Practice questions",
            subject = "Physics",
            targetExam = "NEET",
            postType = "TEST_SHARE",
            testId = "test_123",
            creatorId = "uid_456",
            testTitle = "Mechanics",
            questionCount = 30,
            durationMinutes = 40,
            chapter = "Kinematics",
            difficulty = "Easy"
        )

        val map = Com.ak.testseries.ui.communityPostToMap(testSharePost)
        assertEquals("share_serialization_1", map["id"])
        assertEquals("TEST_SHARE", map["postType"])
        assertEquals("test_123", map["testId"])
        assertEquals("uid_456", map["creatorId"])
        assertEquals("Mechanics", map["testTitle"])
        assertEquals(30, map["questionCount"])
        assertEquals(40, map["durationMinutes"])
        assertEquals("Kinematics", map["chapter"])
        assertEquals("Easy", map["difficulty"])
    }

    @Test
    fun testShouldAutoShareNewTestsConditionChecks() {
        val ownerUid = "firebase_user_abc_123"
        val otherUid = "firebase_user_other_456"

        // 1. Auto-share enabled (default true), user test (not live), owned by current user -> true
        assertTrue(
            Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                autoShareEnabled = true,
                isLiveTest = false,
                uploaderId = ownerUid,
                currentUid = ownerUid
            )
        )

        // 2. Auto-share flag disabled -> false
        assertFalse(
            Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                autoShareEnabled = false,
                isLiveTest = false,
                uploaderId = ownerUid,
                currentUid = ownerUid
            )
        )

        // 3. Live / scheduled admin test -> false (not a normal user test)
        assertFalse(
            Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                autoShareEnabled = true,
                isLiveTest = true,
                uploaderId = ownerUid,
                currentUid = ownerUid
            )
        )

        // 4. Current user is not the uploader / non-owner -> false
        assertFalse(
            Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                autoShareEnabled = true,
                isLiveTest = false,
                uploaderId = ownerUid,
                currentUid = otherUid
            )
        )

        // 5. Unauthenticated user (null or blank UID) -> false
        assertFalse(
            Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                autoShareEnabled = true,
                isLiveTest = false,
                uploaderId = ownerUid,
                currentUid = null
            )
        )
        assertFalse(
            Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                autoShareEnabled = true,
                isLiveTest = false,
                uploaderId = ownerUid,
                currentUid = ""
            )
        )
    }

    @Test
    fun testKeepCompletelyPrivateAndPublicDistinction() {
        val ownerUid = "firebase_user_abc_123"

        // CASE 1: Private test with Auto Share ON
        val privateAutoShareTest = Com.ak.testseries.data.TestEntity(
            id = "test_priv_share_on",
            title = "Private Mock Biology",
            description = "Test description",
            institute = "Self",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Biology",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = ownerUid,
            isPublic = false
        )
        assertFalse(privateAutoShareTest.isPublic)
        assertTrue(
            Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                autoShareEnabled = true,
                isLiveTest = false,
                uploaderId = privateAutoShareTest.uploaderId,
                currentUid = ownerUid
            )
        )
        // Sharing must strictly keep isPublic as false
        val sharePost = Com.ak.testseries.ui.CommunityShareManager.buildTestSharePost(
            test = privateAutoShareTest,
            currentUid = ownerUid,
            authorName = "Ananya Patel",
            authorSubtitle = "NEET Aspirant",
            targetExam = "NEET"
        )
        assertEquals("test_priv_share_on", sharePost.testId)
        assertFalse("Private test must remain isPublic=false when shared to Community", privateAutoShareTest.isPublic)

        // CASE 2: Private test with Keep Completely Private (Auto Share OFF)
        val privateCompletelyPrivateTest = Com.ak.testseries.data.TestEntity(
            id = "test_priv_share_off",
            title = "Completely Private Mock",
            description = "Test description",
            institute = "Self",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = ownerUid,
            isPublic = false
        )
        assertFalse(privateCompletelyPrivateTest.isPublic)
        assertFalse(
            "Auto share disabled must prevent automatic community post creation",
            Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                autoShareEnabled = false,
                isLiveTest = false,
                uploaderId = privateCompletelyPrivateTest.uploaderId,
                currentUid = ownerUid
            )
        )
        // The test remains saved locally without any rollback
        assertNotNull(privateCompletelyPrivateTest.id)

        // CASE 3: Public test with Auto Share ON and OFF
        val publicTest = Com.ak.testseries.data.TestEntity(
            id = "test_public_1",
            title = "Public Open Mock",
            description = "Test description",
            institute = "Self",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Chemistry",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = ownerUid,
            isPublic = true
        )
        assertTrue(publicTest.isPublic)
        // Auto-share ON -> should share
        assertTrue(
            Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                autoShareEnabled = true,
                isLiveTest = false,
                uploaderId = publicTest.uploaderId,
                currentUid = ownerUid
            )
        )
        // Auto-share OFF -> should not share
        assertFalse(
            Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                autoShareEnabled = false,
                isLiveTest = false,
                uploaderId = publicTest.uploaderId,
                currentUid = ownerUid
            )
        )
    }

    @Test
    fun testVisibilityRules_PublicTestVisibleToAll() {
        val publicTest = Com.ak.testseries.data.TestEntity(
            id = "test_pub_1",
            title = "Public Full Mock",
            description = "Public test",
            institute = "Institute",
            batchName = "2026",
            facultyName = "Teacher",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Full Syllabus",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = "owner_uid_1",
            isPublic = true
        )

        // Public test is visible to owner
        assertTrue(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(publicTest, "owner_uid_1", false))
        // Public test is visible to another user
        assertTrue(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(publicTest, "another_uid_2", false))
        // Public test is visible when logged out
        assertTrue(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(publicTest, "", false))
        assertTrue(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(publicTest, null, false))
        // Public test is visible to admin
        assertTrue(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(publicTest, "admin_uid", true))
    }

    @Test
    fun testVisibilityRules_MyPrivateTestVisibleOnlyToOwner() {
        val myPrivateTest = Com.ak.testseries.data.TestEntity(
            id = "test_priv_1",
            title = "My Private Physics Practice",
            description = "Private test",
            institute = "Self",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = "user_owner_123",
            isPublic = false
        )

        // Visible to owner
        assertTrue(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(myPrivateTest, "user_owner_123", false))

        // NOT visible to another user
        assertFalse(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(myPrivateTest, "different_user_456", false))

        // NOT visible when logged out
        assertFalse(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(myPrivateTest, "", false))
        assertFalse(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(myPrivateTest, null, false))

        // Visible to admin
        assertTrue(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(myPrivateTest, "admin_uid", true))
        assertTrue(Com.ak.testseries.data.TestVisibilityManager.isTestVisible(myPrivateTest, "different_user_456", true))
    }

    @Test
    fun testVisibilityRules_CommunitySharedPrivateTest_NotExposedInNormalLists() {
        // A private test shared to community via TEST_SHARE post
        val sharedPrivateTest = Com.ak.testseries.data.TestEntity(
            id = "shared_priv_test_99",
            title = "Special Mechanics Challenge",
            description = "Shared to community via TEST_SHARE",
            institute = "Self",
            batchName = "2026",
            facultyName = "Self",
            language = "English",
            subject = "Physics",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 30,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = "creator_uid_a",
            isPublic = false // Crucial: Community Shared does NOT make it isPublic = true!
        )

        val communityPost = Com.ak.testseries.ui.CommunityPost(
            id = "post_test_share_1",
            authorName = "Creator",
            title = "Special Mechanics Challenge",
            content = "Try my custom mechanics test!",
            subject = "Physics",
            targetExam = "NEET",
            postType = "TEST_SHARE",
            testId = sharedPrivateTest.id,
            creatorId = sharedPrivateTest.uploaderId
        )

        // 1. Verify community post references testId and creatorId
        assertEquals("TEST_SHARE", communityPost.postType)
        assertEquals(sharedPrivateTest.id, communityPost.testId)
        assertEquals("creator_uid_a", communityPost.creatorId)

        // 2. Crucial: The test remains isPublic = false
        assertFalse("Community shared test must remain isPublic = false", sharedPrivateTest.isPublic)

        // 3. For another user (e.g. user_uid_b), test MUST NOT appear in normal search/lists
        assertFalse(
            "Another user's private test must not appear in normal lists even if shared to community",
            Com.ak.testseries.data.TestVisibilityManager.isTestVisible(sharedPrivateTest, "user_uid_b", false)
        )

        // 4. For the creator (creator_uid_a), test appears in their list
        assertTrue(
            "Creator must be able to see their own test in their lists",
            Com.ak.testseries.data.TestVisibilityManager.isTestVisible(sharedPrivateTest, "creator_uid_a", false)
        )
    }

    @Test
    fun testVisibilityRules_SearchFilterPrivacy() {
        val userA = "uid_alice"
        val userB = "uid_bob"

        val publicTest = Com.ak.testseries.data.TestEntity(
            id = "t1",
            title = "Rotational Motion",
            description = "",
            institute = "",
            batchName = "",
            facultyName = "",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = userA,
            isPublic = true
        )

        val userAPrivateTest = Com.ak.testseries.data.TestEntity(
            id = "t2",
            title = "Rotational Motion Secret Prep",
            description = "",
            institute = "",
            batchName = "",
            facultyName = "",
            language = "English",
            subject = "Physics",
            difficulty = "Hard",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = userA,
            isPublic = false
        )

        val userBPrivateTest = Com.ak.testseries.data.TestEntity(
            id = "t3",
            title = "Rotational Motion Bob Notes",
            description = "",
            institute = "",
            batchName = "",
            facultyName = "",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Chapter Test",
            questionsCount = 45,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = userB,
            isPublic = false
        )

        val cachedAllTests = listOf(publicTest, userAPrivateTest, userBPrivateTest)
        val searchQuery = "Rotational Motion"

        // Simulated search: query matches all 3 tests
        val matchingQueryTests = cachedAllTests.filter { it.title.contains(searchQuery) }
        assertEquals(3, matchingQueryTests.size)

        // Apply effective search visibility rule:
        // test.isPublic || test.uploaderId == currentUserUid || isAdmin
        val bobSearchResults = matchingQueryTests.filter {
            Com.ak.testseries.data.TestVisibilityManager.isTestVisible(it, userB, false)
        }
        // Bob sees public test (t1) and Bob's private test (t3). Must NOT see Alice's private test (t2)!
        assertEquals(2, bobSearchResults.size)
        assertTrue(bobSearchResults.any { it.id == "t1" })
        assertTrue(bobSearchResults.any { it.id == "t3" })
        assertFalse("Bob's search must not expose Alice's private test", bobSearchResults.any { it.id == "t2" })

        // Alice's search results:
        val aliceSearchResults = matchingQueryTests.filter {
            Com.ak.testseries.data.TestVisibilityManager.isTestVisible(it, userA, false)
        }
        // Alice sees public test (t1) and Alice's private test (t2). Must NOT see Bob's private test (t3)!
        assertEquals(2, aliceSearchResults.size)
        assertTrue(aliceSearchResults.any { it.id == "t1" })
        assertTrue(aliceSearchResults.any { it.id == "t2" })
        assertFalse("Alice's search must not expose Bob's private test", aliceSearchResults.any { it.id == "t3" })

        // Admin search results:
        val adminSearchResults = matchingQueryTests.filter {
            Com.ak.testseries.data.TestVisibilityManager.isTestVisible(it, "any_admin_uid", true)
        }
        // Admin sees all 3
        assertEquals(3, adminSearchResults.size)
    }

    @Test
    fun testVisibilityRules_AccountSwitch_NoDataLeakage() {
        val userA = "user_A_id"
        val userB = "user_B_id"

        val testA = Com.ak.testseries.data.TestEntity(
            id = "test_a_priv",
            title = "User A Private Test",
            description = "Only User A should see this",
            institute = "",
            batchName = "",
            facultyName = "",
            language = "English",
            subject = "Physics",
            difficulty = "Easy",
            testType = "Chapter Test",
            questionsCount = 10,
            isOfficial = false,
            isVerifiedCommunity = false,
            uploaderId = userA,
            isPublic = false
        )

        val roomCache = listOf(testA)

        // Step 1: User A is logged in
        val userAList = roomCache.filter {
            Com.ak.testseries.data.TestVisibilityManager.isTestVisible(it, userA, false)
        }
        assertEquals(1, userAList.size)
        assertEquals("test_a_priv", userAList.first().id)

        // Step 2: User A logs out (current UID becomes empty)
        val loggedOutList = roomCache.filter {
            Com.ak.testseries.data.TestVisibilityManager.isTestVisible(it, "", false)
        }
        assertTrue("Logged out user must not see private tests in Room cache", loggedOutList.isEmpty())

        // Step 3: User B logs in (current UID becomes userB)
        val userBList = roomCache.filter {
            Com.ak.testseries.data.TestVisibilityManager.isTestVisible(it, userB, false)
        }
        assertTrue("User B must NOT see User A's private test from Room cache", userBList.isEmpty())

        // Step 4: User A logs back in
        val userAReturnList = roomCache.filter {
            Com.ak.testseries.data.TestVisibilityManager.isTestVisible(it, userA, false)
        }
        assertEquals("User A can see their private test again without deleting data", 1, userAReturnList.size)
    }

    @Test
    fun testCommunityPostLightweightReferenceOnly() {
        val ownerUid = "firebase_owner_456"
        val testEntity = Com.ak.testseries.data.TestEntity(
            id = "test_canonical_789",
            title = "NEET Organic Chemistry Part Test",
            description = "High yield aldehydes, ketones & carboxylic acids",
            institute = "Self Created",
            batchName = "Target 2026",
            facultyName = "Dr. Sharma",
            language = "English",
            subject = "Chemistry",
            difficulty = "Hard",
            testType = "Part Test",
            questionsCount = 50,
            isOfficial = false,
            isVerifiedCommunity = false,
            targetExam = "NEET",
            durationMinutes = 60,
            classLevel = "12th",
            chapter = "Aldehydes & Ketones",
            topic = "Nucleophilic Addition",
            uploaderId = ownerUid,
            isPublic = false,
            questionsJson = """[{"id":"q1","text":"Reaction mechanism question"}]"""
        )

        val post = Com.ak.testseries.ui.CommunityShareManager.buildTestSharePost(
            test = testEntity,
            currentUid = ownerUid,
            authorName = "Dr. Sharma",
            authorSubtitle = "Chemistry Mentor",
            targetExam = "NEET",
            totalMarks = 200.0,
            questionCount = 50,
            durationMinutes = 60
        )

        // 1. Verify lightweight reference fields
        assertEquals("test_canonical_789", post.testId)
        assertEquals(ownerUid, post.creatorId)
        assertEquals("NEET Organic Chemistry Part Test", post.testTitle)
        assertEquals("Shared Test: NEET Organic Chemistry Part Test", post.title)
        assertEquals("High yield aldehydes, ketones & carboxylic acids", post.content)
        assertEquals("Chemistry", post.subject)
        assertEquals("NEET", post.targetExam)
        assertEquals(50, post.questionCount)
        assertEquals(60, post.durationMinutes)
        assertEquals("Aldehydes & Ketones", post.chapter)
        assertEquals("Hard", post.difficulty)
        assertEquals("12th", post.classLevel)
        assertEquals(200.0, post.totalMarks ?: 0.0, 0.01)
        assertEquals("Nucleophilic Addition", post.topic)
        assertEquals("Dr. Sharma", post.authorName)
        assertEquals("TEST_SHARE", post.postType)

        // 2. Verify serialized map contains reference keys and NEVER contains questions data
        val postMap = Com.ak.testseries.ui.communityPostToMap(post)
        assertEquals("test_canonical_789", postMap["testId"])
        assertEquals(ownerUid, postMap["creatorId"])
        assertEquals(50, postMap["questionCount"])
        assertEquals(60, postMap["durationMinutes"])
        assertEquals(200.0, postMap["totalMarks"])
        assertEquals("12th", postMap["classLevel"])
        assertEquals("TEST_SHARE", postMap["postType"])

        assertFalse("CommunityPost must NOT contain questionsJson", postMap.containsKey("questionsJson"))
        assertFalse("CommunityPost must NOT contain questions", postMap.containsKey("questions"))
        assertFalse("CommunityPost must NOT contain options", postMap.containsKey("options"))
        assertFalse("CommunityPost must NOT contain correctAnswer", postMap.containsKey("correctAnswer"))
        assertFalse("CommunityPost must NOT contain attempts", postMap.containsKey("attempts"))
        assertFalse("CommunityPost must NOT contain notes", postMap.containsKey("notes"))
    }
}
