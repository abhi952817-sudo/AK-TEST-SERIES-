package Com.ak.testseries.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

object FirebaseAiService {
    private const val TAG = "FirebaseAiService"
    const val DEFAULT_MODEL = "gemini-3.8-flash"
    private const val REQUEST_TIMEOUT_MS = 60_000L

    private val defaultTextModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = DEFAULT_MODEL)
    }

    private val defaultJsonModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
    }

    private fun jsonModelWithSystemInstruction(systemInstruction: String): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
            systemInstruction = content { text(systemInstruction) }
        )
    }

    suspend fun generateText(prompt: String): String {
        return try {
            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                defaultTextModel.generateContent(prompt)
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Firebase AI returned an empty response.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI text generation timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI text generation failed", e)
            throw e
        }
    }

    suspend fun generateJson(prompt: String, systemInstruction: String? = null): String {
        return try {
            val model = if (!systemInstruction.isNullOrBlank()) {
                jsonModelWithSystemInstruction(systemInstruction)
            } else {
                defaultJsonModel
            }
            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                model.generateContent(prompt)
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Firebase AI returned an empty JSON response.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI JSON generation timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI JSON generation failed", e)
            throw e
        }
    }

    suspend fun generateFromPdfBytes(pdfBytes: ByteArray, prompt: String): String {
        return try {
            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                defaultJsonModel.generateContent(
                    content {
                        inlineData(pdfBytes, "application/pdf")
                        text(prompt)
                    }
                )
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Firebase AI returned an empty PDF extraction response.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI PDF extraction timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI PDF extraction failed", e)
            throw e
        }
    }
}
