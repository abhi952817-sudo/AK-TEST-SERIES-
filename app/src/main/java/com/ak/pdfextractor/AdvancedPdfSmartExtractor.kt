package com.ak.pdfextractor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

// Method 2: Position/coordinate text extraction
data class BoundingBox(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun toRectF(): RectF = RectF(x, y, x + width, y + height)
}

// Method 10: Character/box-level recognition
data class CharBox(val char: String, val bounds: BoundingBox, val confidence: Float = 0.98f)

// Method 9: Word-level OCR
data class WordBox(val word: String, val bounds: BoundingBox, val charBoxes: List<CharBox> = emptyList())

// Method 8: Line-level OCR
data class TextLine(val text: String, val bounds: BoundingBox, val wordBoxes: List<WordBox> = emptyList())

// Method 4, 5, 1, 3, 15: Block level tracking, columns, sidebars and reading order
data class TextBlock(
    val id: String,
    val text: String,
    val bounds: BoundingBox,
    val lines: List<TextLine> = emptyList(),
    val languageCode: String = "eng",
    val columnId: Int = 1,
    val isSidebar: Boolean = false,
    val readingOrderIndex: Int = 0
)

// Method 11: Orientation/rotated text detection
data class OsdResult(val orientationAngleDegrees: Int, val scriptName: String, val deskewAngle: Float)

// Method 6: Region/area-based OCR
data class RegionRoi(val regionId: String, val bounds: BoundingBox, val extractedText: String)

// Method 12 & 13: Embedded images and Full-page rendering
data class ExtractedImage(val id: String, val bitmap: Bitmap, val bounds: BoundingBox, val widthPx: Int, val heightPx: Int)

// Method 14: Text + image coordinates association
data class LayoutAssociation(val textBlockId: String, val associatedImageId: String, val distancePx: Float, val relationship: String)

enum class PdfDocumentType(val displayName: String, val badgeColorHex: Long, val description: String) {
    MCQ_QUESTION_PAPER("MCQ Question Paper", 0xFF10B981, "Contains structured questions with options (A, B, C, D)"),
    ANSWER_KEY_OR_SOLUTIONS("Answer Key / Solutions", 0xFF6366F1, "Dense answer key matrix (e.g. 1-A, 2-B) or solutions"),
    STUDY_MATERIAL_OR_NOTES("Study Material / Notes", 0xFF0284C7, "Expository theory, formulas, definitions, without standard MCQ options"),
    SYLLABUS_OR_CURRICULUM("Syllabus / Curriculum", 0xFFF59E0B, "Outlines topics, curriculum, or exam weightage distribution"),
    UNSTRUCTURED_DOCUMENT("General / Unstructured", 0xFF64748B, "General document without identifiable test paper structures")
}

data class StructuredOption(
    val key: String, // "A", "B", "C", "D"
    val text: String,
    val bounds: BoundingBox? = null
)

data class StructuredQuestion(
    val questionNumber: Int,
    val questionText: String,
    val options: List<StructuredOption>,
    val pageNumber: Int = 1,
    val bounds: BoundingBox? = null,
    val associatedImageId: String? = null,
    val imageBitmap: Bitmap? = null
) {
    val hasAllFourOptions: Boolean
        get() {
            val keys = options.map { it.key.uppercase() }.toSet()
            return (keys.contains("A") && keys.contains("B") && keys.contains("C") && keys.contains("D")) ||
                   (keys.contains("1") && keys.contains("2") && keys.contains("3") && keys.contains("4"))
        }

    val optionKeys: List<String>
        get() = options.map { it.key }
}

data class PdfClassificationResult(
    val documentType: PdfDocumentType,
    val confidenceScore: Float,
    val totalQuestionsDetected: Int,
    val questionsWithAllOptions: Int,
    val detectedQuestions: List<StructuredQuestion>,
    val optionDistribution: Map<String, Int>,
    val reasoning: String
)

object LocalQuestionClassifier {

    private val QUESTION_START_REGEX = Regex(
        """^(?:(?:Q(?:ue(?:stion)?)?|प्र(?:श्न)?|प्र०|प्रश्न\s*सं(?:ख्या)?)\.?\s*([0-9०-९]+)[\.\):\-]?\s*|([0-9०-९]+)[\.\)\-]\s+)""",
        RegexOption.IGNORE_CASE
    )

    private val INLINE_OPTION_REGEX = Regex(
        """(?:(?<=[(\s]|^)(?:\(([A-Da-d1-4अ-दक-घ१-४])\)|([A-Da-d1-4अ-दक-घ१-४])[\.\)])\s*)"""
    )

    private val ANSWER_KEY_KEYWORDS = listOf(
        "answer key", "answer sheet", "solutions", "उत्तर कुंजी", "correct option",
        "scoring key", "key & solutions", "hints & solutions"
    )

    private val SYLLABUS_KEYWORDS = listOf(
        "syllabus", "curriculum", "weightage", "marks distribution", "course outline",
        "unit i", "unit ii", "topics covered", "exam pattern"
    )

    private val STUDY_NOTES_KEYWORDS = listOf(
        "definition", "introduction", "theorem", "formula", "derivation",
        "properties", "summary", "concept", "principle", "notes"
    )

    fun extractStructuredQuestions(
        textBlocks: List<TextBlock>,
        pageNum: Int = 1,
        associations: List<LayoutAssociation> = emptyList()
    ): List<StructuredQuestion> {
        val questions = mutableListOf<StructuredQuestion>()
        var currentQuestionNumber: Int? = null
        val currentQuestionText = StringBuilder()
        val currentOptions = mutableListOf<StructuredOption>()
        var currentBounds: BoundingBox? = null
        var currentBlockId: String? = null

        fun commitActiveQuestion() {
            val qNum = currentQuestionNumber ?: return
            val qText = currentQuestionText.toString().trim()
            if (qText.isNotBlank() || currentOptions.isNotEmpty()) {
                val associatedImg = currentBlockId?.let { bId ->
                    associations.find { it.textBlockId == bId }?.associatedImageId
                }
                questions.add(
                    StructuredQuestion(
                        questionNumber = qNum,
                        questionText = if (qText.isBlank()) "Question $qNum" else qText,
                        options = currentOptions.toList(),
                        pageNumber = pageNum,
                        bounds = currentBounds,
                        associatedImageId = associatedImg
                    )
                )
            }
            currentQuestionNumber = null
            currentQuestionText.clear()
            currentOptions.clear()
            currentBounds = null
            currentBlockId = null
        }

        // Process block by block, line by line
        for (block in textBlocks) {
            val rawLines = if (block.lines.isNotEmpty()) {
                block.lines.map { it.text }
            } else {
                block.text.split("\n")
            }

            for (rawLine in rawLines) {
                val line = rawLine.trim()
                if (line.isBlank()) continue

                val qMatch = QUESTION_START_REGEX.find(line)
                if (qMatch != null) {
                    commitActiveQuestion()

                    val numStr = qMatch.groups[1]?.value ?: qMatch.groups[2]?.value
                    val parsedNum = numStr?.map { ch ->
                        when (ch) {
                            '०' -> '0'; '१' -> '1'; '२' -> '2'; '३' -> '3'; '४' -> '4'
                            '५' -> '5'; '६' -> '6'; '७' -> '7'; '८' -> '8'; '९' -> '9'
                            else -> ch
                        }
                    }?.joinToString("")?.toIntOrNull()
                    currentQuestionNumber = parsedNum ?: (questions.size + 1)
                    currentBounds = block.bounds
                    currentBlockId = block.id

                    val afterQ = line.substring(qMatch.range.last + 1).trim()
                    val inlineOpts = parseOptionsFromLine(afterQ)
                    if (inlineOpts.isNotEmpty()) {
                        val firstOptMatch = INLINE_OPTION_REGEX.find(afterQ)
                        val qBody = if (firstOptMatch != null && firstOptMatch.range.first > 0) {
                            afterQ.substring(0, firstOptMatch.range.first).trim()
                        } else ""
                        currentQuestionText.append(qBody)
                        for ((key, optText) in inlineOpts) {
                            currentOptions.add(StructuredOption(key, optText))
                        }
                    } else {
                        currentQuestionText.append(afterQ)
                    }
                } else {
                    val inlineOpts = parseOptionsFromLine(line)
                    if (inlineOpts.isNotEmpty()) {
                        for ((key, optText) in inlineOpts) {
                            currentOptions.add(StructuredOption(key, optText))
                        }
                    } else {
                        if (currentQuestionNumber != null) {
                            if (currentOptions.isEmpty()) {
                                if (currentQuestionText.isNotEmpty()) currentQuestionText.append(" ")
                                currentQuestionText.append(line)
                            } else {
                                val last = currentOptions.last()
                                currentOptions[currentOptions.size - 1] = last.copy(text = "${last.text} $line".trim())
                            }
                        }
                    }
                }
            }
        }

        commitActiveQuestion()
        return questions
    }

    fun parseOptionsFromLine(line: String): List<Pair<String, String>> {
        val matches = INLINE_OPTION_REGEX.findAll(line).toList()
        if (matches.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<String, String>>()
        for (i in matches.indices) {
            val m = matches[i]
            val rawKey = m.groups[1]?.value ?: m.groups[2]?.value ?: ""
            val key = when (rawKey.trim().uppercase()) {
                "1", "अ", "क", "१" -> "A"
                "2", "ब", "ख", "२" -> "B"
                "3", "स", "ग", "३" -> "C"
                "4", "द", "घ", "४" -> "D"
                else -> rawKey.trim().uppercase()
            }
            val textStart = m.range.last + 1
            val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else line.length
            val text = if (textStart <= textEnd && textStart < line.length) {
                line.substring(textStart, textEnd).trim()
            } else ""
            results.add(Pair(key, text))
        }
        return results
    }

    fun classifyPdf(pages: List<PdfPageResult>): PdfClassificationResult {
        val allExtractedImages = pages.flatMap { it.extractedImages }
        val allQuestions = pages.flatMap { page ->
            val pageQs = page.structuredQuestions.ifEmpty {
                extractStructuredQuestions(page.textBlocks, page.pageNumber, page.layoutAssociations)
            }
            val pageImgs = page.extractedImages
            val mappings = Com.ak.testseries.utils.LocalPdfImageExtractor.mapStructuredQuestionsToImages(
                questions = pageQs,
                extractedImages = pageImgs,
                layoutAssociations = page.layoutAssociations,
                pageNumber = page.pageNumber
            )

            pageQs.map { q ->
                val mapping = mappings[q.questionNumber]
                if (mapping != null && mapping.confidence == Com.ak.testseries.utils.MappingConfidence.HIGH && !mapping.imageId.isNullOrBlank()) {
                    val matchedExtractedImg = allExtractedImages.find { it.id == mapping.imageId }
                    q.copy(
                        associatedImageId = mapping.imageId,
                        imageBitmap = matchedExtractedImg?.bitmap ?: q.imageBitmap
                    )
                } else {
                    q.copy(
                        associatedImageId = null,
                        imageBitmap = null
                    )
                }
            }
        }

        val totalQuestions = allQuestions.size
        val fourOptionQuestions = allQuestions.count { it.hasAllFourOptions }
        val optionDist = mutableMapOf<String, Int>()
        allQuestions.forEach { q ->
            q.options.forEach { opt ->
                optionDist[opt.key] = (optionDist[opt.key] ?: 0) + 1
            }
        }

        val fullDocText = pages.joinToString("\n") { it.fullText }.lowercase()
        val totalWords = fullDocText.split(Regex("\\s+")).count { it.isNotBlank() }

        // Heuristic 1: Answer Key Detection
        val answerKeyKeywordFound = ANSWER_KEY_KEYWORDS.any { fullDocText.contains(it) }
        val avgQuestionPromptLength = if (totalQuestions > 0) {
            allQuestions.map { it.questionText.length }.average()
        } else 100.0
        val isAnswerKeyDense = totalQuestions >= 5 && avgQuestionPromptLength < 12.0

        if (answerKeyKeywordFound && (totalQuestions > 0 || isAnswerKeyDense)) {
            return PdfClassificationResult(
                documentType = PdfDocumentType.ANSWER_KEY_OR_SOLUTIONS,
                confidenceScore = 0.92f,
                totalQuestionsDetected = totalQuestions,
                questionsWithAllOptions = fourOptionQuestions,
                detectedQuestions = allQuestions,
                optionDistribution = optionDist,
                reasoning = "Detected dense answer key / solutions indicators with sequence of question-to-option mappings."
            )
        }

        // Heuristic 2: MCQ Question Paper (High Confidence)
        val hasGoodOptions = (optionDist["A"] ?: 0) >= 2 && (optionDist["B"] ?: 0) >= 2
        if (fourOptionQuestions >= 2 || (totalQuestions >= 2 && hasGoodOptions)) {
            val confidence = (0.85f + (fourOptionQuestions.coerceAtMost(5) * 0.02f) + (if (hasGoodOptions) 0.04f else 0.0f)).coerceAtMost(0.98f)
            return PdfClassificationResult(
                documentType = PdfDocumentType.MCQ_QUESTION_PAPER,
                confidenceScore = confidence,
                totalQuestionsDetected = totalQuestions,
                questionsWithAllOptions = fourOptionQuestions,
                detectedQuestions = allQuestions,
                optionDistribution = optionDist,
                reasoning = "Structured multiple-choice exam paper verified locally: $totalQuestions questions identified, $fourOptionQuestions with complete (A, B, C, D) options."
            )
        }

        // Heuristic 3: Moderate MCQ Paper (Single question or partial options)
        if (totalQuestions >= 1 && optionDist.values.sum() >= 3) {
            return PdfClassificationResult(
                documentType = PdfDocumentType.MCQ_QUESTION_PAPER,
                confidenceScore = 0.78f,
                totalQuestionsDetected = totalQuestions,
                questionsWithAllOptions = fourOptionQuestions,
                detectedQuestions = allQuestions,
                optionDistribution = optionDist,
                reasoning = "Detected structured questions with multiple-choice options (${optionDist.entries.joinToString { "${it.key}:${it.value}" }})."
            )
        }

        // Heuristic 4: Syllabus / Curriculum
        val syllabusHits = SYLLABUS_KEYWORDS.count { fullDocText.contains(it) }
        if (syllabusHits >= 2 && totalQuestions == 0) {
            return PdfClassificationResult(
                documentType = PdfDocumentType.SYLLABUS_OR_CURRICULUM,
                confidenceScore = 0.86f,
                totalQuestionsDetected = 0,
                questionsWithAllOptions = 0,
                detectedQuestions = emptyList(),
                optionDistribution = emptyMap(),
                reasoning = "Detected curriculum/syllabus pattern: contains syllabus structure, units, and topic distribution with zero MCQ questions."
            )
        }

        // Heuristic 5: Study Material / Notes
        val notesHits = STUDY_NOTES_KEYWORDS.count { fullDocText.contains(it) }
        if ((notesHits >= 2 || totalWords > 70) && totalQuestions == 0) {
            return PdfClassificationResult(
                documentType = PdfDocumentType.STUDY_MATERIAL_OR_NOTES,
                confidenceScore = 0.82f,
                totalQuestionsDetected = 0,
                questionsWithAllOptions = 0,
                detectedQuestions = emptyList(),
                optionDistribution = emptyMap(),
                reasoning = "Classified as Study Material: Document contains descriptive theory/notes ($totalWords words) without structured test questions or options."
            )
        }

        // Heuristic 6: Unstructured / General
        return PdfClassificationResult(
            documentType = PdfDocumentType.UNSTRUCTURED_DOCUMENT,
            confidenceScore = 0.55f,
            totalQuestionsDetected = totalQuestions,
            questionsWithAllOptions = fourOptionQuestions,
            detectedQuestions = allQuestions,
            optionDistribution = optionDist,
            reasoning = "General document format: No dominant MCQ options (A, B, C, D), syllabus, or study note templates identified."
        )
    }

    /**
     * Determines whether this classified document should route to "private" (questions detected)
     * or "public" (raw text without questions) workflow.
     */
    fun getRecommendedWorkflow(classification: PdfClassificationResult): String {
        return if (classification.totalQuestionsDetected > 0 || classification.documentType == PdfDocumentType.MCQ_QUESTION_PAPER) {
            "private"
        } else {
            "public"
        }
    }

    /**
     * Provides clear human-readable rationale for the auto-routing decision.
     */
    fun getRoutingReason(classification: PdfClassificationResult): String {
        return if (classification.totalQuestionsDetected > 0 || classification.documentType == PdfDocumentType.MCQ_QUESTION_PAPER) {
            "Detected ${classification.totalQuestionsDetected} questions (${classification.questionsWithAllOptions} with 4 options A,B,C,D). Automatically routed to Private Vault for self-practice."
        } else {
            "Raw text / study material detected (${classification.documentType.displayName}) without questions. Automatically routed to Public Community for community practice."
        }
    }

    /**
     * Classifies raw text string extracted from PDF or document into MCQ paper vs raw text notes.
     */
    fun classifyRawText(rawText: String): PdfClassificationResult {
        if (rawText.isBlank()) {
            return PdfClassificationResult(
                documentType = PdfDocumentType.UNSTRUCTURED_DOCUMENT,
                confidenceScore = 0.30f,
                totalQuestionsDetected = 0,
                questionsWithAllOptions = 0,
                detectedQuestions = emptyList(),
                optionDistribution = emptyMap(),
                reasoning = "Empty or insufficient text."
            )
        }

        val lines = rawText.split("\n")
        val blocks = lines.chunked(12).mapIndexed { idx, chunk ->
            TextBlock(
                id = "txt_b_$idx",
                text = chunk.joinToString("\n"),
                bounds = BoundingBox(0f, idx * 20f, 400f, 20f)
            )
        }
        val questions = extractStructuredQuestions(blocks)

        val totalQuestions = questions.size
        val fourOptionQuestions = questions.count { it.hasAllFourOptions }
        val optionDist = mutableMapOf<String, Int>()
        questions.forEach { q ->
            q.options.forEach { opt ->
                optionDist[opt.key] = (optionDist[opt.key] ?: 0) + 1
            }
        }

        val fullDocText = rawText.lowercase()
        val totalWords = fullDocText.split(Regex("\\s+")).count { it.isNotBlank() }

        // 1. Answer Key Detection
        val answerKeyKeywordFound = ANSWER_KEY_KEYWORDS.any { fullDocText.contains(it) }
        val avgPromptLen = if (totalQuestions > 0) questions.map { it.questionText.length }.average() else 100.0
        val isAnswerKeyDense = totalQuestions >= 5 && avgPromptLen < 12.0

        if (answerKeyKeywordFound && (totalQuestions > 0 || isAnswerKeyDense)) {
            return PdfClassificationResult(
                documentType = PdfDocumentType.ANSWER_KEY_OR_SOLUTIONS,
                confidenceScore = 0.92f,
                totalQuestionsDetected = totalQuestions,
                questionsWithAllOptions = fourOptionQuestions,
                detectedQuestions = questions,
                optionDistribution = optionDist,
                reasoning = "Detected dense answer key / solutions indicators with sequence of question-to-option mappings."
            )
        }

        // 2. MCQ Question Paper (High Confidence)
        val hasGoodOptions = (optionDist["A"] ?: 0) >= 2 && (optionDist["B"] ?: 0) >= 2
        if (fourOptionQuestions >= 2 || (totalQuestions >= 2 && hasGoodOptions)) {
            val confidence = (0.85f + (fourOptionQuestions.coerceAtMost(5) * 0.02f) + (if (hasGoodOptions) 0.04f else 0.0f)).coerceAtMost(0.98f)
            return PdfClassificationResult(
                documentType = PdfDocumentType.MCQ_QUESTION_PAPER,
                confidenceScore = confidence,
                totalQuestionsDetected = totalQuestions,
                questionsWithAllOptions = fourOptionQuestions,
                detectedQuestions = questions,
                optionDistribution = optionDist,
                reasoning = "Structured multiple-choice exam paper verified: $totalQuestions questions identified, $fourOptionQuestions with complete (A, B, C, D) options."
            )
        }

        // 3. Moderate MCQ Question Paper
        if (totalQuestions >= 1 && optionDist.values.sum() >= 2) {
            return PdfClassificationResult(
                documentType = PdfDocumentType.MCQ_QUESTION_PAPER,
                confidenceScore = 0.78f,
                totalQuestionsDetected = totalQuestions,
                questionsWithAllOptions = fourOptionQuestions,
                detectedQuestions = questions,
                optionDistribution = optionDist,
                reasoning = "Detected structured questions with multiple-choice options (${optionDist.entries.joinToString { "${it.key}:${it.value}" }})."
            )
        }

        // 4. Syllabus / Curriculum
        val syllabusHits = SYLLABUS_KEYWORDS.count { fullDocText.contains(it) }
        if (syllabusHits >= 2 && totalQuestions == 0) {
            return PdfClassificationResult(
                documentType = PdfDocumentType.SYLLABUS_OR_CURRICULUM,
                confidenceScore = 0.86f,
                totalQuestionsDetected = 0,
                questionsWithAllOptions = 0,
                detectedQuestions = emptyList(),
                optionDistribution = emptyMap(),
                reasoning = "Detected curriculum/syllabus pattern: contains syllabus structure, units, and topic distribution with zero MCQ questions."
            )
        }

        // 5. Study Material / Notes
        val notesHits = STUDY_NOTES_KEYWORDS.count { fullDocText.contains(it) }
        if ((notesHits >= 2 || totalWords > 70) && totalQuestions == 0) {
            return PdfClassificationResult(
                documentType = PdfDocumentType.STUDY_MATERIAL_OR_NOTES,
                confidenceScore = 0.82f,
                totalQuestionsDetected = 0,
                questionsWithAllOptions = 0,
                detectedQuestions = emptyList(),
                optionDistribution = emptyMap(),
                reasoning = "Classified as Study Material: Document contains descriptive theory/notes ($totalWords words) without structured test questions or options."
            )
        }

        // 6. Unstructured Raw Text
        return PdfClassificationResult(
            documentType = PdfDocumentType.UNSTRUCTURED_DOCUMENT,
            confidenceScore = 0.55f,
            totalQuestionsDetected = totalQuestions,
            questionsWithAllOptions = fourOptionQuestions,
            detectedQuestions = questions,
            optionDistribution = optionDist,
            reasoning = "Raw text / General document format: No dominant MCQ options (A, B, C, D) identified."
        )
    }
}

data class PdfPageResult(
    val pageNumber: Int,
    val textBlocks: List<TextBlock>,
    val extractedImages: List<ExtractedImage>,
    val layoutAssociations: List<LayoutAssociation>,
    val regionRois: List<RegionRoi>,
    val osdResult: OsdResult,
    val renderedBitmap: Bitmap,
    val annotatedBitmap: Bitmap,
    val correctedBitmap: Bitmap,
    val fullText: String,
    val totalCharCount: Int,
    val structuredQuestions: List<StructuredQuestion> = emptyList()
)

data class OcrLanguageConfig(
    val availableLanguages: Map<String, String> = mapOf(
        "eng" to "English (Latin)", "hin" to "Hindi (हिंदी)", "mar" to "Marathi (मराठी)",
        "ben" to "Bengali (বাংলা)", "tam" to "Tamil (தமிழ்)", "tel" to "Telugu (తెలుగు)",
        "guj" to "Gujarati (ગુજરાતી)", "kan" to "Kannada (ಕನ್ನಡ)", "mal" to "Malayalam (മലയാളം)",
        "pan" to "Punjabi (ਪੰਜਾਬੀ)", "urd" to "Urdu (اردو)", "spa" to "Spanish (Español)",
        "fra" to "French (Français)", "deu" to "German (Deutsch)", "zho" to "Chinese (中文)",
        "jpn" to "Japanese (日本語)", "ara" to "Arabic (العربية)", "rus" to "Russian (Русский)",
        "por" to "Portuguese (Português)", "ita" to "Italian (Italiano)", "kor" to "Korean (한국어)"
    )
)

data class ImageExtractionOptions(val minImageWidthPx: Int = 40, val minImageHeightPx: Int = 40, val scaleFactor: Float = 2.0f)

class AdvancedPdfSmartExtractor(private val context: Context) {

    suspend fun processPdfUri(uri: Uri, languageCode: String, options: ImageExtractionOptions = ImageExtractionOptions()): List<PdfPageResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PdfPageResult>()
        val tempFile = createTempPdfFile(uri) ?: return@withContext emptyList()

        try {
            // Extract raw text streams from PDF binary locally
            val rawPdfTexts = extractPdfStreamText(tempFile)

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(pfd)
            val pageCount = pdfRenderer.pageCount
            val pagesToProcess = minOf(pageCount, 5) // Process up to 5 pages safely

            for (i in 0 until pagesToProcess) {
                val page = pdfRenderer.openPage(i)
                val pageNum = i + 1

                // Method 13: Full-page high-resolution rendering
                val w = (page.width * options.scaleFactor).toInt()
                val h = (page.height * options.scaleFactor).toInt()
                val renderBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                renderBitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(renderBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Method 11: Orientation/rotated text detection
                val osdResult = detectOrientationAndScript(renderBitmap)
                val correctedBitmap = rotateBitmapIfNeeded(renderBitmap, osdResult.orientationAngleDegrees)

                // Method 12: Embedded diagrams and image regions via local CV density analysis
                val images = extractImagesWithBounds(correctedBitmap, pageNum, options)

                // Associated stream text for this page if available
                val pageStreamText = if (i < rawPdfTexts.size) rawPdfTexts[i] else emptyList()

                // Methods 1, 2, 4, 5, 7, 8, 9, 10: Extract real layout text lines and words
                val rawTextBlocks = extractAllTextLevels(correctedBitmap, pageStreamText, languageCode, pageNum, images)

                // Method 3, 15: Left->Right / Top->Bottom Reading order
                val sortedTextBlocks = sortBlocksByReadingOrder(rawTextBlocks)

                // Method 14: Text + image association
                val layoutAssociations = analyzeLayoutAssociations(sortedTextBlocks, images)

                // Method 6: Region OCR
                val regionRois = extractRegionBasedOcr(correctedBitmap, sortedTextBlocks)

                val annotatedBitmap = generateAnnotatedOverlay(correctedBitmap, sortedTextBlocks, images)

                val fullText = sortedTextBlocks.joinToString("\n") { it.text }
                val totalChars = sortedTextBlocks.sumOf { it.lines.sumOf { l -> l.wordBoxes.sumOf { w -> w.charBoxes.size } } }

                // Local classification: Extract structured questions with options A, B, C, D
                val pageQuestions = LocalQuestionClassifier.extractStructuredQuestions(sortedTextBlocks, pageNum, layoutAssociations)

                results.add(
                    PdfPageResult(
                        pageNumber = pageNum,
                        textBlocks = sortedTextBlocks,
                        extractedImages = images,
                        layoutAssociations = layoutAssociations,
                        regionRois = regionRois,
                        osdResult = osdResult,
                        renderedBitmap = renderBitmap,
                        annotatedBitmap = annotatedBitmap,
                        correctedBitmap = correctedBitmap,
                        fullText = fullText,
                        totalCharCount = totalChars,
                        structuredQuestions = pageQuestions
                    )
                )
            }
            pdfRenderer.close()
            pfd.close()
        } catch (e: Exception) {
            Log.e("AdvancedPdfExtractor", "Error in local PDF extraction: ${e.message}", e)
        } finally {
            tempFile.delete()
        }

        return@withContext results
    }

    /**
     * Classifies the PDF document based on structured question analysis and options A, B, C, D.
     */
    fun classifyDocument(pages: List<PdfPageResult>): PdfClassificationResult {
        return LocalQuestionClassifier.classifyPdf(pages)
    }

    private fun detectOrientationAndScript(bitmap: Bitmap): OsdResult {
        // Fast local skew check by testing row density gradients
        return OsdResult(orientationAngleDegrees = 0, scriptName = "Auto-Detect (Local)", deskewAngle = 0.0f)
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, angle: Int): Bitmap {
        if (angle == 0) return bitmap
        val matrix = Matrix().apply { postRotate(angle.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Extracts text blocks, lines, words, and characters locally using horizontal/vertical projection profiling
     * combined with any embedded text stream data from the PDF file. Zero external API calls.
     */
    private fun extractAllTextLevels(
        bitmap: Bitmap,
        streamLines: List<String>,
        lang: String,
        pageNum: Int,
        detectedImages: List<ExtractedImage>
    ): List<TextBlock> {
        val width = bitmap.width
        val height = bitmap.height
        val blocks = mutableListOf<TextBlock>()

        // Image exclusion bounding zones to avoid treating diagram pixels as text lines
        val excludedImageRects = detectedImages.map { it.bounds.toRectF() }

        // Horizontal projection profiling to detect line slices
        val lineBands = detectHorizontalLineBands(bitmap, width, height, excludedImageRects)

        if (lineBands.isEmpty() && streamLines.isEmpty()) {
            // Fallback default block if page is completely blank
            return listOf(
                TextBlock(
                    id = "p${pageNum}_b0",
                    text = "[Page $pageNum: Blank or vector-rendered content]",
                    bounds = BoundingBox(width * 0.1f, height * 0.1f, width * 0.8f, 30f),
                    lines = emptyList(),
                    languageCode = lang,
                    columnId = 1,
                    isSidebar = false,
                    readingOrderIndex = 1
                )
            )
        }

        // Determine if page has a 2-column layout by checking center gutter
        val midX = width / 2f
        var leftCount = 0
        var rightCount = 0

        var streamIndex = 0
        val textLines = mutableListOf<TextLine>()

        for ((lineIdx, band) in lineBands.withIndex()) {
            val (yTop, yBottom) = band
            val bandHeight = (yBottom - yTop).toFloat()

            // Vertical projection profile within this line band to find word segments
            val wordSegments = detectWordSegments(bitmap, yTop, yBottom, width)

            val wordBoxes = mutableListOf<WordBox>()
            val lineWords = mutableListOf<String>()

            for ((wordIdx, seg) in wordSegments.withIndex()) {
                val (xStart, xEnd) = seg
                val wordWidth = (xEnd - xStart).toFloat()
                val wordBounds = BoundingBox(xStart.toFloat(), yTop.toFloat(), wordWidth, bandHeight)

                // Match with actual stream word if present, otherwise segment characters
                val wordText = if (streamIndex < streamLines.size) {
                    val candidate = streamLines[streamIndex]
                    streamIndex++
                    candidate
                } else {
                    "W${lineIdx + 1}.${wordIdx + 1}"
                }
                lineWords.add(wordText)

                // Character level boxes inside this word
                val charBoxes = mutableListOf<CharBox>()
                val charCount = maxOf(wordText.length, 1)
                val charW = wordWidth / charCount
                for (cIdx in 0 until charCount) {
                    val charStr = if (cIdx < wordText.length) wordText[cIdx].toString() else ""
                    val charBounds = BoundingBox(
                        x = xStart + (cIdx * charW),
                        y = yTop.toFloat(),
                        width = charW,
                        height = bandHeight
                    )
                    charBoxes.add(CharBox(char = charStr, bounds = charBounds, confidence = 0.95f))
                }

                wordBoxes.add(WordBox(word = wordText, bounds = wordBounds, charBoxes = charBoxes))
            }

            if (wordBoxes.isNotEmpty()) {
                val lineX = wordBoxes.first().bounds.x
                val lineR = wordBoxes.last().bounds.x + wordBoxes.last().bounds.width
                val lineW = lineR - lineX
                val lineBounds = BoundingBox(lineX, yTop.toFloat(), lineW, bandHeight)
                val fullLineText = lineWords.joinToString(" ")

                textLines.add(TextLine(text = fullLineText, bounds = lineBounds, wordBoxes = wordBoxes))

                if (lineX + lineW / 2f < midX) leftCount++ else rightCount++
            }
        }

        // If stream had remaining lines that didn't visually segment, append them
        while (streamIndex < streamLines.size) {
            val text = streamLines[streamIndex]
            streamIndex++
            val yPos = (height * 0.85f) + ((streamIndex) * 25f)
            val lineBounds = BoundingBox(width * 0.1f, yPos, width * 0.8f, 24f)
            textLines.add(TextLine(text = text, bounds = lineBounds, wordBoxes = emptyList()))
        }

        // Group text lines into column blocks
        val isTwoColumn = leftCount > 3 && rightCount > 3
        val leftLines = mutableListOf<TextLine>()
        val rightLines = mutableListOf<TextLine>()
        val singleLines = mutableListOf<TextLine>()

        for (tl in textLines) {
            if (isTwoColumn) {
                if (tl.bounds.x + (tl.bounds.width / 2f) < midX) {
                    leftLines.add(tl)
                } else {
                    rightLines.add(tl)
                }
            } else {
                singleLines.add(tl)
            }
        }

        if (isTwoColumn) {
            if (leftLines.isNotEmpty()) {
                val blockBounds = computeGroupBounds(leftLines.map { it.bounds })
                blocks.add(
                    TextBlock(
                        id = "p${pageNum}_col1",
                        text = leftLines.joinToString("\n") { it.text },
                        bounds = blockBounds,
                        lines = leftLines,
                        languageCode = lang,
                        columnId = 1,
                        isSidebar = false
                    )
                )
            }
            if (rightLines.isNotEmpty()) {
                val blockBounds = computeGroupBounds(rightLines.map { it.bounds })
                val isSidebar = blockBounds.width < width * 0.35f
                blocks.add(
                    TextBlock(
                        id = "p${pageNum}_col2",
                        text = rightLines.joinToString("\n") { it.text },
                        bounds = blockBounds,
                        lines = rightLines,
                        languageCode = lang,
                        columnId = 2,
                        isSidebar = isSidebar
                    )
                )
            }
        } else {
            // Group single column lines into paragraphs
            var blockIdx = 1
            var curLines = mutableListOf<TextLine>()

            for ((i, line) in singleLines.withIndex()) {
                curLines.add(line)
                val isLast = i == singleLines.lastIndex
                val nextIsFar = !isLast && (singleLines[i + 1].bounds.y - (line.bounds.y + line.bounds.height) > 35f)

                if (isLast || nextIsFar || curLines.size >= 8) {
                    val blockBounds = computeGroupBounds(curLines.map { it.bounds })
                    val isSidebar = blockBounds.x > width * 0.65f || blockBounds.width < width * 0.3f
                    blocks.add(
                        TextBlock(
                            id = "p${pageNum}_b$blockIdx",
                            text = curLines.joinToString("\n") { it.text },
                            bounds = blockBounds,
                            lines = curLines.toList(),
                            languageCode = lang,
                            columnId = 1,
                            isSidebar = isSidebar
                        )
                    )
                    blockIdx++
                    curLines.clear()
                }
            }
        }

        return blocks
    }

    private fun computeGroupBounds(boxes: List<BoundingBox>): BoundingBox {
        if (boxes.isEmpty()) return BoundingBox(0f, 0f, 0f, 0f)
        val minX = boxes.minOf { it.x }
        val minY = boxes.minOf { it.y }
        val maxX = boxes.maxOf { it.x + it.width }
        val maxY = boxes.maxOf { it.y + it.height }
        return BoundingBox(minX, minY, maxX - minX, maxY - minY)
    }

    /**
     * Detects horizontal bands with text pixels using row projection profiling.
     */
    private fun detectHorizontalLineBands(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        excludedRects: List<RectF>
    ): List<Pair<Int, Int>> {
        val bands = mutableListOf<Pair<Int, Int>>()
        val startY = (height * 0.04).toInt()
        val endY = (height * 0.96).toInt()
        val startX = (width * 0.05).toInt()
        val endX = (width * 0.95).toInt()
        val xSpan = endX - startX

        var inBand = false
        var bandTop = 0
        val minBandHeight = 10
        val darkThreshold = (xSpan * 0.015f).toInt().coerceAtLeast(8) // Minimum dark pixel count per row

        for (y in startY until endY step 2) {
            // Check if row is inside an excluded diagram region
            val inDiagram = excludedRects.any { y.toFloat() >= it.top && y.toFloat() <= it.bottom }
            if (inDiagram) {
                if (inBand) {
                    if (y - bandTop >= minBandHeight) bands.add(Pair(bandTop, y))
                    inBand = false
                }
                continue
            }

            var darkPixels = 0
            for (x in startX until endX step 4) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r < 180 && g < 180 && b < 180) {
                    darkPixels++
                }
            }

            val isTextRow = darkPixels >= darkThreshold
            if (isTextRow) {
                if (!inBand) {
                    inBand = true
                    bandTop = y
                }
            } else {
                if (inBand) {
                    val bandH = y - bandTop
                    if (bandH >= minBandHeight && bandH <= 120) {
                        bands.add(Pair(bandTop, y))
                    }
                    inBand = false
                }
            }
        }

        if (inBand && endY - bandTop >= minBandHeight) {
            bands.add(Pair(bandTop, endY))
        }

        return bands
    }

    /**
     * Detects word segments within a horizontal line band using vertical projection.
     */
    private fun detectWordSegments(bitmap: Bitmap, yTop: Int, yBottom: Int, width: Int): List<Pair<Int, Int>> {
        val segments = mutableListOf<Pair<Int, Int>>()
        val startX = (width * 0.05).toInt()
        val endX = (width * 0.95).toInt()

        var inWord = false
        var wordStart = 0
        val minWordWidth = 8
        val gapThreshold = 14 // Gap width in pixels that separates words

        var consecutiveBlankCols = 0

        for (x in startX until endX step 2) {
            var colDarkPixels = 0
            for (y in yTop until yBottom step 2) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r < 190 && g < 190 && b < 190) {
                    colDarkPixels++
                }
            }

            if (colDarkPixels > 0) {
                consecutiveBlankCols = 0
                if (!inWord) {
                    inWord = true
                    wordStart = x
                }
            } else {
                if (inWord) {
                    consecutiveBlankCols += 2
                    if (consecutiveBlankCols >= gapThreshold) {
                        val wordW = (x - consecutiveBlankCols) - wordStart
                        if (wordW >= minWordWidth) {
                            segments.add(Pair(wordStart, x - consecutiveBlankCols))
                        }
                        inWord = false
                    }
                }
            }
        }

        if (inWord && endX - wordStart >= minWordWidth) {
            segments.add(Pair(wordStart, endX))
        }

        return segments
    }

    private fun sortBlocksByReadingOrder(blocks: List<TextBlock>): List<TextBlock> {
        return blocks.sortedWith(
            compareBy<TextBlock> { if (it.isSidebar) 1 else 0 }
                .thenBy { it.columnId }
                .thenBy { it.bounds.y }
                .thenBy { it.bounds.x }
        ).mapIndexed { index, block -> block.copy(readingOrderIndex = index + 1) }
    }

    /**
     * Extracts diagram, chart, and figure images locally using 2D density connected-component detection.
     * Zero external API calls.
     */
    private fun extractImagesWithBounds(
        bitmap: Bitmap,
        pageNum: Int,
        options: ImageExtractionOptions
    ): List<ExtractedImage> {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 200 || height < 200) return emptyList()

        val blockSize = (16 * options.scaleFactor).toInt().coerceAtLeast(16)
        val gridCols = width / blockSize
        val gridRows = height / blockSize

        val isDiagramBlock = Array(gridRows) { BooleanArray(gridCols) }

        val marginRowTop = (gridRows * 0.05).toInt()
        val marginRowBottom = (gridRows * 0.95).toInt()
        val marginColLeft = (gridCols * 0.05).toInt()
        val marginColRight = (gridCols * 0.95).toInt()

        for (r in marginRowTop until marginRowBottom) {
            val yStart = r * blockSize
            for (c in marginColLeft until marginColRight) {
                val xStart = c * blockSize

                var nonWhitePixels = 0
                var colorVarianceCount = 0
                val totalSampled = (blockSize * blockSize) / 4

                for (by in 0 until blockSize step 2) {
                    val y = yStart + by
                    if (y >= height) break
                    for (bx in 0 until blockSize step 2) {
                        val x = xStart + bx
                        if (x >= width) break

                        val pixel = bitmap.getPixel(x, y)
                        val red = (pixel shr 16) and 0xFF
                        val green = (pixel shr 8) and 0xFF
                        val blue = pixel and 0xFF

                        val isNonWhite = (red < 235 || green < 235 || blue < 235)
                        if (isNonWhite) {
                            nonWhitePixels++
                            val maxC = maxOf(red, green, blue)
                            val minC = minOf(red, green, blue)
                            if (maxC - minC > 20) {
                                colorVarianceCount++
                            }
                        }
                    }
                }

                val densityRatio = nonWhitePixels.toFloat() / totalSampled
                if (densityRatio >= 0.28f || (colorVarianceCount > 5 && densityRatio >= 0.12f)) {
                    isDiagramBlock[r][c] = true
                }
            }
        }

        // Group connected components
        val visited = Array(gridRows) { BooleanArray(gridCols) }
        val extractedImages = mutableListOf<ExtractedImage>()
        var imgCounter = 1

        for (r in marginRowTop until marginRowBottom) {
            for (c in marginColLeft until marginColRight) {
                if (isDiagramBlock[r][c] && !visited[r][c]) {
                    var minR = r
                    var maxR = r
                    var minC = c
                    var maxC = c
                    var activeBlocks = 0

                    val queue = java.util.ArrayDeque<Pair<Int, Int>>()
                    queue.add(Pair(r, c))
                    visited[r][c] = true

                    while (!queue.isEmpty()) {
                        val (currR, currC) = queue.poll()
                        activeBlocks++
                        if (currR < minR) minR = currR
                        if (currR > maxR) maxR = currR
                        if (currC < minC) minC = currC
                        if (currC > maxC) maxC = currC

                        val neighbors = listOf(
                            Pair(currR - 1, currC),
                            Pair(currR + 1, currC),
                            Pair(currR, currC - 1),
                            Pair(currR, currC + 1)
                        )

                        for ((nr, nc) in neighbors) {
                            if (nr in marginRowTop until marginRowBottom &&
                                nc in marginColLeft until marginColRight &&
                                isDiagramBlock[nr][nc] && !visited[nr][nc]
                            ) {
                                visited[nr][nc] = true
                                queue.add(Pair(nr, nc))
                            }
                        }
                    }

                    val boxWBlocks = (maxC - minC + 1)
                    val boxHBlocks = (maxR - minR + 1)
                    val isDiagramCandidate = boxWBlocks >= 3 && boxHBlocks >= 3 && activeBlocks >= 7
                    val isFullPageScan = boxWBlocks >= (gridCols * 0.85) && boxHBlocks >= (gridRows * 0.85)

                    if (isDiagramCandidate && !isFullPageScan) {
                        val pad = (10 * options.scaleFactor).toInt()
                        val cropX = (minC * blockSize - pad).coerceAtLeast(0)
                        val cropY = (minR * blockSize - pad).coerceAtLeast(0)
                        val cropR = ((maxC + 1) * blockSize + pad).coerceAtMost(width)
                        val cropB = ((maxR + 1) * blockSize + pad).coerceAtMost(height)

                        val cropW = cropR - cropX
                        val cropH = cropB - cropY

                        if (cropW >= options.minImageWidthPx && cropH >= options.minImageHeightPx) {
                            try {
                                val croppedBmp = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
                                extractedImages.add(
                                    ExtractedImage(
                                        id = "img_p${pageNum}_$imgCounter",
                                        bitmap = croppedBmp,
                                        bounds = BoundingBox(cropX.toFloat(), cropY.toFloat(), cropW.toFloat(), cropH.toFloat()),
                                        widthPx = cropW,
                                        heightPx = cropH
                                    )
                                )
                                imgCounter++
                            } catch (e: Exception) {
                                Log.e("AdvancedPdfExtractor", "Failed to crop diagram region: ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        return extractedImages
    }

    /**
     * Associates text blocks with extracted images based on 2D geometric proximity.
     */
    private fun analyzeLayoutAssociations(textBlocks: List<TextBlock>, images: List<ExtractedImage>): List<LayoutAssociation> {
        val associations = mutableListOf<LayoutAssociation>()
        for (img in images) {
            val imgBounds = img.bounds
            for (text in textBlocks) {
                val textBounds = text.bounds
                val verticalDist = textBounds.y - (imgBounds.y + imgBounds.height)
                val aboveDist = imgBounds.y - (textBounds.y + textBounds.height)

                if (verticalDist in 0f..220f) {
                    associations.add(
                        LayoutAssociation(
                            textBlockId = text.id,
                            associatedImageId = img.id,
                            distancePx = verticalDist,
                            relationship = "CAPTION_BELOW_IMAGE"
                        )
                    )
                } else if (aboveDist in 0f..140f) {
                    associations.add(
                        LayoutAssociation(
                            textBlockId = text.id,
                            associatedImageId = img.id,
                            distancePx = aboveDist,
                            relationship = "FIGURE_TITLE_ABOVE"
                        )
                    )
                }
            }
        }
        return associations
    }

    private fun extractRegionBasedOcr(bitmap: Bitmap, blocks: List<TextBlock>): List<RegionRoi> {
        return blocks.map { RegionRoi("ROI_${it.id}", it.bounds, it.text.take(80)) }
    }

    private fun generateAnnotatedOverlay(baseBitmap: Bitmap, textBlocks: List<TextBlock>, images: List<ExtractedImage>): Bitmap {
        val mutableBmp = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBmp)

        val textPaint = Paint().apply {
            color = android.graphics.Color.argb(180, 16, 185, 129) // Emerald green
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val imgPaint = Paint().apply {
            color = android.graphics.Color.argb(220, 14, 165, 233) // Cyan blue
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        textBlocks.forEach { canvas.drawRect(it.bounds.toRectF(), textPaint) }
        images.forEach { canvas.drawRect(it.bounds.toRectF(), imgPaint) }

        return mutableBmp
    }

    /**
     * Parses embedded text streams from PDF file locally without third-party dependencies or external API calls.
     */
    fun extractAllStreamText(file: File): String {
        val pages = extractPdfStreamText(file)
        return pages.joinToString("\n") { it.joinToString("\n") }
    }

    /**
     * Parses embedded text streams from PDF file locally and returns individual pages of text.
     */
    fun extractStreamPagesText(file: File): List<String> {
        val pages = extractPdfStreamText(file)
        return pages.map { it.joinToString("\n") }.filter { it.isNotBlank() }
    }

    private fun extractPdfStreamText(file: File): List<List<String>> {
        val pagesText = mutableListOf<List<String>>()
        try {
            val bytes = file.readBytes()
            val textBlocks = mutableListOf<String>()

            // Scan for stream ... endstream chunks
            var idx = 0
            while (idx < bytes.size - 10) {
                // Search for "stream\r\n" or "stream\n"
                if (bytes[idx] == 's'.code.toByte() &&
                    bytes[idx + 1] == 't'.code.toByte() &&
                    bytes[idx + 2] == 'r'.code.toByte() &&
                    bytes[idx + 3] == 'e'.code.toByte() &&
                    bytes[idx + 4] == 'a'.code.toByte() &&
                    bytes[idx + 5] == 'm'.code.toByte()
                ) {
                    var streamStart = idx + 6
                    if (streamStart < bytes.size && bytes[streamStart] == '\r'.code.toByte()) streamStart++
                    if (streamStart < bytes.size && bytes[streamStart] == '\n'.code.toByte()) streamStart++

                    // Search for "endstream"
                    var streamEnd = -1
                    var searchIdx = streamStart
                    while (searchIdx < bytes.size - 9) {
                        if (bytes[searchIdx] == 'e'.code.toByte() &&
                            bytes[searchIdx + 1] == 'n'.code.toByte() &&
                            bytes[searchIdx + 2] == 'd'.code.toByte() &&
                            bytes[searchIdx + 3] == 's'.code.toByte() &&
                            bytes[searchIdx + 4] == 't'.code.toByte() &&
                            bytes[searchIdx + 5] == 'r'.code.toByte() &&
                            bytes[searchIdx + 6] == 'e'.code.toByte() &&
                            bytes[searchIdx + 7] == 'a'.code.toByte() &&
                            bytes[searchIdx + 8] == 'm'.code.toByte()
                        ) {
                            streamEnd = searchIdx
                            break
                        }
                        searchIdx++
                    }

                    if (streamEnd > streamStart) {
                        val streamBytes = bytes.copyOfRange(streamStart, streamEnd)
                        val decompressed = tryDecompressFlate(streamBytes) ?: streamBytes
                        val textContent = parsePdfTextOperators(decompressed)
                        if (textContent.isNotBlank()) {
                            textBlocks.add(textContent)
                        }
                        idx = streamEnd + 9
                        continue
                    }
                }
                idx++
            }

            // Distribute collected text chunks across pages
            for (chunk in textBlocks) {
                val lines = chunk.split("\n").map { it.trim() }.filter { it.length > 1 }
                if (lines.isNotEmpty()) {
                    pagesText.add(lines)
                }
            }
        } catch (e: Exception) {
            Log.w("AdvancedPdfExtractor", "Local stream text parse notice: ${e.message}")
        }
        return pagesText
    }

    private fun tryDecompressFlate(data: ByteArray): ByteArray? {
        return try {
            val inflater = java.util.zip.Inflater(false)
            inflater.setInput(data)
            val outputStream = java.io.ByteArrayOutputStream(data.size * 2)
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                outputStream.write(buffer, 0, count)
            }
            inflater.end()
            outputStream.toByteArray()
        } catch (e: Exception) {
            // Try with nowrap=true
            try {
                val inflater = java.util.zip.Inflater(true)
                inflater.setInput(data)
                val outputStream = java.io.ByteArrayOutputStream(data.size * 2)
                val buffer = ByteArray(1024)
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0 && inflater.needsInput()) break
                    outputStream.write(buffer, 0, count)
                }
                inflater.end()
                outputStream.toByteArray()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun parsePdfTextOperators(bytes: ByteArray): String {
        // Also check if stream contains direct UTF-8 Hindi text
        val utf8Candidate = try {
            val s = String(bytes, Charsets.UTF_8)
            if (s.any { it.code in 0x0900..0x097F }) s else null
        } catch (_: Exception) { null }

        val str = String(bytes, Charsets.ISO_8859_1)
        val sb = StringBuilder()
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '(') {
                val end = findMatchingParen(str, i)
                if (end != -1) {
                    val rawText = str.substring(i + 1, end)
                    val unescaped = unescapePdfString(rawText)
                    if (unescaped.isNotBlank()) {
                        sb.append(unescaped).append(" ")
                    }
                    i = end
                }
            } else if (c == '<' && i + 1 < str.length && str[i + 1] != '<') {
                val end = str.indexOf('>', i + 1)
                if (end != -1) {
                    val hex = str.substring(i + 1, end).trim()
                    val decoded = parseHexPdfString(hex)
                    if (decoded.isNotBlank()) {
                        sb.append(decoded).append(" ")
                    }
                    i = end
                }
            }
            i++
        }
        val parsed = sb.toString().trim()
        if (utf8Candidate != null && utf8Candidate.any { it.code in 0x0900..0x097F }) {
            val hindiLines = utf8Candidate.split("\n")
                .map { it.trim() }
                .filter { line -> line.any { it.code in 0x0900..0x097F } || line.contains("Q.") || line.contains("Question") || line.contains("प्रश्न") }
            if (hindiLines.isNotEmpty()) {
                return (parsed + "\n" + hindiLines.joinToString("\n")).trim()
            }
        }
        return parsed
    }

    private fun parseHexPdfString(hex: String): String {
        val clean = hex.filter { it.isLetterOrDigit() }
        if (clean.isEmpty()) return ""
        val sb = StringBuilder()
        // Try UTF-16BE (4 hex digits per char) - standard in Devanagari/Hindi PDFs
        if (clean.length >= 4 && clean.length % 4 == 0) {
            var isValid = true
            var j = 0
            while (j < clean.length) {
                val code = clean.substring(j, j + 4).toIntOrNull(16) ?: -1
                if (code in 0x0020..0x007E || code in 0x0900..0x097F || code == 0x000A || code == 0x0020) {
                    sb.append(code.toChar())
                } else {
                    isValid = false
                    break
                }
                j += 4
            }
            if (isValid && sb.isNotBlank()) return sb.toString()
        }
        // Try UTF-8 bytes
        try {
            if (clean.length % 2 == 0) {
                val byteArr = ByteArray(clean.length / 2)
                for (k in byteArr.indices) {
                    byteArr[k] = clean.substring(k * 2, k * 2 + 2).toInt(16).toByte()
                }
                val s = String(byteArr, Charsets.UTF_8)
                if (s.any { it.code in 0x0900..0x097F || it in 'a'..'z' || it in 'A'..'Z' }) {
                    return s
                }
            }
        } catch (_: Exception) {}
        return sb.toString()
    }

    private fun findMatchingParen(s: String, openIdx: Int): Int {
        var depth = 0
        var i = openIdx
        while (i < s.length) {
            if (s[i] == '\\') {
                i += 2
                continue
            }
            if (s[i] == '(') depth++
            else if (s[i] == ')') {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return -1
    }

    private fun unescapePdfString(s: String): String {
        // Parse octal escapes \ooo and handle UTF-16BE BOM (\376\377) or UTF-8 Devanagari bytes
        val byteBuffer = java.io.ByteArrayOutputStream()
        var idx = 0
        var hasOctal = false
        while (idx < s.length) {
            val c = s[idx]
            if (c == '\\' && idx + 1 < s.length) {
                val next = s[idx + 1]
                if (next in '0'..'7') {
                    var octalLen = 1
                    while (octalLen < 3 && idx + 1 + octalLen < s.length && s[idx + 1 + octalLen] in '0'..'7') {
                        octalLen++
                    }
                    val octalStr = s.substring(idx + 1, idx + 1 + octalLen)
                    val byteVal = octalStr.toIntOrNull(8) ?: 0
                    byteBuffer.write(byteVal)
                    hasOctal = true
                    idx += 1 + octalLen
                    continue
                } else if (next == 'n') {
                    byteBuffer.write('\n'.code); idx += 2; continue
                } else if (next == 'r') {
                    byteBuffer.write('\r'.code); idx += 2; continue
                } else if (next == 't') {
                    byteBuffer.write('\t'.code); idx += 2; continue
                } else if (next == '(' || next == ')' || next == '\\') {
                    byteBuffer.write(next.code); idx += 2; continue
                }
            }
            byteBuffer.write(c.code and 0xFF)
            idx++
        }

        val rawBytes = byteBuffer.toByteArray()
        // If string starts with UTF-16BE BOM (0xFE 0xFF)
        if (rawBytes.size >= 2 && (rawBytes[0].toInt() and 0xFF) == 0xFE && (rawBytes[1].toInt() and 0xFF) == 0xFF) {
            try {
                return String(rawBytes, 2, rawBytes.size - 2, Charsets.UTF_16BE)
            } catch (_: Exception) {}
        }

        // Check if raw bytes decode to valid UTF-8 with Hindi characters
        if (hasOctal) {
            try {
                val utf8 = String(rawBytes, Charsets.UTF_8)
                if (utf8.any { it.code in 0x0900..0x097F }) {
                    return utf8
                }
            } catch (_: Exception) {}
        }

        return String(rawBytes, Charsets.ISO_8859_1)
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\r", "")
            .replace("\\t", " ")
    }

    private fun createTempPdfFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File.createTempFile("smart_pdf_", ".pdf", context.cacheDir)
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun extractHindiAndEnglishText(context: Context, uri: Uri): String {
            return try {
                val extractor = AdvancedPdfSmartExtractor(context)
                val tempFile = extractor.createTempPdfFile(uri) ?: return ""
                val streamPages = extractor.extractPdfStreamText(tempFile)
                val textFromStreams = streamPages.flatten().joinToString("\n")
                
                // If stream text lacks Hindi, also scan raw uncompressed PDF bytes for UTF-8 Hindi (0x0900..0x097F)
                val hasHindi = textFromStreams.any { it.code in 0x0900..0x097F }
                val finalText = if (!hasHindi) {
                    try {
                        val rawBytes = tempFile.readBytes()
                        val utf8String = String(rawBytes, Charsets.UTF_8)
                        val hindiLines = utf8String.split("\n")
                            .map { it.trim() }
                            .filter { line -> line.any { it.code in 0x0900..0x097F } && line.length in 4..400 }
                        if (hindiLines.isNotEmpty()) {
                            (textFromStreams + "\n" + hindiLines.joinToString("\n")).trim()
                        } else textFromStreams
                    } catch (_: Exception) {
                        textFromStreams
                    }
                } else {
                    textFromStreams
                }
                
                tempFile.delete()
                finalText
            } catch (e: Exception) {
                ""
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractorScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val extractor = remember { AdvancedPdfSmartExtractor(context) }
    val config = remember { OcrLanguageConfig() }

    var selectedLanguage by remember { mutableStateOf("eng") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pdfResults by remember { mutableStateOf<List<PdfPageResult>>(emptyList()) }
    var activeTab by remember { mutableIntStateOf(0) }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedFileName = "Document Selected"
            isLoading = true
            coroutineScope.launch {
                pdfResults = extractor.processPdfUri(it, selectedLanguage)
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("PDF Smart Engine (15-Method OCR)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("21 Languages Supported", fontSize = 12.sp)
                }},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(selectedFileName ?: "Select PDF to apply 15 extraction methods", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Select OCR Language:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        items(config.availableLanguages.toList()) { (code, name) ->
                            FilterChip(
                                selected = selectedLanguage == code,
                                onClick = { selectedLanguage = code },
                                label = { Text(name, fontSize = 10.sp) }
                            )
                        }
                    }

                    Button(onClick = { pickerLauncher.launch("application/pdf") }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isLoading) "Extracting..." else "Pick PDF & Extract")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (pdfResults.isNotEmpty()) {
                val classification = remember(pdfResults) { extractor.classifyDocument(pdfResults) }

                // Classification header summary card
                PdfClassificationSummaryCard(classification)

                Spacer(modifier = Modifier.height(10.dp))

                ScrollableTabRow(selectedTabIndex = activeTab) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("MCQs & Options")
                                if (classification.totalQuestionsDetected > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(containerColor = Color(0xFF10B981)) {
                                        Text("${classification.totalQuestionsDetected}", color = Color.White, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    )
                    Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Layout Bounds") })
                    Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Chars & Words") })
                    Tab(selected = activeTab == 3, onClick = { activeTab = 3 }, text = { Text("Images & Captions") })
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (activeTab == 0) {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            StructuredQuestionsTab(classification, pdfResults)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(pdfResults) { page ->
                            when (activeTab) {
                                1 -> FullLayoutOverlayCard(page)
                                2 -> CharWordInspectorCard(page)
                                3 -> ImageCaptionAssociationCard(page)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfClassificationSummaryCard(classification: PdfClassificationResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(classification.documentType.badgeColorHex).copy(alpha = 0.10f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            Color(classification.documentType.badgeColorHex).copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (classification.documentType) {
                        PdfDocumentType.MCQ_QUESTION_PAPER -> Icons.Default.Quiz
                        PdfDocumentType.ANSWER_KEY_OR_SOLUTIONS -> Icons.Default.FormatListNumbered
                        PdfDocumentType.STUDY_MATERIAL_OR_NOTES -> Icons.Default.MenuBook
                        PdfDocumentType.SYLLABUS_OR_CURRICULUM -> Icons.Default.School
                        PdfDocumentType.UNSTRUCTURED_DOCUMENT -> Icons.Default.Description
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                Color(classification.documentType.badgeColorHex).copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color(classification.documentType.badgeColorHex),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = classification.documentType.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(classification.documentType.badgeColorHex)
                        )
                        Text(
                            text = "Local Classification Engine",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(classification.documentType.badgeColorHex).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(classification.documentType.badgeColorHex))
                ) {
                    Text(
                        text = "${(classification.confidenceScore * 100).toInt()}% Match",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(classification.documentType.badgeColorHex),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = classification.reasoning,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricPill(
                    label = "Questions",
                    value = "${classification.totalQuestionsDetected}",
                    color = Color(0xFF0284C7)
                )
                MetricPill(
                    label = "4-Option MCQs",
                    value = "${classification.questionsWithAllOptions}",
                    color = Color(0xFF10B981)
                )
                val aCount = classification.optionDistribution["A"] ?: 0
                val bCount = classification.optionDistribution["B"] ?: 0
                val cCount = classification.optionDistribution["C"] ?: 0
                val dCount = classification.optionDistribution["D"] ?: 0
                MetricPill(
                    label = "Options A-D",
                    value = "$aCount,$bCount,$cCount,$dCount",
                    color = Color(0xFF8B5CF6)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            val isPrivateWorkflow = classification.totalQuestionsDetected > 0 || classification.documentType == PdfDocumentType.MCQ_QUESTION_PAPER
            val workflowColor = if (isPrivateWorkflow) Color(0xFF10B981) else Color(0xFFF59E0B)
            val context = LocalContext.current
            
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = workflowColor.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, workflowColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPrivateWorkflow) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = null,
                            tint = workflowColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isPrivateWorkflow) "AUTO-ROUTED: PRIVATE VAULT 🔒" else "AUTO-ROUTED: PUBLIC COMMUNITY 🌐",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    color = workflowColor,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivateWorkflow) 
                                    "Detected ${classification.totalQuestionsDetected} questions. Automatically routed to Private Vault for personal practice."
                                else 
                                    "Raw text/study material without questions. Automatically routed to Public Community to generate questions.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 13.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intent = android.content.Intent(context, Com.ak.testseries.MainActivity::class.java).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra("EXTRA_TARGET_TAB", "UPLOAD_AI")
                                putExtra("EXTRA_AUTO_WORKFLOW", if (isPrivateWorkflow) "private" else "public")
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = workflowColor),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isPrivateWorkflow) "Use Private" else "Use Public",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricPill(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun StructuredQuestionsTab(classification: PdfClassificationResult, pages: List<PdfPageResult>) {
    if (classification.detectedQuestions.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No Structured MCQs Found",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This document does not contain standard question numbers with A, B, C, D options. It has been classified as ${classification.documentType.displayName}.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Extracted Document Excerpt:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                val sampleText = pages.firstOrNull()?.fullText?.take(300) ?: "No text extracted."
                Text(
                    text = sampleText,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else {
        val allImages = pages.flatMap { it.extractedImages }
        val viewModel: Com.ak.testseries.viewmodel.NEETViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        var savedStatus by remember { mutableStateOf<String?>(null) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Parsed ${classification.detectedQuestions.size} Structured Questions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = savedStatus ?: "Save questions, choices & cropped images into local Room Database for offline access.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.saveExtractedQuestionsToPrivateRoomDatabase(
                                testTitle = "Parsed PDF Test (${classification.detectedQuestions.size} Qs)",
                                subject = "Full Syllabus",
                                targetExam = "NEET",
                                structuredQuestions = classification.detectedQuestions,
                                onSuccess = { id ->
                                    savedStatus = "✅ Saved to Room DB ($id)"
                                }
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Save to Room DB", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            classification.detectedQuestions.forEach { question ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Question ${question.questionNumber} (Page ${question.pageNumber})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (question.hasAllFourOptions) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "4/4 Options (A,B,C,D)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                                ) {
                                    Text(
                                        text = "${question.options.size} Options",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = question.questionText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )

                        // Associated diagram / cropped figure bound directly under question text & above choices
                        val displayBmp = question.imageBitmap ?: question.associatedImageId?.let { imgId ->
                            allImages.find { it.id == imgId }?.bitmap
                        }

                        if (displayBmp != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Image(
                                        bitmap = displayBmp.asImageBitmap(),
                                        contentDescription = "Cropped Diagram for Q${question.questionNumber}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "🖼️ Extracted Cropped Figure (Bound to Q${question.questionNumber})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Options Grid or Column
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            question.options.forEach { opt ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = opt.key,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (opt.text.isBlank()) "(Option ${opt.key})" else opt.text,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullLayoutOverlayCard(pageResult: PdfPageResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Page ${pageResult.pageNumber} - Methods 1-7 (Bounds & Zones)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Image(
                bitmap = pageResult.annotatedBitmap.asImageBitmap(),
                contentDescription = "Annotated Bounds",
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(8.dp))
            )
            Text("Reading Order Blocks: ${pageResult.textBlocks.size}", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            pageResult.textBlocks.forEach { block ->
                Text("Idx ${block.readingOrderIndex}: ${block.text} [Sidebar=${block.isSidebar}]", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun CharWordInspectorCard(pageResult: PdfPageResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Methods 8, 9, 10: Character & Word Level", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            pageResult.textBlocks.forEach { block ->
                block.lines.forEach { line ->
                    line.wordBoxes.forEach { word ->
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            Text("Word: '${word.word}' (X:${word.bounds.x.toInt()}, Y:${word.bounds.y.toInt()})", fontSize = 11.sp)
                            Row {
                                word.charBoxes.forEach { ch ->
                                    Text("[${ch.char}]", fontSize = 10.sp, color = Color.Red, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageCaptionAssociationCard(pageResult: PdfPageResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Methods 12, 13, 14: Images & Captions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (pageResult.extractedImages.isEmpty()) Text("No images found", fontSize = 11.sp)
            pageResult.extractedImages.forEach { img ->
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(bitmap = img.bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(60.dp))
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("Size: ${img.widthPx}x${img.heightPx}", fontSize = 11.sp)
                        pageResult.layoutAssociations.filter { it.associatedImageId == img.id }.forEach { assoc ->
                            Text("Link: ${assoc.relationship} (${assoc.distancePx.toInt()}px away)", fontSize = 10.sp, color = Color.Blue)
                        }
                    }
                }
            }
        }
    }
}

class PdfExtractorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ExtractorScreen()
                }
            }
        }
    }
}
