package Com.ak.testseries.exam

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Test suite verifying the Master Syllabus hierarchy foundation:
 * Exam -> Stage -> Paper -> Subject -> Chapter -> Topic.
 * Verifies that the model contracts exist, traversal functions work,
 * default values prevent breaking existing 23 registered exams,
 * and no syllabus data is prematurely invented or hardcoded.
 */
class MasterSyllabusHierarchyTest {

    @Test
    fun testMasterSyllabusHierarchyStructure() {
        val topic = TopicConfig(
            topicId = "topic_kinematics_1d",
            topicName = "Motion in a Straight Line",
            topicNameHi = "सरल रेखा में गति",
            orderIndex = 1,
            subtopics = listOf("Position", "Displacement", "Uniform Acceleration")
        )
        assertEquals("topic_kinematics_1d", topic.topicId)
        assertEquals("Motion in a Straight Line", topic.topicName)
        assertEquals("सरल रेखा में गति", topic.topicNameHi)
        assertEquals(1, topic.orderIndex)
        assertEquals(3, topic.subtopics.size)

        val chapter = ChapterConfig(
            chapterId = "chap_kinematics",
            chapterName = "Kinematics",
            chapterNameHi = "शुद्धगतिकी",
            orderIndex = 2,
            academicLevel = "Class 11",
            topics = listOf(topic)
        )
        assertEquals("chap_kinematics", chapter.chapterId)
        assertEquals("Kinematics", chapter.chapterName)
        assertEquals(1, chapter.topics.size)
        assertEquals(topic, chapter.findTopic("topic_kinematics_1d"))

        val subject = SubjectConfig(
            subjectId = "physics",
            subjectName = "Physics",
            sections = listOf(
                SectionConfig("phy_sec_a", "Section A", 35, 35, 4.0, 1.0)
            ),
            chapters = listOf(chapter)
        )
        assertEquals("physics", subject.subjectId)
        assertEquals("Physics", subject.subjectName)
        assertEquals(1, subject.chapters.size)
        assertEquals(chapter, subject.findChapter("chap_kinematics"))

        val paper = PaperConfig(
            paperId = "paper_1",
            paperName = "Paper 1",
            subjects = listOf(subject),
            durationMinutes = 180,
            totalMarks = 300.0
        )
        assertEquals(1, paper.subjects.size)

        val stage = StageConfig(
            stageId = "prelims",
            stageName = "Preliminary",
            papers = listOf(paper)
        )
        assertEquals(1, stage.papers.size)

        val exam = ExamConfig(
            examId = "test_exam",
            examName = "Test Examination",
            shortName = "TEST",
            category = "Testing",
            stages = listOf(stage)
        )

        // Verify top-down traversal
        assertEquals(stage, exam.findStage("prelims"))
        assertEquals(paper, exam.findPaper("paper_1"))
        assertEquals(subject, exam.findSubject("physics"))
        assertEquals(listOf(chapter), exam.getAllChapters())
        assertEquals(listOf(topic), exam.getAllTopics())
    }

    @Test
    fun testUniversalQuestionModelChapterLinkage() {
        val question = UniversalQuestionModel(
            questionId = "q_101",
            examId = "neet_ug",
            stageId = "single_stage",
            paperId = "neet_paper_1",
            subjectId = "physics",
            chapterId = "chap_kinematics",
            chapterName = "Kinematics",
            topicId = "topic_kinematics_1d",
            questionText = "A particle moves with uniform acceleration...",
            options = listOf("10 m/s", "20 m/s", "30 m/s", "40 m/s"),
            correctAnswer = "B"
        )

        assertEquals("chap_kinematics", question.chapterId)
        assertEquals("Kinematics", question.chapterName)
        assertEquals("topic_kinematics_1d", question.topicId)
    }

    @Test
    fun testRegisteredExamsMaintainUnpopulatedChaptersGracefully() {
        val allExams = UniversalExamRegistry.getAllExams()
        assertEquals(25, allExams.size)

        // Ensure default registered exams gracefully have empty chapters and topics without crashing or inventing data
        allExams.forEach { exam ->
            assertNotNull("Exam ${exam.examName} must not be null", exam)
            val chapters = exam.getAllChapters()
            val topics = exam.getAllTopics()
            assertTrue("Registered exam ${exam.examName} has unpopulated chapters by default", chapters.isEmpty())
            assertTrue("Registered exam ${exam.examName} has unpopulated topics by default", topics.isEmpty())
        }
    }
}
