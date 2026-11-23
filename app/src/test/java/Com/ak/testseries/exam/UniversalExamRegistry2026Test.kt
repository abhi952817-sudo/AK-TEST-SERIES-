package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalExamRegistry2026Test {

    @Test
    fun neetUg2026MatchesOfficialQuestionDurationAndMarkingPattern() {
        val exam = UniversalExamRegistry.getExamById("neet_ug")
        assertNotNull(exam)
        requireNotNull(exam)

        assertTrue(exam.verified)
        assertEquals("2026", exam.examYear)
        assertEquals("neet-ug-2026-v1", exam.scoringVersion)
        assertTrue(exam.sourceUrl?.startsWith("https://") == true)

        val paper = exam.stages.single().papers.single()
        assertEquals(180, paper.durationMinutes)
        assertEquals(720.0, paper.totalMarks, 0.001)
        assertEquals(180, paper.subjects.sumOf { subject -> subject.sections.sumOf { it.totalQuestions } })
        assertEquals(180, paper.subjects.sumOf { subject -> subject.sections.sumOf { it.compulsoryQuestions } })

        val physics = paper.subjects.single { it.subjectId == "physics" }.sections.single()
        val chemistry = paper.subjects.single { it.subjectId == "chemistry" }.sections.single()
        val botany = paper.subjects.single { it.subjectId == "botany" }.sections.single()
        val zoology = paper.subjects.single { it.subjectId == "zoology" }.sections.single()
        assertEquals(45, physics.totalQuestions)
        assertEquals(45, chemistry.totalQuestions)
        assertEquals(45, botany.totalQuestions)
        assertEquals(45, zoology.totalQuestions)
        listOf(physics, chemistry, botany, zoology).forEach {
            assertEquals(4.0, it.positiveMarks, 0.001)
            assertEquals(1.0, it.negativeMarks, 0.001)
            assertEquals(QuestionType.MCQ_SINGLE, it.questionType)
        }
        assertNull(UniversalExamRegistry.getMaximumAttemptQuestionsCountForSection("NEET", "Section B", "Physics"))
    }

    @Test
    fun jeeMain2026PaperOneMatchesOfficialQuestionAndMarkingPattern() {
        val exam = UniversalExamRegistry.getExamById("jee_main")
        assertNotNull(exam)
        requireNotNull(exam)

        assertTrue(exam.verified)
        assertEquals("2026", exam.examYear)
        assertEquals("jee-main-2026-p1-v1", exam.scoringVersion)

        val paper = exam.stages.single().papers.single()
        assertEquals(180, paper.durationMinutes)
        assertEquals(300.0, paper.totalMarks, 0.001)
        assertEquals(75, paper.subjects.sumOf { subject -> subject.sections.sumOf { it.totalQuestions } })
        assertEquals(75, paper.subjects.sumOf { subject -> subject.sections.sumOf { it.compulsoryQuestions } })

        paper.subjects.forEach { subject ->
            val sectionA = subject.sections.single { it.sectionId.endsWith("sec_a") }
            val sectionB = subject.sections.single { it.sectionId.endsWith("sec_b") }
            assertEquals(20, sectionA.totalQuestions)
            assertEquals(20, sectionA.compulsoryQuestions)
            assertEquals(QuestionType.MCQ_SINGLE, sectionA.questionType)
            assertEquals(5, sectionB.totalQuestions)
            assertEquals(5, sectionB.compulsoryQuestions)
            assertEquals(QuestionType.NUMERICAL_VALUE, sectionB.questionType)
            listOf(sectionA, sectionB).forEach {
                assertEquals(4.0, it.positiveMarks, 0.001)
                assertEquals(1.0, it.negativeMarks, 0.001)
            }
        }
    }

    @Test
    fun examsWithoutYearSpecificSourceEvidenceAreNotMarkedVerified() {
        val unverified = UniversalExamRegistry.getAllExams()
            .filterNot { it.examId == "neet_ug" || it.examId == "jee_main" }
        assertEquals(23, unverified.size)
        assertTrue(unverified.none { it.verified || it.examYear != null || it.scoringVersion != null })
        assertFalse(UniversalExamRegistry.isExamVerified("jee_advanced"))
    }
}
