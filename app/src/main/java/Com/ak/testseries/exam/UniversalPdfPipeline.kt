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
        val sections = UniversalAIContext.getApplicableSections(examConfig, subjectId)
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
        onProgress: (BatchProgress) -> Unit = {},
        onDocumentExtracted: (UniversalPdfDocumentExtractor.Result) -> Unit = {}
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
        val appContext = requireNotNull(context) { "PDF extraction requires an Android context." }
        
        // Shared extraction path: page text + local diagram/image candidates for every exam.
        val documentExtraction = UniversalPdfDocumentExtractor.extract(
            context = appContext,
            pdfUri = pdfUri,
            testId = "universal_${System.currentTimeMillis()}",
            maxPages = 200
        )
        val compactMarkdown = documentExtraction.compactMarkdown
        onDocumentExtracted(documentExtraction)
        onProgress(
            BatchProgress(
                totalBatches = (expectedQuestions + batchSize - 1) / batchSize,
                currentBatch = 0,
                processedQuestions = extractedQuestions.size,
                statusMessage = "Extracted ${documentExtraction.extractedPageCount} PDF pages and ${documentExtraction.diagramCandidates.size} diagram/image candidates for ${examConfig.examName}."
            )
        )

        val totalBatches = (expectedQuestions + batchSize - 1) / batchSize
        val existingHashes = existingQuestionsCache.associate { it.duplicateHash to it.questionText }
        val effectiveApiKey = apiKey ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
        var offlineFallbackAttempted = false

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

                        val targetedPrompt = """
                            ${UniversalAIContext.buildSystemInstruction(examConfig)}
                            Generate exactly $neededCount high-quality ${deficientLevel.name} difficulty questions for ${examConfig.examName} ($subjectId).
                            Difficulty requirement: Strictly ${deficientLevel.name} level questions only.
                            Output strictly a valid JSON array of questions matching schema.
                        """.trimIndent()

                        try {
                            val regenResult = callGeminiApi(effectiveApiKey, targetedPrompt, null)
                            if (regenResult != null) {
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
                    // The offline parser processes the whole PDF, not one batch. Run it once only;
                    // otherwise an API failure in every batch would duplicate the entire paper repeatedly.
                    if (!offlineFallbackAttempted) {
                        offlineFallbackAttempted = true
                        val parsedFromPdf = OfflineMarkdownQuestionParser.parseMarkdownToQuestions(
                            compactMarkdown, examConfig, subjectId, stageId
                        )
                        val seenHashes = extractedQuestions.map {
                            DuplicateDetectionEngine.generateNormalizedHash(it.questionText, it.options)
                        }.toMutableSet()
                        val uniqueFromPdf = parsedFromPdf.filter { question ->
                            val hash = DuplicateDetectionEngine.generateNormalizedHash(question.questionText, question.options)
                            seenHashes.add(hash)
                        }
                        val remaining = (expectedQuestions - extractedQuestions.size).coerceAtLeast(0)
                        extractedQuestions.addAll(uniqueFromPdf.take(remaining))
                    }
                    if (extractedQuestions.isEmpty()) {
                        onProgress(
                            BatchProgress(
                                totalBatches = totalBatches,
                                currentBatch = currentBatchNumber,
                                processedQuestions = 0,
                                statusMessage = "No valid questions were extracted from this PDF. Please review the PDF or retry."
                            )
                        )
                        break
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

        if (extractedQuestions.size >= expectedQuestions) {
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

    /**
     * Normalizes model-produced question type labels without conflating multiple-select
     * MCQs with multi-statement questions or falling back to an unrelated first exam type.
     */
    internal fun resolveQuestionType(rawType: String, fallback: QuestionType): QuestionType {
        val key = rawType.trim().uppercase().replace(Regex("[\\s-]+"), "_")
        return when (key) {
            "MCQ", "DIRECT_MCQ" -> QuestionType.DIRECT_MCQ
            "MCQ_SINGLE", "SINGLE_CORRECT" -> QuestionType.MCQ_SINGLE
            "MCQ_MULTIPLE", "MULTIPLE_CHOICE", "MULTI_SELECT", "MSQ" -> QuestionType.MCQ_MULTIPLE
            "STATEMENT_I", "STATEMENT_1", "STATEMENT_I_II" -> QuestionType.STATEMENT_I_II
            "ASSERTION_REASON" -> QuestionType.ASSERTION_REASON
            "MATCH", "MATCHING", "MATCH_LIST_I_II" -> QuestionType.MATCH_LIST_I_II
            "MULTI_STATEMENT" -> QuestionType.MULTI_STATEMENT
            "NUMERICAL", "NUMERICAL_VALUE", "NAT", "NVQ" -> QuestionType.NUMERICAL_VALUE
            "TITA" -> QuestionType.TITA
            "DIAGRAM", "DIAGRAM_LABELING", "DIAGRAM_BASED", "IMAGE" -> QuestionType.DIAGRAM_LABELING
            "GRAPH" -> QuestionType.GRAPH
            "PASSAGE_BASED" -> QuestionType.PASSAGE_BASED
            "CASE_BASED" -> QuestionType.CASE_BASED
            "TABLE_BASED" -> QuestionType.TABLE_BASED
            "STATEMENT_BASED" -> QuestionType.STATEMENT_BASED
            "DESCRIPTIVE" -> QuestionType.DESCRIPTIVE
            else -> fallback
        }
    }

    internal fun parseGeminiResponse(
        jsonString: String,
        examConfig: ExamConfig,
        stageId: String,
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
                val resolvedSection = resolveSectionForIndex(examConfig, subjectId, qIndexInBatch)
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

                val fallbackQuestionType = resolvedSection?.questionType
                    ?: examConfig.supportedQuestionTypes.firstOrNull()
                    ?: QuestionType.DIRECT_MCQ
                val parsedQType = resolveQuestionType(item.optString("questionType"), fallbackQuestionType)

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
        apiKey: String,
        prompt: String,
        systemInstruction: String?
    ): GeminiApiExecutionResult? = withContext(Dispatchers.IO) {
        val candidateModels = listOf("gemini-3.8-flash", "gemini-flash-latest", "gemini-2.5-flash")
        for (modelName in candidateModels) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
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
