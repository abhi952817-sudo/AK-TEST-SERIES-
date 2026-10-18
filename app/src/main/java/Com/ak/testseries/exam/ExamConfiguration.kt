package Com.ak.testseries.exam

/**
 * Flexible and scalable exam configuration data structure using enums and data classes.
 * Models examId, stages, papers, subjects, and scoring rules without hardcoding.
 */

enum class ExamCategory {
    MEDICAL,
    ENGINEERING,
    CIVIL_SERVICES,
    UNIVERSITY_ENTRANCE,
    BANKING,
    RAILWAY,
    STAFF_SELECTION,
    TEACHING,
    DEFENCE,
    OTHER
}

enum class QuestionFormat {
    MULTIPLE_CHOICE_SINGLE,
    MULTIPLE_CHOICE_MULTIPLE,
    STATEMENT_BASED,
    ASSERTION_REASONING,
    NUMERICAL_INTEGER,
    MATCHING_LISTS,
    CASE_STUDY,
    PASSAGE_BASED
}

enum class ScoringPolicyType {
    STANDARD_PLUS_MINUS, // e.g., +4 for correct, -1 for incorrect
    ONE_THIRD_NEGATIVE,  // e.g., UPSC +2, -0.66
    NO_NEGATIVE,         // e.g., CTET +1, 0 negative
    PERCENTAGE_BASED
}

data class ScoringRule(
    val policyType: ScoringPolicyType = ScoringPolicyType.STANDARD_PLUS_MINUS,
    val positiveMarks: Double = 4.0,
    val negativeMarks: Double = 1.0,
    val unattemptedMarks: Double = 0.0
)

data class SectionDefinition(
    val sectionId: String,
    val sectionName: String,
    val totalQuestions: Int,
    val compulsoryQuestions: Int,
    val scoringRule: ScoringRule,
    val questionFormat: QuestionFormat = QuestionFormat.MULTIPLE_CHOICE_SINGLE
)

data class SubjectDefinition(
    val subjectId: String,
    val subjectName: String,
    val sections: List<SectionDefinition> = emptyList(),
    val topics: List<String> = emptyList()
)

data class PaperDefinition(
    val paperId: String,
    val paperName: String,
    val durationMinutes: Int,
    val totalMarks: Double,
    val subjects: List<SubjectDefinition> = emptyList()
)

data class StageDefinition(
    val stageId: String,
    val stageName: String, // e.g., "Prelims", "Mains", "Tier 1", "Tier 2"
    val papers: List<PaperDefinition> = emptyList()
)

data class ExamConfiguration(
    val examId: String,
    val examName: String,
    val shortName: String,
    val category: ExamCategory,
    val stages: List<StageDefinition> = emptyList(),
    val difficultyLevel: DifficultyLevel = DifficultyLevel.MEDIUM,
    val supportedQuestionFormats: List<QuestionFormat> = listOf(QuestionFormat.MULTIPLE_CHOICE_SINGLE),
    val aiSystemPrompt: String = "",
    val guidelines: List<String> = emptyList(),
    val isActive: Boolean = true,
    val isVerified: Boolean = false
)
