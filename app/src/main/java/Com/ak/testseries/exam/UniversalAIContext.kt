package Com.ak.testseries.exam

object UniversalAIContext {

    /**
     * Builds the comprehensive AI System Instruction tailored to the specific exam.
     */
    fun buildSystemInstruction(examConfig: ExamConfig): String {
        val diff = examConfig.difficultyRatio
        val qTypes = examConfig.supportedQuestionTypes.joinToString(", ") { "${it.name} (${it.displayName})" }
        val rules = examConfig.patternRules.joinToString("; ")

        return buildString {
            appendLine("=== EXAM-SPECIFIC SYSTEM INSTRUCTION: ${examConfig.examName} (${examConfig.shortName}) ===")
            if (examConfig.aiSystemInstruction.isNotBlank()) {
                appendLine(examConfig.aiSystemInstruction)
            } else {
                appendLine("You are an authoritative senior academic examiner and curriculum developer for ${examConfig.examName}.")
            }
            appendLine()
            appendLine("TARGET DIFFICULTY RATIO (Strict Distribution):")
            appendLine("- Easy: ${diff.easyPercent}% (Direct conceptual, formulaic, foundational recall)")
            appendLine("- Medium: ${diff.mediumPercent}% (Multi-step application, relational reasoning, calculation)")
            appendLine("- Hard: ${diff.hardPercent}% (Multi-concept synthesis, edge cases, high cognitive demand)")
            appendLine()
            appendLine("SUPPORTED QUESTION TYPES FOR THIS EXAM: $qTypes")
            if (rules.isNotBlank()) {
                appendLine("OFFICIAL PATTERN & MARKING RULES: $rules")
            }
            appendLine()
            appendLine("CRITICAL GENERATION CONSTRAINTS:")
            appendLine("1. INTERNAL REASONING: Every question MUST begin with an extensive 'reasoning' block verifying facts, proving why the correct option is uniquely true, and proving why every distractor is false.")
            appendLine("2. ANTI-HALLUCINATION: Do NOT fabricate incorrect scientific/historical facts. Strictly follow the provided source material.")
            if (examConfig.language.contains("English", ignoreCase = true) && !examConfig.language.contains("Hindi", ignoreCase = true)) {
                appendLine("3. LANGUAGE: Strictly English medium. Do not include Hindi translations for this exam (${examConfig.examName}).")
            } else {
                appendLine("3. LANGUAGE & BILINGUAL SUPPORT: ${examConfig.language}. Provide accurate, natural Hindi translations ('questionTextHi', 'optionAHi'..'optionDHi', 'explanationHi') alongside English.")
            }
            appendLine("4. DEFAULT OPTIONS: ${examConfig.defaultOptionCount} options for MCQs (or 0 for Numerical/NAT/TITA questions).")
            appendLine("5. NEEDS IMAGE: Set 'needsImage': true whenever the question requires a diagram, circuit, graph, or illustration.")
        }.trim()
    }

    /**
     * Finds the applicable sections for a given subject within the exam configuration.
     */
    fun getApplicableSections(examConfig: ExamConfig, subject: String): List<SectionConfig> {
        val allSubjects = examConfig.stages.flatMap { it.papers.flatMap { p -> p.subjects } }
        val matchingSubject = allSubjects.find { 
            it.subjectName.equals(subject, ignoreCase = true) || it.subjectId.equals(subject, ignoreCase = true) 
        } ?: allSubjects.firstOrNull()
        return matchingSubject?.sections ?: emptyList()
    }

    /**
     * Builds the complete prompt dynamically injecting the selected exam's system instruction,
     * difficulty ratios, pattern rules, and the compact Markdown payload.
     */
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
        val systemInstruction = buildSystemInstruction(examConfig)
        val diff = examConfig.difficultyRatio
        val qTypes = examConfig.supportedQuestionTypes.joinToString(", ") { "${it.name} (${it.displayName})" }
        val patternList = examConfig.patternRules.joinToString("\n  - ")
        val sections = getApplicableSections(examConfig, subject)
        val sectionDetails = if (sections.isNotEmpty()) {
            sections.joinToString("\n") { s ->
                "  * [${s.sectionId}] \"${s.sectionName}\": ${s.totalQuestions} Questions (${s.compulsoryQuestions} Compulsory) | Type: ${s.questionType.name} (${s.questionType.displayName}) | Scoring: +${s.positiveMarks} correct, -${s.negativeMarks} wrong"
            }
        } else {
            "  * Default Section: +4.0 correct, -1.0 wrong | Type: ${examConfig.supportedQuestionTypes.firstOrNull()?.name ?: "MCQ_SINGLE"}"
        }

        val taskObjective = if (isExtractionMode) {
            """
            TASK OBJECTIVE (STRICT TRANSCRIPTION & EXTRACTION MODE):
            Extract and transcribe the existing questions, options, and answer keys VERBATIM from the COMPACT SOURCE MARKDOWN PAYLOAD below.
            
            CRITICAL TRANSCRIPTION DIRECTIVES:
            1. STRICT VERBATIM TRANSCRIPTION: Do NOT generate, synthesize, or invent new questions. Transcribe ONLY questions, options, and keys that actually exist in the provided source text.
            2. PRESERVE QUESTIONS & OPTIONS: Accurately extract each question's stem and all available options (A, B, C, D / 1, 2, 3, 4) exactly as written in the source text.
            3. PRESERVE OFFICIAL ANSWER KEYS: If an answer key, marked option, or solution is provided in the source text, extract and assign that exact correct answer. If not explicitly stated, verify and solve strictly based on the question facts without inventing alternative keys.
            4. ORIGINAL QUESTION ORDER: Retain the sequential order and numbering from the original paper.
            5. If this chunk contains fewer questions than $currentChunkSize, extract ONLY the existing questions. Do not fabricate filler questions to reach a quota.
            """.trimIndent()
        } else {
            """
            TASK OBJECTIVE:
            Generate exactly $currentChunkSize realistic, exam-aligned questions for:
            - Exam: ${examConfig.examName} (${examConfig.shortName}) - ${examConfig.category}
            - Subject: $subject
            - Test Title: $testTitle
            - Range: Questions $startQ to $endQ (out of $totalQuestions total)
            - Target Difficulty Split: Easy: ${diff.easyPercent}%, Medium: ${diff.mediumPercent}%, Hard: ${diff.hardPercent}%
            - Permitted Question Types: $qTypes
            ${if (patternList.isNotBlank()) "- Official Pattern Rules:\n  - $patternList" else ""}
            - Configured Section Structure & Scoring Rules:
            $sectionDetails
            """.trimIndent()
        }

        return """
            $systemInstruction
            
            --------------------------------------------------------------------------------
            $taskObjective
            
            --------------------------------------------------------------------------------
            STRICT NO-FORMAT-MIXING CONSTRAINTS:
            1. CONSISTENT SECTION STRUCTURE: Each question MUST be assigned its appropriate sectionId, sectionName, positiveMarks, and negativeMarks exactly matching the exam configuration.
            2. NO TYPE POLLUTION:
               - If the section specifies NUMERICAL_VALUE / NVQ / NAT: Provide a single numerical problem without multiple-choice options (options empty or omitted), with exact numerical answer in 'correctAnswer'. Do NOT include A, B, C, D choices.
               - If the section specifies MCQ_SINGLE: Provide exactly ${examConfig.defaultOptionCount} distinct options (${if (examConfig.defaultOptionCount == 5) "'optionAEn'..'optionEEn'" else "'optionAEn'..'optionDEn'"}) with single letter 'correctAnswer' (${if (examConfig.defaultOptionCount == 5) "A, B, C, D, or E" else "A, B, C, or D"}).
               - If the section specifies STATEMENT_I_II: Structure question with Statement I and Statement II with standard analytical options.
               - If the section specifies ASSERTION_REASON: Provide Assertion (A) and Reason (R) with standard 4-option reasoning format.
               - If the section specifies MATCH_LIST_I_II: Provide List I and List II and match combinations.
            3. CONSISTENT SCORING: Use each section's exact positiveMarks and negativeMarks. Never invent arbitrary scoring schemes.
            4. LANGUAGE: ${if (examConfig.language.contains("English") && !examConfig.language.contains("Hindi")) "English medium exclusively. Set Hindi translation fields to null or empty." else "Bilingual medium (${examConfig.language}). Provide natural, fluent Hindi translations."}
            
            --------------------------------------------------------------------------------
            COMPACT SOURCE MARKDOWN PAYLOAD (Cleaned & Token-Optimized):
            ${if (compactMarkdownPayload.isNotBlank()) compactMarkdownPayload.take(50000) else "Source: Standard Official Syllabus for $subject in ${examConfig.examName}."}
            
            --------------------------------------------------------------------------------
            REQUIRED JSON OUTPUT SCHEMA:
            Return ONLY a valid JSON array of objects. Do not wrap in markdown quotes if possible, return strictly raw JSON:
            [
              {
                "sectionId": "sec_id",
                "sectionName": "Section Name",
                "positiveMarks": 4.0,
                "negativeMarks": 1.0,
                "questionType": "MCQ_SINGLE",
                "reasoning": "Step-by-step verification proving the correct answer according to official curriculum and showing why distractors are wrong.",
                "questionTextEn": "English question text",
                ${if (examConfig.language.contains("Hindi")) "\"questionTextHi\": \"Hindi translation of question\"," else ""}
                "optionAEn": "Option A (English)",
                "optionBEn": "Option B (English)",
                "optionCEn": "Option C (English)",
                "optionDEn": "Option D (English)",
                ${if (examConfig.defaultOptionCount == 5) "\"optionEEn\": \"Option E (English)\"," else ""}
                ${if (examConfig.language.contains("Hindi")) """
                "optionAHi": "विकल्प A (हिंदी)",
                "optionBHi": "विकल्प B (हिंदी)",
                "optionCHi": "विकल्प C (हिंदी)",
                "optionDHi": "विकल्प D (हिंदी)",
                ${if (examConfig.defaultOptionCount == 5) "\"optionEHi\": \"विकल्प E (हिंदी)\"," else ""}
                """.trimIndent() else ""}
                "correctAnswer": "A",
                "explanationEn": "Clear, pedagogically sound explanation",
                ${if (examConfig.language.contains("Hindi")) "\"explanationHi\": \"हिंदी में विस्तृत व्याख्या\"," else ""}
                "difficulty": "EASY",
                "chapter": "Chapter name",
                "topic": "Topic name",
                "needsImage": false
              }
            ]
        """.trimIndent()
    }

    /**
     * Builds the extraction prompt for extracting questions from already existing question papers.
     */
    fun buildExtractionPrompt(examConfig: ExamConfig, subjectId: String, expectedQuestions: Int): String {
        val sysInst = buildSystemInstruction(examConfig)
        return """
            $sysInst
            
            You are a strict, high-accuracy question extractor for ${examConfig.examName} ($subjectId).
            Extract up to $expectedQuestions questions from the provided source material.
            
            CRITICAL RULES - DO NOT FABRICATE:
            - DO NOT invent missing options.
            - DO NOT invent missing diagrams (set hasMissingDiagram = true).
            - DO NOT guess answers if they are not explicitly present or derivable.
            - DO NOT invent source facts.
            - DO NOT silently correct ambiguous questions (flag them).
            - DO NOT change numerical values or units.
            - Target Difficulty Baseline: Easy ${examConfig.difficultyRatio.easyPercent}%, Medium ${examConfig.difficultyRatio.mediumPercent}%, Hard ${examConfig.difficultyRatio.hardPercent}%.
            
            ANSWER ACCURACY PIPELINE:
            1. EXTRACT the source question text exactly as it appears.
            2. EXTRACT the options exactly.
            3. EXTRACT the answer key if provided.
            4. INDEPENDENT ANSWER CHECK: Verify the answer independently.
            5. SOURCE/ANSWER-KEY CHECK: Compare independent answer with extracted key.
            6. CONSISTENCY CHECK: If there is a conflict, mark answerConfidence = 0.
            
            Return a JSON array of objects with the following schema:
            [
              {
                "questionText": "exact text",
                "options": ["A", "B", "C", "D"],
                "correctAnswer": "A",
                "explanation": "Independent step-by-step derivation",
                "difficulty": "MEDIUM",
                "hasMissingOptions": false,
                "hasMissingDiagram": false,
                "answerConflictDetected": false,
                "sourceConfidence": 100
              }
            ]
        """.trimIndent()
    }
}
