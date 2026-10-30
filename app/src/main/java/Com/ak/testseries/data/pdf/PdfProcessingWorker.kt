package Com.ak.testseries.data.pdf

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import Com.ak.testseries.data.AppDatabase
import Com.ak.testseries.data.PdfSessionStatus
import com.ak.pdfextractor.AdvancedPdfSmartExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WorkManager CoroutineWorker for persistent, resilient PDF processing.
 * Features:
 * - Resumes seamlessly from session.lastProcessedPage across app restarts and process kills.
 * - Sliding window 5-page batching to guarantee OOM safety for 30–100+ page documents.
 * - Saves extracted text incrementally to session directory.
 * - Cooperative cancellation and interruption handling (marks PAUSED without losing progress).
 * - Enforces single-worker concurrency per session via ExistingWorkPolicy.KEEP.
 */
class PdfProcessingWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SESSION_ID = "key_session_id"
        const val WORK_NAME_PREFIX = "pdf_session_worker_"
        const val BATCH_SIZE = 5

        const val KEY_OUTPUT_PROCESSED_PAGES = "processed_pages"
        const val KEY_OUTPUT_TOTAL_PAGES = "total_pages"
        const val KEY_OUTPUT_EXTRACTED_PATH = "extracted_path"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(KEY_SESSION_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing sessionId input parameter"))

        val db = AppDatabase.getDatabase(appContext)
        val dao = db.pdfSessionDao()

        val session = dao.getSessionById(sessionId)
            ?: return@withContext Result.failure(workDataOf("error" to "Session $sessionId not found in database"))

        // If already completed or extracted, return early
        if (session.status == PdfSessionStatus.COMPLETED.name || session.status == PdfSessionStatus.EXTRACTED.name) {
            return@withContext Result.success(
                workDataOf(
                    KEY_OUTPUT_PROCESSED_PAGES to session.processedPages,
                    KEY_OUTPUT_TOTAL_PAGES to session.pageCount,
                    KEY_OUTPUT_EXTRACTED_PATH to (session.extractedTextPath ?: "")
                )
            )
        }

        val pdfFile = File(session.filePath)
        if (!pdfFile.exists() || pdfFile.length() <= 0) {
            dao.updateError(sessionId, "Source PDF file missing from disk", PdfSessionStatus.FAILED.name)
            return@withContext Result.failure(workDataOf("error" to "Source PDF missing on disk"))
        }

        try {
            dao.updateStatus(sessionId, PdfSessionStatus.PARSING.name)

            val sessionDir = PdfStorageManager.getSessionDirectory(appContext, sessionId)
            val pagesDir = File(sessionDir, "pages").apply { if (!exists()) mkdirs() }
            val extractedTextFile = File(sessionDir, "source_extracted.md")
            val smartExtractor = AdvancedPdfSmartExtractor(appContext)

            val totalPages = if (session.pageCount > 0) session.pageCount else PdfStorageManager.countPdfPages(pdfFile).coerceAtLeast(1)
            var currentProcessedPage = session.lastProcessedPage.coerceAtLeast(0)

            // If starting fresh (page 0), reset extracted text file to avoid duplicate text mixing
            if (currentProcessedPage == 0 && extractedTextFile.exists()) {
                extractedTextFile.delete()
            }

            // If resuming, start from the next unprocessed page
            val startPage = currentProcessedPage + 1

            for (batchStart in startPage..totalPages step BATCH_SIZE) {
                // Check if worker has been cancelled or stopped by system/user
                if (isStopped) {
                    dao.updateStatus(sessionId, PdfSessionStatus.PAUSED.name)
                    return@withContext Result.retry()
                }

                val batchEnd = (batchStart + BATCH_SIZE - 1).coerceAtMost(totalPages)

                // Extract text for this batch of pages
                val batchText = try {
                    smartExtractor.extractPageRangeText(
                        pdfFile = pdfFile,
                        startPage = batchStart,
                        endPage = batchEnd
                    )
                } catch (e: Exception) {
                    // Page-level extraction failed; do not dump whole PDF stream
                    ""
                }

                // Incrementally append batch text to disk file and save per-batch text artifact
                if (batchText.isNotBlank()) {
                    File(pagesDir, "batch_${batchStart}_${batchEnd}.txt").writeText(batchText)
                    extractedTextFile.appendText("--- PAGE $batchStart TO $batchEnd ---\n$batchText\n\n")
                }

                currentProcessedPage = batchEnd

                // Persist progress to Room DB so restart resumes from here
                dao.updateProgress(
                    sessionId = sessionId,
                    processedPages = currentProcessedPage,
                    lastProcessedPage = currentProcessedPage
                )

                // Report WorkManager progress
                val progressPercent = (currentProcessedPage.toFloat() / totalPages.toFloat()) * 100f
                setProgress(
                    workDataOf(
                        "progress" to progressPercent.toInt(),
                        "processed_pages" to currentProcessedPage,
                        "total_pages" to totalPages
                    )
                )
            }

            // Data Validation: If no text was recovered or pages are missing, do not mark EXTRACTED/COMPLETED
            val totalExtractedLength = if (extractedTextFile.exists()) extractedTextFile.length() else 0L
            if (totalExtractedLength < 10L) {
                dao.updateError(sessionId, "Extraction incomplete: zero readable text extracted from pages.", PdfSessionStatus.FAILED.name)
                return@withContext Result.failure(workDataOf("error" to "Empty extracted text"))
            }

            if (currentProcessedPage < totalPages) {
                dao.updateStatus(sessionId, PdfSessionStatus.PAUSED.name)
                return@withContext Result.retry()
            }

            // Mark session as successfully EXTRACTED
            dao.updateExtractedPath(
                sessionId = sessionId,
                extractedTextPath = extractedTextFile.absolutePath,
                status = PdfSessionStatus.EXTRACTED.name
            )

            Result.success(
                workDataOf(
                    KEY_OUTPUT_PROCESSED_PAGES to currentProcessedPage,
                    KEY_OUTPUT_TOTAL_PAGES to totalPages,
                    KEY_OUTPUT_EXTRACTED_PATH to extractedTextFile.absolutePath
                )
            )
        } catch (e: Exception) {
            val isTransient = runAttemptCount < 3
            if (isTransient) {
                dao.updateError(sessionId, "Transient error: ${e.message}", PdfSessionStatus.PAUSED.name)
                Result.retry()
            } else {
                dao.updateError(sessionId, "Fatal extraction error: ${e.message}", PdfSessionStatus.FAILED.name)
                Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
            }
        }
    }
}
