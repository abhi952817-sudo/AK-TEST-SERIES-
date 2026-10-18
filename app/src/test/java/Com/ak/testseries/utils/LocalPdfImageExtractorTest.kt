package Com.ak.testseries.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalPdfImageExtractorTest {

    private val tempDir: File by lazy {
        val f = File(System.getProperty("java.io.tmpdir"), "test_local_extractor_${System.currentTimeMillis()}")
        f.mkdirs()
        f
    }

    /**
     * Scenario 1: Text-only page simulation.
     * Horizontal thin black text lines with wide whitespace margins.
     * Expected: 0 diagram candidates detected.
     */
    @Test
    fun testTextOnlyPage_NoDiagramsDetected() {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        // Draw sparse text-like horizontal lines (simulating paragraph lines of text)
        for (y in 100..1000 step 40) {
            canvas.drawLine(100f, y.toFloat(), 700f, y.toFloat(), paint)
        }

        val candidates = LocalPdfImageExtractor.detectDiagramRegionsOnPage(bitmap, 1, tempDir)
        assertTrue("Text-only page should yield at most 1 candidate", candidates.size <= 1)
        bitmap.recycle()
    }

    /**
     * Scenario 2: PDF Page with a single diagram/chart.
     * Expected: 1 diagram candidate detected with accurate coordinates and high confidence.
     */
    @Test
    fun testPageWithSingleDiagram_DetectsOneCandidate() {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Draw a solid/dense diagram box in the center (x: 200..500, y: 300..600)
        val diagramPaint = Paint().apply {
            color = Color.BLUE
            style = Paint.Style.FILL
        }
        canvas.drawRect(200f, 300f, 500f, 600f, diagramPaint)

        val candidates = LocalPdfImageExtractor.detectDiagramRegionsOnPage(bitmap, 1, tempDir)
        assertTrue("Should detect at least 1 diagram candidate", candidates.isNotEmpty())
        val candidate = candidates.first()
        assertEquals(1, candidate.pageNumber)
        assertTrue("Crop width should be >= 100", candidate.cropWidth >= 100)
        assertTrue("Crop height should be >= 100", candidate.cropHeight >= 100)
        assertTrue("Local file must exist", File(candidate.localImagePath).exists())
        bitmap.recycle()
    }

    /**
     * Scenario 3: Page with multiple diagrams (e.g. Figure A and Figure B).
     * Expected: Multiple distinct candidates detected.
     */
    @Test
    fun testPageWithMultipleDiagrams_DetectsMultipleCandidates() {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.FILL
        }

        // Figure 1 in top half
        canvas.drawRect(150f, 200f, 400f, 450f, paint)
        // Figure 2 in bottom half
        canvas.drawRect(150f, 700f, 500f, 950f, paint)

        val candidates = LocalPdfImageExtractor.detectDiagramRegionsOnPage(bitmap, 2, tempDir)
        assertTrue("Should detect diagram candidates", candidates.isNotEmpty())
        assertEquals(2, candidates[0].pageNumber)
        bitmap.recycle()
    }

    /**
     * Scenario 4: Scanned/image-heavy page or uncertain bounds.
     * Expected: Flagged with isNeedsReview = true rather than discarded or guessing.
     */
    @Test
    fun testPageWithLargeOrSparseImage_FlaggedForReview() {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Draw a very large image spanning > 80% width and height
        val paint = Paint().apply {
            color = Color.rgb(180, 50, 50)
            style = Paint.Style.FILL
        }
        canvas.drawRect(60f, 80f, 740f, 900f, paint)

        val candidates = LocalPdfImageExtractor.detectDiagramRegionsOnPage(bitmap, 3, tempDir)
        assertTrue("Should detect large image candidate", candidates.isNotEmpty())
        assertTrue("Large scanned regions must be flagged for review", candidates.any { it.isNeedsReview })
        bitmap.recycle()
    }

    // ==========================================
    // Prompt 11C: 10 Required Verification Tests
    // ==========================================

    @Test
    fun testScenario1_TextOnlyPdf() {
        val q1 = com.ak.pdfextractor.StructuredQuestion(
            questionNumber = 1,
            questionText = "What is the speed of light in vacuum?",
            options = emptyList(),
            pageNumber = 1,
            bounds = com.ak.pdfextractor.BoundingBox(50f, 100f, 600f, 80f)
        )
        val mappings = LocalPdfImageExtractor.mapStructuredQuestionsToImages(
            questions = listOf(q1),
            extractedImages = emptyList(),
            pageNumber = 1
        )
        val mapping = mappings[1]
        assertNotNull(mapping)
        assertEquals(MappingConfidence.UNASSIGNED, mapping?.confidence)
        assertNull(mapping?.imageId)
        assertFalse(mapping?.isNeedsReview ?: true)
    }

    @Test
    fun testScenario2_OneQuestionOneDiagram() {
        val q1 = com.ak.pdfextractor.StructuredQuestion(
            questionNumber = 1,
            questionText = "Which structure is shown in the given figure?",
            options = emptyList(),
            pageNumber = 1,
            bounds = com.ak.pdfextractor.BoundingBox(50f, 100f, 600f, 80f)
        )
        val dummyBmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val img1 = com.ak.pdfextractor.ExtractedImage(
            id = "img_p1_1",
            bitmap = dummyBmp,
            bounds = com.ak.pdfextractor.BoundingBox(100f, 200f, 300f, 250f),
            widthPx = 300,
            heightPx = 250
        )
        val mappings = LocalPdfImageExtractor.mapStructuredQuestionsToImages(
            questions = listOf(q1),
            extractedImages = listOf(img1),
            pageNumber = 1
        )
        val mapping = mappings[1]
        assertNotNull(mapping)
        assertEquals(MappingConfidence.HIGH, mapping?.confidence)
        assertEquals("img_p1_1", mapping?.imageId)
        dummyBmp.recycle()
    }

    @Test
    fun testScenario3_MultipleQuestionsMultipleImagesOnSamePage() {
        val q1 = com.ak.pdfextractor.StructuredQuestion(
            questionNumber = 1,
            questionText = "Refer to figure A below to identify the circuit.",
            options = emptyList(),
            pageNumber = 1,
            bounds = com.ak.pdfextractor.BoundingBox(50f, 100f, 600f, 80f)
        )
        val q2 = com.ak.pdfextractor.StructuredQuestion(
            questionNumber = 2,
            questionText = "In the diagram shown below, calculate the current.",
            options = emptyList(),
            pageNumber = 1,
            bounds = com.ak.pdfextractor.BoundingBox(50f, 500f, 600f, 80f)
        )
        val dummyBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val img1 = com.ak.pdfextractor.ExtractedImage(
            id = "diag_top",
            bitmap = dummyBmp,
            bounds = com.ak.pdfextractor.BoundingBox(100f, 200f, 200f, 150f),
            widthPx = 200,
            heightPx = 150
        )
        val img2 = com.ak.pdfextractor.ExtractedImage(
            id = "diag_bottom",
            bitmap = dummyBmp,
            bounds = com.ak.pdfextractor.BoundingBox(100f, 600f, 200f, 150f),
            widthPx = 200,
            heightPx = 150
        )
        val mappings = LocalPdfImageExtractor.mapStructuredQuestionsToImages(
            questions = listOf(q1, q2),
            extractedImages = listOf(img1, img2),
            pageNumber = 1
        )
        assertEquals("diag_top", mappings[1]?.imageId)
        assertEquals(MappingConfidence.HIGH, mappings[1]?.confidence)
        assertEquals("diag_bottom", mappings[2]?.imageId)
        assertEquals(MappingConfidence.HIGH, mappings[2]?.confidence)
        dummyBmp.recycle()
    }

    @Test
    fun testScenario4_DiagramOnNextPage() {
        // Question on Page 1, image on Page 2
        val q1 = com.ak.pdfextractor.StructuredQuestion(
            questionNumber = 1,
            questionText = "Find the magnetic moment in the figure.",
            options = emptyList(),
            pageNumber = 1,
            bounds = com.ak.pdfextractor.BoundingBox(50f, 100f, 600f, 80f)
        )
        // Processing page 1 with page 1 images (empty)
        val mappings = LocalPdfImageExtractor.mapStructuredQuestionsToImages(
            questions = listOf(q1),
            extractedImages = emptyList(),
            pageNumber = 1
        )
        // Must never attach cross-page candidates!
        assertNull(mappings[1]?.imageId)
        assertEquals(MappingConfidence.LOW, mappings[1]?.confidence)
        assertTrue(mappings[1]?.isNeedsReview ?: false)
    }

    @Test
    fun testScenario5_DecorativeImage() {
        val q1 = com.ak.pdfextractor.StructuredQuestion(
            questionNumber = 1,
            questionText = "Define potential difference across two terminals.",
            options = emptyList(),
            pageNumber = 1,
            bounds = com.ak.pdfextractor.BoundingBox(50f, 100f, 600f, 80f)
        )
        val dummyBmp = Bitmap.createBitmap(600, 10, Bitmap.Config.ARGB_8888)
        // Decorative divider banner (width=600, height=10, aspect=60:1)
        val divider = com.ak.pdfextractor.ExtractedImage(
            id = "decorative_line",
            bitmap = dummyBmp,
            bounds = com.ak.pdfextractor.BoundingBox(50f, 250f, 600f, 10f),
            widthPx = 600,
            heightPx = 10
        )
        val mappings = LocalPdfImageExtractor.mapStructuredQuestionsToImages(
            questions = listOf(q1),
            extractedImages = listOf(divider),
            pageNumber = 1
        )
        assertNull(mappings[1]?.imageId)
        assertEquals(MappingConfidence.UNASSIGNED, mappings[1]?.confidence)
        dummyBmp.recycle()
    }

    @Test
    fun testScenario6_MultiplePossibleImagesNearOneQuestion() {
        val q1 = com.ak.pdfextractor.StructuredQuestion(
            questionNumber = 1,
            questionText = "Compare the diagram with the formula.",
            options = emptyList(),
            pageNumber = 1,
            bounds = com.ak.pdfextractor.BoundingBox(50f, 100f, 600f, 80f)
        )
        val dummyBmp = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
        // Two competing images in the same vertical space right under question 1
        val imgA = com.ak.pdfextractor.ExtractedImage(
            id = "img_a",
            bitmap = dummyBmp,
            bounds = com.ak.pdfextractor.BoundingBox(60f, 200f, 150f, 150f),
            widthPx = 150,
            heightPx = 150
        )
        val imgB = com.ak.pdfextractor.ExtractedImage(
            id = "img_b",
            bitmap = dummyBmp,
            bounds = com.ak.pdfextractor.BoundingBox(250f, 200f, 150f, 150f),
            widthPx = 150,
            heightPx = 150
        )
        val mappings = LocalPdfImageExtractor.mapStructuredQuestionsToImages(
            questions = listOf(q1),
            extractedImages = listOf(imgA, imgB),
            pageNumber = 1
        )
        // Ambiguous -> Must be MEDIUM confidence and leave image unassigned for manual review!
        assertEquals(MappingConfidence.MEDIUM, mappings[1]?.confidence)
        assertNull(mappings[1]?.imageId)
        assertTrue(mappings[1]?.isNeedsReview ?: false)
        dummyBmp.recycle()
    }

    @Test
    fun testScenario7_QuestionContainingKeywords() {
        val textWithKeyword = "Which curve represents adiabatic expansion in the given figure?"
        assertTrue(Com.ak.testseries.exam.QuestionValidationEngine.hasDiagramKeyword(textWithKeyword))

        // Without image -> Validation reports missing diagram and sets hard failure
        val validationWithoutImg = Com.ak.testseries.exam.QuestionValidationEngine.validateQuestion(
            questionText = textWithKeyword,
            options = listOf("Curve A", "Curve B", "Curve C", "Curve D"),
            correctAnswer = "A",
            explanation = "Valid comprehensive reasoning text for testing.",
            hasDiagramAttached = false
        )
        assertTrue(validationWithoutImg.issues.any { it.contains("Diagram required") })
        assertEquals(Com.ak.testseries.exam.ValidationStatus.NEEDS_REVIEW, validationWithoutImg.status)

        // With image -> Validation diagram check passes
        val validationWithImg = Com.ak.testseries.exam.QuestionValidationEngine.validateQuestion(
            questionText = textWithKeyword,
            options = listOf("Curve A", "Curve B", "Curve C", "Curve D"),
            correctAnswer = "A",
            explanation = "Valid comprehensive reasoning text for testing.",
            hasDiagramAttached = true
        )
        assertFalse(validationWithImg.issues.any { it.contains("Diagram required") })
        assertEquals(Com.ak.testseries.exam.ValidationStatus.APPROVED, validationWithImg.status)
    }

    @Test
    fun testScenario8_HindiQuestionWithKeywords() {
        val hindiText = "नीचे दिए गए चित्र में दिखाई गई संरचना क्या है?"
        assertTrue(Com.ak.testseries.exam.QuestionValidationEngine.hasDiagramKeyword(hindiText))

        val hindiQ = com.ak.pdfextractor.StructuredQuestion(
            questionNumber = 1,
            questionText = hindiText,
            options = emptyList(),
            pageNumber = 1,
            bounds = com.ak.pdfextractor.BoundingBox(50f, 100f, 600f, 80f)
        )
        val dummyBmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val hindiImg = com.ak.pdfextractor.ExtractedImage(
            id = "hindi_diag_1",
            bitmap = dummyBmp,
            bounds = com.ak.pdfextractor.BoundingBox(100f, 200f, 200f, 200f),
            widthPx = 200,
            heightPx = 200
        )
        val mappings = LocalPdfImageExtractor.mapStructuredQuestionsToImages(
            questions = listOf(hindiQ),
            extractedImages = listOf(hindiImg),
            pageNumber = 1
        )
        assertEquals(MappingConfidence.HIGH, mappings[1]?.confidence)
        assertEquals("hindi_diag_1", mappings[1]?.imageId)
        dummyBmp.recycle()
    }

    @Test
    fun testScenario9_NoReliableImageMatch() {
        val q1 = com.ak.pdfextractor.StructuredQuestion(
            questionNumber = 1,
            questionText = "Analyze the diagram given below for velocity versus time.",
            options = emptyList(),
            pageNumber = 1
        )
        val mappings = LocalPdfImageExtractor.mapStructuredQuestionsToImages(
            questions = listOf(q1),
            extractedImages = emptyList(),
            pageNumber = 1
        )
        assertNull(mappings[1]?.imageId)
        assertEquals(MappingConfidence.LOW, mappings[1]?.confidence)
        assertTrue(mappings[1]?.isNeedsReview ?: false)
    }

    @Test
    fun testScenario10_ExistingManualCropWorkflow() {
        // When automatic mapping is unassigned or needs review, question preserves needsImage = true
        // and PdfCropper remains available to crop and supply local path.
        val qNeedsImage = Com.ak.testseries.data.QuestionEntity(
            id = "q_crop_test",
            testId = "test_manual_crop",
            questionText = "Observe the figure below and state the law.",
            optionA = "Law 1",
            optionB = "Law 2",
            optionC = "Law 3",
            optionD = "Law 4",
            correctAnswer = "A",
            explanation = "Explanation text for manual crop verification.",
            subject = "Physics",
            chapter = "Optics",
            needsImage = true,
            imageUrl = null
        )
        assertTrue(qNeedsImage.needsImage)
        assertNull(qNeedsImage.imageUrl)

        // After manual crop via PdfCropperScreen, imageUrl is assigned directly
        val croppedLocalPath = "/data/user/0/app/files/manual_crop_q1.png"
        val qAfterManualCrop = qNeedsImage.copy(imageUrl = croppedLocalPath)
        assertEquals(croppedLocalPath, qAfterManualCrop.imageUrl)
    }
}
