package Com.ak.testseries.exam

import org.junit.Assert.*
import org.junit.Test

class VersionedSyllabusResolverTest {

    private fun snapshot(state: SyllabusApprovalState = SyllabusApprovalState.APPROVED) =
        VersionedSyllabusSnapshot(
            key = SyllabusSnapshotKey("jee_main", "JEE_MAIN_2027", "jee_paper_1", "jee_main_p1", "physics"),
            approvalState = state,
            source = SyllabusSourceEvidence(
                issuer = "National Testing Agency",
                title = "JEE Main 2027 Syllabus",
                sourceUrl = "https://jeemain.nta.nic.in/document/syllabus-2027/",
                publishedYear = 2026,
                sourcePages = listOf(3, 4, 5)
            ),
            nodes = listOf(
                SyllabusTopicNode("physics_unit_01", "Units and Measurements", "मात्रक एवं मापन", officialTextEn = "Units of measurements and SI units.", sourcePage = 3)
            ),
            reviewerId = if (state == SyllabusApprovalState.APPROVED) "admin-reviewer" else null,
            reviewedAtEpochMs = if (state == SyllabusApprovalState.APPROVED) 1L else null
        )

    @Test
    fun onlyExactApprovedSnapshotCanResolve() {
        VersionedSyllabusResolver.clearForTests()
        assertTrue(VersionedSyllabusResolver.register(snapshot()))
        val exact = snapshot().key
        assertNotNull(VersionedSyllabusResolver.resolveApproved(exact))
        assertNull(VersionedSyllabusResolver.resolveApproved(exact.copy(examId = "jee_advanced")))
        assertNull(VersionedSyllabusResolver.resolveApproved(exact.copy(syllabusVersion = "JEE_MAIN_2026")))
        assertNull(VersionedSyllabusResolver.resolveApproved(exact.copy(paperId = "jee_main_p2a")))
    }

    @Test
    fun pendingSnapshotNeverBecomesOfficialPromptInput() {
        VersionedSyllabusResolver.clearForTests()
        val pending = snapshot(SyllabusApprovalState.PENDING_REVIEW)
        assertTrue(VersionedSyllabusResolver.register(pending))
        assertNotNull(VersionedSyllabusResolver.resolveAnyState(pending.key))
        assertNull(VersionedSyllabusResolver.resolveApproved(pending.key))
    }

    @Test
    fun invalidOrDuplicateNodesAreRejected() {
        VersionedSyllabusResolver.clearForTests()
        val base = snapshot()
        val invalid = base.copy(nodes = base.nodes + base.nodes.first())
        assertFalse(VersionedSyllabusResolver.register(invalid))
    }

    @Test
    fun exactKeyRequiresEveryDimension() {
        assertNull(VersionedSyllabusResolver.exactKey("jee_main", "JEE_MAIN_2027", null, "jee_main_p1", "physics"))
        assertNotNull(VersionedSyllabusResolver.exactKey("jee_main", "JEE_MAIN_2027", "jee_paper_1", "jee_main_p1", "physics"))
    }
}
