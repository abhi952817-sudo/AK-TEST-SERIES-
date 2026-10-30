package Com.ak.testseries.data.pdf

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Production scheduler for background PDF processing.
 * Guarantees singleton worker per session using ExistingWorkPolicy.KEEP.
 */
object PdfProcessingScheduler {

    /**
     * Enqueues a persistent background worker for the given sessionId.
     * If a worker is already running or enqueued for this session, it is KEPT.
     */
    fun scheduleProcessing(context: Context, sessionId: String): Operation {
        val inputData = workDataOf(
            PdfProcessingWorker.KEY_SESSION_ID to sessionId
        )

        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<PdfProcessingWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .addTag("pdf_processing")
            .addTag("session_$sessionId")
            .build()

        return WorkManager.getInstance(context).enqueueUniqueWork(
            PdfProcessingWorker.WORK_NAME_PREFIX + sessionId,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Cooperatively cancels the processing worker for a session.
     */
    fun cancelProcessing(context: Context, sessionId: String): Operation {
        return WorkManager.getInstance(context).cancelUniqueWork(
            PdfProcessingWorker.WORK_NAME_PREFIX + sessionId
        )
    }

    /**
     * Observes WorkInfo status flow for real-time UI tracking.
     */
    fun getWorkInfosFlow(context: Context, sessionId: String): Flow<List<WorkInfo>> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(PdfProcessingWorker.WORK_NAME_PREFIX + sessionId)
            .asFlow()
    }
}
