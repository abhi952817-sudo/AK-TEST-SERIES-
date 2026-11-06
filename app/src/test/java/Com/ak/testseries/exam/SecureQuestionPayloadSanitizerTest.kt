package Com.ak.testseries.exam

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SecureQuestionPayloadSanitizerTest {

    @Test
    fun publicPayloadRemovesEveryAnswerAndExplanationAlias() {
        val source = JSONArray().put(
            JSONObject()
                .put("id", "q1")
                .put("questionText", "Question text")
                .put("correctAnswer", "B")
                .put("sourceAnswer", "C")
                .put("answerKey", JSONObject().put("answer", "D"))
                .put("explanationEn", "Private English explanation")
                .put("explanationHi", "निजी हिंदी व्याख्या")
                .put("solution", "Private solution")
                .put("optionA", "A")
        )

        val result = SecureQuestionPayloadSanitizer.separate(source.toString())
        val publicQuestion = JSONArray(result.publicJson).getJSONObject(0)

        assertEquals("Question text", publicQuestion.getString("questionText"))
        assertFalse(publicQuestion.has("correctAnswer"))
        assertFalse(publicQuestion.has("sourceAnswer"))
        assertFalse(publicQuestion.has("answerKey"))
        assertFalse(publicQuestion.has("explanationEn"))
        assertFalse(publicQuestion.has("explanationHi"))
        assertFalse(publicQuestion.has("solution"))

        assertEquals(1, result.answerKeys.size)
        assertEquals("B", result.answerKeys.single()["correctAnswer"])
        assertEquals("Private English explanation", result.answerKeys.single()["explanation"])
        assertEquals("निजी हिंदी व्याख्या", result.answerKeys.single()["explanationHi"])
    }

    @Test
    fun malformedEmptyAndDuplicateQuestionPayloadsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            SecureQuestionPayloadSanitizer.separate("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecureQuestionPayloadSanitizer.separate("not-json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecureQuestionPayloadSanitizer.separate("[]")
        }
        val duplicateIds = JSONArray()
            .put(JSONObject().put("id", "same").put("questionText", "Q1"))
            .put(JSONObject().put("id", "same").put("questionText", "Q2"))
        assertThrows(IllegalArgumentException::class.java) {
            SecureQuestionPayloadSanitizer.separate(duplicateIds.toString())
        }
    }
}
