package Com.ak.testseries.exam

/**
 * Exact-key resolver for versioned official syllabus snapshots.
 *
 * This registry intentionally contains no implicit aliases or fallback matching.
 * Only snapshots explicitly registered as APPROVED can be injected as official scope.
 */
enum class SyllabusApprovalState {
    SOURCE_IDENTIFIED,
    TRANSCRIBED,
    PENDING_REVIEW,
    APPROVED,
    SUPERSEDED,
    REJECTED
}

data class SyllabusSnapshotKey(
    val examId: String,
    val syllabusVersion: String,
    val stageId: String,
    val paperId: String,
    val subjectId: String
)

data class SyllabusSourceEvidence(
    val issuer: String,
    val title: String,
    val sourceUrl: String,
    val publishedYear: Int,
    val sourcePages: List<Int>
)

data class SyllabusTopicNode(
    val nodeId: String,
    val titleEn: String,
    val titleHi: String? = null,
    val parentId: String? = null,
    val officialTextEn: String,
    val sourcePage: Int
)

data class VersionedSyllabusSnapshot(
    val key: SyllabusSnapshotKey,
    val approvalState: SyllabusApprovalState,
    val source: SyllabusSourceEvidence,
    val nodes: List<SyllabusTopicNode>,
    val reviewerId: String? = null,
    val reviewedAtEpochMs: Long? = null
) {
    fun isStructurallyValid(): Boolean {
        if (key.examId.isBlank() || key.syllabusVersion.isBlank() ||
            key.stageId.isBlank() || key.paperId.isBlank() || key.subjectId.isBlank()
        ) return false
        if (source.issuer.isBlank() || source.title.isBlank() || source.sourceUrl.isBlank()) return false
        if (nodes.isEmpty()) return false
        if (nodes.any { it.nodeId.isBlank() || it.titleEn.isBlank() || it.officialTextEn.isBlank() || it.sourcePage < 1 }) return false
        if (nodes.map { it.nodeId }.distinct().size != nodes.size) return false
        return true
    }

    fun canBeUsedAsOfficialScope(): Boolean =
        approvalState == SyllabusApprovalState.APPROVED &&
            reviewerId?.isNotBlank() == true &&
            reviewedAtEpochMs != null &&
            isStructurallyValid()
}

object VersionedSyllabusResolver {
    private val snapshots = mutableMapOf<SyllabusSnapshotKey, VersionedSyllabusSnapshot>()

    @Synchronized
    fun register(snapshot: VersionedSyllabusSnapshot): Boolean {
        if (!snapshot.isStructurallyValid()) return false
        snapshots[snapshot.key] = snapshot
        return true
    }

    @Synchronized
    fun resolveApproved(key: SyllabusSnapshotKey): VersionedSyllabusSnapshot? =
        snapshots[key]?.takeIf { it.canBeUsedAsOfficialScope() }

    @Synchronized
    fun resolveAnyState(key: SyllabusSnapshotKey): VersionedSyllabusSnapshot? =
        snapshots[key]

    @Synchronized
    fun clearForTests() {
        snapshots.clear()
    }

    fun exactKey(
        examId: String?,
        syllabusVersion: String?,
        stageId: String?,
        paperId: String?,
        subjectId: String?
    ): SyllabusSnapshotKey? {
        val values = listOf(examId, syllabusVersion, stageId, paperId, subjectId)
        if (values.any { it.isNullOrBlank() }) return null
        return SyllabusSnapshotKey(
            examId = examId!!,
            syllabusVersion = syllabusVersion!!,
            stageId = stageId!!,
            paperId = paperId!!,
            subjectId = subjectId!!
        )
    }
}
