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
        require(examConfig.active && examConfig.verified) {
            "This exam configuration is not source-verified. PDF ingestion is blocked until official pattern evidence is reviewed."
        }
        require(expectedQuestions > 0 && batchSize > 0 && resumeFromBatch > 0) {
            "Expected question count, batch size and resume batch must be positive."
        }
        val selectedStage = if (stageId.isNotBlank()) {
            examConfig.stages.singleOrNull { it.stageId == stageId }
                ?: throw IllegalArgumentException("Unknown or ambiguous stageId: $stageId")
        } else {
            examConfig.stages.singleOrNull()
                ?: throw IllegalArgumentException("Select an exact exam stage; this exam has multiple stages.")
        }
        val selectedPaper = selectedStage.papers.singleOrNull()
            ?: throw IllegalArgumentException("Select an exact paper; this stage has multiple papers.")
        require(subjectId.isNotBlank() && selectedPaper.subjects.any { it.subjectId == subjectId }) {
            "Select a subject belonging to the selected paper."
        }
        require(selectedPaper.durationMinutes > 0 && selectedPaper.totalMarks > 0.0) {
            "Selected paper has incomplete duration or maximum marks configuration."
        }
        val selectedSubject = selectedPaper.subjects.single { it.subjectId == subjectId }
        require(selectedSubject.sections.isNotEmpty()) {
            "Selected subject has no verified section/marking configuration."
        }
        val maxSubjectQuestions = selectedSubject.sections.sumOf { it.totalQuestions }
        require(expectedQuestions <= maxSubjectQuestions) {
            "Expected question count $expectedQuestions exceeds the verified subject maximum $maxSubjectQuestions."
        }
        require(existingQuestionsCache.size <= expectedQuestions) {
            "Cached questions exceed the configured subject question count."
        }
        val resolvedStageId = selectedStage.stageId
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
        val existingHashes = existingQuestionsCache.associate { it.duplicateHash to it.questionText }.toMutableMap()
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
                    subject = selectedSubject.subjectName,
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
                if (!effectiveApiKey.isNullOrBlank() && compactMarkdown.isNotBlank()) {
                    val apiResult = callGeminiApi(effectiveApiKey, prompt, systemInstruction)
                    if (apiResult != null) {
                        val source = if (compactMarkdown.isNotBlank()) "Extracted PDF Markdown" else "Gemini API Generation"
                        batchModels = parseGeminiResponse(
                            jsonString = apiResult.responseText,
                            examConfig = examConfig,
                            stageId = resolvedStageId,
                            subjectId = subjectId,
                            paperId = selectedPaper.paperId,
                            subjectConfig = selectedSubject,
                            batchIndex = batchIndex,
                            batchSize = batchSize,
                            existingHashes = existingHashes,
                            actualModel = apiResult.modelVersion,
                            actualSource = source
                        )
                    }
                }

                // Only call the model when actual PDF text was extracted; never invent a paper from an empty/unreadable PDF.
                if (!batchModels.isNullOrEmpty()) {
                    require(batchModels.size == questionsInThisBatch) {
                        "Batch $currentBatchNumber returned ${batchModels.size} valid questions; expected $questionsInThisBatch. Partial batch is rejected."
                    }
                    batchModels.forEach { existingHashes[it.duplicateHash] = it.questionText }
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
                                    stageId = resolvedStageId,
                                    subjectId = subjectId,
                                    paperId = selectedPaper.paperId,
                                    subjectConfig = selectedSubject,
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
                                    regenQuestions.forEach { existingHashes[it.duplicateHash] = it.questionText }
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
                    val parsedFromSourcePdf = if (compactMarkdown.isNotBlank()) {
                        OfflineMarkdownQuestionParser.parseMarkdownToQuestions(compactMarkdown, examConfig, subjectId, resolvedStageId)
                    } else {
                        emptyList()
                    }

                    if (parsedFromSourcePdf.isEmpty()) {
                        onProgress(
                            BatchProgress(
                                totalBatches = totalBatches,
                                currentBatch = currentBatchNumber,
                                processedQuestions = extractedQuestions.size,
                                statusMessage = "No valid questions were extracted from the source PDF. No placeholder or fabricated questions were generated; check PDF text/OCR and retry."
                            )
                        )
                    } else {
                        val reviewedBatch = parsedFromSourcePdf
                            .drop(batchIndex * batchSize)
                            .take(questionsInThisBatch)
                            .mapNotNull { candidate ->
                            val candidateSection = selectedSubject.sections.singleOrNull { it.sectionId == candidate.sectionId }
                                ?: return@mapNotNull null
                            val duplicate = DuplicateDetectionEngine.checkDuplicate(
                                candidate.questionText,
                                candidate.options,
                                existingHashes
                            )
                            if (duplicate.isDuplicate) return@mapNotNull null
                            val validation = QuestionValidationEngine.validateQuestion(
                                questionText = candidate.questionText,
                                options = candidate.options,
                                correctAnswer = candidate.correctAnswer,
                                explanation = candidate.explanation,
                                hasDiagramAttached = !candidate.diagramUrl.isNullOrBlank(),
                                duplicateResult = duplicate,
                                questionType = candidate.questionType,
                                expectedOptionCount = candidateSection.optionsCount
                            )
                            candidate.copy(
                                validationStatus = ValidationStatus.NEEDS_REVIEW,
                                validationReasons = (
                                    validation.issues +
                                        candidate.validationReasons +
                                        "PDF-extracted content is unverified. Admin must confirm provenance, answer key and exam pattern."
                                    ).distinct(),
                                qualityScore = validation.qualityScore,
                                qualityMetrics = validation.qualityMetrics,
                                duplicateHash = DuplicateDetectionEngine.generateNormalizedHash(candidate.questionText, candidate.options),
                                sourceName = "Uploaded PDF (unverified provenance)",
                                isOfficialPYQ = false
                            )
                        }
                        require(reviewedBatch.size == questionsInThisBatch) {
                            "Source PDF batch $currentBatchNumber produced ${reviewedBatch.size} valid questions; expected $questionsInThisBatch. Partial batch is rejected."
                        }
                        extractedQuestions.addAll(reviewedBatch)
                        onProgress(
                            BatchProgress(
                                totalBatches = totalBatches,
                                currentBatch = currentBatchNumber,
                                processedQuestions = extractedQuestions.size,
                                statusMessage = "Extracted ${reviewedBatch.size} source questions. All are marked NEEDS_REVIEW until an Admin verifies them."
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

        require(extractedQuestions.size == expectedQuestions) {
            "Incomplete extraction: expected $expectedQuestions questions but validated ${extractedQuestions.size}. No partial test is returned."
        }
        onProgress(
            BatchProgress(
                totalBatches = totalBatches,
                currentBatch = totalBatches,
                processedQuestions = extractedQuestions.size,
                statusMessage = "Extraction Complete! ${extractedQuestions.size} validated questions ready for Review."
            )
        )

        return@withContext extractedQuestions
    }

    private fun parseGeminiResponse(
        jsonString: String,
        examConfig: ExamConfig,
        stageId: String,
        subjectId: String,
        paperId: String,
        subjectConfig: SubjectConfig,
        batchIndex: Int,
        batchSize: Int,
        existingHashes: Map<String, String>,
        actualModel: String = "UNAVAILABLE",
        actualSource: String = "UNAVAILABLE"
    ): List<UniversalQuestionModel> {
        val resultList = mutableListOf<UniversalQuestionModel>()
        val seenHashes = existingHashes.toMutableMap()
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

                val qIndexInBatch = (batchIndex * batchSize) + i + 1
                var sectionQuestionEnd = 0
                val resolvedSection = subjectConfig.sections.singleOrNull { section ->
                    sectionQuestionEnd += section.totalQuestions
                    qIndexInBatch <= sectionQuestionEnd
                }
                val expectedSection = resolvedSection ?: continue
                val suppliedSectionId = item.optString("sectionId").takeIf { it.isNotBlank() }
                if (suppliedSectionId != null && suppliedSectionId != expectedSection.sectionId) continue
                val parsedSecId = expectedSection.sectionId
                val sectionConfig = subjectConfig.sections.singleOrNull { it.sectionId == parsedSecId } ?: continue
                val suppliedSectionName = item.optString("sectionName").takeIf { it.isNotBlank() }
                if (suppliedSectionName != null && !suppliedSectionName.equals(sectionConfig.sectionName, true)) continue
                val parsedSecName = sectionConfig.sectionName
                val parsedPosMarks = if (item.has("positiveMarks")) item.optDouble("positiveMarks", Double.NaN) else sectionConfig.positiveMarks
                val parsedNegMarks = if (item.has("negativeMarks")) item.optDouble("negativeMarks", Double.NaN) else sectionConfig.negativeMarks
                if (!parsedPosMarks.isFinite() || parsedPosMarks <= 0.0 || !parsedNegMarks.isFinite() || parsedNegMarks < 0.0) continue
                if (parsedPosMarks != sectionConfig.positiveMarks || parsedNegMarks != sectionConfig.negativeMarks) continue

                val diffStr = item.optString("difficulty").uppercase()
                val diffLevel = when (diffStr) {
                    "EASY" -> DifficultyLevel.EASY
                    "MEDIUM" -> DifficultyLevel.MEDIUM
                    "HARD" -> DifficultyLevel.HARD
                    else -> continue
                }

                val qTypeStr = item.optString("questionType").trim().uppercase()
                val suppliedType = when {
                    qTypeStr.isBlank() -> sectionConfig.questionType
                    qTypeStr in setOf("MCQ", "MCQ_SINGLE", "DIRECT_MCQ") -> QuestionType.MCQ_SINGLE
                    qTypeStr in setOf("NUMERICAL", "NUMERICAL_VALUE", "NAT", "NVQ", "TITA") -> QuestionType.NUMERICAL_VALUE
                    else -> continue
                }
                if (suppliedType != sectionConfig.questionType ||
                    suppliedType !in examConfig.supportedQuestionTypes) continue

                val isNumerical = sectionConfig.questionType in setOf(
                    QuestionType.NUMERICAL_VALUE, QuestionType.NUMERICAL, QuestionType.TITA
                )
                if (isNumerical && options.isNotEmpty()) continue
                if (!isNumerical && (options.size != sectionConfig.optionsCount ||
                    options.any { it.isBlank() } ||
                    options.distinctBy { it.trim().lowercase() }.size != sectionConfig.optionsCount)) continue
                val finalOptions = if (isNumerical) emptyList() else options

                val rawAnswer = item.optString("correctAnswer").trim()
                val correctAnswer = rawAnswer.uppercase()
                val validAnswerKey = if (isNumerical) correctAnswer.toDoubleOrNull()?.isFinite() == true
                    else correctAnswer in setOf("A", "B", "C", "D", "1", "2", "3", "4")
                if (!validAnswerKey) continue
                val explanation = item.optString("explanationEn").ifBlank { item.optString("explanation") }

                val hash = DuplicateDetectionEngine.generateNormalizedHash(qText, finalOptions)
                val dupResult = DuplicateDetectionEngine.checkDuplicate(qText, finalOptions, seenHashes)
                val validation = QuestionValidationEngine.validateQuestion(
                    questionText = qText,
                    options = finalOptions,
                    correctAnswer = correctAnswer,
                    explanation = explanation,
                    hasDiagramAttached = false,
                    duplicateResult = dupResult,
                    questionType = sectionConfig.questionType,
                    expectedOptionCount = sectionConfig.optionsCount
                )

                val model = UniversalQuestionModel(
                    questionId = "uq_${examConfig.examId}_${java.util.UUID.randomUUID()}",
                    examId = examConfig.examId,
                    stageId = stageId,
                    paperId = selectedPaper.paperId,
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
                    questionType = sectionConfig.questionType,
                    positiveMarks = parsedPosMarks,
                    negativeMarks = parsedNegMarks,
                    difficultyScore = validation.difficultyScore,
                    qualityScore = validation.qualityScore,
                    qualityMetrics = validation.qualityMetrics,
                    validationStatus = ValidationStatus.NEEDS_REVIEW,
                    validationReasons = (validation.issues + "AI/PDF-derived content requires Admin verification of source, answer key and exam pattern before approval.").distinct(),
                    duplicateHash = hash,
                    sourceName = actualSource,
                    isOfficialPYQ = false,
                    sourcePage = item.optInt("sourcePage", 0).coerceAtLeast(0),
                    questionVersion = 1,
                    validationVersion = 1,
                    generationModel = actualModel,
                    generationTimestamp = System.currentTimeMillis()
                )
                resultList.add(model)
                seenHashes[hash] = qText\n                seenHashes[hash] = qText
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
