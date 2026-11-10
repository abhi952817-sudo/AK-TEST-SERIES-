package Com.ak.testseries.utils

import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extracts questions from the actual uploaded PDF using Gemini.
 * Fails closed: never invents questions, options, answer keys or explanations.
 */
object GeminiPdfExtractor {
    private const val MAX_PDF_BYTES = 15 * 1024 * 1024

    suspend fun processPdfWithGemini(
        pdfUrl: String,
        testId: String,
        apiKey: String,
        totalQuestionsExpected: Int
    ) = withContext(Dispatchers.IO) {
        val db = FirebaseFirestore.getInstance()
        val testRef = db.collection("LiveTests").document(testId)

        try {
            require(pdfUrl.isNotBlank()) { "Source PDF URL is missing." }
            require(apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                "Gemini API key is missing. PDF questions were not generated."
            }
            require(totalQuestionsExpected > 0) { "Expected question count must be greater than zero." }

            testRef.update("processingStatus", "Downloading source PDF").await()
            val pdfBytes = downloadPdf(pdfUrl)
            require(pdfBytes.isNotEmpty()) { "Downloaded PDF is empty." }
            val pdfBase64 = Base64.encodeToString(pdfBytes, Base64.NO_WRAP)

            val prompt = """
                Extract questions ONLY from the attached source PDF.
                Do not invent, rewrite into new questions, infer missing questions, or use outside knowledge.
                Return exactly $totalQuestionsExpected questions only when that exact number is present and readable in the PDF.
                If the PDF contains fewer readable questions, return only the readable questions; the app will reject the incomplete batch.
                Preserve original wording, all answer options, source answer key and explanation only when present in the PDF.
                Never guess an answer key. If the answer is not explicitly supported by the PDF, return an empty correctOption.
                Each object must contain:
                - questionText: non-empty string
                - options: array of exactly 4 or 5 non-empty option strings
                - correctOption: exactly A, B, C, D, or E
                - explanation: non-empty source-grounded explanation
                Return ONLY a JSON array without markdown fences or commentary.
            """.trimIndent()

            testRef.update("processingStatus", "Extracting questions with Gemini").await()
            val candidateModels = listOf("gemini-3.8-flash", "gemini-flash-latest", "gemini-2.5-flash")
            var responseText: String? = null
            var activeModel: String? = null
            var lastApiError: String? = null

            for (model in candidateModels) {
                var connection: HttpURLConnection? = null
                try {
                    val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                    connection = endpoint.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.connectTimeout = 45_000
                    connection.readTimeout = 90_000
                    connection.doOutput = true

                    val request = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().apply {
                            put("parts", JSONArray()
                                .put(JSONObject().put("text", prompt))
                                .put(JSONObject().put("inlineData", JSONObject().apply {
                                    put("mimeType", "application/pdf")
                                    put("data", pdfBase64)
                                }))
                            )
                        }))
                        put("generationConfig", JSONObject().apply {
                            put("responseMimeType", "application/json")
                        })
                    }

                    connection.outputStream.use { output ->
                        output.write(request.toString().toByteArray(Charsets.UTF_8))
                    }
                    val statusCode = connection.responseCode
                    val responseBody = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (statusCode !in 200..299) {
                        lastApiError = "Gemini model $model returned HTTP $statusCode."
                        continue
                    }

                    val responseObject = JSONObject(responseBody)
                    val candidates = responseObject.optJSONArray("candidates")
                    val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text").orEmpty()
                    if (text.isNotBlank()) {
                        responseText = text
                        activeModel = model
                        break
                    }
                    lastApiError = "Gemini model $model returned no question content."
                } catch (e: Exception) {
                    lastApiError = e.message ?: "Gemini request failed for $model."
                } finally {
                    connection?.disconnect()
                }
            }

            require(!responseText.isNullOrBlank()) {
                lastApiError ?: "All configured Gemini models failed. No questions were created."
            }

            val clean = responseText!!.trim()
                .removePrefix("\u0060\u0060\u0060json")
                .removePrefix("\u0060\u0060\u0060")
                .removeSuffix("\u0060\u0060\u0060")
                .trim()
            val questionArray = when {
                clean.startsWith("[") -> JSONArray(clean)
                clean.startsWith("{") -> {
                    val wrapper = JSONObject(clean)
                    wrapper.optJSONArray("questions") ?: wrapper.optJSONArray("data") ?: JSONArray()
                }
                else -> {
                    val start = clean.indexOf('[')
                    val end = clean.lastIndexOf(']')
                    if (start >= 0 && end > start) JSONArray(clean.substring(start, end + 1)) else JSONArray()
                }
            }

            require(questionArray.length() == totalQuestionsExpected) {
                "Gemini extracted " + questionArray.length() + " questions; exactly " +
                    totalQuestionsExpected + " are required. No partial or synthetic test was published."
            }

            val questions = ArrayList<HashMap<String, Any>>(questionArray.length())
            val answerKeys = ArrayList<HashMap<String, Any>>(questionArray.length())
            for (index in 0 until questionArray.length()) {
                val question = questionArray.optJSONObject(index)
                    ?: throw IllegalArgumentException("Question " + (index + 1) + " is not a valid JSON object.")
                val text = question.optString("questionText").trim()
                val optionsArray = question.optJSONArray("options")
                val options = if (optionsArray != null) {
                    (0 until optionsArray.length()).map { optionsArray.optString(it).trim() }
                } else {
                    listOf("A", "B", "C", "D", "E").map { letter ->
                        question.optString("option" + letter)
                            .ifBlank { question.optString("option" + letter + "En") }
                            .trim()
                    }.filter { it.isNotBlank() }
                }
                val answer = question.optString("correctOption")
                    .ifBlank { question.optString("correctAnswer") }.trim().uppercase()
                val explanation = question.optString("explanation").trim()

                require(text.isNotBlank()) { "Question " + (index + 1) + " has no question text." }
                require(options.size in 4..5 && options.all { it.isNotBlank() }) {
                    "Question " + (index + 1) + " must contain exactly four or five complete options."
                }
                require(options.map { it.lowercase() }.distinct().size == options.size) {
                    "Question " + (index + 1) + " contains duplicate options."
                }
                require(options.none { it.matches(Regex("Option [A-E]", RegexOption.IGNORE_CASE)) }) {
                    "Question " + (index + 1) + " contains placeholder options."
                }
                require(answer in if (options.size == 5) setOf("A", "B", "C", "D", "E") else setOf("A", "B", "C", "D")) {
                    "Question " + (index + 1) + " has no valid source answer key."
                }
                require(explanation.isNotBlank()) {
                    "Question " + (index + 1) + " has no source-grounded explanation."
                }

                val answerIndex = when (answer) {
                    "A" -> 0
                    "B" -> 1
                    "C" -> 2
                    "D" -> 3
                    "E" -> 4
                    else -> throw IllegalArgumentException("Question " + (index + 1) + " has an invalid answer key.")
                }
                val questionId = "q_" + (index + 1)
                val questionRecord = hashMapOf<String, Any>(
                    "id" to questionId,
                    "text" to text,
                    "questionText" to text,
                    "options" to options,
                    "optionA" to options[0],
                    "optionB" to options[1],
                    "optionC" to options[2],
                    "optionD" to options[3],
                    "questionType" to "MCQ_SINGLE",
                    "section" to "SECTION_A",
                    "generationModel" to (activeModel ?: "unknown"),
                    "sourceName" to "Uploaded PDF processed by Gemini",
                    "sourcePdfUrl" to pdfUrl,
                    "sourceVerified" to true
                )
                if (options.size == 5) questionRecord["optionE"] = options[4]
                questions.add(questionRecord)
                answerKeys.add(hashMapOf(
                    "id" to questionId,
                    "correctAnswer" to answer,
                    "correctAnswerIndex" to answerIndex,
                    "explanation" to explanation
                ))
            }

            testRef.update("processingStatus", "Saving verified Gemini questions").await()
            val batch = db.batch()
            questions.forEach { question ->
                val questionRef = testRef.collection("Questions").document(question["id"].toString())
                batch.set(questionRef, question)
            }
            batch.set(
                testRef.collection("private_keys").document("answerKey"),
                mapOf("testId" to testId, "questions" to answerKeys, "sourceVerified" to true)
            )
            val questionsJson = JSONArray().apply {
                questions.forEach { put(JSONObject(it)) }
            }.toString()
            batch.update(testRef, "questionsCount", questions.size)
            batch.update(testRef, "questionsJson", questionsJson)
            batch.update(testRef, "questionsVerified", true)
            batch.update(testRef, "processingStatus", "Ready")
            batch.commit().await()
        } catch (e: Exception) {
            android.util.Log.e("GeminiPdfExtractor", "PDF question extraction failed for $testId", e)
            try {
                testRef.update(
                    mapOf(
                        "processingStatus" to "Failed: " + (e.message ?: "Gemini PDF extraction failed"),
                        "questionsVerified" to false
                    )
                ).await()
            } catch (statusError: Exception) {
                android.util.Log.e("GeminiPdfExtractor", "Could not update failed extraction status", statusError)
            }
        }
    }

    private fun downloadPdf(pdfUrl: String): ByteArray {
        val connection = URL(pdfUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            val statusCode = connection.responseCode
            require(statusCode in 200..299) { "Source PDF download failed with HTTP $statusCode." }
            val declaredLength = connection.contentLengthLong
            require(declaredLength <= MAX_PDF_BYTES || declaredLength == -1L) {
                "PDF exceeds the supported 15 MB inline-processing limit."
            }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_PDF_BYTES) {
                        "PDF exceeds the supported 15 MB inline-processing limit."
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
