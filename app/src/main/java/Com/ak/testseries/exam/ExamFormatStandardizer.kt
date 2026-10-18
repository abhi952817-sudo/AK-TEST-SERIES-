package Com.ak.testseries.exam

/**
 * Exam Format Standardizer
 * Ensures clear, consistent output formatting for every exam.
 * Strictly enforces each exam's configured question pattern, section structure,
 * question type, options, answer, explanation, marks, and required metadata without mixing formats.
 */
object ExamFormatStandardizer {

    data class FormattedSectionGroup(
        val sectionId: String,
        val sectionName: String,
        val questionType: QuestionType,
        val positiveMarks: Double,
        val negativeMarks: Double,
        val totalQuestions: Int,
        val compulsoryQuestions: Int,
        val questions: List<UniversalQuestionModel>
    )

    data class StandardizedExamPaper(
        val examId: String,
        val examName: String,
        val shortName: String,
        val category: String,
        val durationMinutes: Int,
        val totalMarks: Double,
        val totalQuestions: Int,
        val sectionGroups: List<FormattedSectionGroup>,
        val metadata: Map<String, String>
    )

    /**
     * Standardizes a raw list of questions for a specific exam configuration,
     * grouping them into their exact configured sections, assigning proper marks,
     * enforcing question type integrity, and ensuring options and answers match the format.
     */
    fun standardizeExamOutput(
        examConfig: ExamConfig,
        subjectId: String,
        questions: List<UniversalQuestionModel>,
        durationMinutes: Int = 0
    ): StandardizedExamPaper {
        val applicableSections = UniversalAIContext.getApplicableSections(examConfig, subjectId)
        val defaultSection = SectionConfig(
            sectionId = "SECTION_A",
            sectionName = "Section A",
            totalQuestions = questions.size.coerceAtLeast(1),
            compulsoryQuestions = questions.size.coerceAtLeast(1),
            positiveMarks = UniversalExamRegistry.getMarkingSchemeForExam(examConfig.examName).first,
            negativeMarks = UniversalExamRegistry.getMarkingSchemeForExam(examConfig.examName).second,
            questionType = examConfig.supportedQuestionTypes.firstOrNull() ?: QuestionType.DIRECT_MCQ
        )
        val sectionsToUse = if (applicableSections.isNotEmpty()) applicableSections else listOf(defaultSection)

        val groupedQuestions = mutableMapOf<String, MutableList<UniversalQuestionModel>>()
        sectionsToUse.forEach { groupedQuestions[it.sectionId] = mutableListOf() }

        questions.forEachIndexed { index, q ->
            val section = UniversalPdfPipeline.resolveSectionForIndex(examConfig, subjectId, index + 1)
                ?: sectionsToUse.first()

            val isNumerical = (section.questionType == QuestionType.NUMERICAL_VALUE ||
                    section.questionType == QuestionType.NUMERICAL ||
                    section.questionType == QuestionType.TITA)

            // Strictly sanitize options to prevent format pollution
            val sanitizedOptions = if (isNumerical) {
                emptyList()
            } else if (q.options.size == 4) {
                q.options
            } else {
                listOf(
                    q.options.getOrElse(0) { "Option A" },
                    q.options.getOrElse(1) { "Option B" },
                    q.options.getOrElse(2) { "Option C" },
                    q.options.getOrElse(3) { "Option D" }
                )
            }

            val standardizedQ = q.copy(
                examId = examConfig.examId,
                sectionId = section.sectionId,
                sectionName = section.sectionName,
                questionType = section.questionType,
                positiveMarks = section.positiveMarks,
                negativeMarks = section.negativeMarks,
                options = sanitizedOptions,
                optionsHi = if (isNumerical) null else q.optionsHi
            )

            groupedQuestions.getOrPut(section.sectionId) { mutableListOf() }.add(standardizedQ)
        }

        val sectionGroups = sectionsToUse.mapNotNull { sec ->
            val qList = groupedQuestions[sec.sectionId] ?: emptyList()
            if (qList.isNotEmpty()) {
                FormattedSectionGroup(
                    sectionId = sec.sectionId,
                    sectionName = sec.sectionName,
                    questionType = sec.questionType,
                    positiveMarks = sec.positiveMarks,
                    negativeMarks = sec.negativeMarks,
                    totalQuestions = qList.size,
                    compulsoryQuestions = sec.compulsoryQuestions.coerceAtMost(qList.size),
                    questions = qList
                )
            } else null
        }

        val totalMarksCalculated = sectionGroups.sumOf { group ->
            group.compulsoryQuestions * group.positiveMarks
        }

        val resolvedDuration = if (durationMinutes > 0) durationMinutes else {
            examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.durationMinutes ?: 180
        }

        return StandardizedExamPaper(
            examId = examConfig.examId,
            examName = examConfig.examName,
            shortName = examConfig.shortName,
            category = examConfig.category,
            durationMinutes = resolvedDuration,
            totalMarks = totalMarksCalculated,
            totalQuestions = questions.size,
            sectionGroups = sectionGroups,
            metadata = mapOf(
                "subject" to subjectId,
                "easyPercent" to "${examConfig.difficultyRatio.easyPercent}%",
                "mediumPercent" to "${examConfig.difficultyRatio.mediumPercent}%",
                "hardPercent" to "${examConfig.difficultyRatio.hardPercent}%",
                "patternRules" to examConfig.patternRules.joinToString(" | ")
            )
        )
    }

    /**
     * Formats a single question into a clean, professional string representation
     * honoring the exact question type, marks, and explanation.
     */
    fun formatQuestionPreview(q: UniversalQuestionModel, index: Int = 1): String {
        return buildString {
            appendLine("Q$index [${q.sectionName.ifBlank { "Section A" }} • +${q.positiveMarks}/-${q.negativeMarks} Marks • ${q.questionType.displayName}]:")
            appendLine(q.questionText)
            if (!q.questionTextHi.isNullOrBlank()) {
                appendLine("हिंदी: ${q.questionTextHi}")
            }
            if (q.options.isNotEmpty()) {
                val labels = listOf("A", "B", "C", "D")
                q.options.forEachIndexed { i, opt ->
                    val label = labels.getOrElse(i) { "${i + 1}" }
                    val hiOpt = q.optionsHi?.getOrNull(i)
                    if (!hiOpt.isNullOrBlank()) {
                        appendLine("  ($label) $opt  [हिंदी: $hiOpt]")
                    } else {
                        appendLine("  ($label) $opt")
                    }
                }
            }
            appendLine("Correct Answer: ${q.correctAnswer}")
            if (q.explanation.isNotBlank()) {
                appendLine("Explanation: ${q.explanation}")
            }
            if (!q.explanationHi.isNullOrBlank()) {
                appendLine("व्याख्या: ${q.explanationHi}")
            }
        }.trim()
    }
}
