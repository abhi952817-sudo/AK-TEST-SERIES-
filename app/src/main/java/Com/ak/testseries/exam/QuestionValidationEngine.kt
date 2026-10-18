package Com.ak.testseries.exam

/**
 * Multi-Stage High-Accuracy Question Validation & Quality Scoring Engine
 * Enforces strict verification before allowing questions to be published.
 */
object QuestionValidationEngine {

    val DIAGRAM_KEYWORDS = listOf("diagram", "figure", "given below", "image", "shown in", "चित्र", "आकृति")

    fun hasDiagramKeyword(text: String): Boolean {
        val textLower = text.lowercase()
        return DIAGRAM_KEYWORDS.any { textLower.contains(it) }
    }

    data class ValidationResult(
        val status: ValidationStatus,
        val qualityScore: Int,
        val qualityMetrics: QualityMetrics,
        val difficulty: DifficultyLevel,
        val difficultyScore: Int,
        val issues: List<String>
    )

    data class BatchDifficultyValidation(
        val isCompliant: Boolean,
        val easyCount: Int,
        val mediumCount: Int,
        val hardCount: Int,
        val targetEasyCount: Int,
        val targetMediumCount: Int,
        val targetHardCount: Int,
        val deficientLevels: List<DifficultyLevel>,
        val excessLevels: List<DifficultyLevel>,
        val issueDescription: String? = null
    )

    fun validateBatchDifficulty(
        questions: List<UniversalQuestionModel>,
        targetRatio: DifficultyRatio
    ): BatchDifficultyValidation {
        val total = questions.size
        if (total == 0) {
            return BatchDifficultyValidation(
                isCompliant = true,
                easyCount = 0, mediumCount = 0, hardCount = 0,
                targetEasyCount = 0, targetMediumCount = 0, targetHardCount = 0,
                deficientLevels = emptyList(), excessLevels = emptyList(),
                issueDescription = null
            )
        }

        val actualEasy = questions.count { it.difficulty == DifficultyLevel.EASY }
        val actualMed = questions.count { it.difficulty == DifficultyLevel.MEDIUM }
        val actualHard = questions.count { it.difficulty == DifficultyLevel.HARD }

        val targetEasy = Math.round(total * (targetRatio.easyPercent / 100.0)).toInt()
        val targetMed = Math.round(total * (targetRatio.mediumPercent / 100.0)).toInt()
        val targetHard = (total - targetEasy - targetMed).coerceAtLeast(0)

        // Tolerance based on batch size (at least +/- 1)
        val tolerance = (total * 0.15).toInt().coerceAtLeast(1)

        val deficient = mutableListOf<DifficultyLevel>()
        val excess = mutableListOf<DifficultyLevel>()

        if (targetRatio.easyPercent > 0 && actualEasy == 0) {
            deficient.add(DifficultyLevel.EASY)
        } else if (actualEasy < (targetEasy - tolerance)) {
            deficient.add(DifficultyLevel.EASY)
        } else if (actualEasy > (targetEasy + tolerance)) {
            excess.add(DifficultyLevel.EASY)
        }

        if (targetRatio.mediumPercent > 0 && actualMed == 0) {
            deficient.add(DifficultyLevel.MEDIUM)
        } else if (actualMed < (targetMed - tolerance)) {
            deficient.add(DifficultyLevel.MEDIUM)
        } else if (actualMed > (targetMed + tolerance)) {
            excess.add(DifficultyLevel.MEDIUM)
        }

        if (targetRatio.hardPercent > 0 && actualHard == 0) {
            deficient.add(DifficultyLevel.HARD)
        } else if (actualHard < (targetHard - tolerance)) {
            deficient.add(DifficultyLevel.HARD)
        } else if (actualHard > (targetHard + tolerance)) {
            excess.add(DifficultyLevel.HARD)
        }

        val isCompliant = deficient.isEmpty() && excess.isEmpty()
        val issueDescription = if (!isCompliant) {
            val defStr = if (deficient.isNotEmpty()) "Deficient in: ${deficient.joinToString { it.name }}" else ""
            val excStr = if (excess.isNotEmpty()) "Excess in: ${excess.joinToString { it.name }}" else ""
            val details = listOf(defStr, excStr).filter { it.isNotBlank() }.joinToString("; ")
            "Difficulty mismatch: Target (${targetRatio.summary()}) vs Actual (Easy: $actualEasy, Med: $actualMed, Hard: $actualHard). $details"
        } else null

        return BatchDifficultyValidation(
            isCompliant = isCompliant,
            easyCount = actualEasy,
            mediumCount = actualMed,
            hardCount = actualHard,
            targetEasyCount = targetEasy,
            targetMediumCount = targetMed,
            targetHardCount = targetHard,
            deficientLevels = deficient,
            excessLevels = excess,
            issueDescription = issueDescription
        )
    }

    fun validateQuestion(
        questionText: String,
        options: List<String>,
        correctAnswer: String,
        explanation: String,
        hasDiagramAttached: Boolean = false,
        duplicateResult: DuplicateDetectionEngine.DuplicateResult? = null,
        sourceAnswer: String? = null,
        questionType: QuestionType = QuestionType.DIRECT_MCQ,
        expectedOptionCount: Int = 4
    ): ValidationResult {
        val issues = mutableListOf<String>()
        var completeness = 100
        var optionIntegrity = 100
        var answerConfidence = 100
        var explanationConsistency = 100
        var diagramIntegrity = 100
        var ocrIntegrity = 100
        
        var hasHardFailure = false

        // 1. Duplicate Check
        if (duplicateResult?.exactDuplicate == true) {
            return ValidationResult(
                status = ValidationStatus.DUPLICATE,
                qualityScore = 0,
                qualityMetrics = QualityMetrics(duplicateRisk = 100),
                difficulty = DifficultyLevel.MEDIUM,
                difficultyScore = 50,
                issues = listOf("⚠ Exact duplicate question detected")
            )
        } else if (duplicateResult?.nearDuplicate == true) {
            issues.add("⚠ Near duplicate question detected")
            hasHardFailure = true
        }

        // 2. Question Completeness & OCR Check
        val trimmedQ = questionText.trim()
        if (trimmedQ.isEmpty()) {
            issues.add("⚠ Source incomplete: Question text is missing")
            completeness = 0
            hasHardFailure = true
        } else if (trimmedQ.length < 10) {
            issues.add("⚠ Source incomplete: Question text is too short")
            completeness = 50
            hasHardFailure = true
        }
        
        val ocrGarbagePatterns = listOf("page", "header", "footer", "www.", ".com")
        if (ocrGarbagePatterns.any { trimmedQ.lowercase().contains(it) }) {
            issues.add("⚠ OCR Artifacts: Accidental page/header/footer text detected")
            ocrIntegrity = 50
        }

        val isNumericalType = (questionType == QuestionType.NUMERICAL_VALUE ||
                               questionType == QuestionType.NUMERICAL ||
                               questionType == QuestionType.TITA)

        // 3. Option Validation (Integrity)
        if (isNumericalType) {
            // NAT/TITA questions are option-free by design
            optionIntegrity = 100
        } else {
            val minOptions = expectedOptionCount.coerceIn(4, 5)
            if (options.size < minOptions) {
                issues.add("⚠ Source incomplete: Fewer than $minOptions options provided (${options.size} found)")
                optionIntegrity = 0
                hasHardFailure = true
            } else {
                val emptyOpts = options.count { it.trim().isEmpty() }
                if (emptyOpts > 0) {
                    issues.add("⚠ Source incomplete: One or more options are empty")
                    optionIntegrity = 50
                    hasHardFailure = true
                }
                val uniqueOptions = options.map { it.trim().lowercase() }.toSet()
                if (uniqueOptions.size < options.size) {
                    issues.add("⚠ Option Integrity: Duplicate options found")
                    optionIntegrity -= 40
                    hasHardFailure = true
                }
            }
        }

        // 4. Answer Validation (Confidence)
        val trimmedAnswer = correctAnswer.trim()
        val normalizedAnswer = trimmedAnswer.uppercase()
        if (trimmedAnswer.isEmpty()) {
            issues.add("⚠ Answer conflict detected: Answer missing")
            answerConfidence = 0
            hasHardFailure = true
        } else if (isNumericalType) {
            // Numerical/NAT/TITA: Validate integer or decimal numerical value
            val isNumeric = trimmedAnswer.toDoubleOrNull() != null ||
                            trimmedAnswer.matches(Regex("""^[+-]?\d+(\.\d+)?$"""))
            if (!isNumeric) {
                issues.add("⚠ Answer conflict detected: Numerical question expects a valid numeric value, found '$trimmedAnswer'")
                answerConfidence = 0
                hasHardFailure = true
            }
        } else {
            val validAnswers = if (expectedOptionCount >= 5) {
                listOf("A", "B", "C", "D", "E", "1", "2", "3", "4", "5")
            } else {
                listOf("A", "B", "C", "D", "1", "2", "3", "4")
            }
            if (normalizedAnswer !in validAnswers && !options.any { it.trim().equals(trimmedAnswer, ignoreCase = true) }) {
                issues.add("⚠ Answer conflict detected: Answer '$trimmedAnswer' does not match permitted options ($validAnswers)")
                answerConfidence = 0
                hasHardFailure = true
            } else if (normalizedAnswer.contains("MULTIPLE") || normalizedAnswer.contains(",")) {
                issues.add("⚠ Multiple options may be correct")
                answerConfidence = 50
                hasHardFailure = true
            }
        }
        
        // 4.1 Golden Answer / Reference Check
        if (!sourceAnswer.isNullOrEmpty()) {
            val normalizedSource = sourceAnswer.trim().uppercase()
            if (normalizedAnswer != normalizedSource && normalizedSource != "UNVERIFIED") {
                issues.add("⚠ Answer conflict detected: AI answer ($normalizedAnswer) != Source answer ($normalizedSource)")
                answerConfidence = 0
                hasHardFailure = true
            }
        }

        // 5. Diagram Requirement
        val mentionsDiagram = hasDiagramKeyword(trimmedQ)
        if (mentionsDiagram && !hasDiagramAttached) {
            issues.add("⚠ Diagram required: Question mentions a figure but none attached")
            diagramIntegrity = 0
            hasHardFailure = true
        }

        // 6. Explanation Consistency
        if (explanation.trim().isEmpty()) {
            issues.add("⚠ Explanation missing")
            explanationConsistency = 0
        } else if (explanation.length < 15) {
            explanationConsistency = 50
        }

        val qualityMetrics = QualityMetrics(
            questionCompleteness = completeness,
            optionIntegrity = optionIntegrity,
            answerConfidence = answerConfidence,
            sourceConfidence = 100, // Conceptually we assume 100 unless told otherwise by AI
            explanationConsistency = explanationConsistency,
            diagramIntegrity = diagramIntegrity,
            ocrIntegrity = ocrIntegrity,
            ambiguityScore = if (hasHardFailure) 80 else 10,
            duplicateRisk = if (duplicateResult?.nearDuplicate == true) 80 else 0
        )

        // Calculate blended Quality Score
        val rawScore = (completeness + optionIntegrity + answerConfidence + explanationConsistency + diagramIntegrity + ocrIntegrity) / 6
        val finalScore = rawScore.coerceIn(0, 100)

        // Determine Status based on Hard Failures & Quality Score
        var status = when {
            finalScore >= 80 -> ValidationStatus.APPROVED
            finalScore >= 60 -> ValidationStatus.NEEDS_REVIEW
            else -> ValidationStatus.REJECTED
        }
        if (hasHardFailure && status == ValidationStatus.APPROVED) {
            status = ValidationStatus.NEEDS_REVIEW
        }

        // Calculate Difficulty
        val (difficulty, diffScore) = estimateDifficulty(trimmedQ, options, explanation)

        return ValidationResult(
            status = status,
            qualityScore = finalScore,
            qualityMetrics = qualityMetrics,
            difficulty = difficulty,
            difficultyScore = diffScore,
            issues = issues
        )
    }

    private fun estimateDifficulty(
        question: String,
        options: List<String>,
        explanation: String
    ): Pair<DifficultyLevel, Int> {
        val wordCount = question.split("\\s+".toRegex()).size
        val hasFormulaOrNumbers = question.contains(Regex("[0-9+\\-*/=^√%π]"))
        
        // Difficulty should consider: concept complexity, reasoning steps, calculation complexity
        val complexWords = listOf("calculate", "derive", "maximum", "minimum", "evaluate", "relation", "integrate", "differentiate", "गड़ना", "अनुपात")
        val complexCount = complexWords.count { question.lowercase().contains(it) }
        
        var score = 40 // base medium
        if (wordCount > 35) score += 10
        if (hasFormulaOrNumbers) score += 15
        score += (complexCount * 12)
        
        // distractor quality (option length)
        val avgOptionLength = if (options.isNotEmpty()) options.map { it.length }.average() else 0.0
        if (avgOptionLength > 20) score += 10
        
        val boundedScore = score.coerceIn(10, 95)
        val level = when {
            boundedScore < 40 -> DifficultyLevel.EASY
            boundedScore <= 70 -> DifficultyLevel.MEDIUM
            else -> DifficultyLevel.HARD
        }
        
        return Pair(level, boundedScore)
    }
}
