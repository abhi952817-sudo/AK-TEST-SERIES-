package Com.ak.testseries.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Canonical QuestionEntity JSON serializer shared by local storage, community
 * sharing, backups and tests. Keeps five-option and exam-section metadata intact.
 */
object QuestionJsonSerializer {

    fun serialize(questions: List<QuestionEntity>): String {
        val array = JSONArray()
        questions.forEach { question ->
            val item = JSONObject().apply {
                put("id", question.id)
                put("testId", question.testId)
                put("questionText", question.questionText)
                put("optionA", question.optionA)
                put("optionB", question.optionB)
                put("optionC", question.optionC)
                put("optionD", question.optionD)
                question.optionE?.let { put("optionE", it) }
                put("correctAnswer", question.correctAnswer)
                put("explanation", question.explanation)
                put("subject", question.subject)
                put("chapter", question.chapter)
                put("topic", question.topic)
                put("difficulty", question.difficulty)
                put("isBookmarked", question.isBookmarked)
                put("questionTextHi", question.questionTextHi ?: "")
                put("optionAHi", question.optionAHi ?: "")
                put("optionBHi", question.optionBHi ?: "")
                put("optionCHi", question.optionCHi ?: "")
                put("optionDHi", question.optionDHi ?: "")
                put("optionEHi", question.optionEHi ?: "")
                put("explanationHi", question.explanationHi ?: "")
                put("questionType", question.questionType)
                put("section", question.section)
                put("needsImage", question.needsImage)
                put("imageUrl", question.imageUrl ?: "")
            }
            array.put(item)
        }
        return array.toString()
    }
}
