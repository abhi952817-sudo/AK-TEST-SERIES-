package Com.ak.testseries.security

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GeminiNativePdfClientTest {

    @Test
    fun sendsOriginalPdfAndPersonalKeyHeaderToGeminiGenerateContent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfBytes = "%PDF-1.4\n% native pdf test fixture\n".toByteArray(Charsets.US_ASCII)
        val pdfFile = File(context.cacheDir, "native-gemini-test.pdf").apply {
            writeBytes(pdfBytes)
        }
        val server = ServerSocket(0)
        val capturedBody = AtomicReference<String>()
        val capturedHeaders = AtomicReference<Map<String, String>>(emptyMap())
        val serverThread = Thread {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                fun readLine(): String {
                    val bytes = java.io.ByteArrayOutputStream()
                    while (true) {
                        val value = input.read()
                        if (value < 0 || value == 10) break
                        if (value != 13) bytes.write(value)
                    }
                    return bytes.toString(Charsets.US_ASCII.name())
                }
                readLine() // request line
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = readLine()
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        headers[line.substring(0, separator).trim().lowercase()] =
                            line.substring(separator + 1).trim()
                    }
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val bodyBytes = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val count = input.read(bodyBytes, offset, length - offset)
                    if (count < 0) break
                    offset += count
                }
                capturedHeaders.set(headers)
                capturedBody.set(String(bodyBytes, 0, offset, Charsets.UTF_8))
                val response = """{"candidates":[{"content":{"parts":[{"text":"[{\"questionTextEn\":\"Source question\"}]"}]}}]}"""
                    .toByteArray(Charsets.UTF_8)
                val output = socket.getOutputStream()
                output.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n")
                        .toByteArray(Charsets.US_ASCII)
                )
                output.write(response)
                output.flush()
            }
        }.apply { start() }

        try {
            val result = GeminiNativePdfClient.generate(
                context = context,
                pdfUri = Uri.fromFile(pdfFile),
                apiKey = "student-owned-test-key",
                prompt = "Extract only source questions",
                systemInstruction = "Universal source extraction policy",
                apiBaseUrl = "http://127.0.0.1:${server.localPort}"
            )

            assertTrue("Gemini response should be returned", result.isSuccess)
            assertTrue(result.getOrThrow().contains("Source question"))
            assertEquals("student-owned-test-key", capturedHeaders.get()["x-goog-api-key"])
            assertEquals("application/json", capturedHeaders.get()["content-type"])

            val request = JSONObject(capturedBody.get())
            assertEquals(
                "Universal source extraction policy",
                request.getJSONObject("systemInstruction")
                    .getJSONArray("parts").getJSONObject(0).getString("text")
            )
            val parts = request.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
            assertEquals("Extract only source questions", parts.getJSONObject(0).getString("text"))
            val inlinePdf = parts.getJSONObject(1).getJSONObject("inlineData")
            assertEquals("application/pdf", inlinePdf.getString("mimeType"))
            assertTrue(pdfBytes.contentEquals(Base64.decode(inlinePdf.getString("data"), Base64.DEFAULT)))
        } finally {
            server.close()
            serverThread.join(2_000)
            pdfFile.delete()
        }
    }
}
