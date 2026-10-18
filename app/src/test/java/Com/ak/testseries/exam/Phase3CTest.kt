package Com.ak.testseries.exam

import org.junit.Test
import org.junit.Assert.*
import Com.ak.testseries.data.SyllabusData

class Phase3CTest {

    @Test
    fun test50QuestionBatchPipeline() {
        val engine = QuestionValidationEngine
        val duplicateEngine = DuplicateDetectionEngine
        
        var approvedCount = 0
        var needsReviewCount = 0
        var rejectedCount = 0
        
        var falseApprovalCount = 0
        
        val existingHashes = mutableMapOf<String, String>()
        
        // Generate 50 questions
        for (i in 1..50) {
            var qText = "Physics Concept $i: What is the SI unit and formula for quantity $i in physics mechanics?"
            var options = listOf("Newton", "Joule", "Watt", "Pascal")
            var sysAnswer = "A"
            var sourceAnswer = "A"
            var hasDiagram = false
            var dupResult: DuplicateDetectionEngine.DuplicateResult? = null
            
            // Inject deliberate failures based on index to simulate real extraction issues
            when (i) {
                // Missing Option (2 questions)
                5, 15 -> options = listOf("Newton", "Joule") 
                // Missing Diagram (4 questions)
                10, 20, 30, 40 -> {
                    qText = "Based on the diagram given below, find X."
                    hasDiagram = false
                }
                // Golden Answer Mismatch (5 questions)
                12, 22, 32, 42, 48 -> {
                    sourceAnswer = "B"
                }
                // Invalid Answer (2 questions)
                8, 18 -> sysAnswer = "E"
                // Multiple answers (2 questions)
                7, 17 -> sysAnswer = "A,B"
                // Exact Duplicate (2 questions)
                25, 35 -> {
                    val hash = duplicateEngine.generateNormalizedHash(qText, options)
                    existingHashes[hash] = duplicateEngine.normalizeText(qText)
                    dupResult = duplicateEngine.checkDuplicate(qText, options, existingHashes)
                }
                // Near Duplicate (2 questions)
                26, 36 -> {
                    val nearText = "Physics Concept 25: What is the SI unit and formula for quantity 25 in physics mechanics?"
                    val hash = duplicateEngine.generateNormalizedHash(nearText, options)
                    existingHashes[hash] = duplicateEngine.normalizeText(nearText)
                    dupResult = duplicateEngine.checkDuplicate(qText, options, existingHashes)
                }
                // Easy, Medium, Hard distribution simulation
                in 41..45 -> { // Calculation/Hard
                    qText = "Calculate the maximum velocity if the relation is v = 2t + 3."
                }
                in 46..50 -> { // Concept/Medium
                    qText = "According to the principle of conservation of energy, what happens therefore?"
                }
            }
            
            val result = engine.validateQuestion(
                questionText = qText,
                options = options,
                correctAnswer = sysAnswer,
                explanation = "Standard explanation for $i",
                hasDiagramAttached = hasDiagram,
                duplicateResult = dupResult,
                sourceAnswer = sourceAnswer
            )
            
            when (result.status) {
                ValidationStatus.APPROVED -> approvedCount++
                ValidationStatus.NEEDS_REVIEW -> needsReviewCount++
                ValidationStatus.REJECTED -> rejectedCount++
                ValidationStatus.DUPLICATE -> rejectedCount++ // Treating duplicate as rejected for counts
                else -> {}
            }
            
            // Verification: Safety check - ensure no bad questions were approved
            if (result.status == ValidationStatus.APPROVED) {
                if (options.size < 4 || sysAnswer != sourceAnswer || sysAnswer == "E" || sysAnswer.contains(",") || qText.contains("diagram given below")) {
                    falseApprovalCount++
                }
            }
            
            // Add normal questions to hash map to test duplicates later
            if (result.status == ValidationStatus.APPROVED && dupResult == null) {
                val hash = duplicateEngine.generateNormalizedHash(qText, options)
                existingHashes[hash] = duplicateEngine.normalizeText(qText)
            }
        }
        
        println("Processed 50 Questions.")
        println("Approved: $approvedCount")
        println("Needs Review: $needsReviewCount")
        println("Rejected/Duplicate: $rejectedCount")
        println("False Approvals: $falseApprovalCount")
        
        assertEquals("There should be ZERO false approvals", 0, falseApprovalCount)
        assertTrue("Should have expected approved count (got $approvedCount)", approvedCount >= 25)
    }
    
    @Test
    fun testSyllabusRegression() {
        // Verify NEET
        val neetSubjects = SyllabusData.getSubjectsForExam("NEET")
        assertTrue(neetSubjects.contains("Physics"))
        assertTrue(neetSubjects.contains("Biology") || (neetSubjects.contains("Botany") && neetSubjects.contains("Zoology")))
        assertFalse(neetSubjects.contains("Mathematics"))
        
        // Verify SSC
        val sscSubjects = SyllabusData.getSubjectsForExam("SSC CGL")
        assertTrue(sscSubjects.contains("Quantitative Aptitude"))
        assertTrue(sscSubjects.contains("English") || sscSubjects.contains("English Comprehension"))
        assertFalse(sscSubjects.contains("Biology")) // Uses GA and GS instead
        
        println("Syllabus Regression Test Passed.")
    }

    @Test
    fun testUnknownAnswerScoringSafety() {
        val qUnknown = Com.ak.testseries.data.QuestionEntity(
            id = "q_ocr_unverified",
            testId = "test_private_1",
            questionText = "Question with no answer key",
            optionA = "Choice A",
            optionB = "Choice B",
            optionC = "Choice C",
            optionD = "Choice D",
            correctAnswer = "", // UNKNOWN answer key from OCR
            explanation = "",
            subject = "Physics",
            chapter = "Motion"
        )

        // Case 1: Student skips -> SKIPPED, score = 0
        val breakdownSkipped = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "NEET",
            questions = listOf(qUnknown),
            userAnswers = emptyMap(),
            timeSpentSeconds = 30
        )
        assertEquals(0, breakdownSkipped.correctCount)
        assertEquals(0, breakdownSkipped.wrongCount)
        assertEquals(1, breakdownSkipped.unansweredCount)
        assertEquals(0.0, breakdownSkipped.finalScore, 0.001)
        assertEquals(0.0, breakdownSkipped.negativeMarksPenalty, 0.001)

        // Case 2: Student selects an option while answer key is unknown -> UNVERIFIED, score = 0, no negative marking penalty
        val breakdownAnswered = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "NEET",
            questions = listOf(qUnknown),
            userAnswers = mapOf("q_ocr_unverified" to "A"),
            timeSpentSeconds = 30
        )
        assertEquals(0, breakdownAnswered.correctCount)
        assertEquals(0, breakdownAnswered.wrongCount)
        assertEquals(0.0, breakdownAnswered.finalScore, 0.001)
        assertEquals(0.0, breakdownAnswered.negativeMarksPenalty, 0.001)
        assertEquals(0.0, breakdownAnswered.accuracyPercent, 0.001)
    }
}
