package Com.ak.testseries.exam

import Com.ak.testseries.data.QuestionEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

private data class GoldenVectorDocument(val fixtures: List<GoldenScoringVector>)
private data class GoldenScoringVector(
    val id: String,
    val examId: String,
    val questions: List<GoldenScoringQuestion>,
    val answers: Map<String, String>,
    val expected: GoldenScoringExpected,
    val sectionId: String? = null,
    val questionType: String? = null
)
private data class GoldenScoringQuestion(
    val id: String,
    val correctAnswer: String,
    val questionType: String? = null
)
private data class GoldenScoringExpected(
    val correctCount: Int,
    val wrongCount: Int,
    val unansweredCount: Int,
    val positiveMarksEarned: Double,
    val negativeMarksPenalty: Double,
    val rawScore: Double,
    val finalScore: Double,
    val maxScore: Double
)

class SharedScoringGoldenVectorParityTest {
    @Test
    fun androidEngineMatchesSharedGoldenVectors() {
        val fixturePath = System.getProperty("sharedScoringFixtures")
            ?: error("Gradle must provide the sharedScoringFixtures system property")
        val json = File(fixturePath).readText()
        val adapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(GoldenVectorDocument::class.java)
        val document = adapter.fromJson(json)
            ?: error("Shared scoring golden fixture file is empty or invalid")

        assertEquals("Expected nine initial shared vectors", 9, document.fixtures.size)

        document.fixtures.forEach { fixture ->
            val questions = fixture.questions.map { source ->
                QuestionEntity(
                    id = source.id,
                    testId = "golden_${fixture.id}",
                    questionText = "Golden fixture question",
                    optionA = "A",
                    optionB = "B",
                    optionC = "C",
                    optionD = "D",
                    correctAnswer = source.correctAnswer,
                    explanation = "",
                    subject = "General",
                    chapter = "Golden",
                    questionType = source.questionType ?: fixture.questionType ?: "MCQ_SINGLE",
                    section = fixture.sectionId ?: "SECTION_A"
                )
            }
            val result = ExamScoringEngine.evaluateTestAttempt(
                targetExam = fixture.examId,
                questions = questions,
                userAnswers = fixture.answers,
                timeSpentSeconds = 0L
            )
            val expected = fixture.expected
            assertEquals("${fixture.id}: correctCount", expected.correctCount, result.correctCount)
            assertEquals("${fixture.id}: wrongCount", expected.wrongCount, result.wrongCount)
            assertEquals("${fixture.id}: unansweredCount", expected.unansweredCount, result.unansweredCount)
            assertEquals("${fixture.id}: positiveMarksEarned", expected.positiveMarksEarned, result.positiveMarksEarned, 0.005)
            assertEquals("${fixture.id}: negativeMarksPenalty", expected.negativeMarksPenalty, result.negativeMarksPenalty, 0.005)
            assertEquals("${fixture.id}: rawScore", expected.rawScore, result.rawScore, 0.005)
            assertEquals("${fixture.id}: finalScore", expected.finalScore, result.finalScore, 0.005)
            assertEquals("${fixture.id}: maxScore", expected.maxScore, result.maxScore, 0.005)
        }
    }
}
