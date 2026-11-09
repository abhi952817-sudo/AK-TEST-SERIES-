package Com.ak.testseries.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Canonical QuestionEntity JSON serializer shared by local storage, community
 * sharing, backups and tests. Keeps five-option and exam-section metadata intact.
 */
object QuestionJsonSerializer {

    fun toMap(question: QuestionEntity): Map<String, Any?> = linkedMapOf<String, Any?>(
        "id" to question.id,
        "testId" to question.testId,
        "questionText" to question.questionText,
        "optionA" to question.optionA,
        "optionB" to question.optionB,
        "optionC" to question.optionC,
        "optionD" to question.optionD,
        "correctAnswer" to question.correctAnswer,
        "explanation" to question.explanation,
        "subject" to question.subject,
        "chapter" to question.chapter,
        "topic" to question.topic,
        "difficulty" to question.difficulty,
        "isBookmarked" to question.isBookmarked,
        "questionTextHi" to (question.questionTextHi ?: ""),
        "optionAHi" to (question.optionAHi ?: ""),
        "optionBHi" to (question.optionBHi ?: ""),
        "optionCHi" to (question.optionCHi ?: ""),
        "optionDHi" to (question.optionDHi ?: ""),
        "optionEHi" to (question.optionEHi ?: ""),
        "explanationHi" to (question.explanationHi ?: ""),
        "questionType" to question.questionType,
        "section" to question.section,
        "needsImage" to question.needsImage,
        "imageUrl" to (question.imageUrl ?: "")
    ).apply {
        question.optionE?.let { this["optionE"] = it }
    }

    fun serialize(questions: List<QuestionEntity>): String {
        val array = JSONArray()
        questions.forEach { question -> array.put(JSONObject(toMap(question))) }
        return array.toString()
    }
}
