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
        existingHashes: Map<String, String>,
        actualModel: String = "UNAVAILABLE",
        actualSource: String = "UNAVAILABLE"
    ): List<UniversalQuestionModel> {
        val envelope = try {
            GeminiResponseContract.parseEnvelope(jsonString)
        } catch (e: Exception) {
            android.util.Log.e("UniversalPdfPipeline", "Rejected Gemini response envelope: ${e.message}")
            return emptyList()
        }
        val resultList = mutableListOf<UniversalQuestionModel>()
        for (i in 0 until envelope.questions.length()) {
            val item = envelope.questions.optJSONObject(i) ?: continue
            val qText = item.optString("questionTextEn").ifBlank { item.optString("questionText") }.trim()
            if (qText.isBlank()) continue
            val qIndexInBatch = (batchIndex * 10) + i + 1
            val resolvedSection = resolveSectionForIndex(examConfig, subjectId, qIndexInBatch)
            val contractCheck = GeminiResponseContract.validateQuestion(
                item, examConfig, subjectId, qIndexInBatch,
                actualSource.contains("Extract", ignoreCase = true)
            )
            val parsedType = runCatching { QuestionType.valueOf(item.optString("questionType").uppercase()) }
                .getOrNull() ?: resolvedSection?.questionType ?: QuestionType.DIRECT_MCQ
            val optionKeys = listOf("optionAEn", "optionBEn", "optionCEn", "optionDEn", "optionEEn")
            val options = optionKeys.map { key ->
                item.optString(key).ifBlank { item.optString(key.removeSuffix("En")) }
            }.filter { it.isNotBlank() }
            val numerical = parsedType in setOf(QuestionType.NUMERICAL, QuestionType.NUMERICAL_VALUE, QuestionType.TITA)
            val finalOptions = if (numerical) emptyList() else options
            val answer = if (item.isNull("correctAnswer")) "" else item.optString("correctAnswer").trim()
            val sourceAnswer = item.optString("sourceAnswer").takeIf { it.isNotBlank() && !it.equals("null", true) }
            val explanation = item.optString("explanationEn").ifBlank { item.optString("explanation") }
            val sectionId = item.optString("sectionId").takeIf { it.isNotBlank() && it != "null" } ?: resolvedSection?.sectionId.orEmpty()
            val sectionName = item.optString("sectionName").takeIf { it.isNotBlank() && it != "null" } ?: resolvedSection?.sectionName.orEmpty()
            val positiveMarks = if (item.has("positiveMarks") && !item.isNull("positiveMarks")) item.optDouble("positiveMarks", 0.0) else resolvedSection?.positiveMarks ?: 0.0
            val negativeMarks = if (item.has("negativeMarks") && !item.isNull("negativeMarks")) item.optDouble("negativeMarks", 0.0) else resolvedSection?.negativeMarks ?: 0.0
            val difficulty = runCatching { DifficultyLevel.valueOf(item.optString("difficulty").uppercase()) }
                .getOrDefault(DifficultyLevel.MEDIUM)
            val hash = DuplicateDetectionEngine.generateNormalizedHash(qText, finalOptions)
            val duplicate = DuplicateDetectionEngine.checkDuplicate(qText, finalOptions, existingHashes)
            val validation = QuestionValidationEngine.validateQuestion(
                qText, finalOptions, answer, explanation, false, duplicate, sourceAnswer,
                parsedType, resolvedSection?.optionsCount ?: examConfig.defaultOptionCount
            )
            val reviewReasons = (validation.issues + contractCheck.reasons + envelope.errors).distinct()
            val requiresReview = !contractCheck.accepted || envelope.status == "NEEDS_REVIEW" ||
                item.optBoolean("needsReview", false) ||
                validation.status in setOf(ValidationStatus.NEEDS_REVIEW, ValidationStatus.REJECTED, ValidationStatus.DUPLICATE)
            resultList.add(
                UniversalQuestionModel(
                    questionId = "uq_${examConfig.examId}_${System.currentTimeMillis()}_${batchIndex}_$i",
                    examId = examConfig.examId,
                    stageId = stageId,
                    paperId = examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.paperId.orEmpty(),
                    sectionId = sectionId,
                    sectionName = sectionName,
                    subjectId = subjectId,
                    questionText = qText,
                    questionTextHi = item.optString("questionTextHi").takeIf { it.isNotBlank() && it != "null" },
                    options = finalOptions,
                    optionsHi = if (finalOptions.isEmpty()) null else listOf("optionAHi", "optionBHi", "optionCHi", "optionDHi", "optionEHi")
                        .take(finalOptions.size).map { item.optString(it) }
                        .takeIf { values -> values.size == finalOptions.size && values.all { it.isNotBlank() } },
                    correctAnswer = answer,
                    sourceAnswer = sourceAnswer,
                    explanation = explanation,
                    explanationHi = item.optString("explanationHi").takeIf { it.isNotBlank() && it != "null" },
                    difficulty = difficulty,
                    questionType = parsedType,
                    positiveMarks = positiveMarks,
                    negativeMarks = negativeMarks,
                    difficultyScore = validation.difficultyScore,
                    qualityScore = if (requiresReview) minOf(validation.qualityScore, 59) else validation.qualityScore,
                    qualityMetrics = validation.qualityMetrics,
                    validationStatus = if (requiresReview) ValidationStatus.NEEDS_REVIEW else validation.status,
                    validationReasons = reviewReasons,
                    duplicateHash = hash,
                    sourceName = actualSource,
                    sourcePage = item.optInt("sourcePageStart", batchIndex + 1),
                    isOfficialPYQ = item.optBoolean("isOfficialPYQ", false),
                    questionVersion = 1,
                    validationVersion = 1,
                    generationModel = actualModel,
                    generationTimestamp = System.currentTimeMillis()
                )
            )
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
