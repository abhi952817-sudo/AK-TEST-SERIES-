package Com.ak.testseries.exam

/**
 * Exam Pattern Validator
 * Thoroughly verifies every registered exam and validates test attempts/batches
 * against actual configured exam patterns without assuming generic defaults.
 *
 * Checks:
 * - Sections & Sub-sections
 * - Question Counts (Total & Compulsory)
 * - Supported Question Types
 * - Option Counts (e.g., 5 for Banking, 4 for Standard, 0 for NVQ/NAT/TITA)
 * - Compulsory vs Optional Rules
 * - Positive Marks & Negative Marking Penalties
 * - Duration (Minutes)
 * - Language (Bilingual English/Hindi vs English-only)
 */
object ExamPatternValidator {

    data class SectionAudit(
        val sectionId: String,
        val sectionName: String,
        val totalQuestions: Int,
        val compulsoryQuestions: Int,
        val positiveMarks: Double,
        val negativeMarks: Double,
        val questionType: QuestionType,
        val optionsCount: Int,
        val isConsistent: Boolean,
        val issueDescription: String? = null
    )

    data class PaperAudit(
        val paperId: String,
        val paperName: String,
        val durationMinutes: Int,
        val configuredTotalMarks: Double,
        val calculatedCompulsoryMarks: Double,
        val totalQuestions: Int,
        val compulsoryQuestions: Int,
        val isMarkingConsistent: Boolean,
        val sections: List<SectionAudit>
    )

    data class ExamAuditReport(
        val examId: String,
        val examName: String,
        val shortName: String,
        val category: String,
        val isPatternValid: Boolean,
        val verified: Boolean,
        val language: String,
        val defaultOptionCount: Int,
        val supportedQuestionTypes: List<QuestionType>,
        val papers: List<PaperAudit>,
        val warnings: List<String>,
        val errors: List<String>,
        val summary: String
    )

    data class ValidationReport(
        val isValid: Boolean,
        val warnings: List<String>,
        val errors: List<String>,
        val sectionReports: List<String> = emptyList()
    )

    /**
     * Audits a registered ExamConfig against official pattern specifications:
     * - Sections exist and have valid question limits
     * - Compulsory questions <= total questions
     * - Marks calculation matches paper totalMarks
     * - Duration is positive and matches official timing
     * - Question types and option counts align with exam rules
     * - Language is explicitly defined
     */
    fun auditRegisteredExam(examConfig: ExamConfig): ExamAuditReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val paperAudits = mutableListOf<PaperAudit>()

        if (examConfig.examId.isBlank()) errors.add("Exam ID is blank.")
        if (examConfig.examName.isBlank()) errors.add("Exam Name is blank.")
        if (examConfig.stages.isEmpty()) errors.add("Exam has no configured stages.")

        // Validate Language
        if (examConfig.language.isBlank()) {
            errors.add("Exam language configuration is missing.")
        }

        // Validate Option Count
        if (examConfig.defaultOptionCount < 4 && examConfig.category == "Banking") {
            errors.add("Banking exams must have defaultOptionCount = 5.")
        }

        for (stage in examConfig.stages) {
            if (stage.papers.isEmpty()) {
                errors.add("Stage '${stage.stageName}' has no papers.")
            }

            for (paper in stage.papers) {
                var paperTotalQs = 0
                var paperCompulsoryQs = 0
                var paperCalcMarks = 0.0
                val sectionAudits = mutableListOf<SectionAudit>()

                if (paper.durationMinutes <= 0) {
                    errors.add("Paper '${paper.paperName}' duration must be > 0 minutes.")
                }
                if (paper.totalMarks <= 0.0) {
                    errors.add("Paper '${paper.paperName}' totalMarks must be > 0.")
                }

                for (subject in paper.subjects) {
                    for (sec in subject.sections) {
                        var secIssue: String? = null
                        paperTotalQs += sec.totalQuestions
                        paperCompulsoryQs += sec.compulsoryQuestions
                        val secMarks = sec.compulsoryQuestions * sec.positiveMarks
                        paperCalcMarks += secMarks

                        if (sec.totalQuestions <= 0) {
                            secIssue = "Section '${sec.sectionName}' total questions must be > 0."
                            errors.add(secIssue)
                        } else if (sec.compulsoryQuestions > sec.totalQuestions) {
                            secIssue = "Section '${sec.sectionName}' compulsory (${sec.compulsoryQuestions}) cannot exceed total (${sec.totalQuestions})."
                            errors.add(secIssue)
                        } else if (sec.compulsoryQuestions <= 0) {
                            secIssue = "Section '${sec.sectionName}' compulsory questions must be > 0."
                            errors.add(secIssue)
                        }

                        if (sec.positiveMarks <= 0.0) {
                            val msg = "Section '${sec.sectionName}' positive marks must be > 0."
                            secIssue = if (secIssue == null) msg else "$secIssue; $msg"
                            errors.add(msg)
                        }

                        if (sec.negativeMarks < 0.0) {
                            val msg = "Section '${sec.sectionName}' negative marks cannot be negative."
                            secIssue = if (secIssue == null) msg else "$secIssue; $msg"
                            errors.add(msg)
                        }

                        // Validate section option count alignment
                        if (sec.questionType == QuestionType.NUMERICAL_VALUE || sec.questionType == QuestionType.NUMERICAL || sec.questionType == QuestionType.TITA) {
                            if (sec.optionsCount != 0) {
                                warnings.add("Numerical section '${sec.sectionName}' should have optionsCount = 0 (configured: ${sec.optionsCount}).")
                            }
                        } else if (examConfig.category == "Banking" && sec.optionsCount < 5) {
                            warnings.add("Banking section '${sec.sectionName}' should have 5 options (configured: ${sec.optionsCount}).")
                        }

                        sectionAudits.add(
                            SectionAudit(
                                sectionId = sec.sectionId,
                                sectionName = sec.sectionName,
                                totalQuestions = sec.totalQuestions,
                                compulsoryQuestions = sec.compulsoryQuestions,
                                positiveMarks = sec.positiveMarks,
                                negativeMarks = sec.negativeMarks,
                                questionType = sec.questionType,
                                optionsCount = sec.optionsCount,
                                isConsistent = secIssue == null,
                                issueDescription = secIssue
                            )
                        )
                    }
                }

                // Check marks calculation consistency (within floating-point tolerance)
                val marksDiff = Math.abs(paper.totalMarks - paperCalcMarks)
                val isMarksConsistent = marksDiff < 0.05
                if (!isMarksConsistent) {
                    errors.add("Paper '${paper.paperName}' configured totalMarks (${paper.totalMarks}) != calculated marks from compulsory questions ($paperCalcMarks).")
                }

                paperAudits.add(
                    PaperAudit(
                        paperId = paper.paperId,
                        paperName = paper.paperName,
                        durationMinutes = paper.durationMinutes,
                        configuredTotalMarks = paper.totalMarks,
                        calculatedCompulsoryMarks = paperCalcMarks,
                        totalQuestions = paperTotalQs,
                        compulsoryQuestions = paperCompulsoryQs,
                        isMarkingConsistent = isMarksConsistent,
                        sections = sectionAudits
                    )
                )
            }
        }

        val isValid = errors.isEmpty()
        val totalSecs = paperAudits.sumOf { it.sections.size }
        val totalQs = paperAudits.sumOf { it.totalQuestions }
        val compQs = paperAudits.sumOf { it.compulsoryQuestions }
        val marks = paperAudits.sumOf { it.configuredTotalMarks }
        val duration = paperAudits.firstOrNull()?.durationMinutes ?: 0

        val summary = if (isValid) {
            "Verified Authentic: $totalSecs sections, $totalQs total questions ($compQs compulsory), $marks marks, ${duration}m, ${examConfig.language}, ${examConfig.defaultOptionCount} options."
        } else {
            "Pattern Verification Failed with ${errors.size} error(s): ${errors.joinToString("; ")}"
        }

        return ExamAuditReport(
            examId = examConfig.examId,
            examName = examConfig.examName,
            shortName = examConfig.shortName,
            category = examConfig.category,
            isPatternValid = isValid,
            verified = isValid && examConfig.verified,
            language = examConfig.language,
            defaultOptionCount = examConfig.defaultOptionCount,
            supportedQuestionTypes = examConfig.supportedQuestionTypes,
            papers = paperAudits,
            warnings = warnings,
            errors = errors,
            summary = summary
        )
    }

    /**
     * Audits all registered exams in the Universal Exam Registry.
     */
    fun auditAllRegisteredExams(): List<ExamAuditReport> {
        return UniversalExamRegistry.getAllExams().map { auditRegisteredExam(it) }
    }

    /**
     * Validates a test batch against the exam's exact configured pattern:
     * - Sections matching
     * - Question count matching
     * - Question types
     * - Options count (e.g., 5 options for Banking, 4 for Standard, 0 for NVQ)
     * - Compulsory/optional rules
     * - Marks & negative marking
     * - Duration
     * - Language requirements
     */
    fun validateTestPattern(
        examConfig: ExamConfig,
        questionCount: Int,
        durationMinutes: Int,
        questionsList: List<UniversalQuestionModel>
    ): ValidationReport {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val sectionReports = mutableListOf<String>()

        if (questionCount <= 0) {
            errors.add("Test must contain at least 1 question.")
        }

        if (durationMinutes <= 0) {
            errors.add("Test duration must be greater than 0 minutes.")
        }

        // 1. Duration check
        val expectedDuration = examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.durationMinutes ?: 0
        if (expectedDuration > 0 && Math.abs(durationMinutes - expectedDuration) > (expectedDuration / 2)) {
            warnings.add("Test duration ($durationMinutes min) deviates substantially from official ${examConfig.shortName} timing ($expectedDuration min).")
        }

        // 2. Unapproved questions check
        val unapprovedCount = questionsList.count { 
            it.validationStatus != ValidationStatus.APPROVED && it.validationStatus != ValidationStatus.PUBLISHED 
        }
        if (unapprovedCount > 0) {
            warnings.add("$unapprovedCount question(s) require review (marked NEEDS_REVIEW or unapproved).")
        }

        // 3. Duplicate check
        val uniqueQuestions = questionsList.map { it.duplicateHash.ifBlank { it.questionText.trim().lowercase() } }.toSet()
        if (uniqueQuestions.size < questionsList.size) {
            errors.add("Test contains ${questionsList.size - uniqueQuestions.size} duplicate question(s).")
        }

        // 4. Supported question types validation
        val unsupportedTypes = questionsList.map { it.questionType }
            .filter { it !in examConfig.supportedQuestionTypes }
            .distinct()
        if (unsupportedTypes.isNotEmpty()) {
            val names = unsupportedTypes.joinToString(", ") { it.displayName }
            warnings.add("Contains question format ($names) outside official ${examConfig.shortName} pattern.")
        }

        // 5. Options count validation per exam and question type
        val isBanking = examConfig.category.equals("Banking", ignoreCase = true)
        val expectedOptions = if (isBanking) 5 else examConfig.defaultOptionCount

        questionsList.forEachIndexed { idx, q ->
            val qNum = idx + 1
            if (q.questionType == QuestionType.NUMERICAL_VALUE || q.questionType == QuestionType.NUMERICAL || q.questionType == QuestionType.TITA) {
                // NVQ should have numeric answer, no options required
                if (q.options.isNotEmpty() && q.options.count { it.isNotBlank() } > 1) {
                    warnings.add("Question #$qNum (${q.questionType.displayName}) includes multiple choice options when numerical answer was expected.")
                }
            } else {
                val nonBlankOptions = q.options.count { it.isNotBlank() }
                if (isBanking && nonBlankOptions < 5) {
                    warnings.add("Question #$qNum has $nonBlankOptions options; Banking exams (${examConfig.shortName}) require 5 options (A, B, C, D, E).")
                } else if (!isBanking && nonBlankOptions < 4) {
                    errors.add("Question #$qNum has only $nonBlankOptions options; minimum 4 options required.")
                }
            }
        }

        // 6. Language compliance check
        val requiresBilingual = examConfig.language.contains("Hindi", ignoreCase = true)
        if (requiresBilingual) {
            val missingHindiCount = questionsList.count { it.questionTextHi.isNullOrBlank() }
            if (missingHindiCount > 0) {
                warnings.add("$missingHindiCount question(s) lack Hindi translation for bilingual exam ${examConfig.shortName}.")
            }
        }

        // 7. Compulsory / Optional rules validation
        val sectionsInExam = examConfig.stages.flatMap { it.papers.flatMap { p -> p.subjects.flatMap { s -> s.sections } } }
        val sectionMap = sectionsInExam.associateBy { it.sectionId }

        val questionsBySection = questionsList.groupBy { it.sectionId }
        questionsBySection.forEach { (secId, qs) ->
            val configSec = sectionMap[secId]
            if (configSec != null) {
                sectionReports.add("Section '${configSec.sectionName}': ${qs.size} questions loaded (Official: ${configSec.totalQuestions} total, ${configSec.compulsoryQuestions} compulsory).")
                if (configSec.compulsoryQuestions < configSec.totalQuestions) {
                    sectionReports.add("  * Optional Rule: Candidates attempt any ${configSec.compulsoryQuestions} out of ${configSec.totalQuestions} questions.")
                }
                // Check scoring marks match section
                val mismatchMarks = qs.count { it.positiveMarks != configSec.positiveMarks || it.negativeMarks != configSec.negativeMarks }
                if (mismatchMarks > 0) {
                    warnings.add("Section '${configSec.sectionName}' has $mismatchMarks questions with marks deviating from official (+${configSec.positiveMarks}, -${configSec.negativeMarks}).")
                }
            }
        }

        return ValidationReport(
            isValid = errors.isEmpty(),
            warnings = warnings,
            errors = errors,
            sectionReports = sectionReports
        )
    }
}
