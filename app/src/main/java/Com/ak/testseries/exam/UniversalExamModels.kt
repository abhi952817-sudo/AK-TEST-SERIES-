package Com.ak.testseries.exam

/**
 * Universal Exam Master Configuration Models
 * Supports 20+ Major Indian Competitive Examinations
 */
enum class QuestionType {
    // Standard Question Types (Formalized for Universal Exams)
    DIRECT_MCQ,
    STATEMENT_I_II,
    ASSERTION_REASON,
    MATCH_LIST_I_II,
    MULTI_STATEMENT,
    NUMERICAL_VALUE,
    DIAGRAM_LABELING,
    GRAPH,

    // Aliases & Extended Formats
    MCQ_SINGLE,
    MCQ_MULTIPLE,
    NUMERICAL,
    TITA,
    DESCRIPTIVE,
    PASSAGE_BASED,
    CASE_BASED,
    MATCHING,
    STATEMENT_BASED,
    DIAGRAM_BASED,
    TABLE_BASED;

    val displayName: String
        get() = when (this) {
            DIRECT_MCQ, MCQ_SINGLE -> "Direct MCQ"
            STATEMENT_I_II, STATEMENT_BASED -> "Statement I & II"
            ASSERTION_REASON -> "Assertion-Reason"
            MATCH_LIST_I_II, MATCHING -> "Match List I & II"
            MULTI_STATEMENT -> "Multi-Statement"
            NUMERICAL_VALUE, NUMERICAL, TITA -> "Numerical Value"
            DIAGRAM_LABELING, DIAGRAM_BASED -> "Diagram Labeling"
            GRAPH -> "Graph Based"
            MCQ_MULTIPLE -> "Multiple Correct"
            DESCRIPTIVE -> "Descriptive"
            PASSAGE_BASED -> "Passage Based"
            CASE_BASED -> "Case Based"
            TABLE_BASED -> "Table Based"
        }
}

data class DifficultyRatio(
    val easyPercent: Int = 40,
    val mediumPercent: Int = 40,
    val hardPercent: Int = 20
) {
    fun summary(): String = "Easy: ${easyPercent}%, Medium: ${mediumPercent}%, Hard: ${hardPercent}%"
}

enum class ValidationStatus {
    PROCESSING,
    EXTRACTED,
    VALIDATING,
    NEEDS_REVIEW,
    APPROVED,
    PUBLISHED,
    REJECTED,
    DUPLICATE,
    ARCHIVED
}

enum class DifficultyLevel {
    EASY,
    MEDIUM,
    HARD
}

data class TopicConfig(
    val topicId: String,
    val topicName: String,
    val topicNameHi: String? = null,
    val orderIndex: Int = 0,
    val subtopics: List<String> = emptyList()
)

data class ChapterConfig(
    val chapterId: String,
    val chapterName: String,
    val chapterNameHi: String? = null,
    val orderIndex: Int = 0,
    val academicLevel: String? = null, // e.g., "Class 11", "Class 12" where applicable
    val topics: List<TopicConfig> = emptyList()
)

data class SectionConfig(
    val sectionId: String,
    val sectionName: String,
    val totalQuestions: Int,
    val compulsoryQuestions: Int = totalQuestions,
    val positiveMarks: Double,
    val negativeMarks: Double,
    val questionType: QuestionType = QuestionType.MCQ_SINGLE,
    val optionsCount: Int = 4
)

data class SubjectConfig(
    val subjectId: String,
    val subjectName: String,
    val sections: List<SectionConfig> = emptyList(),
    val chapters: List<ChapterConfig> = emptyList(),
    val topics: List<String> = emptyList()
)

data class PaperConfig(
    val paperId: String,
    val paperName: String,
    val subjects: List<SubjectConfig>,
    val durationMinutes: Int,
    val totalMarks: Double
)

data class StageConfig(
    val stageId: String,
    val stageName: String, // e.g., "Tier 1", "Prelims", "Mains"
    val papers: List<PaperConfig>
)

data class ExamConfig(
    val examId: String,
    val examName: String,
    val shortName: String,
    val category: String,
    val stages: List<StageConfig>,
    val difficultyRatio: DifficultyRatio = DifficultyRatio(40, 40, 20),
    val supportedQuestionTypes: List<QuestionType> = listOf(QuestionType.MCQ_SINGLE),
    val aiSystemInstruction: String = "",
    val patternRules: List<String> = emptyList(),
    val language: String = "Bilingual (English & Hindi)",
    val defaultOptionCount: Int = 4,
    val active: Boolean = true,
    val verified: Boolean = false // Indicates if the configuration has been audited/verified
)

typealias ExamPatternConfig = ExamConfig

data class DiagramCropMetadata(
    val sourcePdfUrl: String,
    val pageNumber: Int,
    val cropCoordinates: String = "",
    val cropWidth: Int = 0,
    val cropHeight: Int = 0
)

data class QualityMetrics(
    val questionCompleteness: Int = 100,
    val optionIntegrity: Int = 100,
    val answerConfidence: Int = 100,
    val sourceConfidence: Int = 100,
    val explanationConsistency: Int = 100,
    val diagramIntegrity: Int = 100,
    val ocrIntegrity: Int = 100,
    val ambiguityScore: Int = 0,
    val duplicateRisk: Int = 0
)

data class UniversalQuestionModel(
    val questionId: String,
    val examId: String,
    val stageId: String = "",
    val paperId: String = "",
    val sectionId: String = "",
    val sectionName: String = "",
    val subjectId: String,
    val chapterId: String = "",
    val chapterName: String = "",
    val topicId: String = "",
    val questionText: String,
    val questionTextHi: String? = null,
    val options: List<String>,
    val optionsHi: List<String>? = null,
    val correctAnswer: String, // "A", "B", "C", "D" or index
    val sourceAnswer: String? = null, // Authoritative answer from key
    val explanation: String = "",
    val explanationHi: String? = null,
    val difficulty: DifficultyLevel = DifficultyLevel.MEDIUM,
    val questionType: QuestionType = QuestionType.DIRECT_MCQ,
    val positiveMarks: Double = 4.0,
    val negativeMarks: Double = 1.0,
    val difficultyScore: Int = 50, // 0-100
    val qualityScore: Int = 85, // 0-100
    val qualityMetrics: QualityMetrics = QualityMetrics(),
    val validationStatus: ValidationStatus = ValidationStatus.NEEDS_REVIEW,
    val validationReasons: List<String> = emptyList(),
    val duplicateHash: String = "",
    val diagramUrl: String? = null,
    val diagramMetadata: DiagramCropMetadata? = null,
    val sourceName: String = "",
    val sourcePage: Int = 1,
    val isOfficialPYQ: Boolean = false,
    val questionVersion: Int = 1,
    val validationVersion: Int = 1,
    val generationModel: String = "",
    val generationTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// Traversal extension functions for the Master Syllabus hierarchy: Exam -> Stage -> Paper -> Subject -> Chapter -> Topic
fun ExamConfig.findStage(stageId: String): StageConfig? = stages.find { it.stageId == stageId }
fun ExamConfig.findPaper(paperId: String): PaperConfig? = stages.flatMap { it.papers }.find { it.paperId == paperId }
fun ExamConfig.findSubject(subjectId: String): SubjectConfig? = stages.flatMap { it.papers.flatMap { p -> p.subjects } }.find { it.subjectId == subjectId }
fun ExamConfig.getAllChapters(): List<ChapterConfig> = stages.flatMap { it.papers.flatMap { p -> p.subjects.flatMap { s -> s.chapters } } }
fun ExamConfig.getAllTopics(): List<TopicConfig> = getAllChapters().flatMap { it.topics }

fun SubjectConfig.findChapter(chapterId: String): ChapterConfig? = chapters.find { it.chapterId == chapterId }
fun ChapterConfig.findTopic(topicId: String): TopicConfig? = topics.find { it.topicId == topicId }
