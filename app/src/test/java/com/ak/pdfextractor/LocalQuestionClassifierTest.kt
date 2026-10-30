package com.ak.pdfextractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalQuestionClassifierTest {

    @Test
    fun testExtractStructuredQuestionsWithInlineOptions() {
        val dummyBounds = BoundingBox(0f, 0f, 100f, 20f)
        val textBlocks = listOf(
            TextBlock(
                id = "b1",
                text = "1. Which of the following is a unit of energy? (A) Watt (B) Joule (C) Newton (D) Pascal",
                bounds = dummyBounds
            ),
            TextBlock(
                id = "b2",
                text = "Q.2 What is the SI unit of electric current? (A) Ampere (B) Volt (C) Ohm (D) Coulomb",
                bounds = dummyBounds
            )
        )

        val questions = LocalQuestionClassifier.extractStructuredQuestions(textBlocks)
        assertEquals(2, questions.size)

        val q1 = questions[0]
        assertEquals(1, q1.questionNumber)
        assertTrue(q1.questionText.contains("Which of the following is a unit of energy?"))
        assertEquals(4, q1.options.size)
        assertEquals("A", q1.options[0].key)
        assertEquals("Watt", q1.options[0].text)
        assertEquals("B", q1.options[1].key)
        assertEquals("Joule", q1.options[1].text)
        assertEquals("C", q1.options[2].key)
        assertEquals("Newton", q1.options[2].text)
        assertEquals("D", q1.options[3].key)
        assertEquals("Pascal", q1.options[3].text)
        assertTrue(q1.hasAllFourOptions)

        val q2 = questions[1]
        assertEquals(2, q2.questionNumber)
        assertTrue(q2.hasAllFourOptions)
    }

    @Test
    fun testExtractStructuredQuestionsWithSeparateLines() {
        val dummyBounds = BoundingBox(0f, 0f, 100f, 20f)
        val textBlocks = listOf(
            TextBlock(
                id = "b1",
                text = "Question 1: The speed of light in vacuum is approximately:\n(A) 3 x 10^8 m/s\n(B) 3 x 10^6 m/s\n(C) 3 x 10^5 km/s\n(D) 3 x 10^3 m/s",
                bounds = dummyBounds
            )
        )

        val questions = LocalQuestionClassifier.extractStructuredQuestions(textBlocks)
        assertEquals(1, questions.size)

        val q1 = questions[0]
        assertEquals(1, q1.questionNumber)
        assertTrue(q1.questionText.contains("speed of light"))
        assertEquals(4, q1.options.size)
        assertTrue(q1.hasAllFourOptions)
        assertEquals("3 x 10^8 m/s", q1.options[0].text)
        assertEquals("3 x 10^6 m/s", q1.options[1].text)
    }

    @Test
    fun testClassifyMcqQuestionPaper() {
        val dummyBounds = BoundingBox(0f, 0f, 100f, 20f)
        val dummyOsd = OsdResult(0, "Auto", 0f)

        // Mock bitmap is not accessed by classifyPdf, but we provide mock or create empty if needed.
        // classifyPdf operates purely on textBlocks, fullText, and pre-extracted questions.
        val questions = listOf(
            StructuredQuestion(
                questionNumber = 1,
                questionText = "What is the capital of France?",
                options = listOf(
                    StructuredOption("A", "London"),
                    StructuredOption("B", "Berlin"),
                    StructuredOption("C", "Paris"),
                    StructuredOption("D", "Madrid")
                )
            ),
            StructuredQuestion(
                questionNumber = 2,
                questionText = "Which planet is known as the Red Planet?",
                options = listOf(
                    StructuredOption("A", "Earth"),
                    StructuredOption("B", "Mars"),
                    StructuredOption("C", "Jupiter"),
                    StructuredOption("D", "Venus")
                )
            )
        )

        val dummyPage = PdfPageResult(
            pageNumber = 1,
            textBlocks = emptyList(),
            extractedImages = emptyList(),
            layoutAssociations = emptyList(),
            regionRois = emptyList(),
            osdResult = dummyOsd,
            renderedBitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888),
            annotatedBitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888),
            correctedBitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888),
            fullText = "1. What is the capital of France? (A) London (B) Berlin (C) Paris (D) Madrid\n2. Which planet is known as the Red Planet? (A) Earth (B) Mars (C) Jupiter (D) Venus",
            totalCharCount = 200,
            structuredQuestions = questions
        )

        val result = LocalQuestionClassifier.classifyPdf(listOf(dummyPage))
        assertEquals(PdfDocumentType.MCQ_QUESTION_PAPER, result.documentType)
        assertEquals(2, result.totalQuestionsDetected)
        assertEquals(2, result.questionsWithAllOptions)
        assertTrue(result.confidenceScore >= 0.85f)
        assertTrue(result.reasoning.contains("multiple-choice exam paper verified locally"))
    }

    @Test
    fun testClassifyAnswerKey() {
        val dummyOsd = OsdResult(0, "Auto", 0f)
        val answerKeyText = "NEET 2026 OFFICIAL ANSWER KEY & SOLUTIONS\n1. (A)\n2. (B)\n3. (C)\n4. (D)\n5. (A)\n6. (C)\n7. (B)"

        val textBlocks = listOf(
            TextBlock("b0", "NEET 2026 OFFICIAL ANSWER KEY & SOLUTIONS", BoundingBox(0f, 0f, 100f, 20f)),
            TextBlock("b1", "1. (A)", BoundingBox(0f, 20f, 100f, 20f)),
            TextBlock("b2", "2. (B)", BoundingBox(0f, 40f, 100f, 20f)),
            TextBlock("b3", "3. (C)", BoundingBox(0f, 60f, 100f, 20f)),
            TextBlock("b4", "4. (D)", BoundingBox(0f, 80f, 100f, 20f)),
            TextBlock("b5", "5. (A)", BoundingBox(0f, 100f, 100f, 20f))
        )

        val dummyPage = PdfPageResult(
            pageNumber = 1,
            textBlocks = textBlocks,
            extractedImages = emptyList(),
            layoutAssociations = emptyList(),
            regionRois = emptyList(),
            osdResult = dummyOsd,
            renderedBitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888),
            annotatedBitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888),
            correctedBitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888),
            fullText = answerKeyText,
            totalCharCount = answerKeyText.length
        )

        val result = LocalQuestionClassifier.classifyPdf(listOf(dummyPage))
        assertEquals(PdfDocumentType.ANSWER_KEY_OR_SOLUTIONS, result.documentType)
        assertTrue(result.confidenceScore >= 0.90f)
    }

    @Test
    fun testClassifyStudyMaterial() {
        val dummyOsd = OsdResult(0, "Auto", 0f)
        val notesText = """
            Chapter 4: Laws of Motion
            Introduction and Fundamental Definition of Force
            Sir Isaac Newton formulated three fundamental laws governing mechanics.
            The first law is also called the principle of inertia.
            Formula: F = ma where F is the applied external force, m is inertial mass, and a is acceleration.
            In summary, inertia represents the intrinsic resistance of matter to changes in velocity.
            Notes and Important Formulas to remember for competitive exams.
        """.trimIndent()

        val textBlocks = listOf(
            TextBlock("b1", notesText, BoundingBox(0f, 0f, 100f, 200f))
        )

        val dummyPage = PdfPageResult(
            pageNumber = 1,
            textBlocks = textBlocks,
            extractedImages = emptyList(),
            layoutAssociations = emptyList(),
            regionRois = emptyList(),
            osdResult = dummyOsd,
            renderedBitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888),
            annotatedBitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888),
            correctedBitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888),
            fullText = notesText,
            totalCharCount = notesText.length
        )

        val result = LocalQuestionClassifier.classifyPdf(listOf(dummyPage))
        assertEquals(PdfDocumentType.STUDY_MATERIAL_OR_NOTES, result.documentType)
        assertEquals(0, result.totalQuestionsDetected)
    }

    @Test
    fun testAutoRouteToPrivateWorkflowWhenQuestionsDetected() {
        val testPaperRawText = """
            NEET UG 2026 MOCK TEST - PHYSICS & CHEMISTRY
            Time: 180 Minutes | Total Marks: 720
            
            1. The dimension of universal gravitational constant G is:
            (A) [M^-1 L^3 T^-2]
            (B) [M L^2 T^-1]
            (C) [M^0 L^0 T^0]
            (D) [M^-2 L^2 T^-1]
            
            2. In a photoelectric experiment, stopping potential depends on:
            (A) Intensity of incident light
            (B) Frequency of incident radiation
            (C) Distance from source
            (D) Area of the plate
            
            3. Which of the following is a diamagnetic substance?
            (A) Bismuth
            (B) Aluminum
            (C) Iron
            (D) Platinum
        """.trimIndent()

        val classification = LocalQuestionClassifier.classifyRawText(testPaperRawText)
        assertTrue(classification.totalQuestionsDetected >= 2)
        assertEquals(PdfDocumentType.MCQ_QUESTION_PAPER, classification.documentType)

        val recommendedWorkflow = LocalQuestionClassifier.getRecommendedWorkflow(classification)
        assertEquals("private", recommendedWorkflow)

        val reason = LocalQuestionClassifier.getRoutingReason(classification)
        assertTrue(reason.contains("Private Vault"))
        assertTrue(reason.contains("questions"))
    }

    @Test
    fun testAutoRouteToPublicWorkflowWhenRawTextNotes() {
        val notesRawText = """
            Biomolecules Revision Notes for NEET Aspirants
            Proteins are polymers of amino acids connected by peptide bonds.
            There are 20 standard amino acids involved in translation.
            Primary structure refers to the linear sequence of polypeptide chain.
            Secondary structure includes alpha-helix and beta-pleated sheets stabilized by hydrogen bonds.
            Tertiary structure is the three-dimensional conformation essential for biological activity.
            Enzymes act as biocatalysts by lowering the activation energy barrier of chemical reactions.
            Summary and key definitions for comprehensive revision.
        """.trimIndent()

        val classification = LocalQuestionClassifier.classifyRawText(notesRawText)
        assertEquals(0, classification.totalQuestionsDetected)
        assertEquals(PdfDocumentType.STUDY_MATERIAL_OR_NOTES, classification.documentType)

        val recommendedWorkflow = LocalQuestionClassifier.getRecommendedWorkflow(classification)
        assertEquals("public", recommendedWorkflow)

        val reason = LocalQuestionClassifier.getRoutingReason(classification)
        assertTrue(reason.contains("Public Community"))
    }

    @Test
    fun testAutoRouteToPublicWorkflowWhenUnstructuredGeneralText() {
        val generalRawText = """
            Announcement: Annual science seminar scheduled for next Monday at main auditorium.
            All faculty and students are requested to assemble by 9 AM sharp.
            Guest lectures on contemporary developments in renewable energy and nanotechnology.
        """.trimIndent()

        val classification = LocalQuestionClassifier.classifyRawText(generalRawText)
        assertEquals(0, classification.totalQuestionsDetected)

        val recommendedWorkflow = LocalQuestionClassifier.getRecommendedWorkflow(classification)
        assertEquals("public", recommendedWorkflow)
    }

    @Test
    fun testPdfPageChunking() {
        val samplePages = listOf(
            "Page 1: Mechanics and Kinematics",
            "Page 2: Laws of Motion",
            "Page 3: Work, Energy and Power",
            "Page 4: Gravitation and Orbital Mechanics",
            "Page 5: Thermodynamics and Heat Transfer"
        )
        val pageChunkSize = 2
        val totalChunks = (samplePages.size + pageChunkSize - 1) / pageChunkSize
        assertEquals(3, totalChunks)

        // Chunk 1: Pages 1..2
        val chunk1Start = 0 * pageChunkSize + 1
        val chunk1End = minOf((0 + 1) * pageChunkSize, samplePages.size)
        assertEquals(1, chunk1Start)
        assertEquals(2, chunk1End)
        val chunk1Pages = samplePages.subList(chunk1Start - 1, chunk1End)
        assertEquals(2, chunk1Pages.size)
        assertTrue(chunk1Pages[0].contains("Page 1"))
        assertTrue(chunk1Pages[1].contains("Page 2"))

        // Chunk 2: Pages 3..4
        val chunk2Start = 1 * pageChunkSize + 1
        val chunk2End = minOf((1 + 1) * pageChunkSize, samplePages.size)
        assertEquals(3, chunk2Start)
        assertEquals(4, chunk2End)
        val chunk2Pages = samplePages.subList(chunk2Start - 1, chunk2End)
        assertEquals(2, chunk2Pages.size)
        assertTrue(chunk2Pages[0].contains("Page 3"))
        assertTrue(chunk2Pages[1].contains("Page 4"))

        // Chunk 3: Page 5
        val chunk3Start = 2 * pageChunkSize + 1
        val chunk3End = minOf((2 + 1) * pageChunkSize, samplePages.size)
        assertEquals(5, chunk3Start)
        assertEquals(5, chunk3End)
        val chunk3Pages = samplePages.subList(chunk3Start - 1, chunk3End)
        assertEquals(1, chunk3Pages.size)
        assertTrue(chunk3Pages[0].contains("Page 5"))

        // Resume behavior: starting from currentChunkIndex = 2 skips chunks 0 and 1
        val resumedChunkIndex = 2
        val remainingChunks = (resumedChunkIndex until totalChunks).toList()
        assertEquals(listOf(2), remainingChunks)
    }

    @Test
    fun testAnomalyDetectionIncompleteOptionsFlaggedForReview() {
        val dummyBounds = BoundingBox(0f, 0f, 100f, 20f)
        val textBlocks = listOf(
            TextBlock(
                id = "b1",
                text = "1. Which element has the highest electronegativity? (A) Fluorine (B) Chlorine",
                bounds = dummyBounds
            )
        )

        val questions = LocalQuestionClassifier.extractStructuredQuestions(textBlocks)
        assertEquals(1, questions.size)
        val q1 = questions[0]
        assertEquals(1, q1.questionNumber)
        assertEquals(2, q1.options.size)
        assertTrue("Question with incomplete options must be flagged for review", q1.isNeedsReview)
        assertNotNull("Anomaly reason should be present", q1.anomalyReason)
        assertTrue(q1.anomalyReason!!.contains("Incomplete options"))
    }

    @Test
    fun testAnomalyDetectionBrokenMathGlyphsFlaggedForReview() {
        val dummyBounds = BoundingBox(0f, 0f, 100f, 20f)
        val textBlocks = listOf(
            TextBlock(
                id = "b1",
                text = "1. The integral ??? dt is equal to \ufffd (A) 0 (B) 1 (C) -1 (D) Infinity",
                bounds = dummyBounds
            )
        )

        val questions = LocalQuestionClassifier.extractStructuredQuestions(textBlocks)
        assertEquals(1, questions.size)
        val q1 = questions[0]
        assertEquals(1, q1.questionNumber)
        assertTrue("Broken math glyphs must trigger needsReview flag", q1.isNeedsReview)
        assertNotNull(q1.anomalyReason)
        assertTrue(q1.anomalyReason!!.contains("Broken math") || q1.anomalyReason!!.contains("unparsed"))
    }

    @Test
    fun testCleanCompleteFourOptionsNotFlaggedForReview() {
        val dummyBounds = BoundingBox(0f, 0f, 100f, 20f)
        val textBlocks = listOf(
            TextBlock(
                id = "b1",
                text = "1. What is the value of gravitational acceleration? (A) 9.8 m/s^2 (B) 8.9 m/s^2 (C) 10.5 m/s^2 (D) 9.0 m/s^2",
                bounds = dummyBounds
            )
        )

        val questions = LocalQuestionClassifier.extractStructuredQuestions(textBlocks)
        assertEquals(1, questions.size)
        val q1 = questions[0]
        assertEquals(4, q1.options.size)
        assertTrue(q1.hasAllFourOptions)
        org.junit.Assert.assertFalse("Clean complete question must not be flagged for review", q1.isNeedsReview)
        org.junit.Assert.assertNull(q1.anomalyReason)
    }
}
