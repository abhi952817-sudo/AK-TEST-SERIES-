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
        assertEquals("All registered competitive exams should match registry size", 25, allExams.size)

        val auditReports = ExamPatternValidator.auditAllRegisteredExams()
        assertEquals(25, auditReports.size)

        val failedExams = auditReports.filter { !it.isPatternValid }
        if (failedExams.isNotEmpty()) {
            val failureMessages = failedExams.joinToString("\n") { 
                "${it.examName} (${it.examId}): ${it.errors.joinToString("; ")}" 
            }
            fail("The following exams failed pattern audit:\n$failureMessages")
        }

        // Verify every exam passed
        auditReports.forEach { report ->
            assertTrue("Exam ${report.examName} must have a valid pattern", report.isPatternValid)
            assertTrue("Exam ${report.examName} must be marked verified = true", report.verified)
            assertTrue("Exam ${report.examName} must have language configured", report.language.isNotBlank())
            assertTrue("Exam ${report.examName} must have at least 1 paper", report.papers.isNotEmpty())
            report.papers.forEach { paper ->
                assertTrue("Paper ${paper.paperName} marks must be consistent", paper.isMarkingConsistent)
                assertTrue("Paper ${paper.paperName} duration must be > 0", paper.durationMinutes > 0)
                assertTrue("Paper ${paper.paperName} total questions must be > 0", paper.totalQuestions > 0)
                assertTrue("Paper ${paper.paperName} compulsory questions must be <= total", paper.compulsoryQuestions <= paper.totalQuestions)
            }
        }
    }

    @Test
    fun verifyNeetUgConfiguredPattern() {
        val neet = UniversalExamRegistry.getExamById("neet_ug")
        assertNotNull("NEET UG must exist", neet)
        neet!!

        assertEquals("NEET UG", neet.examName)
        assertEquals(4, neet.defaultOptionCount)
        assertEquals("Bilingual (English & Hindi)", neet.language)
        assertTrue(neet.verified)

        val paper = neet.stages.first().papers.first()
        assertEquals(200, paper.durationMinutes)
        assertEquals(720.0, paper.totalMarks, 0.001)
        assertEquals(4, paper.subjects.size) // Physics, Chemistry, Botany, Zoology

        // Each subject: Section A (35 Qs, 35 comp, +4, -1), Section B (15 Qs, 10 comp, +4, -1)
        paper.subjects.forEach { subj ->
            assertEquals(2, subj.sections.size)
            val secA = subj.sections[0]
            val secB = subj.sections[1]

            assertEquals(35, secA.totalQuestions)
            assertEquals(35, secA.compulsoryQuestions)
            assertEquals(4.0, secA.positiveMarks, 0.001)
            assertEquals(1.0, secA.negativeMarks, 0.001)

            assertEquals(15, secB.totalQuestions)
            assertEquals(10, secB.compulsoryQuestions) // Attempt any 10 rule
            assertEquals(4.0, secB.positiveMarks, 0.001)
            assertEquals(1.0, secB.negativeMarks, 0.001)
        }

        val totalAttempted = paper.subjects.sumOf { s -> s.sections.sumOf { it.compulsoryQuestions } }
        assertEquals(180, totalAttempted)
        assertEquals(720.0, totalAttempted * 4.0, 0.001)
    }

    @Test
    fun verifyJeeMainConfiguredPattern() {
        val jee = UniversalExamRegistry.getExamById("jee_main")
        assertNotNull("JEE Main must exist", jee)
        jee!!

        assertEquals("JEE Main", jee.examName)
        assertEquals(4, jee.defaultOptionCount)
        assertEquals("Bilingual (English & Hindi)", jee.language)
        assertTrue(jee.verified)

        val paper = jee.stages.first().papers.first()
        assertEquals(180, paper.durationMinutes)
        assertEquals(300.0, paper.totalMarks, 0.001)
        assertEquals(3, paper.subjects.size) // Physics, Chemistry, Mathematics

        // Section A (20 MCQs, 20 comp, +4, -1, 4 options)
        // Section B (10 NVQs, 5 comp, +4, -1, 0 options)
        paper.subjects.forEach { subj ->
            assertEquals(2, subj.sections.size)
            val secA = subj.sections[0]
            val secB = subj.sections[1]

            assertEquals(20, secA.totalQuestions)
            assertEquals(20, secA.compulsoryQuestions)
            assertEquals(4, secA.optionsCount)
            assertEquals(QuestionType.MCQ_SINGLE, secA.questionType)

            assertEquals(10, secB.totalQuestions)
            assertEquals(5, secB.compulsoryQuestions) // Attempt any 5 rule
            assertEquals(0, secB.optionsCount) // Numerical value questions have 0 options
            assertEquals(QuestionType.NUMERICAL_VALUE, secB.questionType)
        }

        val totalAttempted = paper.subjects.sumOf { s -> s.sections.sumOf { it.compulsoryQuestions } }
        assertEquals(75, totalAttempted)
        assertEquals(300.0, totalAttempted * 4.0, 0.001)
    }

    @Test
    fun verifyBankingExams5OptionsPattern() {
        val bankingIds = listOf("ibps_po", "ibps_clerk", "sbi_clerk", "sbi_po")
        bankingIds.forEach { examId ->
            val exam = UniversalExamRegistry.getExamById(examId)
            assertNotNull("Banking exam $examId must exist", exam)
            exam!!

            assertEquals("Banking exam $examId must have defaultOptionCount = 5", 5, exam.defaultOptionCount)
            assertEquals("Banking", exam.category)
            assertTrue(exam.verified)

            val paper = exam.stages.first().papers.first()
            assertEquals(60, paper.durationMinutes)
            assertEquals(100.0, paper.totalMarks, 0.001)

            paper.subjects.forEach { subj ->
                subj.sections.forEach { sec ->
                    assertEquals("Banking section ${sec.sectionName} in $examId must have optionsCount = 5", 5, sec.optionsCount)
                    assertEquals(1.0, sec.positiveMarks, 0.001)
                    assertEquals(0.25, sec.negativeMarks, 0.001)
                }
            }
        }
    }

    @Test
    fun verifyEnglishMediumExams() {
        val cat = UniversalExamRegistry.getExamById("cat")
        assertNotNull(cat)
        assertEquals("English", cat!!.language)
        assertTrue(cat.verified)
        assertEquals(120, cat.stages.first().papers.first().durationMinutes)
        assertEquals(198.0, cat.stages.first().papers.first().totalMarks, 0.001)

        val gate = UniversalExamRegistry.getExamById("gate")
        assertNotNull(gate)
        assertEquals("English", gate!!.language)
        assertTrue(gate.verified)
        assertEquals(180, gate.stages.first().papers.first().durationMinutes)
        assertEquals(100.0, gate.stages.first().papers.first().totalMarks, 0.001)
    }

    @Test
    fun verifyZeroNegativeMarkingExams() {
        val ctet = UniversalExamRegistry.getExamById("ctet")
        assertNotNull(ctet)
        ctet!!.stages.first().papers.first().subjects.forEach { s ->
            s.sections.forEach { sec ->
                assertEquals("CTET section ${sec.sectionName} must have 0.0 negative marks", 0.0, sec.negativeMarks, 0.001)
            }
        }

        val ugcNet = UniversalExamRegistry.getExamById("ugc_net")
        assertNotNull(ugcNet)
        ugcNet!!.stages.first().papers.first().subjects.forEach { s ->
            s.sections.forEach { sec ->
                assertEquals("UGC NET section ${sec.sectionName} must have 0.0 negative marks", 0.0, sec.negativeMarks, 0.001)
            }
        }

        val sscMts = UniversalExamRegistry.getExamById("ssc_mts")
        assertNotNull(sscMts)
        val mtsSubjects = sscMts!!.stages.first().papers.first().subjects
        // Session 1 Math & Reasoning must have 0.0 negative marks
        val session1Math = mtsSubjects.find { it.subjectId == "session_1_math" }!!
        assertEquals(0.0, session1Math.sections.first().negativeMarks, 0.001)
        val session1Reason = mtsSubjects.find { it.subjectId == "session_1_reasoning" }!!
        assertEquals(0.0, session1Reason.sections.first().negativeMarks, 0.001)
        // Session 2 GA & English must have 1.0 negative mark
        val session2Ga = mtsSubjects.find { it.subjectId == "session_2_ga" }!!
        assertEquals(1.0, session2Ga.sections.first().negativeMarks, 0.001)
    }

    @Test
    fun verifyDynamicPatternSubtitleGenerationForAllExams() {
        val allExams = UniversalExamRegistry.getAllExams()
        assertEquals(25, allExams.size)

        allExams.forEach { exam ->
            val subtitle = UniversalExamRegistry.getExamDisplaySubtitle(exam)
            assertTrue("Subtitle for ${exam.examName} must not be blank", subtitle.isNotBlank())
            assertTrue("Subtitle for ${exam.examName} must contain Qs", subtitle.contains("Qs"))
            assertTrue("Subtitle for ${exam.examName} must contain Marks", subtitle.contains("Marks"))
            assertTrue("Subtitle for ${exam.examName} must contain Mins", subtitle.contains("Mins"))
            assertTrue("Subtitle for ${exam.examName} must contain marking pattern", subtitle.contains("(+"))
        }

        // Verify specific calculated subtitles come directly from registry
        val neetSub = UniversalExamRegistry.getExamDisplaySubtitleByName("NEET UG")
        assertTrue("NEET subtitle should indicate 180/200 Qs and 720 Marks: $neetSub", neetSub.contains("180/200 Qs • 720 Marks • 200 Mins (+4, -1)"))

        val jeeSub = UniversalExamRegistry.getExamDisplaySubtitleByName("JEE Main")
        assertTrue("JEE subtitle should indicate 75/90 Qs and 300 Marks: $jeeSub", jeeSub.contains("75/90 Qs • 300 Marks • 180 Mins (+4, -1)"))

        val cuetSub = UniversalExamRegistry.getExamDisplaySubtitleByName("CUET UG")
        assertTrue("CUET subtitle should indicate 40/50 Qs and 200 Marks: $cuetSub", cuetSub.contains("40/50 Qs • 200 Marks • 45 Mins (+5, -1)"))

        val gateSub = UniversalExamRegistry.getExamDisplaySubtitleByName("GATE")
        assertTrue("GATE subtitle should indicate 65 Qs and 100 Marks: $gateSub", gateSub.contains("65 Qs • 100 Marks • 180 Mins (+1, -0.33)"))

        val ctetSub = UniversalExamRegistry.getExamDisplaySubtitleByName("CTET")
        assertTrue("CTET subtitle should indicate 0 negative marking: $ctetSub", ctetSub.contains("(+1, 0 -ve)"))
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
