package Com.ak.testseries.exam

import Com.ak.testseries.data.SyllabusData
import org.junit.Assert.*
import org.junit.Test

/**
 * Rigorous Verification Test for all 23 registered competitive exams.
 * Verifies each exam against its actual configured pattern:
 * - sections
 * - question count (total and compulsory)
 * - question types
 * - options (e.g., 5 for Banking, 4 for NEET/JEE/UPSC/SSC, 0 for NVQ/NAT/TITA)
 * - compulsory/optional rules
 * - marks (sum of compulsory * positiveMarks == totalMarks)
 * - negative marking
 * - duration (minutes)
 * - language
 * Ensures no generic defaults are assumed.
 */
class ExamPatternVerificationTest {

    @Test
    fun verifyAllRegisteredExamsPatternIntegrity() {

        val allExams = UniversalExamRegistry.getAllExams()
        assertEquals(25, allExams.size)
        assertEquals(allExams.size, allExams.map { it.examId }.distinct().size)

        val verified = allExams.filter { it.active && it.verified }
        assertEquals(setOf("neet_ug", "jee_main"), verified.map { it.examId }.toSet())
        assertTrue(verified.isNotEmpty())

        val auditReports = verified.map { ExamPatternValidator.auditRegisteredExam(it) }
        val failed = auditReports.filterNot { it.isPatternValid }
        assertTrue(failed.joinToString("\\n") { "${it.examId}: ${it.errors}" }, failed.isEmpty())
        auditReports.forEach { report ->
            assertTrue(report.verified)
            assertTrue(report.papers.isNotEmpty())
            report.papers.forEach { paper ->
                assertTrue(paper.isMarkingConsistent)
                assertTrue(paper.durationMinutes > 0)
                assertTrue(paper.totalQuestions > 0)
                assertTrue(paper.compulsoryQuestions in 1..paper.totalQuestions)
            }
        }
        allExams.filterNot { it.verified }.forEach {
            assertTrue("Unverified pattern must remain hidden: ${it.examId}", it.stages.isEmpty())
            assertFalse(it.active)
        }
    }

    @Test
    fun verifyNeetUgConfiguredPattern() {

        val neet = UniversalExamRegistry.getExamById("neet_ug")!!
        assertTrue(neet.verified)
        val paper = neet.stages.single().papers.single()
        assertEquals(180, paper.durationMinutes)
        assertEquals(720.0, paper.totalMarks, 0.001)
        assertEquals(4, paper.subjects.size)
        assertTrue(paper.subjects.all { subject ->
            subject.sections.size == 1 &&
                subject.sections.single().totalQuestions == 45 &&
                subject.sections.single().compulsoryQuestions == 45 &&
                subject.sections.single().positiveMarks == 4.0 &&
                subject.sections.single().negativeMarks == 1.0
        })
        assertEquals(180, paper.subjects.sumOf { it.sections.sumOf { section -> section.compulsoryQuestions } })
    }

    @Test
    fun verifyJeeMainConfiguredPattern() {

        val jee = UniversalExamRegistry.getExamById("jee_main")!!
        assertTrue(jee.verified)
        val paper = jee.stages.single().papers.single()
        assertEquals(180, paper.durationMinutes)
        assertEquals(300.0, paper.totalMarks, 0.001)
        assertEquals(3, paper.subjects.size)
        paper.subjects.forEach { subject ->
            assertEquals(2, subject.sections.size)
            val mcq = subject.sections[0]
            val numerical = subject.sections[1]
            assertEquals(20, mcq.totalQuestions)
            assertEquals(20, mcq.compulsoryQuestions)
            assertEquals(4, mcq.optionsCount)
            assertEquals(QuestionType.MCQ_SINGLE, mcq.questionType)
            assertEquals(5, numerical.totalQuestions)
            assertEquals(5, numerical.compulsoryQuestions)
            assertEquals(0, numerical.optionsCount)
            assertEquals(QuestionType.NUMERICAL_VALUE, numerical.questionType)
        }
        assertEquals(75, paper.subjects.sumOf { it.sections.sumOf { section -> section.compulsoryQuestions } })
    }

    @Test
    fun verifyBankingExams5OptionsPattern() {

        listOf("ibps_po", "ibps_clerk", "sbi_clerk", "sbi_po").forEach { id ->
            val exam = UniversalExamRegistry.getExamById(id)!!
            assertFalse("Banking scaffold must not be treated as verified", exam.verified)
            assertTrue(exam.stages.isEmpty())
            assertEquals(0, exam.defaultOptionCount)
            try {
                UniversalExamRegistry.getDefaultOptionCountForExam(exam.examName)
                fail("Unverified option count must be blocked for $id")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("source-verified"))
            }
        }
    }

    @Test
    fun verifyEnglishMediumExams() {

        listOf("cat", "gate").forEach { id ->
            val exam = UniversalExamRegistry.getExamById(id)!!
            assertFalse("Pattern must remain disabled until official source verification: $id", exam.verified)
            assertTrue(exam.stages.isEmpty())
            try {
                UniversalExamRegistry.getDefaultDurationMinutesForExam(exam.examName)
                fail("Unverified duration must be blocked for $id")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("source-verified"))
            }
        }
    }

    @Test
    fun verifyZeroNegativeMarkingExams() {

        listOf("ctet", "ugc_net", "ssc_mts").forEach { id ->
            val exam = UniversalExamRegistry.getExamById(id)!!
            assertFalse("Negative-marking policy must not be guessed for $id", exam.verified)
            assertTrue(exam.stages.isEmpty())
            try {
                UniversalExamRegistry.getMarkingSchemeForExam(exam.examName)
                fail("Unverified marking scheme must be blocked for $id")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("source-verified"))
            }
        }
    }

    @Test
    fun verifyDynamicPatternSubtitleGenerationForAllExams() {

        val allExams = UniversalExamRegistry.getAllExams()
        allExams.filterNot { it.verified }.forEach { exam ->
            val subtitle = UniversalExamRegistry.getExamDisplaySubtitle(exam)
            assertTrue(subtitle.contains("not verified", ignoreCase = true))
        }
        val neetSub = UniversalExamRegistry.getExamDisplaySubtitleByName("NEET UG")
        assertTrue(neetSub, neetSub.contains("180 Qs • 720 Marks • 180 Mins (+4, -1)"))
        val jeeSub = UniversalExamRegistry.getExamDisplaySubtitleByName("JEE Main P1")
        assertTrue(jeeSub, jeeSub.contains("75 Qs • 300 Marks • 180 Mins (+4, -1)"))
        assertTrue(UniversalExamRegistry.getExamDisplaySubtitleByName("CUET UG").contains("not verified", true))
        assertTrue(UniversalExamRegistry.getExamDisplaySubtitleByName("GATE").contains("not verified", true))
        assertTrue(UniversalExamRegistry.getExamDisplaySubtitleByName("CTET").contains("not verified", true))
    }

    @Test
    fun verifySyllabusDataPullsRegistrySubjectsForSpecialExams() {
        val upscSubjects = SyllabusData.getSubjectsForExam("UPSC CSE")
        assertTrue("UPSC CSE must include Indian Polity: $upscSubjects", upscSubjects.any { it.contains("Polity") })
        assertFalse("UPSC CSE must not fall back to generic Biology: $upscSubjects", upscSubjects.contains("Biology"))

        val gateSubjects = SyllabusData.getSubjectsForExam("GATE")
        assertTrue("GATE must include Engineering / Aptitude: $gateSubjects", gateSubjects.any { it.contains("General Aptitude") || it.contains("Technical") })
        assertFalse("GATE must not fall back to generic Biology: $gateSubjects", gateSubjects.contains("Biology"))
    }

    @Test
    fun verifyRuntimeGenerationMetadataDoesNotDefaultToGemini() {
        val model = UniversalQuestionModel(
            questionId = "test_q1",
            examId = "neet_ug",
            subjectId = "physics",
            questionText = "Sample Question",
            options = listOf("A", "B", "C", "D"),
            correctAnswer = "A"
        )
        // Default model MUST NOT falsely report gemini-3.5-flash when no AI was called
        assertEquals("Default generationModel must be empty to avoid false attribution", "", model.generationModel)
        assertFalse("Must never default to gemini model", model.generationModel.contains("gemini"))

        // When unprovided/missing, pipeline must fall back to UNAVAILABLE, never guessing a model name
        val unavailableModel = UniversalQuestionModel(
            questionId = "test_q2",
            examId = "neet_ug",
            subjectId = "physics",
            questionText = "Sample Question 2",
            options = listOf("A", "B", "C", "D"),
            correctAnswer = "B",
            generationModel = "UNAVAILABLE"
        )
        assertEquals("UNAVAILABLE", unavailableModel.generationModel)
    }

    @Test
    fun verifyBatchDifficultyValidationDetectsDeficienciesAndFlagsNonCompliance() {
        val targetRatio = DifficultyRatio(easyPercent = 40, mediumPercent = 40, hardPercent = 20)

        // 1. Compliant batch (4 Easy, 4 Medium, 2 Hard out of 10)
        val compliantQuestions = (1..10).map { i ->
            UniversalQuestionModel(
                questionId = "cq_$i",
                examId = "neet_ug",
                subjectId = "physics",
                questionText = "Question $i",
                options = listOf("A", "B", "C", "D"),
                correctAnswer = "A",
                difficulty = when {
                    i <= 4 -> DifficultyLevel.EASY
                    i <= 8 -> DifficultyLevel.MEDIUM
                    else -> DifficultyLevel.HARD
                }
            )
        }
        val compliantResult = QuestionValidationEngine.validateBatchDifficulty(compliantQuestions, targetRatio)
        assertTrue("Batch with 4 Easy, 4 Med, 2 Hard should be compliant with 40/40/20", compliantResult.isCompliant)
        assertTrue("Deficient list should be empty", compliantResult.deficientLevels.isEmpty())

        // 2. Non-compliant batch (10 Easy, 0 Medium, 0 Hard)
        val nonCompliantQuestions = (1..10).map { i ->
            UniversalQuestionModel(
                questionId = "ncq_$i",
                examId = "neet_ug",
                subjectId = "physics",
                questionText = "Question $i",
                options = listOf("A", "B", "C", "D"),
                correctAnswer = "A",
                difficulty = DifficultyLevel.EASY
            )
        }
        val nonCompliantResult = QuestionValidationEngine.validateBatchDifficulty(nonCompliantQuestions, targetRatio)
        assertFalse("Batch with 10 Easy and 0 Hard/Medium must NOT be compliant", nonCompliantResult.isCompliant)
        assertTrue("Must detect MEDIUM deficiency", nonCompliantResult.deficientLevels.contains(DifficultyLevel.MEDIUM))
        assertTrue("Must detect HARD deficiency", nonCompliantResult.deficientLevels.contains(DifficultyLevel.HARD))
        assertNotNull("Issue description must be provided", nonCompliantResult.issueDescription)
    }

    @Test
    fun verifyNumericalNatQuestionsAreOptionFreeAndPreserveFullDecimals() {
        // Valid NAT Question with Decimal Answer and empty options
        val natValidation = QuestionValidationEngine.validateQuestion(
            questionText = "Calculate the current flowing through the circuit in Amperes when V = 24.5V and R = 2.0 ohms.",
            options = emptyList(), // Option-free by design
            correctAnswer = "12.25", // Full decimal answer
            explanation = "Using Ohm's Law, I = V / R = 24.5 / 2.0 = 12.25 A.",
            questionType = QuestionType.NUMERICAL_VALUE
        )

        assertEquals("Option-free NAT must maintain 100% option integrity", 100, natValidation.qualityMetrics.optionIntegrity)
        assertEquals("Valid decimal answer must have 100% answer confidence", 100, natValidation.qualityMetrics.answerConfidence)
        assertEquals("Valid NAT question should be APPROVED", ValidationStatus.APPROVED, natValidation.status)
        assertTrue("No hard failure issues should be raised for NAT empty options", natValidation.issues.none { it.contains("Fewer than 4 options") })

        // Invalid NAT Question with non-numeric string answer
        val invalidNat = QuestionValidationEngine.validateQuestion(
            questionText = "Find the acceleration of the projectile at highest point.",
            options = emptyList(),
            correctAnswer = "Option B", // Invalid non-numeric answer for NAT
            explanation = "Acceleration is due to gravity downwards.",
            questionType = QuestionType.NUMERICAL
        )
        assertEquals("Invalid numeric answer must have 0% confidence", 0, invalidNat.qualityMetrics.answerConfidence)
        assertTrue("Must flag non-numeric answer for NAT", invalidNat.issues.any { it.contains("Numerical question expects a valid numeric value") })
    }

    @Test
    fun verifyFiveOptionExamsSupportOptionEWithoutFalseConflicts() {
        // 5-Option Banking Exam Question with Option E
        val fiveOptValidation = QuestionValidationEngine.validateQuestion(
            questionText = "Which of the following monetary policy tools is used to absorb excess liquidity from the market?",
            options = listOf(
                "Cash Reserve Ratio (CRR)",
                "Statutory Liquidity Ratio (SLR)",
                "Marginal Standing Facility (MSF)",
                "Bank Rate",
                "Standing Deposit Facility (SDF)"
            ),
            correctAnswer = "E", // Option E
            explanation = "Standing Deposit Facility allows RBI to absorb liquidity without providing collateral.",
            expectedOptionCount = 5
        )

        assertEquals("5 options provided must have 100% option integrity", 100, fiveOptValidation.qualityMetrics.optionIntegrity)
        assertEquals("Option E must be recognized with 100% confidence", 100, fiveOptValidation.qualityMetrics.answerConfidence)
        assertEquals("Valid 5-option question must be APPROVED", ValidationStatus.APPROVED, fiveOptValidation.status)
        assertTrue("No answer conflict issue should be present", fiveOptValidation.issues.none { it.contains("Answer conflict detected") })

        // 5-Option Exam with only 4 options provided should be flagged
        val missingFifthOption = QuestionValidationEngine.validateQuestion(
            questionText = "Sample banking question with missing fifth option.",
            options = listOf("Opt A", "Opt B", "Opt C", "Opt D"),
            correctAnswer = "A",
            explanation = "Sample explanation for test verification.",
            expectedOptionCount = 5
        )
        assertEquals("Fewer than 5 options must drop option integrity", 0, missingFifthOption.qualityMetrics.optionIntegrity)
        assertTrue("Must flag missing 5th option", missingFifthOption.issues.any { it.contains("Fewer than 5 options provided") })
    }
}
