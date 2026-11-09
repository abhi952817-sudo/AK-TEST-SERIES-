package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyllabusCatalogTest {

    @Test
    fun testNeetUgSyllabusCatalog() {
        val chapters = SyllabusCatalog.getChapterDetailsForExam("NEET")
        assertTrue("NEET UG 2026 syllabus should include the full NMC unit/topic hierarchy", chapters.size >= 50)

        val subjects = chapters.map { it.subject }.distinct()
        assertTrue("Should contain Physics", subjects.contains("Physics"))
        assertTrue("Should contain Chemistry", subjects.contains("Chemistry"))
        assertTrue("Should contain Botany", subjects.contains("Botany"))
        assertTrue("Should contain Zoology", subjects.contains("Zoology"))

        // NTA/NMC do not publish official chapter-wise weightage or expected question counts.
        chapters.forEach { ch ->
            assertTrue("Chapter title should not be blank", ch.chapter.isNotBlank())
            assertEquals("Unpublished official weightage must not be fabricated", 0, ch.weightagePercent)
            assertEquals("Unpublished expected question count must not be fabricated", 0, ch.totalQuestionsExpected)
            assertEquals("Every NEET entry must be source-versioned", "Official NMC 2026", ch.priority)
            assertTrue("Official unit topics should not be empty", ch.keyTopics.isNotEmpty())
        }
        assertEquals(20, chapters.count { it.subject == "Physics" })
        assertEquals(20, chapters.count { it.subject == "Chemistry" })
        assertTrue("Biology must be split into the app's Botany/Zoology filters",
            subjects.containsAll(listOf("Botany", "Zoology")))
        assertEquals(NeetUg2026Syllabus.VERSION, "NEET_UG_2026_NMC_2025-12-22")
    }

    @Test
    fun testJeeMainSyllabusCatalog() {
        val chapters = SyllabusCatalog.getChapterDetailsForExam("JEE_MAINS")
        assertTrue("JEE syllabus should have at least 25 chapters", chapters.size >= 25)

        val subjects = chapters.map { it.subject }.distinct()
        assertTrue("Should contain Mathematics", subjects.contains("Mathematics"))
        assertTrue("Should contain Physics", subjects.contains("Physics"))
        assertTrue("Should contain Chemistry", subjects.contains("Chemistry"))

        chapters.forEach { ch ->
            assertTrue("Chapter title should not be blank", ch.chapter.isNotBlank())
            assertTrue("Weightage should be positive", ch.weightagePercent > 0)
            assertTrue("Key topics should not be empty", ch.keyTopics.isNotEmpty())
        }
    }

    @Test
    fun testUpscCdsSyllabusCatalog() {
        val chapters = SyllabusCatalog.getChapterDetailsForExam("UPSC_CDS")
        assertTrue("UPSC CDS syllabus should have at least 15 chapters", chapters.size >= 15)

        val subjects = chapters.map { it.subject }.distinct()
        assertTrue("Should contain Elementary Mathematics", subjects.contains("Elementary Mathematics"))
        assertTrue("Should contain English", subjects.contains("English"))
        assertTrue("Should contain General Knowledge", subjects.contains("General Knowledge"))
    }

    @Test
    fun testSscCglSyllabusCatalog() {
        val chapters = SyllabusCatalog.getChapterDetailsForExam("SSC_CGL")
        assertTrue("SSC CGL syllabus should have at least 20 chapters", chapters.size >= 20)

        val subjects = chapters.map { it.subject }.distinct()
        assertTrue("Should contain Quantitative Aptitude", subjects.contains("Quantitative Aptitude"))
        assertTrue("Should contain Reasoning", subjects.contains("Reasoning"))
        assertTrue("Should contain English", subjects.contains("English"))
        assertTrue("Should contain General Awareness", subjects.contains("General Awareness"))
    }

    @Test
    fun testSyllabusEntitiesGeneration() {
        val entities = SyllabusCatalog.getAllSyllabusEntities()
        assertTrue("Total syllabus entities should be at least 80", entities.size >= 80)

        entities.forEach { entity ->
            assertTrue("Entity ID should not be blank", entity.id.isNotBlank())
            assertTrue("Chapter name should not be blank", entity.chapter.isNotBlank())
            assertTrue("Subject should not be blank", entity.subject.isNotBlank())
            assertTrue("Topics string should not be blank", entity.topics.isNotBlank())
            assertTrue("Total tests should be positive", entity.totalTests > 0)
        }
    }
}
