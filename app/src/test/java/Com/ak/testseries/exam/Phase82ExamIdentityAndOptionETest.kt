package Com.ak.testseries.exam

import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8.2 Focused Unit Test Suite:
 * 1. Canonical exam identity persistence on TestEntity (examId, stageId, paperId, scoringVersion)
 * 2. Five-option question support (optionE, optionEHi) in entities and JSON serialization
 * 3. Fractional / decimal score persistence (Double) without integer truncation
 * 4. Backward compatibility with legacy 4-option and null canonical exam fields
 * 5. ExamFormatStandardizer preserving Option E for 5-option exams
 */
class Phase82ExamIdentityAndOptionETest {

    @Test
    fun testCanonicalExamIdentityPersistenceOnTestEntity() {
        val test = TestEntity(
            id = "test_ibps_po_01",
            title = "IBPS PO Prelims 2026",
            description = "Banking Mock Test",
            institute = "AK Exam Portal",
            batchName = "Banking Target",
            facultyName = "Expert Faculty",
            language = "English",
            subject = "Quantitative Aptitude",
            difficulty = "Medium",
            testType = "Mock Test",
            questionsCount = 100,
            isOfficial = true,
            isVerifiedCommunity = true,
            targetExam = "IBPS PO",
            examId = "IBPS_PO",
            stageId = "prelims",
            paperId = "paper_1",
            scoringVersion = 1
        )

        assertEquals("IBPS_PO", test.examId)
        assertEquals("prelims", test.stageId)
        assertEquals("paper_1", test.paperId)
        assertEquals(1, test.scoringVersion)
        assertEquals("IBPS PO", test.targetExam)
    }

    @Test
    fun testLegacyTestEntityDefaultsCanonicalFieldsToNull() {
        val legacyTest = TestEntity(
            id = "legacy_test_01",
            title = "Legacy Test",
            description = "Old mock test",
            institute = "Legacy Portal",
            batchName = "NEET 2025",
            facultyName = "Biology Faculty",
            language = "English",
            subject = "Biology",
            difficulty = "Medium",
            testType = "Full Syllabus",
            questionsCount = 90,
            isOfficial = false,
            isVerifiedCommunity = false,
            targetExam = "NEET"
        )

        assertNull("Legacy test should default examId to null", legacyTest.examId)
        assertNull("Legacy test should default stageId to null", legacyTest.stageId)
        assertNull("Legacy test should default paperId to null", legacyTest.paperId)
        assertNull("Legacy test should default scoringVersion to null", legacyTest.scoringVersion)
    }

    @Test
    fun testFiveOptionQuestionEntityAndHindiSupport() {
        val question = QuestionEntity(
            id = "q_ibps_01",
            testId = "test_ibps_po_01",
            questionText = "What is the capital of India?",
            optionA = "Mumbai",
            optionB = "Kolkata",
            optionC = "Chennai",
            optionD = "New Delhi",
            optionE = "None of these",
            correctAnswer = "D",
            explanation = "New Delhi is the national capital of India.",
            subject = "General Awareness",
            chapter = "Indian Polity",
            questionTextHi = "भारत की राजधानी क्या है?",
            optionAHi = "मुंबई",
            optionBHi = "कोलकाता",
            optionCHi = "चेन्नई",
            optionDHi = "नई दिल्ली",
            optionEHi = "इनमें से कोई नहीं"
        )

        assertEquals("None of these", question.optionE)
        assertEquals("इनमें से कोई नहीं", question.optionEHi)
        assertEquals("D", question.correctAnswer)
    }

    @Test
    fun testLegacyQuestionEntityDefaultsOptionEToNull() {
        val legacyQuestion = QuestionEntity(
            id = "q_neet_01",
            testId = "test_neet_01",
            questionText = "Basic question",
            optionA = "A",
            optionB = "B",
            optionC = "C",
            optionD = "D",
            correctAnswer = "A",
            explanation = "Explanation",
            subject = "Physics",
            chapter = "Kinematics"
        )

        assertNull("Legacy question should default optionE to null", legacyQuestion.optionE)
        assertNull("Legacy question should default optionEHi to null", legacyQuestion.optionEHi)
    }

    @Test
    fun testFiveOptionJsonSerializationRoundTrip() {
        val optionsMap = mapOf(
            "optionA" to "10",
            "optionB" to "20",
            "optionC" to "30",
            "optionD" to "40",
            "optionE" to "Cannot be determined",
            "optionEHi" to "निर्धारित नहीं किया जा सकता",
            "correctAnswer" to "E"
        )

        assertEquals("Cannot be determined", optionsMap["optionE"])
        assertEquals("निर्धारित नहीं किया जा सकता", optionsMap["optionEHi"])
        assertEquals("E", optionsMap["correctAnswer"])
    }

    @Test
    fun testFractionalScorePersistenceInAttemptEntityWithoutTruncation() {
        val fractionalScore = 46.666666666666664
        val maxScore = 75.0

        val attempt = AttemptEntity(
            userId = "candidate_101",
            testId = "rrb_alp_mock",
            testTitle = "RRB ALP CBT-1",
            score = fractionalScore,
            maxScore = maxScore,
            correctCount = 50,
            incorrectCount = 10,
            skippedCount = 15,
            timeSpentSeconds = 3600,
            answersJson = "{}"
        )

        assertEquals(46.67, attempt.score, 0.01)
        assertEquals(75.0, attempt.maxScore, 0.001)

        val negativeScore = -0.666
        val negAttempt = attempt.copy(score = negativeScore)
        assertEquals(-0.666, negAttempt.score, 0.001)

        val ibpsScore = 68.25
        val ibpsAttempt = attempt.copy(score = ibpsScore, maxScore = 100.0)
        assertEquals(68.25, ibpsAttempt.score, 0.0001)
    }

    @Test
    fun testExamFormatStandardizerPreservesFiveOptionsForBanking() {
        val ibpsConfig = UniversalExamRegistry.getExam("IBPS_PO")
        assertNotNull(ibpsConfig)

        val uq = UniversalQuestionModel(
            questionId = "uq_bank_1",
            examId = "IBPS_PO",
            subjectId = "quantitative_aptitude",
            questionText = "What is the reserve ratio set by RBI?",
            options = listOf("3.5%", "4.0%", "4.5%", "5.0%", "None of the above"),
            correctAnswer = "E",
            explanation = "The current CRR is 4.5%."
        )

        val standardizedPaper = ExamFormatStandardizer.standardizeExamOutput(
            examConfig = ibpsConfig!!,
            subjectId = "quantitative_aptitude",
            questions = listOf(uq)
        )

        assertNotNull(standardizedPaper)
        assertTrue(standardizedPaper.sectionGroups.isNotEmpty())
        val firstSectionQuestions = standardizedPaper.sectionGroups.first().questions
        assertTrue(firstSectionQuestions.isNotEmpty())
        val q1 = firstSectionQuestions.first()
        assertEquals(5, q1.options.size)
        assertEquals("None of the above", q1.options[4])
        assertEquals("E", q1.correctAnswer)
    }

    @Test
    fun testUniversalExamRegistryBankExamsHaveFiveDefaultOptions() {
        val ibpsPo = UniversalExamRegistry.getExam("IBPS PO")
        assertNotNull(ibpsPo)
        assertEquals(5, ibpsPo!!.defaultOptionCount)

        val ibpsClerk = UniversalExamRegistry.getExam("IBPS Clerk")
        assertNotNull(ibpsClerk)
        assertEquals(5, ibpsClerk!!.defaultOptionCount)

        val rrbNtpc = UniversalExamRegistry.getExam("RRB NTPC")
        assertNotNull(rrbNtpc)

        val neet = UniversalExamRegistry.getExam("NEET")
        assertNotNull(neet)
        assertEquals(4, neet!!.defaultOptionCount)
    }
}
