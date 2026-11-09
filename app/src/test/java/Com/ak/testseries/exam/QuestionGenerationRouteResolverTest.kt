package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionGenerationRouteResolverTest {

    @Test
    fun resolvesExactNeetSubjectWithoutChangingExamIdentity() {
        val result = QuestionGenerationRouteResolver.resolve("NEET", "Physics")
        assertNotNull(result.route)
        assertEquals("neet_ug", result.route?.examConfig?.examId)
        assertEquals("Physics", result.route?.subjectName)
        assertTrue(result.route?.stageId?.isNotBlank() == true)
        assertTrue(result.route?.paperId?.isNotBlank() == true)
        assertTrue(result.route?.subjectId?.isNotBlank() == true)
    }

    @Test
    fun rejectsUnknownOrAmbiguousExamInsteadOfFallingBackToNeet() {
        val result = QuestionGenerationRouteResolver.resolve("JEE", "Physics")
        assertNull(result.route)
        assertTrue(result.error.orEmpty().contains("unknown or ambiguous"))
    }

    @Test
    fun rejectsSubjectThatDoesNotBelongToSelectedExam() {
        val result = QuestionGenerationRouteResolver.resolve("NEET", "General Awareness")
        assertNull(result.route)
        assertTrue(result.error.orEmpty().contains("not configured"))
    }

    @Test
    fun rejectsAllSubjectsUntilSubjectDistributionIsExplicitlyImplemented() {
        val result = QuestionGenerationRouteResolver.resolve("NEET", "All Subjects")
        assertNull(result.route)
        assertTrue(result.error.orEmpty().contains("subject-distribution plan"))
    }
}
