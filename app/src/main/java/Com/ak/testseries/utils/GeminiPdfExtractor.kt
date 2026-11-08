package Com.ak.testseries.utils

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Deprecated legacy entry point.
 *
 * This API does not receive a verified ExamConfig and historically did not attach
 * the PDF bytes to the Gemini request. Its old fallback fabricated questions and
 * marked the test Ready. Keep the signature for source compatibility, but fail
 * closed until callers migrate to UniversalPdfPipeline with a real source payload
 * and exam configuration.
 */
object GeminiPdfExtractor {

    @Deprecated(
        message = "Use UniversalPdfPipeline with a verified ExamConfig and attached source PDF.",
        replaceWith = ReplaceWith("UniversalPdfPipeline.processPdfForExam(...)")
    )
    suspend fun processPdfWithGemini(
        pdfUrl: String,
        testId: String,
        apiKey: String,
        totalQuestionsExpected: Int
    ) = withContext(Dispatchers.IO) {
        val reason = "Legacy Gemini PDF extraction is disabled: this path lacks a verified exam configuration and does not submit the PDF source bytes. No questions were generated or saved. Migrate this test to UniversalPdfPipeline."
        try {
            FirebaseFirestore.getInstance()
                .collection("LiveTests")
                .document(testId)
                .update(
                    mapOf(
                        "processingStatus" to "Needs Review",
                        "processingError" to reason,
                        "questionCount" to 0
                    )
                )
                .await()
        } catch (e: Exception) {
            android.util.Log.e("GeminiPdfExtractor", reason, e)
        }
    }
}
