package Com.ak.testseries.exam

import android.content.Context
import android.net.Uri
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
    fun resolveSectionForIndex(
        examConfig: ExamConfig,
        subjectId: String,
        qIndexInSubject: Int,
        stageId: String? = null
    ): SectionConfig? {
        if (qIndexInSubject <= 0) return null
        val sections = UniversalAIContext.getApplicableSections(examConfig, subjectId, stageId)
        if (sections.isEmpty()) return null
        var runningCount = 0
        for (sec in sections) {
            runningCount += sec.totalQuestions
            if (qIndexInSubject <= runningCount) {
                return sec
            }
        }
        return null
    }

    data class AdminPdfRouteResult(
        val status: String,
        val documentText: String,
        val routing: JSONObject?,
        val analysis: JSONObject?,
        val error: String? = null
    )

    /**
     * Admin PDF entry point. Extracts text, asks Gemini to classify the document,
     * then invokes the matching syllabus or test-coverage prompt. This function
     * returns a reviewable draft only; persistence and publication require Admin UI approval.
     */
    suspend fun processAdminUploadedPdf(
        context: Context,
        pdfUri: Uri,
        examConfig: ExamConfig,
        apiKey: String? = null,
        selectedSubject: String = "",
        approvedSyllabusJson: String = ""
    ): AdminPdfRouteResult = withContext(Dispatchers.IO) {
        val effectiveApiKey = apiKey?.takeIf { it.isNotBlank() }
        if (effectiveApiKey.isNullOrBlank()) {
            return@withContext AdminPdfRouteResult("NEEDS_REVIEW", "", null, null, "Gemini API key is unavailable.")
        }

        val extractedText = try {
            val tempFile = java.io.File.createTempFile("admin_route_", ".pdf", context.cacheDir)
            try {
                val copied = context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
                if (!copied || tempFile.length() <= 0L) ""
                else com.ak.pdfextractor.AdvancedPdfSmartExtractor(context).extractAllStreamText(tempFile)
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            return@withContext AdminPdfRouteResult("NEEDS_REVIEW", "", null, null, "PDF text extraction failed: ${e.message ?: "unknown error"}")
        }

        if (extractedText.isBlank()) {
            return@withContext AdminPdfRouteResult("NEEDS_REVIEW", "", null, null, "No readable PDF text was extracted. OCR or manual review is required.")
        }
        val documentText = CompactMarkdownFormatter.formatToCompactMarkdown(extractedText).take(50000)

        val routingResult = callGeminiApi(
            effectiveApiKey,
            UniversalAIContext.buildPdfDocumentRoutingPrompt(examConfig, documentText),
            UniversalAIContext.buildSystemInstruction(examConfig)
        ) ?: return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, null, null, "Gemini document classification failed.")

        val routingJson = try { JSONObject(routingResult.responseText) } catch (_: Exception) {
            return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, null, null, "Gemini returned invalid routing JSON.")
        }
        val routeStatus = routingJson.optString("status", "NEEDS_REVIEW").uppercase()
        val nextRoute = routingJson.optString("nextRoute", "ADMIN_REVIEW").uppercase()
        if (routeStatus != "CLASSIFIED" || nextRoute == "ADMIN_REVIEW" || nextRoute == "UNKNOWN") {
            return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, routingJson, null, "Document classification requires Admin review.")
        }

        val analysisPrompt = when (nextRoute) {
            "SYLLABUS_CATALOG_EXTRACTION" -> UniversalAIContext.buildSyllabusCatalogExtractionPrompt(
                examConfig = examConfig,
                documentText = documentText
            )
            "TEST_COVERAGE_MAPPING" -> {
                if (approvedSyllabusJson.isBlank()) {
                    return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, routingJson, null, "No Admin-approved syllabus catalog was supplied for test mapping.")
                }
                UniversalAIContext.buildTestCoverageMappingPrompt(examConfig, documentText, approvedSyllabusJson)
            }
            "QUESTION_EXTRACTION" -> UniversalAIContext.buildDynamicGenerationPrompt(
                examConfig = examConfig,
                subject = selectedSubject.ifBlank { routingJson.optString("subject", "Core Domain") },
                testTitle = routingJson.optString("testTitle", "Admin PDF Import"),
                compactMarkdownPayload = documentText,
                currentChunkSize = 20,
                startQ = 1,
                endQ = 20,
                totalQuestions = 20,
                isExtractionMode = true
            )
            else -> return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, routingJson, null, "Unsupported or unsafe route: $nextRoute")
        }

        val analysisResult = callGeminiApi(effectiveApiKey, analysisPrompt, UniversalAIContext.buildSystemInstruction(examConfig))
            ?: return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, routingJson, null, "Gemini document processing failed.")
        val analysisJson = try { JSONObject(analysisResult.responseText) } catch (_: Exception) {
            return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, routingJson, null, "Gemini returned invalid analysis JSON.")
        }
        // Never allow model output alone to publish a syllabus or a test.
        analysisJson.put("publishAllowed", false)
        AdminPdfRouteResult("DRAFT_REVIEW", documentText, routingJson, analysisJson)
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
        val effectiveApiKey = apiKey?.takeIf { it.isNotBlank() }

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
                    isExtractionMode = compactMarkdown.isNotBlank(),
                    stageId = stageId
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
                    val realExtractedFromMarkdown = if (compactMarkdown.isNotBlank()) {
                        OfflineMarkdownQuestionParser.parseMarkdownToQuestions(compactMarkdown, examConfig, subjectId, stageId)
                    } else emptyList()

                    // This is a PDF pipeline: if the supplied PDF cannot be read,
                    // do not substitute unrelated bundled questions and imply they came from it.
                    for (i in 1..questionsInThisBatch) {
                        val qIndex = (batchIndex * batchSize) + i
                        val markdownCandidate = realExtractedFromMarkdown.getOrNull(qIndex - 1)
                        if (markdownCandidate == null) continue

                        val resolvedSection = resolveSectionForIndex(examConfig, subjectId, qIndex, stageId)
                        val qType = resolvedSection?.questionType ?: markdownCandidate?.questionType ?: continue
                        val secId = resolvedSection?.sectionId
                            ?: markdownCandidate?.sectionId?.takeIf { it.isNotBlank() } ?: continue
                        val secName = resolvedSection?.sectionName
                            ?: markdownCandidate?.sectionName?.takeIf { it.isNotBlank() } ?: continue
                        val posMark = resolvedSection?.positiveMarks ?: markdownCandidate?.positiveMarks ?: continue
                        val negMark = resolvedSection?.negativeMarks ?: markdownCandidate?.negativeMarks ?: continue

                        val questionText = markdownCandidate.questionText.takeIf { it.isNotBlank() } ?: continue
                        val options = markdownCandidate.options
                        val isOptionFree = qType in setOf(QuestionType.NUMERICAL, QuestionType.NUMERICAL_VALUE, QuestionType.TITA, QuestionType.DESCRIPTIVE)
                        if (!isOptionFree && options.size < examConfig.defaultOptionCount.coerceIn(2, 5)) continue
                        val correctAnswer = markdownCandidate.correctAnswer.takeIf { it.isNotBlank() } ?: continue
                        val explanation = markdownCandidate.explanation

                        val hash = DuplicateDetectionEngine.generateNormalizedHash(questionText, options)
                        val dupResult = DuplicateDetectionEngine.checkDuplicate(questionText, options, existingHashes)
                        val validation = QuestionValidationEngine.validateQuestion(
                            questionText = questionText,
                            options = options,
                            correctAnswer = correctAnswer,
                            sourceAnswer = markdownCandidate?.sourceAnswer,
                            explanation = explanation,
                            hasDiagramAttached = markdownCandidate?.diagramUrl != null,
                            duplicateResult = dupResult,
                            questionType = qType,
                            expectedOptionCount = resolvedSection?.optionsCount ?: examConfig.defaultOptionCount
                        )
                        if (validation.status == ValidationStatus.DUPLICATE || validation.status == ValidationStatus.REJECTED) continue

                        extractedQuestions.add(
                            UniversalQuestionModel(
                                questionId = "uq_${examConfig.examId}_${System.currentTimeMillis()}_$qIndex",
                                examId = examConfig.examId,
                                stageId = stageId,
                                paperId = examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.paperId ?: "",
                                sectionId = secId,
                                sectionName = secName,
                                subjectId = subjectId,
                                questionText = questionText,
                                questionTextHi = markdownCandidate?.questionTextHi,
                                options = options,
                                optionsHi = markdownCandidate?.optionsHi,
                                correctAnswer = correctAnswer,
                                sourceAnswer = markdownCandidate?.sourceAnswer,
                                explanation = explanation,
                                explanationHi = markdownCandidate?.explanationHi,
                                difficulty = markdownCandidate?.difficulty ?: DifficultyLevel.MEDIUM,
                                questionType = qType,
                                positiveMarks = posMark,
                                negativeMarks = negMark,
                                difficultyScore = validation.difficultyScore,
                                qualityScore = validation.qualityScore,
                                qualityMetrics = validation.qualityMetrics,
                                validationStatus = validation.status,
                                validationReasons = validation.issues,
                                duplicateHash = hash,
                                diagramUrl = markdownCandidate?.diagramUrl,
                                diagramMetadata = markdownCandidate?.diagramMetadata,
                                sourceName = if (markdownCandidate != null) "Extracted PDF Markdown" else "${examConfig.examName} Bundled Verified Catalog",
                                sourcePage = markdownCandidate?.sourcePage ?: 1,
                                isOfficialPYQ = markdownCandidate?.isOfficialPYQ ?: false,
                                questionVersion = 1,
                                validationVersion = 1,
                                generationModel = if (markdownCandidate != null) "pdf-real-extractor-v1" else "bundled-exam-catalog-v1",
                                generationTimestamp = System.currentTimeMillis()
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
                val resolvedSection = resolveSectionForIndex(examConfig, subjectId, qIndexInBatch, stageId)
                val parsedSecId = item.optString("sectionId").takeIf { it.isNotBlank() && it != "null" } ?: resolvedSection?.sectionId ?: continue
                val parsedSecName = item.optString("sectionName").takeIf { it.isNotBlank() && it != "null" } ?: resolvedSection?.sectionName ?: continue
                val parsedPosMarks = if (item.has("positiveMarks") && !item.isNull("positiveMarks")) item.optDouble("positiveMarks", Double.NaN) else resolvedSection?.positiveMarks ?: continue
                val parsedNegMarks = if (item.has("negativeMarks") && !item.isNull("negativeMarks")) item.optDouble("negativeMarks", Double.NaN) else resolvedSection?.negativeMarks ?: continue
                if (!parsedPosMarks.isFinite() || !parsedNegMarks.isFinite() || parsedPosMarks < 0.0 || parsedNegMarks < 0.0) continue

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
