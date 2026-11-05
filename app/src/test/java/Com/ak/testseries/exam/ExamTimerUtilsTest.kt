package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Test

class ExamTimerUtilsTest {
    @Test
    fun elapsedTimeUsesConfiguredPaperDurationRatherThanQuestionCount() {
        val configuredDuration = 180L * 60L
        assertEquals(0L, calculateElapsedExamTimeSeconds(configuredDuration, configuredDuration))
        assertEquals(10L * 60L, calculateElapsedExamTimeSeconds(configuredDuration, 170L * 60L))
        assertEquals(configuredDuration, calculateElapsedExamTimeSeconds(configuredDuration, 0L))
    }

    @Test
    fun elapsedTimeIsClampedToValidRange() {
        assertEquals(0L, calculateElapsedExamTimeSeconds(600L, 900L))
        assertEquals(600L, calculateElapsedExamTimeSeconds(600L, -10L))
        assertEquals(0L, calculateElapsedExamTimeSeconds(-10L, 0L))
    }
}
