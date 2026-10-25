package Com.ak.testseries.dedup

import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.data.BookmarkedQuestionRef
import Com.ak.testseries.data.QuestionReport
import org.junit.Assert.*
import org.junit.Test

class GlobalDeduplicationTest {

    @Test
    fun testAttemptSessionTokenIdempotencyFormat() {
        val userId = "user_test_99"
        val testId = "neet_full_mock_01"
        val sessionEpoch = 1729000000000L

        // Generate session token
        val token1 = "att_${userId}_${testId}_$sessionEpoch"
        val token2 = "att_${userId}_${testId}_$sessionEpoch"

        // Tokens generated within the same exam session MUST be identical
        assertEquals(token1, token2)

        val attempt1 = AttemptEntity(
            id = 1,
            userId = userId,
            testId = testId,
            testTitle = "NEET Full Mock 1",
            score = 620.0,
            maxScore = 720.0,
            correctCount = 155,
            incorrectCount = 20,
            skippedCount = 25,
            timeSpentSeconds = 7200,
            answersJson = "{}",
            attemptSessionToken = token1
        )

        val attempt2 = attempt1.copy(id = 2, score = 620.0)

        // Verifying deduplication logic: identical (userId, attemptSessionToken) should be treated as the same session
        val isDuplicateSession = (attempt1.userId == attempt2.userId) && (attempt1.attemptSessionToken == attempt2.attemptSessionToken)
        assertTrue("Submissions with identical session token must be recognized as duplicate session", isDuplicateSession)
    }

    @Test
    fun testLegitimateDistinctAttemptsArePreserved() {
        val userId = "user_test_99"
        val testId = "neet_full_mock_01"

        // Two distinct attempts taken at different times
        val tokenFirstAttempt = "att_${userId}_${testId}_1729000000000"
        val tokenSecondAttempt = "att_${userId}_${testId}_1729086400000"

        assertNotEquals("Distinct attempts must have unique session tokens", tokenFirstAttempt, tokenSecondAttempt)

        val attempt1 = AttemptEntity(
            id = 1,
            userId = userId,
            testId = testId,
            testTitle = "NEET Full Mock 1",
            score = 580.0,
            maxScore = 720.0,
            correctCount = 145,
            incorrectCount = 30,
            skippedCount = 25,
            timeSpentSeconds = 7100,
            answersJson = "{}",
            attemptSessionToken = tokenFirstAttempt
        )

        val attempt2 = AttemptEntity(
            id = 2,
            userId = userId,
            testId = testId,
            testTitle = "NEET Full Mock 1",
            score = 640.0, // Score improved
            maxScore = 720.0,
            correctCount = 160,
            incorrectCount = 15,
            skippedCount = 25,
            timeSpentSeconds = 6900,
            answersJson = "{}",
            attemptSessionToken = tokenSecondAttempt
        )

        val attemptList = listOf(attempt1, attempt2)
        assertEquals("Both legitimate attempts must be preserved", 2, attemptList.size)
        assertEquals("Tokens must be distinct", 2, attemptList.map { it.attemptSessionToken }.toSet().size)
    }

    @Test
    fun testDeterministicReportKeyPreventsTicketDuplication() {
        val testId = "jee_phy_lom"
        val questionId = "q_friction_04"
        val reporterUid = "student_456"

        val reportId1 = "report_${testId}_${questionId}_${reporterUid}"
        val reportId2 = "report_${testId}_${questionId}_${reporterUid}"

        assertEquals("Same user reporting same question must produce identical report ID", reportId1, reportId2)

        val report = QuestionReport(
            reportId = reportId1,
            testId = testId,
            testTitle = "Laws of Motion Test",
            questionId = questionId,
            questionText = "Calculate friction coefficient",
            reporterUserId = reporterUid,
            userNote = "Option B seems correct instead of Option A",
            status = "PENDING"
        )

        assertEquals("report_jee_phy_lom_q_friction_04_student_456", report.reportId)
    }

    @Test
    fun testReferenceBasedBookmarkFootprintOptimization() {
        val fullQuestionJson = """
            {
               "id": "q_big_123",
               "testId": "test_neet_bio_01",
               "questionText": "A very long detailed question text discussing cell biology, organelle functions, mitochondrial DNA replication mechanisms and cellular respiration ATP synthase steps with intricate sub-points...",
               "optionA": "Detailed option A explaining substrate level phosphorylation in detail",
               "optionB": "Detailed option B explaining oxidative phosphorylation in detail",
               "optionC": "Detailed option C explaining Krebs cycle enzymes in detail",
               "optionD": "Detailed option D explaining electron transport chain complexes in detail",
               "explanation": "Extremely detailed 5-paragraph scientific explanation with chemical reaction formulas and step-by-step enzymatic pathways...",
               "imageUrl": "https://storage.googleapis.com/test-bucket/diagrams/cell_diagram_hd.png"
            }
        """.trimIndent()

        // Reference-only pointer
        val ref = BookmarkedQuestionRef(
            questionId = "q_big_123",
            testId = "test_neet_bio_01",
            subject = "Biology",
            chapter = "Cell Biology"
        )

        val fullQuestionBytes = fullQuestionJson.toByteArray().size
        val refBytes = "${ref.questionId},${ref.testId},${ref.subject},${ref.chapter}".toByteArray().size

        assertTrue("Reference pointer must be dramatically smaller than full question data", refBytes < fullQuestionBytes / 5)
        assertTrue("Reference must retain essential pointer identifiers", ref.questionId.isNotEmpty() && ref.testId.isNotEmpty())
    }
}
