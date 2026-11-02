package com.ak.pdfextractor

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Result data wrapper for on-device ML Kit Text Recognition.
 */
data class MlKitOcrResult(
    val fullText: String,
    val textBlocks: List<TextBlock>
)

/**
 * MlKitLocalOcrEngine
 * 
 * Provides 100% On-Device, lightweight OCR using Google ML Kit's unbundled Latin Text Recognizer.
 * - Zero APK bloat (uses thin client connecting to Google Play Services on device).
 * - Offline execution: no network connectivity or API keys required.
 * - Extracts both plain text and layout-accurate TextBlocks for bounding box and option parsing.
 */
object MlKitLocalOcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Executes local OCR on a given page bitmap.
     * Returns structured TextBlock objects compatible with LocalQuestionClassifier.
     */
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap, pageNum: Int = 1): MlKitOcrResult =
        suspendCancellableCoroutine { cont ->
            try {
                if (bitmap.isRecycled) {
                    if (cont.isActive) cont.resume(MlKitOcrResult("", emptyList()))
                    return@suspendCancellableCoroutine
                }

                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val blocks = visionText.textBlocks.mapIndexed { idx, block ->
                            val bBox = block.boundingBox ?: Rect(0, 0, 0, 0)
                            val textLines = block.lines.map { line ->
                                val lBox = line.boundingBox ?: Rect(0, 0, 0, 0)
                                TextLine(
                                    text = line.text,
                                    bounds = BoundingBox(
                                        x = lBox.left.toFloat(),
                                        y = lBox.top.toFloat(),
                                        width = lBox.width().toFloat(),
                                        height = lBox.height().toFloat()
                                    )
                                )
                            }
                            TextBlock(
                                id = "ml_p${pageNum}_b$idx",
                                text = block.text,
                                bounds = BoundingBox(
                                    x = bBox.left.toFloat(),
                                    y = bBox.top.toFloat(),
                                    width = bBox.width().toFloat(),
                                    height = bBox.height().toFloat()
                                ),
                                lines = textLines,
                                readingOrderIndex = idx
                            )
                        }
                        if (cont.isActive) {
                            cont.resume(MlKitOcrResult(fullText = visionText.text, textBlocks = blocks))
                        }
                    }
                    .addOnFailureListener { exc ->
                        Log.w("MlKitLocalOcrEngine", "On-device ML Kit OCR notice: ${exc.message}")
                        if (cont.isActive) {
                            cont.resume(MlKitOcrResult(fullText = "", textBlocks = emptyList()))
                        }
                    }
            } catch (e: Throwable) {
                Log.w("MlKitLocalOcrEngine", "Exception initiating ML Kit OCR: ${e.message}")
                if (cont.isActive) {
                    cont.resume(MlKitOcrResult(fullText = "", textBlocks = emptyList()))
                }
            }
        }
}
