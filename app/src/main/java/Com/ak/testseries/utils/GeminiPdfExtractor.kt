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
        totalQuestionsExpected: Int,
        targetExam: String,
        subject: String
    ) = withContext(Dispatchers.IO) {
        val db = FirebaseFirestore.getInstance()
        val testRef = db.collection("LiveTests").document(testId)

        try {
            require(pdfUrl.isNotBlank()) { "Source PDF URL is missing." }
            require(apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                "Gemini API key is missing. PDF questions were not generated."
            }
            require(totalQuestionsExpected > 0) { "Expected question count must be greater than zero." }
            val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExam)
                ?: throw IllegalArgumentException("Selected exam is not registered.")
            val stage = examConfig.stages.singleOrNull()
                ?: throw IllegalArgumentException("Exam has multiple stages; an explicit stage selector is required.")
            val paper = stage.papers.singleOrNull()
                ?: throw IllegalArgumentException("Exam stage has multiple papers; an explicit paper selector is required.")
            val subjectConfig = paper.subjects.singleOrNull {
                it.subjectId.equals(subject, true) || it.subjectName.equals(subject, true)
            } ?: throw IllegalArgumentException("Selected subject does not belong uniquely to the selected exam paper.")
            val configuredSections = subjectConfig.sections
            require(configuredSections.isNotEmpty()) { "Selected subject has no configured question sections." }

            testRef.update("processingStatus", "Downloading source PDF").await()
            val pdfBytes = downloadPdf(pdfUrl)
            require(pdfBytes.isNotEmpty()) { "Downloaded PDF is empty." }
            val pdfBase64 = Base64.encodeToString(pdfBytes, Base64.NO_WRAP)

            val sectionRules = configuredSections.joinToString("\n") { section ->
                "- ${section.sectionId}: ${section.sectionName}; type=${section.questionType.name}; options=${section.optionsCount}; correct=+${section.positiveMarks}; incorrect=-${section.negativeMarks}"
            }
            val prompt = """
                You are extracting an exam paper for ${examConfig.examName}, subject ${subjectConfig.subjectName}.
                Extract questions ONLY from the attached source PDF. Never invent a question or replace missing source content.
                Return exactly $totalQuestionsExpected questions only when that exact number is present and readable in the PDF.
                If fewer readable questions exist, return only those; the app will reject the incomplete batch.
                Use only the following configured section rules:
                $sectionRules
                Every question must include sectionId, questionType, positiveMarks and negativeMarks matching one configured section exactly.
                Preserve original wording and all source options. Never invent or guess an answer key.
                If the source contains an answer key, preserve it. If no source answer key exists, solve the question and include a source-grounded explanation, but do not claim it is an official key.
                Each object must contain questionText, sectionId, sectionName, questionType, positiveMarks, negativeMarks,
                options (an array; empty only for a configured numerical section), correctAnswer, and explanation.
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
                val sectionId = question.optString("sectionId").trim()
                val section = configuredSections.singleOrNull { it.sectionId == sectionId }
                    ?: throw IllegalArgumentException("Question " + (index + 1) + " has an unknown section.")
                val questionType = question.optString("questionType").uppercase()
                require(questionType == section.questionType.name) {
                    "Question " + (index + 1) + " does not match the configured section question type."
                }
                val positiveMarks = question.optDouble("positiveMarks", Double.NaN)
                val negativeMarks = question.optDouble("negativeMarks", Double.NaN)
                require(kotlin.math.abs(positiveMarks - section.positiveMarks) < 0.0001 &&
                    kotlin.math.abs(negativeMarks - section.negativeMarks) < 0.0001
                ) { "Question " + (index + 1) + " has incorrect positive or negative marking." }

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
                val answer = question.optString("correctAnswer")
                    .ifBlank { question.optString("correctOption") }.trim().uppercase()
                val explanation = question.optString("explanation").trim()
                val numeric = section.questionType == Com.ak.testseries.exam.QuestionType.NUMERICAL_VALUE ||
                    section.questionType == Com.ak.testseries.exam.QuestionType.NUMERICAL ||
                    section.questionType == Com.ak.testseries.exam.QuestionType.TITA

                require(text.length >= 10) { "Question " + (index + 1) + " has missing or incomplete question text." }
                require(explanation.length >= 15) { "Question " + (index + 1) + " has no usable explanation." }
                if (numeric) {
                    require(section.optionsCount == 0 && options.isEmpty()) {
                        "Numerical question " + (index + 1) + " must not contain MCQ options."
                    }
                    require(answer.toDoubleOrNull()?.isFinite() == true) {
                        "Numerical question " + (index + 1) + " has an invalid answer."
                    }
                } else {
                    require(section.optionsCount in 4..5 && options.size == section.optionsCount &&
                        options.all { it.isNotBlank() }
                    ) { "Question " + (index + 1) + " does not match its configured option count." }
                    require(options.map { it.lowercase() }.distinct().size == options.size) {
                        "Question " + (index + 1) + " contains duplicate options."
                    }
                    require(options.none { it.matches(Regex("(option|choice|distractor option)\\s+[A-E]", RegexOption.IGNORE_CASE)) }) {
                        "Question " + (index + 1) + " contains placeholder options."
                    }
                    val validAnswers = if (options.size == 5) setOf("A", "B", "C", "D", "E") else setOf("A", "B", "C", "D")
                    require(answer in validAnswers) {
                        "Question " + (index + 1) + " has no valid source answer key."
                    }
                }
                val questionId = "q_" + (index + 1)
                val questionRecord = hashMapOf<String, Any>(
                    "id" to questionId,
                    "text" to text,
                    "questionText" to text,
                    "options" to options,
                    "questionType" to questionType,
                    "section" to section.sectionId,
                    "sectionId" to section.sectionId,
                    "sectionName" to section.sectionName,
                    "positiveMarks" to section.positiveMarks,
                    "negativeMarks" to section.negativeMarks,
                    "subject" to subjectConfig.subjectName,
                    "generationModel" to (activeModel ?: "unknown"),
                    "sourceName" to "Uploaded PDF processed by Gemini",
                    "sourcePdfUrl" to pdfUrl,
                    "sourceVerified" to true
                )
                if (options.size >= 4) {
                    questionRecord["optionA"] = options[0]
                    questionRecord["optionB"] = options[1]
                    questionRecord["optionC"] = options[2]
                    questionRecord["optionD"] = options[3]
                    if (options.size == 5) questionRecord["optionE"] = options[4]
                }
                questions.add(questionRecord)
                answerKeys.add(hashMapOf(
                    "id" to questionId,
                    "correctAnswer" to answer,
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
            batch.update(testRef, "examId", examConfig.examId)
            batch.update(testRef, "stageId", stage.stageId)
            batch.update(testRef, "paperId", paper.paperId)
            batch.update(testRef, "scoringVersion", 1)
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
