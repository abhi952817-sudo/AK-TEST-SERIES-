package Com.ak.testseries.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import Com.ak.testseries.exam.QuestionValidationEngine
import com.ak.pdfextractor.BoundingBox
import com.ak.pdfextractor.ExtractedImage
import com.ak.pdfextractor.LayoutAssociation
import com.ak.pdfextractor.StructuredQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Confidence levels for deterministic Question ↔ Image mapping.
 */
enum class MappingConfidence {
    HIGH,          // Strong evidence: same page, keyword, sensible spatial proximity / LayoutAssociation. Auto-attached.
    MEDIUM,        // Ambiguous/uncertain: multiple candidates competing nearby. Marked for review, NOT auto-attached.
    LOW,           // Weak or conflicting signal: unassigned.
    UNASSIGNED     // Decorative, header/footer, or unassociated candidate.
}

/**
 * Deterministic mapping result linking a question to an extracted image candidate.
 */
data class QuestionImageMapping(
    val questionNumber: Int,
    val questionId: String = "",
    val sourcePage: Int,
    val imageId: String? = null,
    val localImagePath: String? = null,
    val cropCoordinates: String = "",
    val cropWidth: Int = 0,
    val cropHeight: Int = 0,
    val distancePx: Float = 0f,
    val associationReason: String = "",
    val confidence: MappingConfidence = MappingConfidence.UNASSIGNED,
    val isNeedsReview: Boolean = false
)

/**
 * Candidate region representing a detected diagram, chart, or image on a PDF page.
 */
data class DiagramCandidate(
    val pageNumber: Int,
    val cropX: Int,
    val cropY: Int,
    val cropWidth: Int,
    val cropHeight: Int,
    val localImagePath: String,
    val confidence: Float, // 0.0f - 1.0f confidence that this is a diagram
    val isNeedsReview: Boolean = false // Flagged if boundary/aspect ratio is uncertain
)

/**
 * LocalPdfImageExtractor
 * Extracts candidate diagram and image regions locally from rendered PDF pages.
 * 
 * - Reuses PdfCropper.renderPage() using Android native PdfRenderer.
 * - Zero Gemini / Cloud calls.
 * - Stores cropped candidates in private app internal files directory.
 * - Manual crop remains available as fallback.
 */
object LocalPdfImageExtractor {

    private const val DEFAULT_SCALE = 2f
    private const val MIN_REGION_WIDTH_DP = 60
    private const val MIN_REGION_HEIGHT_DP = 50

    /**
     * Extracts diagram/image candidates from all or specified pages of a PDF.
     */
    suspend fun extractDiagramCandidates(
        context: Context,
        pdfUri: Uri,
        testId: String = "private_${System.currentTimeMillis()}",
        maxPagesToScan: Int = 50
    ): List<DiagramCandidate> = withContext(Dispatchers.Default) {
        val candidates = mutableListOf<DiagramCandidate>()
        val totalPages = PdfCropper.getPdfPageCount(context, pdfUri)
        if (totalPages <= 0) return@withContext emptyList()

        val pagesToProcess = minOf(totalPages, maxPagesToScan)
        val outputDir = File(context.filesDir, "private_tests/$testId/diagrams").apply {
            if (!exists()) mkdirs()
        }

        for (pageIndex in 0 until pagesToProcess) {
            val pageNumber = pageIndex + 1
            val pageBitmap = PdfCropper.renderPage(context, pdfUri, pageIndex, DEFAULT_SCALE) ?: continue
            
            try {
                val pageCandidates = detectDiagramRegionsOnPage(pageBitmap, pageNumber, outputDir)
                candidates.addAll(pageCandidates)
            } finally {
                // Recycle bitmap if needed to conserve memory
                if (!pageBitmap.isRecycled) {
                    pageBitmap.recycle()
                }
            }
        }

        candidates
    }

    /**
     * Detects non-text/diagram regions on a rendered page bitmap using lightweight local rules.
     * 
     * Detection Logic:
     * 1. Downsamples the page to a coarse grid (block size e.g. 16x16 or 24x24 px).
     * 2. For each block, determines pixel density and color variance:
     *    - Text lines typically exhibit low area density with horizontal whitespace between lines.
     *    - Diagrams, photos, charts, and figures exhibit higher 2D continuous pixel density or multi-row color variation.
     * 3. Aggregates adjacent non-white, dense blocks into candidate bounding boxes.
     * 4. Filters out standard header/footer banners, single text lines, and margin artifacts.
     * 5. Crops valid bounding boxes and writes to private internal storage.
     */
    fun detectDiagramRegionsOnPage(
        bitmap: Bitmap,
        pageNumber: Int,
        outputDir: File
    ): List<DiagramCandidate> {
        val width = bitmap.width
        val height = bitmap.height

        if (width < 200 || height < 200) return emptyList()

        // Downsample grid configuration
        val blockSize = (16 * DEFAULT_SCALE).toInt().coerceAtLeast(16)
        val gridCols = width / blockSize
        val gridRows = height / blockSize

        // Boolean 2D array indicating blocks with high non-white content or graphical patterns
        val isDiagramBlock = Array(gridRows) { BooleanArray(gridCols) }

        // Skip 5% margin on top/bottom/left/right (headers, footers, page borders)
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
                val totalSampled = (blockSize * blockSize) / 4 // Sample 1 in 4 pixels for speed

                for (by in 0 until blockSize step 2) {
                    val y = yStart + by
                    if (y >= height) break
                    for (bx in 0 until blockSize step 2) {
                        val x = xStart + bx
                        if (x >= width) break

                        val pixel = bitmap.getPixel(x, y)
                        val red = Color.red(pixel)
                        val green = Color.green(pixel)
                        val blue = Color.blue(pixel)

                        // If pixel is sufficiently dark or has saturated color (not paper white or light gray)
                        val isNonWhite = (red < 235 || green < 235 || blue < 235)
                        if (isNonWhite) {
                            nonWhitePixels++
                            // Check if color is non-grayscale (colored diagrams, charts)
                            val maxC = maxOf(red, green, blue)
                            val minC = minOf(red, green, blue)
                            if (maxC - minC > 20) {
                                colorVarianceCount++
                            }
                        }
                    }
                }

                // If block has substantial graphical density (e.g. >= 35% non-white) or color variance
                val densityRatio = nonWhitePixels.toFloat() / totalSampled
                if (densityRatio >= 0.35f || (colorVarianceCount > 5 && densityRatio >= 0.15f)) {
                    isDiagramBlock[r][c] = true
                }
            }
        }

        // Group connected components into candidate boxes
        val visited = Array(gridRows) { BooleanArray(gridCols) }
        val candidateBoxes = mutableListOf<CandidateBox>()

        for (r in marginRowTop until marginRowBottom) {
            for (c in marginColLeft until marginColRight) {
                if (isDiagramBlock[r][c] && !visited[r][c]) {
                    // Flood fill to find component bounding box
                    var minR = r
                    var maxR = r
                    var minC = c
                    var maxC = c
                    var activeBlocks = 0

                    val queue = java.util.ArrayDeque<Pair<Int, Int>>()
                    queue.add(Pair(r, c))
                    visited[r][c] = true

                    while (!queue.isEmpty()) {
                        val curr = queue.poll() ?: break
                        val currR = curr.first
                        val currC = curr.second
                        activeBlocks++
                        if (currR < minR) minR = currR
                        if (currR > maxR) maxR = currR
                        if (currC < minC) minC = currC
                        if (currC > maxC) maxC = currC

                        // 4-way connectivity
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

                    val boxWidthBlocks = (maxC - minC + 1)
                    val boxHeightBlocks = (maxR - minR + 1)
                    val boxArea = boxWidthBlocks * boxHeightBlocks
                    val blockFillRatio = activeBlocks.toFloat() / boxArea.coerceAtLeast(1)

                    // A genuine diagram/image spans multiple blocks in both horizontal and vertical directions
                    // with solid density (fillRatio >= 0.28). Sparse text lines have low fillRatio (< 0.25).
                    val isDiagramCandidate = boxWidthBlocks >= 3 && boxHeightBlocks >= 3 && activeBlocks >= 6 && blockFillRatio >= 0.35f
                    val isFullPageScan = boxWidthBlocks >= (gridCols * 0.98) && boxHeightBlocks >= (gridRows * 0.98)

                    if (isDiagramCandidate && !isFullPageScan) {
                        candidateBoxes.add(
                            CandidateBox(
                                minC = minC,
                                maxC = maxC,
                                minR = minR,
                                maxR = maxR,
                                activeBlocks = activeBlocks
                            )
                        )
                    }
                }
            }
        }

        val results = mutableListOf<DiagramCandidate>()

        // Merge overlapping or vertically adjacent boxes that belong to the same figure
        val mergedBoxes = mergeAdjacentBoxes(candidateBoxes)

        for ((index, box) in mergedBoxes.withIndex()) {
            // Calculate pixel bounds with padding
            val padPx = (10 * DEFAULT_SCALE).toInt()
            val cropX = (box.minC * blockSize - padPx).coerceAtLeast(0)
            val cropY = (box.minR * blockSize - padPx).coerceAtLeast(0)
            val cropR = ((box.maxC + 1) * blockSize + padPx).coerceAtMost(width)
            val cropB = ((box.maxR + 1) * blockSize + padPx).coerceAtMost(height)

            val cropW = cropR - cropX
            val cropH = cropB - cropY

            val minW = (MIN_REGION_WIDTH_DP * DEFAULT_SCALE).toInt()
            val minH = (MIN_REGION_HEIGHT_DP * DEFAULT_SCALE).toInt()

            if (cropW >= minW && cropH >= minH) {
                // Determine confidence & review status
                val fillRatio = box.activeBlocks.toFloat() / (((box.maxC - box.minC + 1) * (box.maxR - box.minR + 1)).coerceAtLeast(1))
                val isNeedsReview = fillRatio < 0.35f || cropW > (width * 0.70f) || cropH > (height * 0.55f)
                val confidence = if (isNeedsReview) 0.60f else 0.90f

                val rawCroppedBmp = try {
                    Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
                } catch (e: Exception) {
                    null
                }

                if (rawCroppedBmp != null) {
                    // Downscale large images (max 640px dimension) and compress using WebP/JPEG 65% to minimize token payload
                    val maxDim = 640
                    val scaledBmp = if (rawCroppedBmp.width > maxDim || rawCroppedBmp.height > maxDim) {
                        val aspect = rawCroppedBmp.width.toFloat() / rawCroppedBmp.height.toFloat()
                        val targetW: Int
                        val targetH: Int
                        if (rawCroppedBmp.width > rawCroppedBmp.height) {
                            targetW = maxDim
                            targetH = (maxDim / aspect).toInt().coerceAtLeast(1)
                        } else {
                            targetH = maxDim
                            targetW = (maxDim * aspect).toInt().coerceAtLeast(1)
                        }
                        Bitmap.createScaledBitmap(rawCroppedBmp, targetW, targetH, true)
                    } else {
                        rawCroppedBmp
                    }

                    val file = File(outputDir, "diag_p${pageNumber}_${index + 1}_${System.currentTimeMillis()}.webp")
                    try {
                        FileOutputStream(file).use { out ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                scaledBmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 65, out)
                            } else {
                                @Suppress("DEPRECATION")
                                scaledBmp.compress(Bitmap.CompressFormat.WEBP, 65, out)
                            }
                        }
                        results.add(
                            DiagramCandidate(
                                pageNumber = pageNumber,
                                cropX = cropX,
                                cropY = cropY,
                                cropWidth = cropW,
                                cropHeight = cropH,
                                localImagePath = file.absolutePath,
                                confidence = confidence,
                                isNeedsReview = isNeedsReview
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        if (scaledBmp !== rawCroppedBmp && !scaledBmp.isRecycled) {
                            scaledBmp.recycle()
                        }
                        if (!rawCroppedBmp.isRecycled) {
                            rawCroppedBmp.recycle()
                        }
                    }
                }
            }
        }

        return results
    }

    private data class CandidateBox(
        val minC: Int,
        val maxC: Int,
        val minR: Int,
        val maxR: Int,
        val activeBlocks: Int
    )

    private fun mergeAdjacentBoxes(boxes: List<CandidateBox>): List<CandidateBox> {
        if (boxes.size <= 1) return boxes
        val merged = mutableListOf<CandidateBox>()
        val sorted = boxes.sortedBy { it.minR }

        var current = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            // If vertically close and horizontal overlap is high
            val verticalGap = next.minR - current.maxR
            val horizontalOverlap = minOf(current.maxC, next.maxC) - maxOf(current.minC, next.minC)

            if (verticalGap <= 2 && horizontalOverlap > 0) {
                current = CandidateBox(
                    minC = minOf(current.minC, next.minC),
                    maxC = maxOf(current.maxC, next.maxC),
                    minR = minOf(current.minR, next.minR),
                    maxR = maxOf(current.maxR, next.maxR),
                    activeBlocks = current.activeBlocks + next.activeBlocks
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    /**
     * Determines whether an extracted image is likely decorative (e.g. separator lines, icons, small bullets, or banners).
     */
    fun isDecorativeImage(widthPx: Int, heightPx: Int): Boolean {
        if (widthPx < 60 || heightPx < 50) return true
        val aspect = widthPx.toFloat() / heightPx.toFloat().coerceAtLeast(1f)
        if (aspect > 6.0f && heightPx < 40) return true // Horizontal divider line or thin banner
        if (aspect < 0.15f && widthPx < 30) return true // Vertical margin rule
        return false
    }

    /**
     * Deterministically maps StructuredQuestions to ExtractedImages on a PDF page.
     * Uses strict deterministic rules:
     * 1. Same PDF page (strictly scoped to pageNumber).
     * 2. Existing LayoutAssociation if present and verified.
     * 3. Diagram keywords ("diagram", "figure", "given below", "image", "shown in", "चित्र", "आकृति").
     * 4. Spatial proximity within the question's vertical band on the page.
     * 5. Filters out decorative / separator images.
     * 6. Ambiguous / multiple competing candidates are marked MEDIUM / NEEDS_REVIEW (never guessed).
     */
    fun mapStructuredQuestionsToImages(
        questions: List<StructuredQuestion>,
        extractedImages: List<ExtractedImage>,
        layoutAssociations: List<LayoutAssociation> = emptyList(),
        pageNumber: Int = 1
    ): Map<Int, QuestionImageMapping> {
        val results = mutableMapOf<Int, QuestionImageMapping>()
        if (questions.isEmpty()) return results

        // Filter out decorative / separator / artifact images
        val validImages = extractedImages.filter { img ->
            !isDecorativeImage(img.widthPx, img.heightPx)
        }

        if (validImages.isEmpty()) {
            questions.forEach { q ->
                val hasKeyword = QuestionValidationEngine.hasDiagramKeyword(q.questionText)
                results[q.questionNumber] = QuestionImageMapping(
                    questionNumber = q.questionNumber,
                    sourcePage = pageNumber,
                    associationReason = if (hasKeyword) "Diagram mentioned in text but no candidate image on page $pageNumber" else "No image candidates on page",
                    confidence = if (hasKeyword) MappingConfidence.LOW else MappingConfidence.UNASSIGNED,
                    isNeedsReview = hasKeyword
                )
            }
            return results
        }

        // Track already assigned image IDs to prevent multiple questions grabbing the same image
        val assignedImageIds = mutableSetOf<String>()

        questions.forEachIndexed { idx, q ->
            val hasKeyword = QuestionValidationEngine.hasDiagramKeyword(q.questionText)
            val qTop = q.bounds?.y ?: (idx * 150f)
            val nextQTop = questions.getOrNull(idx + 1)?.bounds?.y ?: Float.MAX_VALUE

            // Signal 1: Check existing LayoutAssociation for this question
            val existingAssoc = layoutAssociations.find { assoc ->
                (q.associatedImageId != null && assoc.associatedImageId == q.associatedImageId) ||
                (assoc.distancePx <= 220f && validImages.any { it.id == assoc.associatedImageId })
            }

            if (existingAssoc != null) {
                val matchedImg = validImages.find { it.id == existingAssoc.associatedImageId }
                if (matchedImg != null && !assignedImageIds.contains(matchedImg.id)) {
                    assignedImageIds.add(matchedImg.id)
                    results[q.questionNumber] = QuestionImageMapping(
                        questionNumber = q.questionNumber,
                        sourcePage = pageNumber,
                        imageId = matchedImg.id,
                        cropCoordinates = "${matchedImg.bounds.x.toInt()},${matchedImg.bounds.y.toInt()}",
                        cropWidth = matchedImg.widthPx,
                        cropHeight = matchedImg.heightPx,
                        distancePx = existingAssoc.distancePx,
                        associationReason = "Direct LayoutAssociation (${existingAssoc.relationship}, distance=${existingAssoc.distancePx}px)",
                        confidence = MappingConfidence.HIGH,
                        isNeedsReview = false
                    )
                    return@forEachIndexed
                }
            }

            // Signal 2: Question contains diagram keyword
            if (hasKeyword) {
                // Find candidates strictly on the same page within this question's vertical band
                val candidateMatches = validImages.filter { img ->
                    !assignedImageIds.contains(img.id) &&
                    img.bounds.y >= (qTop - 50f) && img.bounds.y <= (nextQTop + 70f)
                }

                when (candidateMatches.size) {
                    1 -> {
                        val matched = candidateMatches.first()
                        assignedImageIds.add(matched.id)
                        val dist = (matched.bounds.y - qTop).coerceAtLeast(0f)
                        results[q.questionNumber] = QuestionImageMapping(
                            questionNumber = q.questionNumber,
                            sourcePage = pageNumber,
                            imageId = matched.id,
                            cropCoordinates = "${matched.bounds.x.toInt()},${matched.bounds.y.toInt()}",
                            cropWidth = matched.widthPx,
                            cropHeight = matched.heightPx,
                            distancePx = dist,
                            associationReason = "1-to-1 spatial match on page $pageNumber with diagram keyword",
                            confidence = MappingConfidence.HIGH,
                            isNeedsReview = false
                        )
                    }
                    0 -> {
                        // If no image is strictly in the vertical band, check if there's exactly 1 unassigned image on the page
                        val unassignedOnPage = validImages.filter { !assignedImageIds.contains(it.id) }
                        if (unassignedOnPage.size == 1 && questions.size == 1) {
                            val matched = unassignedOnPage.first()
                            assignedImageIds.add(matched.id)
                            results[q.questionNumber] = QuestionImageMapping(
                                questionNumber = q.questionNumber,
                                sourcePage = pageNumber,
                                imageId = matched.id,
                                cropCoordinates = "${matched.bounds.x.toInt()},${matched.bounds.y.toInt()}",
                                cropWidth = matched.widthPx,
                                cropHeight = matched.heightPx,
                                distancePx = kotlin.math.abs(matched.bounds.y - qTop),
                                associationReason = "Single question and single diagram candidate on page $pageNumber",
                                confidence = MappingConfidence.HIGH,
                                isNeedsReview = false
                            )
                        } else {
                            results[q.questionNumber] = QuestionImageMapping(
                                questionNumber = q.questionNumber,
                                sourcePage = pageNumber,
                                associationReason = "Question mentions diagram but no valid candidate found in spatial band on page $pageNumber",
                                confidence = MappingConfidence.LOW,
                                isNeedsReview = true
                            )
                        }
                    }
                    else -> {
                        // Multiple candidates in the same spatial zone -> AMBIGUOUS
                        // Rule: Never guess! Leave unassigned and mark for review.
                        results[q.questionNumber] = QuestionImageMapping(
                            questionNumber = q.questionNumber,
                            sourcePage = pageNumber,
                            associationReason = "Ambiguous: ${candidateMatches.size} candidate images compete for question ${q.questionNumber} on page $pageNumber",
                            confidence = MappingConfidence.MEDIUM,
                            isNeedsReview = true
                        )
                    }
                }
                return@forEachIndexed
            }

            // Signal 3: No keyword and no LayoutAssociation
            // Check if there is an unambiguous candidate strictly enclosed inside question bounds
            val strictlyEnclosed = validImages.filter { img ->
                !assignedImageIds.contains(img.id) &&
                q.bounds != null &&
                img.bounds.y >= q.bounds.y && (img.bounds.y + img.bounds.height) <= (q.bounds.y + q.bounds.height)
            }

            if (strictlyEnclosed.size == 1 && questions.size == 1) {
                // Single question completely enclosing single image
                val matched = strictlyEnclosed.first()
                assignedImageIds.add(matched.id)
                results[q.questionNumber] = QuestionImageMapping(
                    questionNumber = q.questionNumber,
                    sourcePage = pageNumber,
                    imageId = matched.id,
                    cropCoordinates = "${matched.bounds.x.toInt()},${matched.bounds.y.toInt()}",
                    cropWidth = matched.widthPx,
                    cropHeight = matched.heightPx,
                    distancePx = 0f,
                    associationReason = "Single question fully enclosing diagram region",
                    confidence = MappingConfidence.HIGH,
                    isNeedsReview = false
                )
            } else {
                // Rule: Never guess if there's no keyword and no unambiguous association
                results[q.questionNumber] = QuestionImageMapping(
                    questionNumber = q.questionNumber,
                    sourcePage = pageNumber,
                    associationReason = "No diagram keywords or unambiguous spatial association",
                    confidence = MappingConfidence.UNASSIGNED,
                    isNeedsReview = false
                )
            }
        }

        return results
    }

    /**
     * Maps extracted DiagramCandidates to questions based on page matching and diagram keywords.
     */
    fun mapCandidatesToQuestions(
        questions: List<Triple<Int, String, Int>>, // List of (questionNumber, questionText, pageNumber)
        candidates: List<DiagramCandidate>
    ): Map<Int, QuestionImageMapping> {
        val results = mutableMapOf<Int, QuestionImageMapping>()
        val candidatesByPage = candidates.groupBy { it.pageNumber }
        val assignedPaths = mutableSetOf<String>()

        questions.forEach { (qNum, qText, pageNum) ->
            val hasKeyword = QuestionValidationEngine.hasDiagramKeyword(qText)
            val pageCandidates = (candidatesByPage[pageNum] ?: emptyList()).filter {
                !assignedPaths.contains(it.localImagePath) && !it.isNeedsReview
            }

            if (pageCandidates.isEmpty()) {
                results[qNum] = QuestionImageMapping(
                    questionNumber = qNum,
                    sourcePage = pageNum,
                    associationReason = if (hasKeyword) "Diagram mentioned but no candidate on page $pageNum" else "No candidates on page",
                    confidence = if (hasKeyword) MappingConfidence.LOW else MappingConfidence.UNASSIGNED,
                    isNeedsReview = hasKeyword
                )
                return@forEach
            }

            if (hasKeyword) {
                if (pageCandidates.size == 1) {
                    val cand = pageCandidates.first()
                    assignedPaths.add(cand.localImagePath)
                    results[qNum] = QuestionImageMapping(
                        questionNumber = qNum,
                        sourcePage = pageNum,
                        localImagePath = cand.localImagePath,
                        cropCoordinates = "${cand.cropX},${cand.cropY}",
                        cropWidth = cand.cropWidth,
                        cropHeight = cand.cropHeight,
                        associationReason = "1-to-1 match on page $pageNum with diagram keyword",
                        confidence = MappingConfidence.HIGH,
                        isNeedsReview = false
                    )
                } else {
                    // Multiple candidates on the same page with keyword -> AMBIGUOUS
                    results[qNum] = QuestionImageMapping(
                        questionNumber = qNum,
                        sourcePage = pageNum,
                        associationReason = "Ambiguous: ${pageCandidates.size} candidates on page $pageNum",
                        confidence = MappingConfidence.MEDIUM,
                        isNeedsReview = true
                    )
                }
            } else {
                results[qNum] = QuestionImageMapping(
                    questionNumber = qNum,
                    sourcePage = pageNum,
                    associationReason = "No diagram keyword present in question",
                    confidence = MappingConfidence.UNASSIGNED,
                    isNeedsReview = false
                )
            }
        }

        return results
    }
}
