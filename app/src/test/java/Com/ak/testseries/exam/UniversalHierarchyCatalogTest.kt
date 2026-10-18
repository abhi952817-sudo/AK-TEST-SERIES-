package Com.ak.testseries.exam

import org.junit.Assert.*
import org.junit.Test

class UniversalHierarchyCatalogTest {

    @Test
    fun testAllExamsStructure() {
        val exams = UniversalHierarchyCatalog.getAllExams()
        assertTrue("Should have at least 6 exams in the hierarchy", exams.size >= 6)

        val examIds = exams.map { it.id }
        assertTrue("Should contain JEE Mains", examIds.contains("jee_mains"))
        assertTrue("Should contain NEET", examIds.contains("neet_ug"))
        assertTrue("Should contain UPSC", examIds.contains("upsc_cds"))
        assertTrue("Should contain SSC CGL", examIds.contains("ssc_cgl"))
        assertTrue("Should contain Class 11", examIds.contains("class_11"))
        assertTrue("Should contain Class 12", examIds.contains("class_12"))

        exams.forEach { exam ->
            assertTrue("Exam ID should not be blank", exam.id.isNotBlank())
            assertTrue("Exam nameEn should not be blank", exam.nameEn.isNotBlank())
            assertTrue("Exam nameHi should not be blank", exam.nameHi.isNotBlank())
            assertTrue("Exam should have subjects", exam.subjects.isNotEmpty())
        }
    }

    @Test
    fun testJeeMainsHierarchyIntegrity() {
        val jee = UniversalHierarchyCatalog.getExamHierarchy("JEE")
        assertEquals("jee_mains", jee.id)
        assertEquals("JEE Mains", jee.nameEn)

        val physics = jee.subjects.firstOrNull { it.nameEn == "Physics" }
        assertNotNull("Physics subject should exist in JEE", physics)
        assertTrue("Physics should have chapters", physics!!.chapters.isNotEmpty())

        val lom = physics.chapters.firstOrNull { it.id == "jee_phy_lom" }
        assertNotNull("Laws of Motion chapter should exist", lom)
        assertEquals("Laws of Motion", lom!!.nameEn)
        assertEquals("गति के नियम", lom.nameHi)

        // Check topics in Laws of Motion
        val topicNames = lom.topics.map { it.nameEn }
        assertTrue("Should contain Newton's Laws topic", topicNames.contains("Newton's Laws"))
        assertTrue("Should contain Friction topic", topicNames.contains("Friction"))
        assertTrue("Should contain Circular Motion topic", topicNames.contains("Circular Motion"))

        val friction = lom.topics.first { it.nameEn == "Friction" }
        assertEquals("घर्षण", friction.nameHi)
        assertTrue("Friction should have practice tests", friction.practiceTests.isNotEmpty())
        assertTrue("Friction should have PYQs", friction.pyqTests.isNotEmpty())

        // Verify PYQs
        val pyqYears = friction.pyqTests.mapNotNull { it.year }
        assertTrue("Should contain 2023 PYQ", pyqYears.contains(2023))
        assertTrue("Should contain 2022 PYQ", pyqYears.contains(2022))
        assertTrue("Should contain 2021 PYQ", pyqYears.contains(2021))
    }

    @Test
    fun testRealTestFilteringForTopic() {
        val test1 = Com.ak.testseries.data.TestEntity(
            id = "real_jee_phy_lom_p1",
            title = "Laws of Motion Real Practice Test",
            description = "Description 1",
            institute = "AK Test Series",
            batchName = "Target 2026",
            facultyName = "Admin",
            language = "English",
            subject = "Physics",
            difficulty = "Medium",
            testType = "Topic Test",
            questionsCount = 20,
            isOfficial = true,
            isVerifiedCommunity = true,
            targetExam = "JEE Mains",
            chapter = "Laws of Motion",
            topic = "Friction",
            durationMinutes = 25
        )

        val test2 = Com.ak.testseries.data.TestEntity(
            id = "real_jee_phy_lom_pyq23",
            title = "JEE 2023 Real Friction PYQ",
            description = "Description 2",
            institute = "AK Test Series",
            batchName = "Target 2026",
            facultyName = "Admin",
            language = "English",
            subject = "Physics",
            difficulty = "Hard",
            testType = "PYQ",
            questionsCount = 25,
            isOfficial = true,
            isVerifiedCommunity = true,
            targetExam = "JEE Mains",
            chapter = "Laws of Motion",
            topic = "Friction",
            durationMinutes = 30
        )

        val allTests = listOf(test1, test2)
        val (practice, pyqs) = UniversalHierarchyCatalog.getRealTestsForTopic(
            allTests = allTests,
            examName = "JEE Mains",
            subjectName = "Physics",
            chapterName = "Laws of Motion",
            topicName = "Friction"
        )

        assertEquals(1, practice.size)
        assertEquals("real_jee_phy_lom_p1", practice[0].id)

        assertEquals(1, pyqs.size)
        assertEquals("real_jee_phy_lom_pyq23", pyqs[0].id)
    }
}
