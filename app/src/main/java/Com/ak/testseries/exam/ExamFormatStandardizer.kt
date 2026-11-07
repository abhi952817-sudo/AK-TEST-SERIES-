package Com.ak.testseries.exam

/**
 * Produces a standardized view only from a source-verified exam, exact paper and subject.
 * Missing question data is rejected; this class never manufactures options or sections.
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

    fun standardizeExamOutput(
        examConfig: ExamConfig,
        subjectId: String,
        questions: List<UniversalQuestionModel>,
        durationMinutes: Int = 0
    ): StandardizedExamPaper {
        require(examConfig.active && examConfig.verified) {
            "Cannot standardize questions against an unverified exam pattern."
        }
        require(questions.isNotEmpty()) { "No real questions were supplied for standardization." }
        val stage = examConfig.stages.singleOrNull()
            ?: throw IllegalArgumentException("Select an exact stage before standardizing.")
        val paper = stage.papers.singleOrNull()
            ?: throw IllegalArgumentException("Select an exact paper before standardizing.")
        val subject = paper.subjects.singleOrNull {
            it.subjectId.equals(subjectId.trim(), true) ||
                it.subjectName.equals(subjectId.trim(), true)
        } ?: throw IllegalArgumentException("Unknown or ambiguous subject '$subjectId'.")
        val applicableSections = subject.sections
        require(applicableSections.isNotEmpty()) { "Selected subject has no verified sections." }
        require(applicableSections.map { it.sectionId }.distinct().size == applicableSections.size) {
            "Section IDs must be unique within the selected subject."
        }

        val groupedQuestions = applicableSections.associate { it.sectionId to mutableListOf<UniversalQuestionModel>() }.toMutableMap()
        val seenQuestionIds = mutableSetOf<String>()

        questions.forEachIndexed { index, question ->
            require(question.questionId.isNotBlank() && seenQuestionIds.add(question.questionId)) {
                "Question ${index + 1} has a missing or duplicate question ID."
            }
            require(question.questionText.isNotBlank()) { "Question ${index + 1} has no question text." }
            require(question.correctAnswer.isNotBlank()) { "Question ${index + 1} has no answer key." }

            val section = UniversalPdfPipeline.resolveSectionForIndex(examConfig, subject.subjectId, index + 1)
                ?: throw IllegalArgumentException("Question ${index + 1} is outside the configured paper sections.")
            val expectedType = section.questionType
            require(question.questionType == expectedType) {
                "Question ${index + 1} type conflicts with section '${section.sectionId}'."
            }
            if (question.positiveMarks > 0.0) {
                require(question.positiveMarks == section.positiveMarks) {
                    "Question ${index + 1} positive marks conflict with the verified section policy."
                }
            }
            if (question.negativeMarks > 0.0) {
                require(question.negativeMarks == section.negativeMarks) {
                    "Question ${index + 1} negative marks conflict with the verified section policy."
                }
            }

            val isNumerical = expectedType in setOf(
                QuestionType.NUMERICAL_VALUE, QuestionType.NUMERICAL, QuestionType.TITA
            )
            val answerKey = question.correctAnswer.trim().uppercase()
            val answerKeyValid = if (isNumerical) answerKey.toDoubleOrNull()?.isFinite() == true
                else answerKey in setOf("A", "B", "C", "D", "1", "2", "3", "4")
            require(answerKeyValid) { "Question ${index + 1} has an invalid answer key for its configured type." }
            val sanitizedOptions = if (isNumerical) {
                require(question.options.isEmpty()) {
                    "Numerical question ${index + 1} must not contain MCQ options."
                }
                emptyList()
            } else {
                require(question.options.size == section.optionsCount &&
                    question.options.all { it.isNotBlank() } &&
                    question.options.distinctBy { it.trim().lowercase() }.size == section.optionsCount) {
                    "Question ${index + 1} must contain exactly ${section.optionsCount} distinct non-empty options."
                }
                if (!question.optionsHi.isNullOrEmpty()) {
                    require(question.optionsHi.size == section.optionsCount &&
                        question.optionsHi.all { it.isNotBlank() }) {
                        "Question ${index + 1} has incomplete Hindi options."
                    }
                }
                question.options
            }

            val standardized = question.copy(
                examId = examConfig.examId,
                subjectId = subject.subjectId,
                sectionId = section.sectionId,
                sectionName = section.sectionName,
                questionType = section.questionType,
                positiveMarks = section.positiveMarks,
                negativeMarks = section.negativeMarks,
                options = sanitizedOptions,
                optionsHi = if (isNumerical) null else question.optionsHi
            )
            groupedQuestions.getValue(section.sectionId).add(standardized)
        }

        val sectionGroups = applicableSections.mapNotNull { section ->
            val sectionQuestions = groupedQuestions.getValue(section.sectionId)
            if (sectionQuestions.isEmpty()) null else FormattedSectionGroup(
                sectionId = section.sectionId,
                sectionName = section.sectionName,
                questionType = section.questionType,
                positiveMarks = section.positiveMarks,
                negativeMarks = section.negativeMarks,
                totalQuestions = sectionQuestions.size,
                compulsoryQuestions = section.compulsoryQuestions.coerceAtMost(sectionQuestions.size),
                questions = sectionQuestions
            )
        }
        val totalMarks = sectionGroups.sumOf { it.compulsoryQuestions * it.positiveMarks }
        val resolvedDuration = durationMinutes.takeIf { it > 0 } ?: paper.durationMinutes
        require(resolvedDuration > 0) { "Verified paper duration is not configured." }

        return StandardizedExamPaper(
            examId = examConfig.examId,
            examName = examConfig.examName,
            shortName = examConfig.shortName,
            category = examConfig.category,
            durationMinutes = resolvedDuration,
            totalMarks = totalMarks,
            totalQuestions = questions.size,
            sectionGroups = sectionGroups,
            metadata = mapOf(
                "subject" to subject.subjectName,
                "stageId" to stage.stageId,
                "paperId" to paper.paperId,
                "patternVerified" to "true",
                "easyPercent" to "${examConfig.difficultyRatio.easyPercent}%",
                "mediumPercent" to "${examConfig.difficultyRatio.mediumPercent}%",
                "hardPercent" to "${examConfig.difficultyRatio.hardPercent}%",
                "patternRules" to examConfig.patternRules.joinToString(" | ")
            )
        )
    }

    fun formatQuestionPreview(q: UniversalQuestionModel, index: Int = 1): String {
        return buildString {
            appendLine("Q$index [${q.sectionName.ifBlank { "Section not verified" }} • +${q.positiveMarks}/-${q.negativeMarks} Marks • ${q.questionType.displayName}]:")
            appendLine(q.questionText)
            if (!q.questionTextHi.isNullOrBlank()) appendLine("हिंदी: ${q.questionTextHi}")
            if (q.options.isNotEmpty()) {
                val labels = listOf("A", "B", "C", "D", "E")
                q.options.forEachIndexed { i, option ->
                    val label = labels.getOrElse(i) { "Option ${i + 1}" }
                    val hindiOption = q.optionsHi?.getOrNull(i)
                    if (!hindiOption.isNullOrBlank()) appendLine("  ($label) $option  [हिंदी: $hindiOption]")
                    else appendLine("  ($label) $option")
                }
            }
            if (q.correctAnswer.isNotBlank()) appendLine("Correct Answer: ${q.correctAnswer}")
            if (q.explanation.isNotBlank()) appendLine("Explanation: ${q.explanation}")
            if (!q.explanationHi.isNullOrBlank()) appendLine("व्याख्या: ${q.explanationHi}")
        }.trim()
    }
}
