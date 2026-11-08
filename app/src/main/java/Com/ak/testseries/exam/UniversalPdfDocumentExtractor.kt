package Com.ak.testseries.exam

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import Com.ak.pdfextractor.AdvancedPdfSmartExtractor
import Com.ak.pdfextractor.PdfPageResult
import Com.ak.testseries.utils.CompactMarkdownFormatter
import Com.ak.testseries.utils.DiagramCandidate
import Com.ak.testseries.utils.LocalPdfImageExtractor
import Com.ak.testseries.utils.QuestionImageMapping
import Com.ak.testseries.utils.PdfCropper
import java.util.Collections
import java.util.IdentityHashMap

/**
 * One extraction entry point for every exam PDF workflow.
 *
 * Reuses the existing PDF stream parser, page renderer, ML Kit OCR,
 * layout/image detector and local diagram cropper. It does not call Gemini
 * and does not invent missing text or questions.
 */
object UniversalPdfDocumentExtractor {

    data class Result(
        val sourcePageCount: Int,
        val extractedPageCount: Int,
        val pageTexts: List<String>,
        val fullText: String,
        val compactMarkdown: String,
        val diagramCandidates: List<DiagramCandidate>,
        val questionImageMappings: Map<Int, QuestionImageMapping>,
        val diagramExtractionError: String? = null
    ) {
        val diagramPaths: List<String>
            get() = diagramCandidates.map { it.localImagePath }
    }

    suspend fun extract(
        context: Context,
        pdfUri: Uri,
        testId: String,
        maxPages: Int = 50,
        languageCode: String = "eng"
    ): Result {
        require(testId.isNotBlank()) { "A stable PDF session/test ID is required." }

        val sourcePageCount = PdfCropper.getPdfPageCount(context, pdfUri)
        require(sourcePageCount > 0) { "The selected file is not a readable PDF." }
        require(sourcePageCount <= maxPages) {
            "PDF has $sourcePageCount pages; the current safe extraction limit is $maxPages. No partial document was accepted."
        }

        val pageResults = AdvancedPdfSmartExtractor(context).processPdfUri(
            uri = pdfUri,
            languageCode = languageCode,
            maxPages = maxPages
        )

        try {
            require(pageResults.size == sourcePageCount) {
                "PDF extraction was incomplete: expected $sourcePageCount pages but extracted ${pageResults.size}. Retry or inspect the PDF."
            }

            val pageTexts = pageResults
                .sortedBy { it.pageNumber }
                .map { it.fullText.trim() }

            require(pageTexts.any { it.isNotBlank() }) {
                "No readable text was extracted. The PDF may be image-only, encrypted, malformed, or use an unsupported text encoding."
            }

            val fullText = pageTexts.mapIndexed { index, text ->
                "--- PAGE ${index + 1} ---\n$text"
            }.joinToString("\n\n")

            val compactMarkdown = CompactMarkdownFormatter.formatToCompactMarkdown(fullText)
            require(compactMarkdown.isNotBlank()) { "PDF text formatting produced no usable content." }

            var diagrams: List<DiagramCandidate> = emptyList()
            var diagramError: String? = null
            try {
                diagrams = LocalPdfImageExtractor.extractDiagramCandidates(
                    context = context,
                    pdfUri = pdfUri,
                    testId = testId,
                    maxPagesToScan = sourcePageCount
                )
            } catch (error: Exception) {
                diagramError = error.message ?: error::class.java.simpleName
            }

            val structuredQuestions = pageResults.flatMap { it.structuredQuestions }
            val questionImageMappings = LocalPdfImageExtractor.mapCandidatesToQuestions(
                questions = structuredQuestions.map { question ->
                    Triple(question.questionNumber, question.questionText, question.pageNumber)
                },
                candidates = diagrams
            )

            return Result(
                sourcePageCount = sourcePageCount,
                extractedPageCount = pageResults.size,
                pageTexts = pageTexts,
                fullText = fullText,
                compactMarkdown = compactMarkdown,
                diagramCandidates = diagrams,
                questionImageMappings = questionImageMappings,
                diagramExtractionError = diagramError
            )
        } finally {
            recyclePageBitmaps(pageResults)
        }
    }

    private fun recyclePageBitmaps(pages: List<PdfPageResult>) {
        val uniqueBitmaps = Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>())
        pages.forEach { page ->
            uniqueBitmaps.add(page.renderedBitmap)
            uniqueBitmaps.add(page.annotatedBitmap)
            uniqueBitmaps.add(page.correctedBitmap)
            page.extractedImages.forEach { uniqueBitmaps.add(it.bitmap) }
        }
        uniqueBitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
