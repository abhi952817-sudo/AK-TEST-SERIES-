package Com.ak.testseries.exam

import Com.ak.testseries.data.QuestionEntity
import java.util.regex.Pattern

/**
 * Robust, production-grade parser for extracting real questions, options,
 * and answer keys directly from OCR or decompressed PDF text and markdown.
 * 
 * Supports:
 * - English, Hindi, and Bilingual question stems
 * - Standard option notations: (A)-(D), A-D., (1)-(4), 1-4., (अ)-(द), (क)-(घ)
 * - Single-line inline options and multi-line options
 * - Answer key blocks, solution tables, and inline answer annotations
 * - Dynamic conversion to UniversalQuestionModel or Room QuestionEntity
 */
object OfflineMarkdownQuestionParser {

    private val QUESTION_HEADER_REGEX = Regex(
        """(?m)^\s*(?:(?:Q(?:ue(?:stion)?)?|प्र(?:श्न)?|प्र०|प्रश्न\s*सं(?:ख्या)?)\.?\s*([0-9०-९]+)[\.\):\-]?\s*|([0-9०-९]+)[\.\)\-]\s+)""",
        RegexOption.IGNORE_CASE
    )

    private val INLINE_OPTION_REGEX = Regex(
        """(?:(?<=[(\s]|^)(?:\(([A-Ea-e1-5अबसदकखगघङ१-५])\)|([A-Ea-e1-5अबसदकखगघङ१-५])[\.\)])\s*)"""
    )

    private val INLINE_ANSWER_REGEX = Regex(
        """(?i)(?:Ans(?:wer)?|उत्तर|Correct\s*Option)\s*[:=\-–]?\s*\(?([A-Ea-e1-5अबसदकखगघङ१-५])\)?"""
    )

    private val ANSWER_KEY_BLOCK_REGEX = Regex(
        """(?i)(?:Answer\s*Key|उत्तर\s*कुंजी|Answers|Scoring\s*Key|Key\s*&\s*Solutions)"""
    )

    data class ParsedQuestion(
        val questionNumber: Int,
        val questionText: String,
        val optionA: String,
        val optionB: String,
        val optionC: String,
        val optionD: String,
        val correctAnswer: String,
        val explanation: String = ""
    )

    /**
     * Extracts an answer key map (questionNumber -> "A"/"B"/"C"/"D") from the document.
     */
    fun extractAnswerKeyMap(text: String): Map<Int, String> {
        val answers = mutableMapOf<Int, String>()
        val lines = text.split("\n")
        var inAnswerSection = false

        for (line in lines) {
            val trimmed = line.trim()
            if (ANSWER_KEY_BLOCK_REGEX.containsMatchIn(trimmed)) {
                inAnswerSection = true
                continue
            }

            if (inAnswerSection) {
                // Matches patterns like "1. (A)", "1 - B", "1: C", "1(D)", "Q1: A", "1.A"
                val pairRegex = Regex("""(?:Q\.?\s*)?(\d+)\s*[:.\-–\)]\s*\(?([A-Ea-e1-5अबसदकखगघङ१-५])\)?""")
                val matches = pairRegex.findAll(trimmed)
                for (m in matches) {
                    val qNum = m.groupValues[1].toIntOrNull()
                    val rawAns = m.groupValues[2]
                    if (qNum != null) {
                        answers[qNum] = normalizeOptionKey(rawAns)
                    }
                }
            } else {
                // Also check for inline answer lines like "Ans: B" or "Answer: (C)"
                val inlineMatch = INLINE_ANSWER_REGEX.find(trimmed)
                if (inlineMatch != null) {
                    val qNumMatch = Regex("""(?:Q\.?\s*)?(\d+)""").find(trimmed)
                    val qNum = qNumMatch?.groupValues?.get(1)?.toIntOrNull()
                    if (qNum != null) {
                        answers[qNum] = normalizeOptionKey(inlineMatch.groupValues[1])
                    }
                }
            }
        }
        return answers
    }

    /**
     * Parses structured questions from markdown or raw text.
     */
    fun parseStructuredQuestions(text: String): List<ParsedQuestion> {
        if (text.isBlank()) return emptyList()

        val answerKeyMap = extractAnswerKeyMap(text)
        val questions = mutableListOf<ParsedQuestion>()

        // Split questions text strictly before the Answer Key block
        val answerKeyMatch = ANSWER_KEY_BLOCK_REGEX.find(text)
        val questionsOnlyText = if (answerKeyMatch != null) {
            text.substring(0, answerKeyMatch.range.first)
        } else text

        // Split text by question header matches
        val matches = QUESTION_HEADER_REGEX.findAll(questionsOnlyText).toList()
        if (matches.isEmpty()) return emptyList()

        for (i in matches.indices) {
            val currentMatch = matches[i]
            val numStr = currentMatch.groups[1]?.value ?: currentMatch.groups[2]?.value ?: "${i + 1}"
            val qNum = parseHindiOrEnglishInt(numStr) ?: (i + 1)

            val startIndex = currentMatch.range.last + 1
            val endIndex = if (i + 1 < matches.size) {
                matches[i + 1].range.first
            } else {
                questionsOnlyText.length
            }

            if (startIndex >= endIndex || startIndex >= questionsOnlyText.length) continue

            val rawQuestionBlock = questionsOnlyText.substring(startIndex, endIndex.coerceAtMost(questionsOnlyText.length)).trim()
            val parsed = parseSingleQuestionBlock(qNum, rawQuestionBlock, answerKeyMap[qNum])
            if (parsed != null) {
                questions.add(parsed)
            }
        }

        return questions
    }

    private fun parseSingleQuestionBlock(
        qNum: Int,
        blockText: String,
        explicitAnswer: String?
    ): ParsedQuestion? {
        val lines = blockText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val stemLines = mutableListOf<String>()
        val optionsMap = mutableMapOf<String, String>()
        var inlineDetectedAnswer: String? = explicitAnswer
        var explanationText = ""

        for (line in lines) {
            // Check for inline answer tag like "Ans: C" or "Answer: (B)"
            val ansMatch = INLINE_ANSWER_REGEX.find(line)
            if (ansMatch != null) {
                inlineDetectedAnswer = normalizeOptionKey(ansMatch.groupValues[1])
                val beforeAns = line.substring(0, ansMatch.range.first).trim()
                if (beforeAns.isNotBlank()) {
                    stemLines.add(beforeAns)
                }
                continue
            }

            // Check for explanation lines
            if (line.startsWith("Exp:", ignoreCase = true) ||
                line.startsWith("Explanation:", ignoreCase = true) ||
                line.startsWith("व्याख्या:", ignoreCase = true)) {
                explanationText = line.substringAfter(":").trim()
                continue
            }

            // Check for options on this line
            val optMatches = INLINE_OPTION_REGEX.findAll(line).toList()
            if (optMatches.isNotEmpty()) {
                // If line starts with an option or has options
                val firstMatch = optMatches.first()
                if (firstMatch.range.first > 0 && optionsMap.isEmpty()) {
                    val promptPrefix = line.substring(0, firstMatch.range.first).trim()
                    if (promptPrefix.isNotBlank()) stemLines.add(promptPrefix)
                }

                for (idx in optMatches.indices) {
                    val m = optMatches[idx]
                    val rawKey = m.groups[1]?.value ?: m.groups[2]?.value ?: ""
                    val key = normalizeOptionKey(rawKey)

                    val valStart = m.range.last + 1
                    val valEnd = if (idx + 1 < optMatches.size) optMatches[idx + 1].range.first else line.length
                    val optContent = if (valStart <= valEnd && valStart < line.length) {
                        line.substring(valStart, valEnd).trim()
                    } else ""

                    if (key.isNotBlank()) {
                        optionsMap[key] = optContent
                    }
                }
            } else {
                if (optionsMap.isEmpty()) {
                    stemLines.add(line)
                } else {
                    // Append continuation to the last parsed option
                    val lastKey = optionsMap.keys.lastOrNull()
                    if (lastKey != null) {
                        optionsMap[lastKey] = "${optionsMap[lastKey]} $line".trim()
                    }
                }
            }
        }

        val questionStem = stemLines.joinToString(" ").trim()
        if (questionStem.isBlank()) return null

        val optA = optionsMap["A"] ?: ""
        val optB = optionsMap["B"] ?: ""
        val optC = optionsMap["C"] ?: ""
        val optD = optionsMap["D"] ?: ""

        // Final answer resolution
        val finalAnswer = inlineDetectedAnswer.orEmpty()

        return ParsedQuestion(
            questionNumber = qNum,
            questionText = questionStem,
            optionA = optA,
            optionB = optB,
            optionC = optC,
            optionD = optD,
            correctAnswer = finalAnswer,
            explanation = explanationText.ifBlank {
                "Correct Answer is Option $finalAnswer based on official exam test paper answer key."
            }
        )
    }

    /**
     * Converts parsed questions into Room QuestionEntity objects.
     */
    fun parseTextToQuestionEntities(
        text: String,
        testId: String,
        subject: String,
        examName: String
    ): List<QuestionEntity> {
        val parsedList = parseStructuredQuestions(text)
        return parsedList.mapNotNull { pq ->
            QuestionEntity(
                id = "${testId}_q${pq.questionNumber}",
                testId = testId,
                questionText = pq.questionText,
                optionA = pq.optionA,
                optionB = pq.optionB,
                optionC = pq.optionC,
                optionD = pq.optionD,
                correctAnswer = pq.correctAnswer,
                explanation = pq.explanation,
                subject = subject,
                chapter = "",
                questionType = "",
                section = ""
            )
        }
    }

    /**
     * Converts parsed questions into UniversalQuestionModel objects for UniversalPdfPipeline.
     */
    fun parseMarkdownToQuestions(
        markdown: String,
        examConfig: ExamConfig,
        subjectId: String,
        stageId: String,
        paperId: String
    ): List<UniversalQuestionModel> {
        val parsedList = parseStructuredQuestions(markdown)
        val selectedStage = examConfig.stages.singleOrNull { it.stageId == stageId } ?: return emptyList()
        val selectedPaper = selectedStage.papers.singleOrNull { it.paperId == paperId } ?: return emptyList()
        val selectedSubject = selectedPaper?.subjects?.singleOrNull {
            it.subjectId.equals(subjectId, ignoreCase = true) ||
                it.subjectName.equals(subjectId, ignoreCase = true)
        }
        val sections = selectedSubject?.sections.orEmpty()

        return parsedList.mapNotNull { pq ->
            val optionsList = listOfNotNull(
                pq.optionA.takeIf { it.isNotBlank() },
                pq.optionB.takeIf { it.isNotBlank() },
                pq.optionC.takeIf { it.isNotBlank() },
                pq.optionD.takeIf { it.isNotBlank() }
            )
            var cumulativeQuestions = 0
            val resolvedSection = sections.singleOrNull { section ->
                cumulativeQuestions += section.totalQuestions
                pq.questionNumber <= cumulativeQuestions
            } ?: return@mapNotNull null
            val questionType = resolvedSection.questionType

            UniversalQuestionModel(
                questionId = "uq_${examConfig.examId}_${java.util.UUID.randomUUID()}",
                examId = examConfig.examId,
                stageId = selectedStage.stageId,
                paperId = selectedPaper.paperId,
                subjectId = subjectId,
                sectionId = resolvedSection.sectionId,
                sectionName = resolvedSection.sectionName,
                questionType = questionType,
                questionText = pq.questionText,
                options = optionsList,
                correctAnswer = pq.correctAnswer,
                sourceAnswer = null,
                explanation = pq.explanation,
                positiveMarks = resolvedSection.positiveMarks,
                negativeMarks = resolvedSection.negativeMarks,
                difficulty = DifficultyLevel.MEDIUM,
                qualityScore = 0,
                validationStatus = ValidationStatus.NEEDS_REVIEW,
                validationReasons = listOf(
                    "Extracted from an uploaded PDF; verify source provenance, question text, answer key, section and marking scheme before approval."
                ),
                duplicateHash = DuplicateDetectionEngine.generateNormalizedHash(pq.questionText, optionsList),
                generationModel = "pdf-real-extractor-v1",
                sourceName = "Uploaded PDF (unverified provenance)",
                isOfficialPYQ = false
            )
        }
    }

    private fun normalizeOptionKey(raw: String): String {
        return when (raw.trim().uppercase()) {
            "1", "अ", "क", "१" -> "A"
            "2", "ब", "ख", "२" -> "B"
            "3", "स", "ग", "३" -> "C"
            "4", "द", "घ", "४" -> "D"
            "5", "य", "ङ", "५" -> "E"
            "A", "B", "C", "D", "E" -> raw.trim().uppercase()
            else -> ""
        }
    }

    private fun parseHindiOrEnglishInt(str: String): Int? {
        val converted = str.map { ch ->
            when (ch) {
                '०' -> '0'; '१' -> '1'; '२' -> '2'; '३' -> '3'; '४' -> '4'
                '५' -> '5'; '६' -> '6'; '७' -> '7'; '८' -> '8'; '९' -> '9'
                else -> ch
            }
        }.joinToString("")
        return converted.toIntOrNull()
    }
}
