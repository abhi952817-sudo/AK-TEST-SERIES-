package Com.ak.testseries.exam

import android.content.Context
import android.net.Uri
import Com.ak.testseries.BuildConfig
import Com.ak.testseries.utils.CompactMarkdownFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Universal PDF Processing Pipeline
 * Handles PDF -> Text -> Question Detection -> Multi-Stage Validation -> Question Bank insertion.
 * Implements Batch Processing, Cost Control, and Retry/Resume mechanisms.
 * Dynamically injects exam-specific aiSystemInstruction, pattern rules, and difficulty ratios into the Gemini API.
 */
object UniversalPdfPipeline {

    data class BatchProgress(
        val totalBatches: Int,
        val currentBatch: Int,
        val processedQuestions: Int,
        val statusMessage: String
    )

    /**
     * Resolves the section configuration for a question based on its sequential index in the subject.
     */
    fun resolveSectionForIndex(examConfig: ExamConfig, subjectId: String, qIndexInSubject: Int): SectionConfig? {
        if (qIndexInSubject <= 0) return null
        val sections = UniversalAIContext.getApplicableSections(examConfig, subjectId)
        if (sections.isEmpty()) return null
        var runningCount = 0
        for (sec in sections) {
            runningCount += sec.totalQuestions
            if (qIndexInSubject <= runningCount) {
                return sec
            }
        }
        // Never map overflow questions to the last configured section.
        return null
    }

    suspend fun processPdfForExam(
        context: Context? = null,
        pdfUri: Uri,
        examConfig: ExamConfig,
        subjectId: String,
        stageId: String = "",
        expectedQuestions: Int = 20,
        batchSize: Int = 10,
        resumeFromBatch: Int = 1,
        existingQuestionsCache: List<UniversalQuestionModel> = emptyList(),
        apiKey: String? = null,
        isAiEnabled: Boolean = true,
        onProgress: (BatchProgress) -> Unit = {}
    ): List<UniversalQuestionModel> = withContext(Dispatchers.IO) {
        if (!isAiEnabled) {
            onProgress(
                BatchProgress(
                    totalBatches = 0,
                    currentBatch = 0,
                    processedQuestions = 0,
                    statusMessage = "AI Engine is disabled by Admin (Global Killswitch active)."
                )
            )
            return@withContext emptyList()
        }
        val extractedQuestions = mutableListOf<UniversalQuestionModel>()
        extractedQuestions.addAll(existingQuestionsCache)
        
        // 1. Extract text from PDF URI and format into compact Markdown
        val compactMarkdown = if (context != null) {
            try {
                // Try decompressed stream extraction via AdvancedPdfSmartExtractor
                var extractedText = ""
                try {
                    val tempFile = java.io.File.createTempFile("pipe_pdf_", ".pdf", context.cacheDir)
                    context.contentResolver.openInputStream(pdfUri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val smartExtractor = com.ak.pdfextractor.AdvancedPdfSmartExtractor(context)
                    extractedText = smartExtractor.extractAllStreamText(tempFile)
                    tempFile.delete()
                } catch (_: Exception) {}

                if (extractedText.isNotBlank()) {
                    CompactMarkdownFormatter.formatToCompactMarkdown(extractedText)
                } else {
                    context.contentResolver.openInputStream(pdfUri)?.use { stream ->
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(stream))
                        val sb = StringBuilder()
                        val buffer = CharArray(4096)
                        var charsRead: Int
                        var total = 0
                        while (reader.read(buffer).also { charsRead = it } != -1) {
                            for (i in 0 until charsRead) {
                                val c = buffer[i]
                                if (c in ' '..'~' || c == '\n' || c == '\r' || c == '\t' || c.code in 0x0900..0x097F) {
                                    sb.append(c)
                                    total++
                                }
                            }
                            if (total > 500000) break
                        }
                        CompactMarkdownFormatter.formatToCompactMarkdown(sb.toString())
                    } ?: ""
                }
            } catch (e: Exception) {
                ""
            }
        } else ""

        val totalBatches = (expectedQuestions + batchSize - 1) / batchSize
        val existingHashes = existingQuestionsCache.associate { it.duplicateHash to it.questionText }
        val effectiveApiKey = apiKey ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }

        // Process in controlled batches to save API tokens and maintain reliability
        for (batchIndex in (resumeFromBatch - 1) until totalBatches) {
            val currentBatchNumber = batchIndex + 1
            val questionsInThisBatch = if (currentBatchNumber == totalBatches) {
                expectedQuestions - (batchIndex * batchSize)
            } else {
                batchSize
            }

            onProgress(
                BatchProgress(
                    totalBatches = totalBatches,
                    currentBatch = currentBatchNumber,
                    processedQuestions = extractedQuestions.size,
                    statusMessage = "Processing Batch $currentBatchNumber of $totalBatches for ${examConfig.examName} ($subjectId)..."
                )
            )

            try {
                val startQ = (batchIndex * batchSize) + 1
                val endQ = (batchIndex * batchSize) + questionsInThisBatch

                // Dynamically build the prompt injecting aiSystemInstruction, pattern rules, and compact Markdown
                val prompt = UniversalAIContext.buildDynamicGenerationPrompt(
                    examConfig = examConfig,
                    subject = subjectId.ifBlank { examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.subjectName ?: "Core Domain" },
                    testTitle = "${examConfig.shortName} Diagnostic Paper",
                    compactMarkdownPayload = compactMarkdown,
                    currentChunkSize = questionsInThisBatch,
                    startQ = startQ,
                    endQ = endQ,
                    totalQuestions = expectedQuestions,
                    isExtractionMode = compactMarkdown.isNotBlank()
                )
                val systemInstruction = UniversalAIContext.buildSystemInstruction(examConfig)

                // Call Gemini API if key is available
                var batchModels: List<UniversalQuestionModel>? = null
                if (!effectiveApiKey.isNullOrBlank()) {
                    val apiResult = callGeminiApi(effectiveApiKey, prompt, systemInstruction)
                    if (apiResult != null) {
                        val source = if (compactMarkdown.isNotBlank()) "Extracted PDF Markdown" else "Gemini API Generation"
                        batchModels = parseGeminiResponse(
                            jsonString = apiResult.responseText,
                            examConfig = examConfig,
                            stageId = stageId,
                            subjectId = subjectId,
                            batchIndex = batchIndex,
                            batchSize = batchSize,
                            existingHashes = existingHashes,
                            actualModel = apiResult.modelVersion,
                            actualSource = source
                        )
                    }
                }

                // If API call succeeded, add parsed models; otherwise generate structured fallback
                if (!batchModels.isNullOrEmpty()) {
                    var finalBatch = batchModels
                    val initialValidation = QuestionValidationEngine.validateBatchDifficulty(finalBatch, examConfig.difficultyRatio)

                    if (!initialValidation.isCompliant && !effectiveApiKey.isNullOrBlank() && initialValidation.deficientLevels.isNotEmpty()) {
                        // Attempt targeted regeneration for deficient difficulty types
                        val deficientLevel = initialValidation.deficientLevels.first()
                        val neededCount = when (deficientLevel) {
                            DifficultyLevel.EASY -> (initialValidation.targetEasyCount - initialValidation.easyCount).coerceIn(1, 3)
                            DifficultyLevel.MEDIUM -> (initialValidation.targetMediumCount - initialValidation.mediumCount).coerceIn(1, 3)
                            DifficultyLevel.HARD -> (initialValidation.targetHardCount - initialValidation.hardCount).coerceIn(1, 3)
                        }

                        onProgress(
                            BatchProgress(
                                totalBatches = totalBatches,
                                currentBatch = currentBatchNumber,
                                processedQuestions = extractedQuestions.size,
                                statusMessage = "Batch $currentBatchNumber: Rebalancing difficulty (${deficientLevel.name} deficient)..."
                            )
                        )

                        val targetedPrompt = UniversalAIContext.buildDifficultyRegenerationPrompt(
                            examConfig = examConfig,
                            subjectId = subjectId,
                            neededCount = neededCount,
                            difficulty = deficientLevel,
                            sourceText = compactMarkdown
                        )

                        try {
                            val regenResult = callGeminiApi(effectiveApiKey, targetedPrompt, null)
                            if (regenResult != null) {
                                val regenQuestions = parseGeminiResponse(
                                    jsonString = regenResult.responseText,
                                    examConfig = examConfig,
                                    stageId = stageId,
                                    subjectId = subjectId,
                                    batchIndex = batchIndex,
                            batchSize = batchSize,
                                    existingHashes = existingHashes,
                                    actualModel = regenResult.modelVersion,
                                    actualSource = "Gemini Difficulty Balancer (${regenResult.modelVersion})"
                                ).filter { it.difficulty == deficientLevel }

                                if (regenQuestions.isNotEmpty()) {
                                    val excessLevel = initialValidation.excessLevels.firstOrNull()
                                    val mutableBatch = finalBatch.toMutableList()
                                    var replaced = 0
                                    for (idx in mutableBatch.indices) {
                                        if (replaced < regenQuestions.size && (excessLevel == null || mutableBatch[idx].difficulty == excessLevel)) {
                                            mutableBatch[idx] = regenQuestions[replaced]
                                            replaced++
                                        }
                                    }
                                    finalBatch = mutableBatch
                                }
                            }
                        } catch (e: Exception) {
                            // Targeted regeneration error fallback to status tagging
                        }
                    }

                    // Post-check: Never silently accept incorrect ratios!
                    val postValidation = QuestionValidationEngine.validateBatchDifficulty(finalBatch, examConfig.difficultyRatio)
                    if (!postValidation.isCompliant) {
                        finalBatch = finalBatch.map { q ->
                            val updatedReasons = (q.validationReasons + listOf("⚠ Ratio Mismatch: ${postValidation.issueDescription}")).distinct()
                            q.copy(
                                validationStatus = ValidationStatus.NEEDS_REVIEW,
                                validationReasons = updatedReasons,
                                qualityScore = (q.qualityScore - 15).coerceAtLeast(40)
                            )
                        }
                    }

                    extractedQuestions.addAll(finalBatch)
                } else {
                    // Real extraction from parsed markdown or authentic exam catalog (Zero dummy data)
                    val resolvedStage = examConfig.stages.firstOrNull { it.stageId == stageId }
                        ?: examConfig.stages.firstOrNull() ?: return@withContext emptyList()
                    val subjectConfig = resolvedStage.papers.flatMap { it.subjects }
                        .firstOrNull { it.subjectId == subjectId } ?: return@withContext emptyList()
                    val resolvedPaper = resolvedStage.papers.firstOrNull { paper ->
                        paper.subjects.any { it.subjectId == subjectId }
                    } ?: return@withContext emptyList()
                    val realExtractedFromMarkdown = if (compactMarkdown.isNotBlank()) {
                        OfflineMarkdownQuestionParser.parseMarkdownToQuestions(compactMarkdown, examConfig, subjectId, resolvedStage.stageId)
                    } else emptyList()
                    val bundledExamTests = BundledExamCatalog.getBundledTestsForExam(examConfig.examName)
                    val bundledQuestions = bundledExamTests.flatMap { it.second }
                        .filter { it.subject.equals(subjectConfig.subjectName, ignoreCase = true) }

                    for (i in 1..questionsInThisBatch) {
                        val qIndex = (batchIndex * batchSize) + i
                        val markdownCandidate = realExtractedFromMarkdown.getOrNull(qIndex - 1)
                            ?.takeIf { it.subjectId == subjectId }
                        val bundledCandidate = if (markdownCandidate == null) {
                            bundledQuestions.getOrNull((qIndex - 1) % bundledQuestions.size.coerceAtLeast(1))
                        } else null
                        if (markdownCandidate == null && bundledCandidate == null) continue

                        val resolvedSection = resolveSectionForIndex(examConfig, subjectId, qIndex) ?: continue
                        val qType = resolvedSection.questionType
                        val secId = resolvedSection.sectionId
                        val secName = resolvedSection.sectionName
                        val posMark = resolvedSection.positiveMarks
                        val negMark = resolvedSection.negativeMarks

                        // Never mix fields from a PDF extraction with an unrelated bundled question.
                        val questionText = markdownCandidate?.questionText ?: bundledCandidate?.questionText.orEmpty()
                        if (questionText.isBlank()) continue
                        val expectedOptionCount = resolvedSection.optionsCount.coerceIn(0, 5)
                        val options = if (markdownCandidate != null) {
                            markdownCandidate.options
                        } else if (expectedOptionCount == 0) {
                            emptyList()
                        } else {
                            listOf(
                                bundledCandidate?.optionA.orEmpty(),
                                bundledCandidate?.optionB.orEmpty(),
                                bundledCandidate?.optionC.orEmpty(),
                                bundledCandidate?.optionD.orEmpty(),
                                bundledCandidate?.optionE.orEmpty()
                            ).take(expectedOptionCount)
                        }
                        val isOptionFree = qType in setOf(
                            QuestionType.NUMERICAL, QuestionType.NUMERICAL_VALUE, QuestionType.TITA, QuestionType.DESCRIPTIVE
                        )
                        if (!isOptionFree && options.size != expectedOptionCount.coerceAtLeast(2)) continue
                        val correctAnswer = if (markdownCandidate != null) markdownCandidate.correctAnswer else bundledCandidate?.correctAnswer.orEmpty()
                        val explanation = if (markdownCandidate != null) markdownCandidate.explanation else bundledCandidate?.explanation.orEmpty()

                        val hash = DuplicateDetectionEngine.generateNormalizedHash(questionText, options)
                        val dupResult = DuplicateDetectionEngine.checkDuplicate(questionText, options, existingHashes)
                        val sourceAnswer = markdownCandidate?.sourceAnswer?.takeIf { it.isNotBlank() }
                        val validation = QuestionValidationEngine.validateQuestion(
                            questionText = questionText,
                            options = options,
                            correctAnswer = correctAnswer,
                            sourceAnswer = sourceAnswer,
                            explanation = explanation,
                            hasDiagramAttached = markdownCandidate?.diagramUrl != null,
                            duplicateResult = dupResult,
                            questionType = qType,
                            expectedOptionCount = expectedOptionCount
                        )
                        if (validation.status == ValidationStatus.DUPLICATE) continue

                        val reviewReasons = (
                            validation.issues + "Offline extraction/catalog fallback requires manual verification before approval."
                        ).distinct()
                        val answerExtracted = !sourceAnswer.isNullOrBlank()
                        extractedQuestions.add(
                            UniversalQuestionModel(
                                questionId = "uq_${examConfig.examId}_${System.currentTimeMillis()}_$qIndex",
                                examId = examConfig.examId,
                                stageId = resolvedStage.stageId,
                                paperId = resolvedPaper.paperId,
                                sectionId = secId,
                                sectionName = secName,
                                subjectId = subjectId,
                                questionText = questionText,
                                questionTextHi = markdownCandidate?.questionTextHi,
                                options = options,
                                optionsHi = markdownCandidate?.optionsHi,
                                correctAnswer = correctAnswer,
                                sourceAnswer = sourceAnswer,
                                explanation = explanation,
                                explanationHi = markdownCandidate?.explanationHi,
                                difficulty = markdownCandidate?.difficulty ?: DifficultyLevel.MEDIUM,
                                questionType = qType,
                                positiveMarks = posMark,
                                negativeMarks = negMark,
                                difficultyScore = validation.difficultyScore,
                                qualityScore = validation.qualityScore.coerceAtMost(69),
                                qualityMetrics = validation.qualityMetrics,
                                validationStatus = ValidationStatus.NEEDS_REVIEW,
                                validationReasons = reviewReasons,
                                duplicateHash = hash,
                                diagramUrl = markdownCandidate?.diagramUrl,
                                diagramMetadata = markdownCandidate?.diagramMetadata,
                                sourceName = if (markdownCandidate != null) "Extracted PDF Markdown" else "${examConfig.examName} Bundled Practice Catalog (Review Required)",
                                sourcePage = markdownCandidate?.sourcePage?.takeIf { it > 0 } ?: -1,
                                isOfficialPYQ = false,
                                questionVersion = 1,
                                validationVersion = 2,
                                generationModel = if (markdownCandidate != null) "pdf-real-extractor-v2" else "bundled-exam-catalog-v1",
                                generationTimestamp = System.currentTimeMillis(),
                                provenanceType = if (markdownCandidate != null) "SOURCE_EXTRACTED" else "GENERATED_PRACTICE",
                                answerStatus = if (answerExtracted) "KEY_EXTRACTED" else "UNVERIFIED",
                                answerConfidence = if (answerExtracted) 60 else 0,
                                verificationSummary = if (answerExtracted)
                                    "Source answer key extracted; correctness still requires manual verification."
                                else "No independently verified answer is available; manual review required.",
                                sourcePageEnd = markdownCandidate?.sourcePageEnd,
                                needsReview = true,
                                reviewReasons = reviewReasons
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                onProgress(
                    BatchProgress(
                        totalBatches = totalBatches,
                        currentBatch = currentBatchNumber,
                        processedQuestions = extractedQuestions.size,
                        statusMessage = "⚠ Batch $currentBatchNumber failed: ${e.message}. Safe to resume."
                    )
                )
                break
            }
            delay(500)
        }

        if (extractedQuestions.size != expectedQuestions) {
            val reason = "Expected $expectedQuestions verified questions, but only ${extractedQuestions.size} passed validation. Batch completeness requires manual review."
            for (index in extractedQuestions.indices) {
                val question = extractedQuestions[index]
                extractedQuestions[index] = question.copy(
                    validationStatus = ValidationStatus.NEEDS_REVIEW,
                    validationReasons = (question.validationReasons + reason).distinct()
                )
            }
            onProgress(
                BatchProgress(
                    totalBatches = totalBatches,
                    currentBatch = totalBatches,
                    processedQuestions = extractedQuestions.size,
                    statusMessage = "Incomplete: ${extractedQuestions.size}/$expectedQuestions verified questions. All returned items require review."
                )
            )
        } else {
            onProgress(
                BatchProgress(
                    totalBatches = totalBatches,
                    currentBatch = totalBatches,
                    processedQuestions = extractedQuestions.size,
                    statusMessage = "Extraction Complete! ${extractedQuestions.size} questions ready for Review."
                )
            )
        }

        return@withContext extractedQuestions
    }

    private fun parseGeminiResponse(
        jsonString: String,
        examConfig: ExamConfig,
        stageId: String,
        subjectId: String,
        batchIndex: Int,
        batchSize: Int,
        existingHashes: Map<String, String>,
        actualModel: String = "UNAVAILABLE",
        actualSource: String = "UNAVAILABLE"
    ): List<UniversalQuestionModel> {
        val resultList = mutableListOf<UniversalQuestionModel>()
        try {
            val responseReport = GeminiResponseValidator.parseResponse(
                raw = jsonString,
                examConfig = examConfig,
                expectedSubjectId = subjectId
            )
            if (!responseReport.isUsable) return emptyList()
            val jsonArray = responseReport.questions

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val qText = item.optString("questionTextEn").ifBlank { item.optString("questionText") }
                if (qText.isBlank()) continue

                val optA = item.optString("optionAEn").ifBlank { item.optString("optionA") }
                val optB = item.optString("optionBEn").ifBlank { item.optString("optionB") }
                val optC = item.optString("optionCEn").ifBlank { item.optString("optionC") }
                val optD = item.optString("optionDEn").ifBlank { item.optString("optionD") }
                val optE = item.optString("optionEEn").ifBlank { item.optString("optionE") }
                val options = listOf(optA, optB, optC, optD, optE).filter { it.isNotBlank() }

                val qIndexInBatch = (batchIndex * batchSize) + i + 1
                val resolvedSection = resolveSectionForIndex(examConfig, subjectId, qIndexInBatch) ?: continue
                val parsedSecId = item.optString("sectionId")
                val parsedSecName = item.optString("sectionName")
                if (parsedSecId != resolvedSection.sectionId || parsedSecName != resolvedSection.sectionName) continue
                val parsedPosMarks = item.optDouble("positiveMarks", Double.NaN)
                val parsedNegMarks = item.optDouble("negativeMarks", Double.NaN)
                if (!parsedPosMarks.isFinite() || !parsedNegMarks.isFinite() ||
                    kotlin.math.abs(parsedPosMarks - resolvedSection.positiveMarks) > 0.000001 ||
                    kotlin.math.abs(parsedNegMarks - resolvedSection.negativeMarks) > 0.000001
                ) continue

                val diffStr = item.optString("difficulty", "MEDIUM").uppercase()
                val diffLevel = when {
                    diffStr.contains("EASY") -> DifficultyLevel.EASY
                    diffStr.contains("HARD") -> DifficultyLevel.HARD
                    else -> DifficultyLevel.MEDIUM
                }

                val qTypeStr = item.optString("questionType").uppercase()
                val parsedQType = when {
                    qTypeStr == "MCQ_MULTIPLE" -> QuestionType.MCQ_MULTIPLE
                    qTypeStr == "MCQ_SINGLE" -> QuestionType.MCQ_SINGLE
                    qTypeStr.contains("STATEMENT_I") || qTypeStr.contains("STATEMENT_1") -> QuestionType.STATEMENT_I_II
                    qTypeStr.contains("ASSERTION") -> QuestionType.ASSERTION_REASON
                    qTypeStr.contains("MATCH") -> QuestionType.MATCH_LIST_I_II
                    qTypeStr.contains("MULTI") -> QuestionType.MULTI_STATEMENT
                    qTypeStr.contains("NUMERICAL") || qTypeStr.contains("NAT") || qTypeStr.contains("NVQ") -> QuestionType.NUMERICAL_VALUE
                    qTypeStr.contains("DIAGRAM") || qTypeStr.contains("IMAGE") -> QuestionType.DIAGRAM_LABELING
                    qTypeStr.contains("GRAPH") -> QuestionType.GRAPH
                    qTypeStr.contains("DIRECT") || qTypeStr.contains("MCQ") -> QuestionType.DIRECT_MCQ
                    else -> runCatching { QuestionType.valueOf(qTypeStr) }.getOrNull()
                        ?: resolvedSection?.questionType ?: continue
                }

                val isOptionFree = parsedQType in setOf(QuestionType.NUMERICAL_VALUE, QuestionType.NUMERICAL, QuestionType.TITA, QuestionType.DESCRIPTIVE)
                val finalOptions = if (isOptionFree) emptyList() else options
                val expectedOptions = resolvedSection?.optionsCount ?: examConfig.defaultOptionCount
                if (!isOptionFree && finalOptions.size != expectedOptions.coerceIn(2, 5)) continue

                val rawAnswer = item.optString("correctAnswer", "").trim()
                val correctAnswer = if (isOptionFree) rawAnswer else rawAnswer.uppercase()
                val sourceAnswer = item.optString("sourceAnswer", "").takeIf { it.isNotBlank() && it != "null" }
                val explanation = item.optString("explanationEn").ifBlank { item.optString("explanation") }

                val hash = DuplicateDetectionEngine.generateNormalizedHash(qText, finalOptions)
                val dupResult = DuplicateDetectionEngine.checkDuplicate(qText, finalOptions, existingHashes)
                val validation = QuestionValidationEngine.validateQuestion(
                    questionText = qText,
                    options = finalOptions,
                    correctAnswer = correctAnswer,
                    sourceAnswer = sourceAnswer,
                    explanation = explanation,
                    hasDiagramAttached = false,
                    duplicateResult = dupResult,
                    questionType = parsedQType,
                    expectedOptionCount = expectedOptions
                )

                val model = UniversalQuestionModel(
                    questionId = "uq_${examConfig.examId}_${System.currentTimeMillis()}_${batchIndex}_$i",
                    examId = examConfig.examId,
                    stageId = stageId,
                    paperId = examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.paperId ?: "",
                    sectionId = parsedSecId,
                    sectionName = parsedSecName,
                    subjectId = subjectId,
                    questionText = qText,
                    questionTextHi = item.optString("questionTextHi").takeIf { it.isNotBlank() },
                    options = finalOptions,
                    optionsHi = if (finalOptions.isEmpty()) null else listOfNotNull(
                        item.optString("optionAHi").takeIf { it.isNotBlank() },
                        item.optString("optionBHi").takeIf { it.isNotBlank() },
                        item.optString("optionCHi").takeIf { it.isNotBlank() },
                        item.optString("optionDHi").takeIf { it.isNotBlank() },
                        item.optString("optionEHi").takeIf { it.isNotBlank() }
                    ).takeIf { it.size == finalOptions.size },
                    correctAnswer = correctAnswer,
                    sourceAnswer = sourceAnswer,
                    explanation = explanation,
                    explanationHi = item.optString("explanationHi").takeIf { it.isNotBlank() },
                    difficulty = diffLevel,
                    questionType = parsedQType,
                    positiveMarks = parsedPosMarks,
                    negativeMarks = parsedNegMarks,
                    difficultyScore = validation.difficultyScore,
                    qualityScore = validation.qualityScore,
                    qualityMetrics = validation.qualityMetrics,
                    validationStatus = validation.status,
                    validationReasons = validation.issues,
                    duplicateHash = hash,
                    sourceName = actualSource,
                    // Never invent a source page from the batch number.
                    sourcePage = item.optInt("sourcePageStart", -1).takeIf { it > 0 } ?: -1,
                    questionVersion = 1,
                    validationVersion = 1,
                    generationModel = actualModel,
                    generationTimestamp = System.currentTimeMillis(),
                    provenanceType = item.optString("provenanceType", ""),
                    answerStatus = item.optString("answerStatus", ""),
                    answerConfidence = item.optDouble("answerConfidence", 0.0).toInt(),
                    verificationSummary = item.optString("verificationSummary", ""),
                    sourcePageEnd = item.optInt("sourcePageEnd", -1).takeIf { it > 0 },
                    needsReview = item.optBoolean("needsReview", true) || validation.status != ValidationStatus.APPROVED,
                    reviewReasons = (
                        (item.optJSONArray("reviewReasons")?.let { reasons ->
                            (0 until reasons.length()).mapNotNull { index -> reasons.optString(index).takeIf { it.isNotBlank() } }
                        } ?: emptyList()) + validation.issues
                    ).distinct()
                )
                resultList.add(model)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultList
    }

    data class GeminiApiExecutionResult(
        val responseText: String,
        val modelName: String,
        val modelVersion: String
    )

    private suspend fun callGeminiApi(
        apiKey: String,
        prompt: String,
        systemInstruction: String?
    ): GeminiApiExecutionResult? = withContext(Dispatchers.IO) {
        val candidateModels = listOf("gemini-3.8-flash", "gemini-flash-latest", "gemini-2.5-flash")
        for (modelName in candidateModels) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", apiKey)
                conn.connectTimeout = 45000
                conn.readTimeout = 45000
                conn.doOutput = true

                val jsonReq = JSONObject().apply {
                    if (!systemInstruction.isNullOrBlank()) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", systemInstruction)
                                })
                            })
                        })
                    }
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                    })
                }

                conn.outputStream.use { os ->
                    os.write(jsonReq.toString().toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val respObj = JSONObject(responseStr)
                    val returnedVersion = respObj.optString("modelVersion").takeIf { it.isNotBlank() } ?: "UNAVAILABLE"
                    val candidates = respObj.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text")
                            if (text.isNotBlank()) {
                                return@withContext GeminiApiExecutionResult(
                                    responseText = text,
                                    modelName = modelName,
                                    modelVersion = returnedVersion
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // try next model
            }
        }
        return@withContext null
    }
}
