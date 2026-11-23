package Com.ak.testseries.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScoringPolicyResolverTest {

    private val key = ScoringPolicyKey(
        examId = "example_exam",
        examYear = "2026",
        scoringVersion = "v2026-1",
        stageId = "stage_1",
        paperId = "paper_1",
        subjectId = "physics",
        sectionId = "section_a",
        questionType = "MCQ_SINGLE"
    )

    private fun policy(overrides: ScoringPolicy.() -> ScoringPolicy = { this }): ScoringPolicy =
        ScoringPolicy(
            examId = key.examId,
            examYear = key.examYear,
            scoringVersion = key.scoringVersion,
            stageId = key.stageId,
            paperId = key.paperId,
            subjectId = key.subjectId,
            subjectName = "Physics",
            sectionId = key.sectionId,
            sectionName = "Section A",
            questionType = key.questionType,
            positiveMarks = 4.0,
            negativeMarks = 1.0,
            status = "APPROVED",
            sourceUrl = "https://official.example.gov/bulletin.pdf",
            sourceTitle = "Official Exam Bulletin 2026",
            verifiedAt = "2026-01-10T10:00:00Z",
            approvedBy = "primary-admin"
        ).overrides()

    @Test
    fun resolvesOnlyTheExactVersionedKey() {
        val resolved = ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy()), key)
        assertEquals(4.0, resolved.positiveMarks, 0.001)
        assertEquals(1.0, resolved.negativeMarks, 0.001)
    }

    @Test
    fun rejectsUnsupportedRegistrySchema() {
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(2, "ACTIVE", listOf(policy()), key)
        }
    }

    @Test
    fun rejectsInactiveRegistry() {
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "DRAFT", listOf(policy()), key)
        }
    }

    @Test
    fun rejectsUnknownYearAndDuplicatePolicies() {
        val wrongYear = policy { copy(examYear = "2025") }
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(wrongYear), key)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy(), policy()), key)
        }
    }

    @Test
    fun rejectsUnapprovedOrUnverifiablePolicies() {
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy { copy(status = "DRAFT") }), key)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy { copy(sourceUrl = "http://example.gov") }), key)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy { copy(sourceUrl = "https://") }), key)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy { copy(sourceUrl = "https://user:pass@example.gov/bulletin.pdf") }), key)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy { copy(verifiedAt = "not-a-date") }), key)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy { copy(verifiedAt = "2026-02-30T10:00:00Z") }), key)
        }
    }

    @Test
    fun rejectsInvalidMarksAndAttemptLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy { copy(positiveMarks = 0.0) }), key)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy { copy(negativeMarks = -0.25) }), key)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScoringPolicyResolver.resolve(1, "ACTIVE", listOf(policy { copy(maxAttemptQuestions = 0) }), key)
        }
    }
}
