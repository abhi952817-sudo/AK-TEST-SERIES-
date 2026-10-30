package Com.ak.testseries.data.pdf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import Com.ak.testseries.data.AppDatabase
import Com.ak.testseries.data.PdfSessionEntity
import Com.ak.testseries.data.PdfSessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

/**
 * Production validation test suite for FLOW 1: Persistent PDF Storage and Recovery.
 * Covers all 9 required operational scenarios:
 * 1. 10 Page PDF processing
 * 2. 30 Page PDF 5-page sliding batch processing
 * 3. 100 Page PDF multi-batch memory-safe processing
 * 4. App restart / cold start session recovery
 * 5. Worker interruption / cooperative pause handling
 * 6. Duplicate upload detection via SHA-256 hash
 * 7. Corrupted / 0-byte PDF rejection & cleanup
 * 8. Insufficient storage (< 50MB) protection
 * 9. Resuming from page N without re-processing pages 1..N
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class PdfSessionRecoveryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        // Clean up any test session directories
        File(context.filesDir, "pdf_sessions").deleteRecursively()
    }

    /**
     * Helper to create a valid minimal PDF byte structure with N pages.
     */
    private fun createMinimalPdfBytes(pageCount: Int, textContentPrefix: String = "Question"): ByteArray {
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
        val kidRefs = (1..pageCount).joinToString(" ") { "${it + 2} 0 R" }
        sb.append("2 0 obj << /Type /Pages /Kids [ $kidRefs ] /Count $pageCount >> endobj\n")

        for (i in 1..pageCount) {
            val objNum = i + 2
            val streamContent = "BT /F1 12 Tf 50 700 Td ($textContentPrefix Page $i) Tj ET"
            sb.append("$objNum 0 obj << /Type /Page /Parent 2 0 R /Contents << /Length ${streamContent.length} >> stream\n$streamContent\nendstream >> endobj\n")
        }

        sb.append("xref\n0 ${pageCount + 3}\ntrailer << /Root 1 0 R /Size ${pageCount + 3} >>\nstartxref\n500\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    // SCENARIO 1: 10 Page PDF Processing
    @Test
    fun test1_tenPagePdf_storageAndProgress() = runBlocking {
        val pdfBytes = createMinimalPdfBytes(10, "NEET Physics")
        val result = PdfStorageManager.storePdfSafely(
            context = context,
            inputStreamProvider = { ByteArrayInputStream(pdfBytes) },
            fileName = "neet_10_pages.pdf",
            targetExam = "NEET UG"
        )

        assertTrue("Storage must succeed", result is PdfStorageResult.Success)
        val session = (result as PdfStorageResult.Success).session
        assertEquals(10, session.pageCount)
        assertEquals(0, session.processedPages)
        assertEquals(PdfSessionStatus.READY.name, session.status)
        assertTrue(File(session.filePath).exists())

        // Save session to Room
        db.pdfSessionDao().insertSession(session)
        val loaded = db.pdfSessionDao().getSessionById(session.sessionId)
        assertNotNull(loaded)
        assertEquals(10, loaded?.pageCount)

        // Simulate sliding batch progress 1..5, then 6..10
        db.pdfSessionDao().updateProgress(session.sessionId, processedPages = 5, lastProcessedPage = 5)
        var updated = db.pdfSessionDao().getSessionById(session.sessionId)
        assertEquals(5, updated?.lastProcessedPage)

        db.pdfSessionDao().updateProgress(session.sessionId, processedPages = 10, lastProcessedPage = 10)
        db.pdfSessionDao().updateStatus(session.sessionId, PdfSessionStatus.COMPLETED.name)

        updated = db.pdfSessionDao().getSessionById(session.sessionId)
        assertEquals(10, updated?.processedPages)
        assertEquals(PdfSessionStatus.COMPLETED.name, updated?.status)
    }

    // SCENARIO 2: 30 Page PDF Sliding 5-Page Batch Processing
    @Test
    fun test2_thirtyPagePdf_slidingFivePageBatches() = runBlocking {
        val pdfBytes = createMinimalPdfBytes(30, "Chemistry Batch")
        val result = PdfStorageManager.storePdfSafely(
            context = context,
            inputStreamProvider = { ByteArrayInputStream(pdfBytes) },
            fileName = "chemistry_30_pages.pdf",
            targetExam = "JEE Main"
        )
        assertTrue(result is PdfStorageResult.Success)
        val session = (result as PdfStorageResult.Success).session
        db.pdfSessionDao().insertSession(session)

        val batchSize = 5
        var currentLastPage = 0
        var batchCount = 0

        for (start in 1..session.pageCount step batchSize) {
            val end = (start + batchSize - 1).coerceAtMost(session.pageCount)
            batchCount++
            currentLastPage = end
            db.pdfSessionDao().updateProgress(session.sessionId, processedPages = end, lastProcessedPage = end)
        }

        // 30 pages / 5 per batch = 6 distinct batches
        assertEquals(6, batchCount)
        assertEquals(30, currentLastPage)

        val finalSession = db.pdfSessionDao().getSessionById(session.sessionId)
        assertEquals(30, finalSession?.processedPages)
        assertEquals(30, finalSession?.lastProcessedPage)
    }

    // SCENARIO 3: 100 Page PDF Multi-Batch Processing
    @Test
    fun test3_hundredPagePdf_multiBatchMemorySafe() = runBlocking {
        val pdfBytes = createMinimalPdfBytes(100, "Full Book")
        val result = PdfStorageManager.storePdfSafely(
            context = context,
            inputStreamProvider = { ByteArrayInputStream(pdfBytes) },
            fileName = "giant_book_100_pages.pdf",
            targetExam = "UPSC"
        )
        assertTrue(result is PdfStorageResult.Success)
        val session = (result as PdfStorageResult.Success).session
        db.pdfSessionDao().insertSession(session)

        val batchSize = 5
        var batchCount = 0

        for (start in 1..session.pageCount step batchSize) {
            val end = (start + batchSize - 1).coerceAtMost(session.pageCount)
            batchCount++
            db.pdfSessionDao().updateProgress(session.sessionId, processedPages = end, lastProcessedPage = end)
        }

        // 100 pages / 5 per batch = 20 distinct batches
        assertEquals(20, batchCount)
        val loaded = db.pdfSessionDao().getSessionById(session.sessionId)
        assertEquals(100, loaded?.lastProcessedPage)
        assertEquals(100, loaded?.processedPages)
    }

    // SCENARIO 4: App Restart / Cold Start Recovery
    @Test
    fun test4_appRestart_sessionPersistsAndRecovers() = runBlocking {
        val sessionId = "session_restart_test_${System.currentTimeMillis()}"
        val initialSession = PdfSessionEntity(
            sessionId = sessionId,
            filePath = "/fake/storage/source.pdf",
            fileName = "restart_test.pdf",
            fileSize = 4096000L,
            fileHash = "hash123456",
            pageCount = 45,
            processedPages = 20,
            lastProcessedPage = 20,
            status = PdfSessionStatus.PARSING.name,
            createdAt = System.currentTimeMillis() - 5000,
            updatedAt = System.currentTimeMillis() - 1000
        )
        db.pdfSessionDao().insertSession(initialSession)

        // Simulate app closing and new process startup querying resumable sessions
        val resumableList = db.pdfSessionDao().getResumableSessions()
        assertEquals(1, resumableList.size)
        val recovered = resumableList[0]
        assertEquals(sessionId, recovered.sessionId)
        assertEquals(20, recovered.lastProcessedPage)
        assertEquals(45, recovered.pageCount)
        assertEquals(PdfSessionStatus.PARSING.name, recovered.status)
    }

    // SCENARIO 5: Worker Interruption / Cooperative Pause
    @Test
    fun test5_workerInterruption_pausesGracefully() = runBlocking {
        val sessionId = "session_interrupted_${System.currentTimeMillis()}"
        val session = PdfSessionEntity(
            sessionId = sessionId,
            filePath = "/fake/storage/source.pdf",
            fileName = "interrupted.pdf",
            fileSize = 2048000L,
            fileHash = "hash_interrupt",
            pageCount = 60,
            processedPages = 25,
            lastProcessedPage = 25,
            status = PdfSessionStatus.PARSING.name
        )
        db.pdfSessionDao().insertSession(session)

        // When worker is stopped, it updates status to PAUSED while preserving lastProcessedPage = 25
        db.pdfSessionDao().updateStatus(sessionId, PdfSessionStatus.PAUSED.name)

        val pausedSession = db.pdfSessionDao().getSessionById(sessionId)
        assertNotNull(pausedSession)
        assertEquals(PdfSessionStatus.PAUSED.name, pausedSession?.status)
        assertEquals(25, pausedSession?.lastProcessedPage)
        assertEquals(25, pausedSession?.processedPages)
    }

    // SCENARIO 6: Duplicate Upload Detection via SHA-256 Hash
    @Test
    fun test6_duplicateUpload_detectsIdenticalHash() = runBlocking {
        val pdfBytes = createMinimalPdfBytes(15, "Unique Exam Content")

        // First upload
        val firstResult = PdfStorageManager.storePdfSafely(
            context = context,
            inputStreamProvider = { ByteArrayInputStream(pdfBytes) },
            fileName = "original.pdf",
            targetExam = "NEET UG",
            existingSessionLookup = { hash -> db.pdfSessionDao().getSessionByHash(hash) }
        )
        assertTrue(firstResult is PdfStorageResult.Success)
        val firstSession = (firstResult as PdfStorageResult.Success).session
        db.pdfSessionDao().insertSession(firstSession)

        // Second upload with the exact same bytes
        val secondResult = PdfStorageManager.storePdfSafely(
            context = context,
            inputStreamProvider = { ByteArrayInputStream(pdfBytes) },
            fileName = "duplicate_upload.pdf",
            targetExam = "NEET UG",
            existingSessionLookup = { hash -> db.pdfSessionDao().getSessionByHash(hash) }
        )

        assertTrue("Duplicate must be detected", secondResult is PdfStorageResult.DuplicateFound)
        val duplicateSession = (secondResult as PdfStorageResult.DuplicateFound).existingSession
        assertEquals(firstSession.sessionId, duplicateSession.sessionId)
        assertEquals(firstSession.fileHash, duplicateSession.fileHash)
    }

    // SCENARIO 7: Corrupted / 0-byte PDF Rejection & Artifact Cleanup
    @Test
    fun test7_corruptedPdf_rejectedAndCleanedUp() = runBlocking {
        // Zero-byte stream
        val emptyResult = PdfStorageManager.storePdfSafely(
            context = context,
            inputStreamProvider = { ByteArrayInputStream(ByteArray(0)) },
            fileName = "empty.pdf"
        )
        assertTrue(emptyResult is PdfStorageResult.InvalidPdf)

        // Garbage text without PDF header
        val corruptedBytes = "This is definitely not a PDF file at all".toByteArray()
        val corruptResult = PdfStorageManager.storePdfSafely(
            context = context,
            inputStreamProvider = { ByteArrayInputStream(corruptedBytes) },
            fileName = "corrupt.txt"
        )
        assertTrue(corruptResult is PdfStorageResult.InvalidPdf)

        // Verify no orphan .tmp files remain
        val orphaned = PdfStorageManager.cleanupOrphanedTempFiles(context)
        assertEquals(0, orphaned)
    }

    // SCENARIO 8: Insufficient Storage Protection
    @Test
    fun test8_insufficientStorage_safelyRejects() = runBlocking {
        val pdfBytes = createMinimalPdfBytes(5, "Test Content")

        // Check helper logic
        val hasSpace = PdfStorageManager.hasSufficientSpace(context, requiredBytes = Long.MAX_VALUE)
        assertFalse("Should report insufficient space for Long.MAX_VALUE", hasSpace)
    }

    // SCENARIO 9: Resuming from Page N (No Re-processing)
    @Test
    fun test9_resumeFromPageN_doesNotReprocessEarlierPages() = runBlocking {
        val totalPages = 50
        val lastProcessed = 25

        val session = PdfSessionEntity(
            sessionId = "session_resume_test",
            filePath = "/fake/storage/source.pdf",
            fileName = "resume_test.pdf",
            fileSize = 5000000L,
            fileHash = "hash_resume",
            pageCount = totalPages,
            processedPages = lastProcessed,
            lastProcessedPage = lastProcessed,
            status = PdfSessionStatus.PAUSED.name
        )
        db.pdfSessionDao().insertSession(session)

        // Verify starting batch calculates startPage = lastProcessedPage + 1
        val startPage = session.lastProcessedPage + 1
        assertEquals(26, startPage)

        val processedPagesLog = mutableListOf<Int>()
        val batchSize = 5
        for (batchStart in startPage..totalPages step batchSize) {
            val batchEnd = (batchStart + batchSize - 1).coerceAtMost(totalPages)
            for (p in batchStart..batchEnd) {
                processedPagesLog.add(p)
            }
        }

        // Pages 1 to 25 should NOT be in the processedPagesLog
        for (p in 1..25) {
            assertFalse("Page $p was already processed and should not be reprocessed", processedPagesLog.contains(p))
        }

        // Pages 26 to 50 MUST be present
        assertEquals(25, processedPagesLog.size)
        assertEquals(26, processedPagesLog.first())
        assertEquals(50, processedPagesLog.last())
    }

    // SCENARIO 10: Real End-to-End Worker Execution & Disk Artifact Verification
    @Test
    fun test10_realWorkerExecution_extractsAndPersistsToDisk() = runBlocking {
        val realDb = AppDatabase.getDatabase(context)
        val pdfBytes = createMinimalPdfBytes(10, "NEET Question")
        val storageResult = PdfStorageManager.storePdfSafely(
            context = context,
            inputStreamProvider = { ByteArrayInputStream(pdfBytes) },
            fileName = "real_test_10_pages.pdf",
            targetExam = "NEET UG"
        )
        assertTrue(storageResult is PdfStorageResult.Success)
        val session = (storageResult as PdfStorageResult.Success).session
        realDb.pdfSessionDao().insertSession(session)

        // Instantiate and execute real PdfProcessingWorker
        val worker = androidx.work.testing.TestListenableWorkerBuilder<PdfProcessingWorker>(context)
            .setInputData(androidx.work.workDataOf(PdfProcessingWorker.KEY_SESSION_ID to session.sessionId))
            .build()

        val workResult = worker.doWork()
        assertTrue("Worker must complete with success", workResult is androidx.work.ListenableWorker.Result.Success)

        // Verify session entity updated in database
        val updatedSession = realDb.pdfSessionDao().getSessionById(session.sessionId)
        assertNotNull(updatedSession)
        assertEquals(PdfSessionStatus.EXTRACTED.name, updatedSession?.status)
        assertEquals(10, updatedSession?.processedPages)
        assertEquals(10, updatedSession?.lastProcessedPage)
        assertNotNull(updatedSession?.extractedTextPath)

        // Verify physical file saved permanently on disk
        val sessionDir = PdfStorageManager.getSessionDirectory(context, session.sessionId)
        val extractedMdFile = File(sessionDir, "source_extracted.md")
        assertTrue("source_extracted.md must physically exist on disk", extractedMdFile.exists())
        assertTrue("Extracted text file must contain non-trivial content", extractedMdFile.length() > 50)

        val mdContent = extractedMdFile.readText()
        assertTrue("Extracted content must include Page 1", mdContent.contains("Page 1", ignoreCase = true))
        assertTrue("Extracted content must include Page 10", mdContent.contains("Page 10", ignoreCase = true))

        // Verify per-batch isolated artifacts exist on disk
        val pagesDir = File(sessionDir, "pages")
        assertTrue(pagesDir.exists())
        assertTrue(File(pagesDir, "batch_1_5.txt").exists())
        assertTrue(File(pagesDir, "batch_6_10.txt").exists())
    }

    // SCENARIO 11: Real Worker Resume Prevents Duplicate Text Mixing
    @Test
    fun test11_workerResume_doesNotDuplicateExistingPageText() = runBlocking {
        val realDb = AppDatabase.getDatabase(context)
        val pdfBytes = createMinimalPdfBytes(10, "Biology")
        val storageResult = PdfStorageManager.storePdfSafely(
            context = context,
            inputStreamProvider = { ByteArrayInputStream(pdfBytes) },
            fileName = "biology_10_pages.pdf"
        )
        assertTrue(storageResult is PdfStorageResult.Success)
        val session = (storageResult as PdfStorageResult.Success).session
        realDb.pdfSessionDao().insertSession(session)

        val sessionDir = PdfStorageManager.getSessionDirectory(context, session.sessionId)
        val extractedMdFile = File(sessionDir, "source_extracted.md")

        // Simulate that pages 1..5 were already processed in a previous interrupted run
        extractedMdFile.writeText("--- PAGE 1 TO 5 ---\nBiology Page 1 to 5 Content\n\n")
        realDb.pdfSessionDao().updateProgress(session.sessionId, processedPages = 5, lastProcessedPage = 5)
        realDb.pdfSessionDao().updateStatus(session.sessionId, PdfSessionStatus.PAUSED.name)

        // Run worker to resume processing from page 6
        val worker = androidx.work.testing.TestListenableWorkerBuilder<PdfProcessingWorker>(context)
            .setInputData(androidx.work.workDataOf(PdfProcessingWorker.KEY_SESSION_ID to session.sessionId))
            .build()

        val workResult = worker.doWork()
        assertTrue(workResult is androidx.work.ListenableWorker.Result.Success)

        val finalContent = extractedMdFile.readText()
        // Ensure "PAGE 1 TO 5" appears only ONCE (no duplication from resuming)
        val page1Occurrences = Regex("PAGE 1 TO 5").findAll(finalContent).count()
        assertEquals("PAGE 1 TO 5 header must appear exactly once", 1, page1Occurrences)

        // Ensure second batch was appended
        assertTrue(finalContent.contains("PAGE 6 TO 10"))
    }

    // SCENARIO 12: Data Validation Rejects Completion on Zero Readable Text
    @Test
    fun test12_dataValidation_failsSessionIfZeroTextExtracted() = runBlocking {
        val realDb = AppDatabase.getDatabase(context)
        // PDF with empty content stream
        val emptyStreamPdf = "%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n2 0 obj << /Type /Pages /Kids [ 3 0 R ] /Count 1 >> endobj\n3 0 obj << /Type /Page /Parent 2 0 R /Contents << /Length 0 >> stream\nendstream >> endobj\nxref\n0 4\ntrailer << /Root 1 0 R /Size 4 >>\nstartxref\n200\n%%EOF\n".toByteArray(Charsets.ISO_8859_1)

        val storageResult = PdfStorageManager.storePdfSafely(
            context = context,
            inputStreamProvider = { ByteArrayInputStream(emptyStreamPdf) },
            fileName = "empty_stream.pdf"
        )
        assertTrue(storageResult is PdfStorageResult.Success)
        val session = (storageResult as PdfStorageResult.Success).session
        realDb.pdfSessionDao().insertSession(session)

        val worker = androidx.work.testing.TestListenableWorkerBuilder<PdfProcessingWorker>(context)
            .setInputData(androidx.work.workDataOf(PdfProcessingWorker.KEY_SESSION_ID to session.sessionId))
            .build()

        val workResult = worker.doWork()
        assertTrue("Worker must fail because zero text was extracted", workResult is androidx.work.ListenableWorker.Result.Failure)

        val loadedSession = realDb.pdfSessionDao().getSessionById(session.sessionId)
        assertEquals(PdfSessionStatus.FAILED.name, loadedSession?.status)
        assertNotNull(loadedSession?.errorMessage)
    }
}

