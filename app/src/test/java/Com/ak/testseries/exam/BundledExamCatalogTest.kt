package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledExamCatalogTest {

    @Test
    fun testNeetUgMockTest1Structure() {
        val (test, questions) = BundledExamCatalog.getNeetUgMockTest1()

        // 1. Verify Test Metadata
        assertEquals("official_neet_ug_mock_01", test.id)
        assertEquals("NEET", test.targetExam)
        assertEquals(200, test.durationMinutes)
        assertEquals(200, test.questionsCount)
        assertEquals(200, questions.size)
        assertTrue(test.isOfficial)
        assertTrue(test.countsTowardRanking)

        // 2. Verify Subject Distribution: 50 each
        val physicsQs = questions.filter { it.subject == "Physics" }
        val chemistryQs = questions.filter { it.subject == "Chemistry" }
        val botanyQs = questions.filter { it.subject == "Botany" }
        val zoologyQs = questions.filter { it.subject == "Zoology" }

        assertEquals(50, physicsQs.size)
        assertEquals(50, chemistryQs.size)
        assertEquals(50, botanyQs.size)
        assertEquals(50, zoologyQs.size)

        // 3. Verify Section Distribution per subject: 35 in Section A, 15 in Section B
        assertEquals(35, physicsQs.count { it.section == "SECTION_A" })
        assertEquals(15, physicsQs.count { it.section == "SECTION_B" })
        assertEquals(35, chemistryQs.count { it.section == "SECTION_A" })
        assertEquals(15, chemistryQs.count { it.section == "SECTION_B" })
        assertEquals(35, botanyQs.count { it.section == "SECTION_A" })
        assertEquals(15, botanyQs.count { it.section == "SECTION_B" })
        assertEquals(35, zoologyQs.count { it.section == "SECTION_A" })
        assertEquals(15, zoologyQs.count { it.section == "SECTION_B" })

        // 4. Verify Bilingual & Options Integrity
        questions.forEach { q ->
            assertTrue("Question text in English should not be blank", q.questionText.isNotBlank())
            assertNotNull("Question text in Hindi should be present", q.questionTextHi)
            assertTrue("Question text in Hindi should not be blank", q.questionTextHi!!.isNotBlank())
            assertTrue("Option A should not be blank", q.optionA.isNotBlank())
            assertTrue("Option B should not be blank", q.optionB.isNotBlank())
            assertTrue("Option C should not be blank", q.optionC.isNotBlank())
            assertTrue("Option D should not be blank", q.optionD.isNotBlank())
            assertTrue("Correct answer should be A, B, C, or D", q.correctAnswer in listOf("A", "B", "C", "D"))
            assertTrue("Explanation should not be blank", q.explanation.isNotBlank())
        }
    }

    @Test
    fun testJeeMainMockTest1Structure() {
        val (test, questions) = BundledExamCatalog.getJeeMainMockTest1()

        // 1. Verify Test Metadata
        assertEquals("official_jee_mains_mock_01", test.id)
        assertEquals("JEE", test.targetExam)
        assertEquals(180, test.durationMinutes)
        assertEquals(90, test.questionsCount)
        assertEquals(90, questions.size)

        // 2. Verify Subject Distribution: 30 each
        val physicsQs = questions.filter { it.subject == "Physics" }
        val chemistryQs = questions.filter { it.subject == "Chemistry" }
        val mathsQs = questions.filter { it.subject == "Mathematics" }

        assertEquals(30, physicsQs.size)
        assertEquals(30, chemistryQs.size)
        assertEquals(30, mathsQs.size)

        // 3. Verify Section Distribution: 20 MCQs (Section A) + 10 Numericals (Section B)
        assertEquals(20, physicsQs.count { it.section == "SECTION_A" && it.questionType == "MCQ" })
        assertEquals(10, physicsQs.count { it.section == "SECTION_B" && it.questionType == "NUMERICAL" })
        assertEquals(20, chemistryQs.count { it.section == "SECTION_A" && it.questionType == "MCQ" })
        assertEquals(10, chemistryQs.count { it.section == "SECTION_B" && it.questionType == "NUMERICAL" })
        assertEquals(20, mathsQs.count { it.section == "SECTION_A" && it.questionType == "MCQ" })
        assertEquals(10, mathsQs.count { it.section == "SECTION_B" && it.questionType == "NUMERICAL" })
    }

    @Test
    fun testExamCatalogFiltering() {
        val neetTests = BundledExamCatalog.getBundledTestsForExam("NEET")
        assertEquals(1, neetTests.size)
        assertEquals("NEET", neetTests[0].first.targetExam)

        val jeeTests = BundledExamCatalog.getBundledTestsForExam("JEE_MAINS")
        assertEquals(1, jeeTests.size)
        assertEquals("JEE", jeeTests[0].first.targetExam)

        val allTests = BundledExamCatalog.getAllBundledTests()
        assertTrue("All tests should contain at least 4 bundled exams", allTests.size >= 4)
    }
}
