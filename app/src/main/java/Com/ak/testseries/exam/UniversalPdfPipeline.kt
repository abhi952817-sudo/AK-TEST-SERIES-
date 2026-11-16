package Com.ak.testseries.exam

import android.content.Context
import android.net.Uri
import Com.ak.testseries.utils.CompactMarkdownFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Universal PDF Processing Pipeline
 * Handles PDF -> Text -> Question Detection -> Multi-Stage Validation -> Question Bank insertion.
 * Implements Batch Processing, Cost Control, and Retry/Resume mechanisms.
 * Dynamically injects exam-specific AI instructions, pattern rules, and difficulty ratios.
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
     * Admin PDF entry point. Extracts text, asks the Admin server connector to classify the document,
     * then invokes the matching syllabus or test-coverage prompt. This function
     * returns a reviewable draft only; persistence and publication require Admin UI approval.
     */
    suspend fun processAdminUploadedPdf(
        context: Context,
        pdfUri: Uri,
        examConfig: ExamConfig,
        selectedSubject: String = "",
        approvedSyllabusJson: String = "",
        serverGenerate: (suspend (String, String?) -> String?)? = null
    ): AdminPdfRouteResult = withContext(Dispatchers.IO) {
        if (serverGenerate == null) {
            return@withContext AdminPdfRouteResult("NEEDS_REVIEW", "", null, null, "Admin server AI connector is not configured.")
        }
        suspend fun executeAdminAi(prompt: String, instruction: String?): String? =
            serverGenerate.invoke(prompt, instruction)

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

        val routingResult = executeAdminAi(
            UniversalAIContext.buildPdfDocumentRoutingPrompt(examConfig, documentText),
            UniversalAIContext.buildSystemInstruction(examConfig)
        ) ?: return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, null, null, "Admin AI document classification failed.")

        val routingJson = try { JSONObject(routingResult) } catch (_: Exception) {
            return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, null, null, "Admin AI returned invalid routing JSON.")
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

        val analysisResult = executeAdminAi(analysisPrompt, UniversalAIContext.buildSystemInstruction(examConfig))
            ?: return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, routingJson, null, "Admin AI document processing failed.")
        val analysisJson = try { JSONObject(analysisResult) } catch (_: Exception) {
            return@withContext AdminPdfRouteResult("NEEDS_REVIEW", documentText, routingJson, null, "Admin AI returned invalid analysis JSON.")
        }
        // Never allow model output alone to publish a syllabus or a test.
        analysisJson.put("publishAllowed", false)
        AdminPdfRouteResult("DRAFT_REVIEW", documentText, routingJson, analysisJson)
    }

    /**
     * Retired Gemini-only legacy pipeline. It previously made direct client-side Gemini
     * requests and could synthesize fallback questions when generation failed.
     * Keep a fail-closed compatibility stub until all external references are removed.
     */
    @Deprecated("Use the canonical Student AI provider/ViewModel flow or the Admin server PDF route.")
    @Suppress("UNUSED_PARAMETER")
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
    ): List<UniversalQuestionModel> {
        onProgress(
            BatchProgress(
                totalBatches = 0,
                currentBatch = 0,
                processedQuestions = existingQuestionsCache.size,
                statusMessage = "Legacy Gemini-only PDF generation is disabled. Use the canonical AI provider workflow."
            )
        )
        return emptyList()
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

}
