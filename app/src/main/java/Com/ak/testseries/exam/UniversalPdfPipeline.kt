package Com.ak.testseries.exam

import android.content.Context
import android.net.Uri
import Com.ak.testseries.utils.CompactMarkdownFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
    fun resolveSectionForIndex(
        examConfig: ExamConfig,
        subjectId: String,
        qIndexInSubject: Int,
        stageId: String? = null,
        paperId: String? = null
    ): SectionConfig? {
        val sections = UniversalAIContext.getApplicableSections(examConfig, subjectId, stageId, paperId)
        if (sections.isEmpty()) return null
        var runningCount = 0
        for (sec in sections) {
            runningCount += sec.totalQuestions
            if (qIndexInSubject <= runningCount) {
                return sec
            }
        }
        return sections.lastOrNull()
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
        paperId: String = "",
        onProgress: (BatchProgress) -> Unit = {}
    ): List<UniversalQuestionModel> = withContext(Dispatchers.IO) {
        val routeResolution = QuestionGenerationRouteResolver.resolve(
            examQuery = examConfig.examId,
            subjectQuery = subjectId,
            stageId = stageId.takeIf { it.isNotBlank() },
            paperId = paperId.takeIf { it.isNotBlank() }
        )
        val questionRoute = routeResolution.route
        if (questionRoute == null) {
            onProgress(
                BatchProgress(
                    totalBatches = 0,
                    currentBatch = 0,
                    processedQuestions = 0,
                    statusMessage = routeResolution.error
                        ?: "PDF processing stopped: exact exam, stage, paper and subject route is unresolved."
                )
            )
            return@withContext emptyList()
        }
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
        // Resume only questions belonging to this exact exam/stage/paper/subject route.
        val scopedExistingQuestions = existingQuestionsCache.filter { question ->
            question.examId == examConfig.examId &&
                question.stageId == questionRoute.stageId &&
                question.paperId == questionRoute.paperId &&
                question.subjectId == questionRoute.subjectId
        }
        val extractedQuestions = mutableListOf<UniversalQuestionModel>()
        extractedQuestions.addAll(scopedExistingQuestions)
        
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
        val existingHashes = scopedExistingQuestions.associate { it.duplicateHash to it.questionText }
        val effectiveApiKey = apiKey?.takeIf { it.isNotBlank() }
        var generationFailed = false

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
                    subject = questionRoute.subjectName,
                    testTitle = "${examConfig.shortName} Diagnostic Paper",
                    compactMarkdownPayload = compactMarkdown,
                    currentChunkSize = questionsInThisBatch,
                    startQ = startQ,
                    endQ = endQ,
                    totalQuestions = expectedQuestions,
                    isExtractionMode = compactMarkdown.isNotBlank(),
                    stageId = questionRoute.stageId,
                    paperId = questionRoute.paperId
                )
                // Add the NTA JEE Main 2027 unit index only for an exact Paper 1 route.
                // It is explicitly marked NOT APPROVED and cannot be used as official topic mapping.
                val selectedStage = examConfig.stages.singleOrNull { it.stageId == questionRoute.stageId }
                val selectedPaper = selectedStage?.papers?.singleOrNull { paper ->
                    paper.subjects.any { it.subjectId == questionRoute.subjectId }
                }
                val jeeMainUnitContext = if (
                    examConfig.examId == JeeMain2027SyllabusIndex.EXAM_ID &&
                    questionRoute.stageId == JeeMain2027SyllabusIndex.STAGE_ID &&
                    selectedPaper?.paperId == JeeMain2027SyllabusIndex.PAPER_ID
                ) {
                    UniversalPdfPromptContract.buildJeeMain2027UnitIndexContext(questionRoute.subjectId)
                } else {
                    ""
                }
                val promptWithSyllabusContext = if (jeeMainUnitContext.isNotBlank()) {
                    "$prompt\n\n$jeeMainUnitContext"
                } else {
                    prompt
                }
                val systemInstruction = UniversalAIContext.buildSystemInstruction(examConfig)

                // Gemini is the only permitted question-generation/extraction engine.
                // Any connection, authentication, quota, or response failure stops this batch.
                val apiResult = callGeminiApi(
                    apiKey = effectiveApiKey,
                    prompt = promptWithSyllabusContext,
                    systemInstruction = systemInstruction,
                    taskType = if (compactMarkdown.isNotBlank()) "question_extraction" else "question_generation"
                )
                val source = if (compactMarkdown.isNotBlank()) "Extracted PDF Markdown" else "Gemini API Generation"
                val batchModels = parseGeminiResponse(
                    jsonString = apiResult.responseText,
                    examConfig = examConfig,
                    stageId = questionRoute.stageId,
                    paperId = questionRoute.paperId,
                    subjectId = questionRoute.subjectId,
                    batchIndex = batchIndex,
                    existingHashes = existingHashes,
                    actualModel = apiResult.modelVersion,
                    actualSource = source
                )
                if (batchModels.isEmpty()) {
                    throw IllegalStateException(
                        "Gemini returned no valid questions. Offline question generation is disabled; review the source/API response and retry."
                    )
                }

                if (batchModels.isNotEmpty()) {
                    var finalBatch = batchModels
                    val initialValidation = QuestionValidationEngine.validateBatchDifficulty(finalBatch, examConfig.difficultyRatio)

                    if (!initialValidation.isCompliant && initialValidation.deficientLevels.isNotEmpty()) {
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

                        val targetedPrompt = """
                            ${UniversalAIContext.buildSystemInstruction(examConfig)}
                            Generate exactly $neededCount high-quality ${deficientLevel.name} difficulty questions for ${examConfig.examName} ($subjectId).
                            Difficulty requirement: Strictly ${deficientLevel.name} level questions only.
                            Output strictly a valid JSON array of questions matching schema.
                        """.trimIndent()

                        try {
                            val regenResult = callGeminiApi(effectiveApiKey, targetedPrompt, null, "question_generation")
                            val regenQuestions = parseGeminiResponse(
                                jsonString = regenResult.responseText,
                                examConfig = examConfig,
                                stageId = stageId,
                                subjectId = subjectId,
                                batchIndex = batchIndex,
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
                }
            } catch (e: Exception) {
                generationFailed = true
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

        if (!generationFailed && extractedQuestions.size >= expectedQuestions) {
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
        paperId: String,
        subjectId: String,
        batchIndex: Int,
        existingHashes: Map<String, String>,
        actualModel: String = "UNAVAILABLE",
        actualSource: String = "UNAVAILABLE"
    ): List<UniversalQuestionModel> {
        val resultList = mutableListOf<UniversalQuestionModel>()
        try {
            val cleanJson = jsonString.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val jsonArray = if (cleanJson.startsWith("[")) {
                JSONArray(cleanJson)
            } else if (cleanJson.startsWith("{")) {
                val obj = JSONObject(cleanJson)
                obj.optJSONArray("questions") ?: obj.optJSONArray("data") ?: JSONArray()
            } else {
                val start = cleanJson.indexOf('[')
                val end = cleanJson.lastIndexOf(']')
                if (start != -1 && end > start) JSONArray(cleanJson.substring(start, end + 1)) else JSONArray()
            }

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

                val qIndexInBatch = (batchIndex * 10) + i + 1
                val resolvedSection = resolveSectionForIndex(examConfig, subjectId, qIndexInBatch, stageId, paperId)
                val parsedSecId = item.optString("sectionId").ifBlank { resolvedSection?.sectionId ?: "SECTION_A" }
                val parsedSecName = item.optString("sectionName").ifBlank { resolvedSection?.sectionName ?: "Section A" }
                val parsedPosMarks = if (item.has("positiveMarks")) item.optDouble("positiveMarks", 4.0) else (resolvedSection?.positiveMarks ?: 4.0)
                val parsedNegMarks = if (item.has("negativeMarks")) item.optDouble("negativeMarks", 1.0) else (resolvedSection?.negativeMarks ?: 1.0)

                val diffStr = item.optString("difficulty", "MEDIUM").uppercase()
                val diffLevel = when {
                    diffStr.contains("EASY") -> DifficultyLevel.EASY
                    diffStr.contains("HARD") -> DifficultyLevel.HARD
                    else -> DifficultyLevel.MEDIUM
                }

                val qTypeStr = item.optString("questionType").uppercase()
                val parsedQType = when {
                    qTypeStr.contains("STATEMENT_I") || qTypeStr.contains("STATEMENT_1") -> QuestionType.STATEMENT_I_II
                    qTypeStr.contains("ASSERTION") -> QuestionType.ASSERTION_REASON
                    qTypeStr.contains("MATCH") -> QuestionType.MATCH_LIST_I_II
                    qTypeStr.contains("MULTI") -> QuestionType.MULTI_STATEMENT
                    qTypeStr.contains("NUMERICAL") || qTypeStr.contains("NAT") || qTypeStr.contains("NVQ") -> QuestionType.NUMERICAL_VALUE
                    qTypeStr.contains("DIAGRAM") || qTypeStr.contains("IMAGE") -> QuestionType.DIAGRAM_LABELING
                    qTypeStr.contains("GRAPH") -> QuestionType.GRAPH
                    qTypeStr.contains("DIRECT") || qTypeStr.contains("MCQ") -> QuestionType.DIRECT_MCQ
                    else -> resolvedSection?.questionType ?: examConfig.supportedQuestionTypes.firstOrNull() ?: QuestionType.DIRECT_MCQ
                }

                val isNumerical = (parsedQType == QuestionType.NUMERICAL_VALUE || parsedQType == QuestionType.NUMERICAL || parsedQType == QuestionType.TITA)
                val finalOptions = if (isNumerical) emptyList() else options

                val rawAnswer = item.optString("correctAnswer", "").trim()
                val correctAnswer = if (isNumerical) {
                    rawAnswer // Preserve full integer, decimal, or negative numerical answer
                } else {
                    if (rawAnswer.length == 1) rawAnswer.uppercase() else rawAnswer
                }
                val explanation = item.optString("explanationEn").ifBlank { item.optString("explanation") }

                val hash = DuplicateDetectionEngine.generateNormalizedHash(qText, finalOptions)
                val dupResult = DuplicateDetectionEngine.checkDuplicate(qText, finalOptions, existingHashes)
                val validation = QuestionValidationEngine.validateQuestion(
                    questionText = qText,
                    options = finalOptions,
                    correctAnswer = correctAnswer,
                    explanation = explanation,
                    hasDiagramAttached = false,
                    duplicateResult = dupResult,
                    questionType = parsedQType,
                    expectedOptionCount = examConfig.defaultOptionCount
                )

                val model = UniversalQuestionModel(
                    questionId = "uq_${examConfig.examId}_${System.currentTimeMillis()}_${batchIndex}_$i",
                    examId = examConfig.examId,
                    stageId = stageId,
                    paperId = paperId,
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
                    sourceAnswer = null,
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
                    sourcePage = batchIndex + 1,
                    questionVersion = 1,
                    validationVersion = 1,
                    generationModel = actualModel,
                    generationTimestamp = System.currentTimeMillis()
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
        apiKey: String?,
        prompt: String,
        systemInstruction: String?,
        taskType: String = "question_generation"
    ): GeminiApiExecutionResult {
        val result = GeminiRequestExecutor.execute(
            prompt = prompt,
            systemInstruction = systemInstruction,
            userApiKey = apiKey,
            taskType = taskType
        )
        return GeminiApiExecutionResult(
            responseText = result.responseText,
            modelName = result.modelName,
            modelVersion = result.modelVersion
        )
    }

}
