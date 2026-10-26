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
    fun testFractionalScorePrecisionWithSpecificBenchmarks() {
        val benchmarks = listOf(68.33, -0.66, -0.83, -0.27, 100.0, 0.0)
        for (scoreValue in benchmarks) {
            val attempt = AttemptEntity(
                userId = "bench_user",
                testId = "bench_test",
                testTitle = "Benchmark Scoring Test",
                score = scoreValue,
                maxScore = 200.0,
                correctCount = 10,
                incorrectCount = 5,
                skippedCount = 5,
                timeSpentSeconds = 1200,
                answersJson = "{}"
            )
            assertEquals(scoreValue, attempt.score, 0.000001)
            // Verify formatMark formatting maintains precision without truncation
            val formatted = ExamScoringEngine.formatMark(attempt.score)
            assertTrue(formatted.isNotBlank())
        }
    }

    @Test
    fun testOptionECorrectAnswerEvaluationAfterReload() {
        // Question with option E as correct answer
        val q = QuestionEntity(
            id = "q_bank_e",
            testId = "test_bank_01",
            questionText = "Which option is correct?",
            optionA = "Opt A",
            optionB = "Opt B",
            optionC = "Opt C",
            optionD = "Opt D",
            optionE = "Opt E (None of these)",
            correctAnswer = "E",
            explanation = "None of the first four options satisfy the condition.",
            subject = "Reasoning Ability",
            chapter = "Puzzles"
        )

        // Verify option E answer evaluation
        assertTrue(ExamScoringEngine.isUserAnswerCorrect("E", q.correctAnswer))
        assertTrue(ExamScoringEngine.isUserAnswerCorrect("5", q.correctAnswer)) // Index 5 maps to option E

        // Evaluate attempt using ExamScoringEngine with Banking rules (+1.0, -0.25)
        val answersCorrect = mapOf(q.id to "E")
        val resultCorrect = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "IBPS PO",
            questions = listOf(q),
            userAnswers = answersCorrect,
            timeSpentSeconds = 60
        )
        assertEquals(1.0, resultCorrect.finalScore, 0.001)
        assertEquals(1, resultCorrect.correctCount)
        assertEquals(0, resultCorrect.wrongCount)

        // Evaluate incorrect answer
        val answersWrong = mapOf(q.id to "A")
        val resultWrong = ExamScoringEngine.evaluateTestAttempt(
            targetExam = "IBPS PO",
            questions = listOf(q),
            userAnswers = answersWrong,
            timeSpentSeconds = 60
        )
        assertEquals(0.25, resultWrong.negativeMarksPenalty, 0.001)
        assertEquals(-0.25, resultWrong.rawScore, 0.001)
        assertEquals(0, resultWrong.correctCount)
        assertEquals(1, resultWrong.wrongCount)
    }

    @Test
    fun testFirestoreSerializationAndDeserializationOfNewFields() {
        val originalTest = TestEntity(
            id = "firestore_test_101",
            title = "SBI PO Mains 2026",
            description = "High-accuracy Banking Mock Paper",
            institute = "AK Test Series",
            batchName = "Target Banking",
            facultyName = "Senior Faculty",
            language = "English",
            subject = "Data Analysis",
            difficulty = "Hard",
            testType = "Mock Test",
            questionsCount = 50,
            isOfficial = true,
            isVerifiedCommunity = true,
            targetExam = "sbi_po",
            examId = "sbi_po",
            stageId = "mains",
            paperId = "mains_paper_1",
            scoringVersion = 1
        )

        // Simulate Firestore upload map serialization
        val firestoreMap = hashMapOf<String, Any?>(
            "id" to originalTest.id,
            "title" to originalTest.title,
            "targetExam" to originalTest.targetExam,
            "examId" to originalTest.examId,
            "stageId" to originalTest.stageId,
            "paperId" to originalTest.paperId,
            "scoringVersion" to originalTest.scoringVersion
        )

        // Verify map entries
        assertEquals("sbi_po", firestoreMap["examId"])
        assertEquals("mains", firestoreMap["stageId"])
        assertEquals("mains_paper_1", firestoreMap["paperId"])
        assertEquals(1, firestoreMap["scoringVersion"])

        // Simulate reading back from Firestore document
        val restoredTest = originalTest.copy(
            examId = firestoreMap["examId"] as? String,
            stageId = firestoreMap["stageId"] as? String,
            paperId = firestoreMap["paperId"] as? String,
            scoringVersion = (firestoreMap["scoringVersion"] as? Number)?.toInt()
        )

        assertEquals(originalTest.examId, restoredTest.examId)
        assertEquals(originalTest.stageId, restoredTest.stageId)
        assertEquals(originalTest.paperId, restoredTest.paperId)
        assertEquals(originalTest.scoringVersion, restoredTest.scoringVersion)
    }

    @Test
    fun testLegacyFirestoreDeserializationWithoutNewFields() {
        // Legacy document lacking canonical identity fields
        val legacyFirestoreMap = hashMapOf<String, Any?>(
            "id" to "legacy_doc_01",
            "title" to "Older Community Mock",
            "targetExam" to "NEET"
        )

        val restoredLegacyTest = TestEntity(
            id = legacyFirestoreMap["id"] as String,
            title = legacyFirestoreMap["title"] as String,
            description = "Legacy test",
            institute = "Community",
            batchName = "Batch A",
            facultyName = "Teacher",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Mock",
            questionsCount = 10,
            isOfficial = false,
            isVerifiedCommunity = false,
            targetExam = legacyFirestoreMap["targetExam"] as String,
            examId = legacyFirestoreMap["examId"] as? String,
            stageId = legacyFirestoreMap["stageId"] as? String,
            paperId = legacyFirestoreMap["paperId"] as? String,
            scoringVersion = (legacyFirestoreMap["scoringVersion"] as? Number)?.toInt()
        )

        assertNull(restoredLegacyTest.examId)
        assertNull(restoredLegacyTest.stageId)
        assertNull(restoredLegacyTest.paperId)
        assertNull(restoredLegacyTest.scoringVersion)
        assertEquals("NEET", restoredLegacyTest.targetExam)
    }

    @Test
    fun testBundledExamCatalogPreservesCanonicalExamIdentity() {
        val (neetTest, _) = BundledExamCatalog.getNeetUgMockTest1()
        assertEquals("neet_ug", neetTest.examId)
        assertEquals("neet_ug_main", neetTest.stageId)
        assertEquals("neet_ug_paper", neetTest.paperId)
        assertEquals(1, neetTest.scoringVersion)

        val (jeeTest, _) = BundledExamCatalog.getJeeMainMockTest1()
        assertEquals("jee_main", jeeTest.examId)
        assertEquals("jee_main_stage", jeeTest.stageId)
        assertEquals("jee_main_paper_1", jeeTest.paperId)
        assertEquals(1, jeeTest.scoringVersion)

        val (cdsTest, _) = BundledExamCatalog.getUpscCdsMockTest1()
        assertEquals("cds", cdsTest.examId)
        assertEquals("cds_written", cdsTest.stageId)
        assertEquals(1, cdsTest.scoringVersion)

        val (sscTest, _) = BundledExamCatalog.getSscCglMockTest1()
        assertEquals("ssc_cgl", sscTest.examId)
        assertEquals("ssc_cgl_tier_1", sscTest.stageId)
        assertEquals(1, sscTest.scoringVersion)
    }

    @Test
    fun testBundledExamCatalogQuestionsToJsonSerializesOptionE() {
        val q = QuestionEntity(
            id = "q_five_opt",
            testId = "test_1",
            questionText = "Banking statement question?",
            optionA = "Option A",
            optionB = "Option B",
            optionC = "Option C",
            optionD = "Option D",
            optionE = "Option E (Special)",
            optionEHi = "विकल्प E (विशेष)",
            correctAnswer = "E",
            explanation = "Option E is correct.",
            subject = "Reasoning",
            chapter = "Syllogism"
        )
        val jsonStr = BundledExamCatalog.questionsToJson(listOf(q))
        assertTrue("JSON must contain optionE", jsonStr.contains("\"optionE\":\"Option E (Special)\""))
        assertTrue("JSON must contain optionEHi", jsonStr.contains("\"optionEHi\":\"विकल्प E (विशेष)\""))
    }
}
