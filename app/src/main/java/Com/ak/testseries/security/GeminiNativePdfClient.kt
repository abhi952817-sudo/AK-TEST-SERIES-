package Com.ak.testseries.security

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends the original PDF bytes to Gemini's native document input.
 * The caller supplies the already-selected credential so this class cannot silently
 * switch a Student to an Admin/shared key.
 */
object GeminiNativePdfClient {
    private const val MAX_PDF_BYTES = 50L * 1024L * 1024L

    suspend fun generate(
        context: Context,
        pdfUri: Uri,
        apiKey: String,
        prompt: String,
        systemInstruction: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "A personal Gemini API key is required." }
            val bytes = context.contentResolver.openInputStream(pdfUri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_PDF_BYTES) { "PDF exceeds Gemini's 50 MB inline PDF limit." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw IllegalArgumentException("The selected PDF could not be opened.")
            require(bytes.isNotEmpty()) { "The selected PDF is empty." }
            require(bytes.size >= 5 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-") {
                "The selected file is not a valid PDF document."
            }

            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val request = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))))
                put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts",
                    JSONArray()
                        .put(JSONObject().put("text", prompt))
                        .put(JSONObject().put("inlineData", JSONObject()
                            .put("mimeType", "application/pdf")
                            .put("data", encoded))))))
                put("generationConfig", JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 0.1))
            }

            val models = listOf("gemini-3.8-flash", "gemini-flash-latest", "gemini-3.5-flash")
            var lastFailure = "Gemini did not return a usable response."
            for (model in models) {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 30_000
                        readTimeout = 180_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("x-goog-api-key", apiKey)
                    }
                    connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        val message = runCatching {
                            JSONObject(responseText).optJSONObject("error")?.optString("message")
                        }.getOrNull().orEmpty()
                        lastFailure = "Gemini PDF request failed (HTTP $code). " +
                            message.take(300).ifBlank { "Check key permissions, quota, and network." }
                        if (code == 400 || code == 401 || code == 403) break
                        continue
                    }
                    val response = JSONObject(responseText)
                    val candidates = response.optJSONArray("candidates")
                    val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val text = buildString {
                        if (parts != null) for (i in 0 until parts.length()) {
                            append(parts.optJSONObject(i)?.optString("text").orEmpty())
                        }
                    }.trim()
                    if (text.isNotBlank()) return@runCatching text
                    lastFailure = "Gemini returned no question content for this PDF."
                } finally {
                    connection?.disconnect()
                }
            }
            throw IllegalStateException(lastFailure)
        }
    }
}
