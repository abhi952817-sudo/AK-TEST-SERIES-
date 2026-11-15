package Com.ak.testseries.crash

import android.content.Context
import android.util.Log
import androidx.work.*
import Com.ak.testseries.NEETApplication
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker responsible for securely synchronizing offline sanitized
 * crash telemetry to Firebase Crashlytics when network connectivity is restored.
 *
 * Firestore upload is intentionally disabled until crash records carry a stable owner UID
 * captured at incident time and a matching least-privilege server/rules contract exists.
 * A delayed worker must never attribute an old device crash to whichever account is signed in later.
 */
class CrashSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pendingReports = CrashReportVault.getPendingReports(applicationContext)
        if (pendingReports.isEmpty()) {
            return Result.success()
        }

        var allSynced = true

        for (report in pendingReports) {
            var syncedToCrashlytics = false

            // 1. Dual Sync Target A: Firebase Crashlytics custom non-fatal record
            try {
                val crashlytics = NEETApplication.getSafeCrashlytics()
                if (crashlytics != null) {
                    crashlytics.setCustomKey("incident_id", report.incidentId)
                    crashlytics.setCustomKey("error_category", report.category)
                    crashlytics.setCustomKey("root_exception_type", report.rootExceptionType)
                    crashlytics.setCustomKey("offline_synced", true)
                    crashlytics.setCustomKey("crash_timestamp", report.timestamp)
                    crashlytics.log("CrashSyncWorker: Offline incident synchronized: ${report.incidentId}")
                    crashlytics.recordException(
                        NonFatalReportException(
                            incidentId = report.incidentId,
                            category = report.category,
                            safeMessage = report.safeUserMessage
                        )
                    )
                    syncedToCrashlytics = true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Crashlytics sync failed for ${report.incidentId}: ${e.message}")
            }

            // Only mark the local incident synchronized after Crashlytics accepts it.
            if (syncedToCrashlytics) {
                CrashReportVault.markReportSynced(applicationContext, report.incidentId)
            } else {
                allSynced = false
            }
        }

        return if (allSynced) Result.success() else Result.retry()
    }

    class NonFatalReportException(
        val incidentId: String,
        val category: String,
        safeMessage: String
    ) : Exception("[$incidentId] $category: $safeMessage")

    companion object {
        private const val TAG = "CrashSyncWorker"
        const val UNIQUE_WORK_NAME = "CrashAnalyticsOfflineSync"

        /**
         * Enqueues a background WorkManager task with network connectivity constraint
         * and exponential backoff retry.
         */
        fun scheduleSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncRequest = OneTimeWorkRequestBuilder<CrashSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15,
                        TimeUnit.SECONDS
                    )
                    .addTag("crash_sync")
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    syncRequest
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to enqueue crash sync worker: ${e.message}")
            }
        }
    }
}
