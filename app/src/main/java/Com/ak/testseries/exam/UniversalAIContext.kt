package Com.ak.testseries.exam

object UniversalAIContext {

    fun buildSystemInstruction(examConfig: ExamConfig): String {
        require(examConfig.active && examConfig.verified) {
            "AI generation is disabled for an unverified exam pattern."
        }
        require(examConfig.aiSystemInstruction.isNotBlank()) {
            "A source-reviewed exam-specific AI instruction is required."
        }
        require(examConfig.defaultOptionCount > 0 && examConfig.supportedQuestionTypes.isNotEmpty()) {
            "Verified question type and option-count policy is incomplete."
        }
        val diff = examConfig.difficultyRatio
        val qTypes = examConfig.supportedQuestionTypes.joinToString(", ") { "${it.name} (${it.displayName})" }
        val rules = examConfig.patternRules.joinToString("; ")

        return buildString {
            appendLine("=== SOURCE-VERIFIED EXAM INSTRUCTION: ${examConfig.examName} (${examConfig.examId}) ===")
            appendLine(examConfig.aiSystemInstruction)
            appendLine()
            appendLine("TARGET DIFFICULTY DISTRIBUTION:")
            appendLine("- Easy: ${diff.easyPercent}%")
            appendLine("- Medium: ${diff.mediumPercent}%")
            appendLine("- Hard: ${diff.hardPercent}%")
            appendLine()
            appendLine("SUPPORTED QUESTION TYPES: $qTypes")
            appendLine("VERIFIED PATTERN RULES: $rules")
            appendLine()
            appendLine("STRICT CONTENT RULES:")
            appendLine("1. Use only facts supported by the supplied source material and verified curriculum.")
            appendLine("2. Never invent question text, options, answer keys, explanations, source pages or exam metadata.")
            appendLine("3. Never add generic marks, section names, option labels or placeholder content.")
            appendLine("4. The application assigns section identity and marks from the verified exam policy. Do not output section IDs or marks.")
            appendLine("5. For MCQ_SINGLE sections, provide exactly ${examConfig.defaultOptionCount} distinct real options and a valid option key.")
            appendLine("6. For numerical sections, provide no MCQ options and a finite numerical answer.")
            appendLine("7. If the source does not contain or support a reliable answer key, leave correctAnswer empty. That question will be rejected from scoring.")
            appendLine("8. Difficulty must be assessed from the question itself. Do not use a fixed default.")
            if (examConfig.language.contains("Hindi", ignoreCase = true)) {
                appendLine("9. Provide accurate English and Hindi question/options/explanation text when the source contains both.")
            } else {
                appendLine("9. Use English only; do not fabricate translations.")
            }
        }.trim()
    }

    fun getApplicableSections(examConfig: ExamConfig, subject: String): List<SectionConfig> {
        require(examConfig.active && examConfig.verified) {
            "Section lookup is disabled for an unverified exam pattern."
        }
        val stage = examConfig.stages.singleOrNull()
            ?: throw IllegalArgumentException("Select an exact exam stage.")
        val paper = stage.papers.singleOrNull()
            ?: throw IllegalArgumentException("Select an exact exam paper.")
        val subjectConfig = paper.subjects.singleOrNull {
            it.subjectName.equals(subject.trim(), true) || it.subjectId.equals(subject.trim(), true)
        } ?: throw IllegalArgumentException("Unknown or ambiguous subject '$subject'.")
        require(subjectConfig.sections.isNotEmpty()) { "Selected subject has no verified sections." }
        return subjectConfig.sections
    }

    fun buildDynamicGenerationPrompt(
        examConfig: ExamConfig,
        subject: String,
        testTitle: String,
        compactMarkdownPayload: String,
        currentChunkSize: Int,
        startQ: Int,
        endQ: Int,
        totalQuestions: Int,
        isExtractionMode: Boolean = false
    ): String {
        require(examConfig.active && examConfig.verified) {
            "Generation is blocked for an unverified exam pattern."
        }
        require(compactMarkdownPayload.isNotBlank()) {
            "A real source document is required; synthetic syllabus fallback is disabled."
        }
        require(currentChunkSize > 0 && startQ > 0 && endQ >= startQ && totalQuestions >= endQ) {
            "Question range metadata is invalid."
        }
        val systemInstruction = buildSystemInstruction(examConfig)
        val diff = examConfig.difficultyRatio
        val qTypes = examConfig.supportedQuestionTypes.joinToString(", ") { it.name }
        val sections = getApplicableSections(examConfig, subject)
        val sectionDetails = sections.joinToString("\n") { section ->
            "  * ${section.sectionId}: ${section.sectionName}; total=${section.totalQuestions}; compulsory=${section.compulsoryQuestions}; type=${section.questionType.name}; positive=${section.positiveMarks}; negative=${section.negativeMarks}; options=${section.optionsCount}"
        }

        val objective = if (isExtractionMode) {
            """
            TASK: VERBATIM SOURCE EXTRACTION.
            Extract only questions that actually exist in the supplied source text.
            Preserve their original order and wording. Do not synthesize missing questions to reach a count.
            If the source lacks a reliable answer key, leave correctAnswer empty; the application will reject it for scoring.
            """.trimIndent()
        } else {
            """
            TASK: SOURCE-BASED QUESTION CONSTRUCTION.
            Create exactly $currentChunkSize questions grounded in the supplied source material for:
            - Exam: ${examConfig.examName} (${examConfig.examId})
            - Subject: $subject
            - Test title: $testTitle
            - Question range: $startQ-$endQ of $totalQuestions
            - Difficulty distribution: Easy ${diff.easyPercent}%, Medium ${diff.mediumPercent}%, Hard ${diff.hardPercent}%
            - Supported question types: $qTypes
            """.trimIndent()
        }

        return """
            $systemInstruction

            $objective

            VERIFIED SUBJECT SECTION POLICY:
            $sectionDetails

            OUTPUT RULES:
            - Return only a JSON array with exactly $currentChunkSize objects if the source supports that many.
            - Do not include sectionId, sectionName, positiveMarks or negativeMarks. The application derives them from the verified section policy and question order.
            - Do not add sample, dummy, generic or placeholder questions/options/answers.
            - MCQ options must be real, distinct and complete. Numerical questions must not contain options.
            - correctAnswer must be a verified option key or finite numeric value. Otherwise use an empty string.
            - difficulty must be EASY, MEDIUM or HARD based on the actual question.
            - Keep explanations factual and concise. Do not output hidden chain-of-thought.

            REQUIRED JSON SHAPE (empty values are required fields to be filled with real source-derived content):
            [
              {
                "questionTextEn": "",
                "questionTextHi": "",
                "optionAEn": "",
                "optionBEn": "",
                "optionCEn": "",
                "optionDEn": "",
                "optionAHi": "",
                "optionBHi": "",
                "optionCHi": "",
                "optionDHi": "",
                "correctAnswer": "",
                "explanationEn": "",
                "explanationHi": "",
                "difficulty": "",
                "chapter": "",
                "topic": "",
                "needsImage": false
              }
            ]

            SOURCE MATERIAL:
            ${compactMarkdownPayload.take(50000)}
        """.trimIndent()
    }

    fun buildExtractionPrompt(examConfig: ExamConfig, subjectId: String, expectedQuestions: Int): String {
        require(examConfig.active && examConfig.verified) {
            "Extraction is blocked for an unverified exam pattern."
        }
        require(expectedQuestions > 0) { "Expected question count must be positive." }
        val sections = getApplicableSections(examConfig, subjectId)
        val sectionRules = sections.joinToString("\n") {
            "- ${it.sectionId}: ${it.sectionName}; ${it.totalQuestions} total; ${it.questionType}; +${it.positiveMarks}/-${it.negativeMarks}"
        }
        val sysInst = buildSystemInstruction(examConfig)
        return """
            $sysInst

            Extract up to $expectedQuestions real questions for subject $subjectId from the supplied source.
            Do not invent missing options, answer keys, diagrams, explanations or source facts.
            If an answer key is absent or ambiguous, leave correctAnswer empty. Do not solve or guess it.
            Preserve exact wording, numerical values, units and source order.
            Use only these verified section rules:
            $sectionRules

            Return a JSON array. Use empty strings for unavailable fields. Do not create filler questions.
        """.trimIndent()
    }
}
