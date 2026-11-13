package Com.ak.testseries.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityMvpContractTest {

    @Test
    fun publicMcqDocumentContainsOptionsButNeverPrivateAnswerKey() {
        val post = CommunityPost(
            creatorId = "student-1",
            creatorRole = "STUDENT",
            title = "Which is the SI unit of force?",
            content = "",
            subject = "Physics",
            targetExam = "NEET UG",
            postType = "MCQ",
            quizOptions = listOf("Newton", "Joule", "Watt", "Pascal"),
            quizCorrectOptionIndex = 0,
            quizExplanation = "Force is measured in newtons."
        )

        val publicData = communityPostToMap(post)
        assertEquals(listOf("Newton", "Joule", "Watt", "Pascal"), publicData["quizOptions"])
        assertEquals("STUDENT", publicData["creatorRole"])
        assertFalse(publicData.containsKey("quizCorrectOptionIndex"))
        assertFalse(publicData.containsKey("correctOptionIndex"))
        assertFalse(publicData.containsKey("correctAnswer"))
        assertFalse(publicData.containsKey("quizExplanation"))
        assertFalse(publicData.containsKey("answerKey"))
    }

    @Test
    fun ordinaryDiscussionDoesNotSerializeMcqOnlyFields() {
        val post = CommunityPost(
            creatorId = "student-1",
            title = "Study plan",
            content = "How do you revise?",
            subject = "Biology",
            targetExam = "NEET UG",
            postType = "DISCUSSION"
        )

        val publicData = communityPostToMap(post)
        assertFalse(publicData.containsKey("quizOptions"))
        assertFalse(publicData.containsKey("creatorRole"))
        assertTrue(publicData["isHidden"] == false)
    }

    @Test
    fun adminMcqCreatorRoleIsPreservedSeparatelyFromStudentCreatorRole() {
        val adminPost = CommunityPost(
            creatorId = "admin-1",
            creatorRole = "ADMIN",
            title = "Admin question",
            subject = "Physics",
            targetExam = "NEET UG",
            postType = "MCQ",
            quizOptions = listOf("A", "B", "C", "D")
        )
        val studentPost = adminPost.copy(creatorId = "student-1", creatorRole = "STUDENT")
        assertEquals("ADMIN", communityPostToMap(adminPost)["creatorRole"])
        assertEquals("STUDENT", communityPostToMap(studentPost)["creatorRole"])
    }
}
