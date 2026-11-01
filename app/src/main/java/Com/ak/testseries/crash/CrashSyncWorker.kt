package Com.ak.testseries.crash

import android.content.Context
import android.util.Log
import android.os.Build
import androidx.work.*
import com.google.firebase.crashlytics.FirebaseCrashlytics
import Com.ak.testseries.BuildConfig
import Com.ak.testseries.NEETApplication
import Com.ak.testseries.data.remote.FirestoreHelper
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker responsible for securely synchronizing offline sanitized
 * crash telemetry to Firebase Crashlytics and real Firebase Firestore cloud database when network connectivity is restored.
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
            var syncedToFirestore = false

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

            // 2. Dual Sync Target B: Real Firebase Firestore cloud collection
            try {
                syncedToFirestore = dispatchToFirestore(report)
            } catch (e: Throwable) {
                Log.w(TAG, "Firestore sync failed for ${report.incidentId}: ${e.message}")
            }

            // If synced to Crashlytics or Firestore successfully, mark as synchronized
            if (syncedToCrashlytics || syncedToFirestore) {
                CrashReportVault.markReportSynced(applicationContext, report.incidentId)
            } else {
                allSynced = false
            }
        }

        return if (allSynced) Result.success() else Result.retry()
    }

    /**
     * Persists the sanitized crash incident record directly into Firebase Firestore
     * under the 'Crash_Reports' collection.
     */
    private suspend fun dispatchToFirestore(report: CrashSanitizer.SanitizedCrash): Boolean {
        return try {
            val db = FirestoreHelper.getInstance(applicationContext)
            val payload = hashMapOf<String, Any>(
                "incidentId" to report.incidentId,
                "category" to report.category,
                "safeUserMessage" to report.safeUserMessage,
                "rootExceptionType" to report.rootExceptionType,
                "sanitizedDetails" to report.sanitizedDetails,
                "timestamp" to report.timestamp,
                "deviceModel" to Build.MODEL,
                "osVersion" to Build.VERSION.SDK_INT,
                "appVersion" to BuildConfig.VERSION_NAME,
                "syncedAt" to System.currentTimeMillis()
            )
            db.collection("Crash_Reports")
                .document(report.incidentId)
                .set(payload)
                .await()
            true
        } catch (_: Throwable) {
            false
        }
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
