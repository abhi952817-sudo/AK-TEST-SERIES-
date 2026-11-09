package Com.ak.testseries.exam

/**
 * Resolves a question-generation route only when the selected exam and subject
 * identify exactly one configured paper. Ambiguous routes are blocked instead
 * of borrowing another exam's or paper's question pattern.
 */
object QuestionGenerationRouteResolver {

    data class Route(
        val examConfig: ExamConfig,
        val stageId: String,
        val paperId: String,
        val subjectId: String,
        val subjectName: String
    )

    data class Resolution(
        val route: Route? = null,
        val error: String? = null
    )

    fun resolve(
        examQuery: String,
        subjectQuery: String,
        stageId: String? = null,
        paperId: String? = null
    ): Resolution {
        val normalizedExam = examQuery.trim()
        val normalizedSubject = subjectQuery.trim()

        if (normalizedExam.isBlank()) {
            return Resolution(error = "Question generation stopped: select an exact exam first.")
        }
        if (normalizedSubject.isBlank() || normalizedSubject.equals("All Subjects", ignoreCase = true)) {
            return Resolution(error = "Question generation stopped: select one configured subject. All-subject generation requires a separate subject-distribution plan.")
        }

        val examConfig = UniversalExamRegistry.getExamById(normalizedExam)
            ?: UniversalExamRegistry.findExamByNameOrShortName(normalizedExam)
            ?: return Resolution(error = "Question generation stopped: '${normalizedExam}' is unknown or ambiguous. Select an exact exam.")
        if (!examConfig.active) {
            return Resolution(error = "Question generation stopped: ${examConfig.examName} is currently inactive.")
        }

        val requestedStageId = stageId?.trim().orEmpty()
        val requestedPaperId = paperId?.trim().orEmpty()
        val matches = examConfig.stages
            .filter { requestedStageId.isBlank() || it.stageId == requestedStageId }
            .flatMap { stage ->
                stage.papers
                    .filter { requestedPaperId.isBlank() || it.paperId == requestedPaperId }
                    .flatMap { paper ->
                        paper.subjects.filter { subject ->
                            subject.subjectId.equals(normalizedSubject, ignoreCase = true) ||
                                subject.subjectName.equals(normalizedSubject, ignoreCase = true)
                        }.map { subject ->
                            Route(
                                examConfig = examConfig,
                                stageId = stage.stageId,
                                paperId = paper.paperId,
                                subjectId = subject.subjectId,
                                subjectName = subject.subjectName
                            )
                        }
                    }
            }

        return when (matches.size) {
            1 -> Resolution(route = matches.single())
            0 -> Resolution(error = "Question generation stopped: '${normalizedSubject}' is not configured for ${examConfig.examName}. No questions were generated.")
            else -> Resolution(error = "Question generation stopped: '${normalizedSubject}' exists in ${matches.size} papers/stages of ${examConfig.examName}. Select a specific paper/stage before generating questions.")
        }
    }
}
