package Com.ak.testseries.exam

import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NeetUgScoringEngineTest {
    private fun testEntity(
        count: Int,
        type: String = "Chapter Test",
        duration: Int = 0
    ) = TestEntity(
        id = "neet-test",
        title = "NEET Test",
        description = "",
        institute = "",
        batchName = "",
        facultyName = "",
        language = "Hindi",
        subject = "Physics",
        difficulty = "Medium",
        testType = type,
        questionsCount = count,
        isOfficial = false,
        isVerifiedCommunity = false,
        targetExam = "NEET",
        durationMinutes = duration,
        examId = "neet_ug",
        stageId = "neet_main",
        paperId = "neet_paper_1",
        scoringVersion = 1
    )

    private fun question(id: String, subject: String = "Physics", key: String = "A") =
        QuestionEntity(
            id = id,
            testId = "neet-test",
            questionText = "Question $id",
            optionA = "A",
            optionB = "B",
            optionC = "C",
            optionD = "D",
            correctAnswer = key,
            explanation = "",
            subject = subject,
            chapter = "Test"
        )

    @Test
    fun correctWrongAndSkippedFollowPlusFourMinusOneZero() {
        val questions = listOf(question("q1"), question("q2"), question("q3"))
        val result = NeetUgScoringEngine.evaluateTestAttempt(
            testEntity(3),
            questions,
            mapOf("q1" to "A", "q2" to "B"),
            120
        )
        assertEquals(1, result.correctCount)
        assertEquals(1, result.wrongCount)
        assertEquals(1, result.unansweredCount)
        assertEquals(4.0, result.positiveMarksEarned, 0.001)
        assertEquals(1.0, result.negativeMarksPenalty, 0.001)
        assertEquals(3.0, result.finalScore, 0.001)
        assertEquals(12.0, result.maxScore, 0.001)
        assertEquals(50.0, result.accuracyPercent, 0.001)
    }

    @Test
    fun multipleAcceptedKeysAreCorrect() {
        val result = NeetUgScoringEngine.evaluateTestAttempt(
            testEntity(1),
            listOf(question("q1", key = "A,C")),
            mapOf("q1" to "C"),
            10
        )
        assertEquals(1, result.correctCount)
        assertEquals(4.0, result.finalScore, 0.001)
    }

    @Test
    fun negativeRawScoreIsNotSilentlyClamped() {
        val result = NeetUgScoringEngine.evaluateTestAttempt(
            testEntity(2),
            listOf(question("q1"), question("q2")),
            mapOf("q1" to "B", "q2" to "C"),
            10
        )
        assertEquals(-2.0, result.rawScore, 0.001)
        assertEquals(-2.0, result.finalScore, 0.001)
    }

    @Test
    fun fullLengthPaperRequiresOfficialSubjectDistribution() {
        val questions = buildList {
            repeat(45) { add(question("p$it", "Physics")) }
            repeat(45) { add(question("c$it", "Chemistry")) }
            repeat(90) { add(question("b$it", "Biology")) }
        }
        val answers = questions.associate { it.id to "A" }
        val result = NeetUgScoringEngine.evaluateTestAttempt(
            testEntity(180, "Full Syllabus", 180),
            questions,
            answers,
            10800
        )
        assertEquals(720.0, result.maxScore, 0.001)
        assertEquals(720.0, result.finalScore, 0.001)
        assertEquals(180, result.correctCount)
    }

    @Test
    fun rejectsWrongExamVersionAndUnknownAnswers() {
        val q = listOf(question("q1"))
        assertThrows(IllegalArgumentException::class.java) {
            NeetUgScoringEngine.evaluateTestAttempt(testEntity(1).copy(scoringVersion = 2), q, mapOf("q1" to "A"), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NeetUgScoringEngine.evaluateTestAttempt(testEntity(1), q, mapOf("other" to "A"), 1)
        }
    }
}
