package Com.ak.testseries.data.backup.drive

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Schedules, updates, and manages WorkManager tasks for automatic Google Drive backups.
 */
object GoogleDriveBackupScheduler {

    const val TAG_AUTO_BACKUP = "ak_drive_auto_backup"
    const val TAG_IMMEDIATE_BACKUP = "ak_drive_immediate_backup"

    /**
     * Reconfigures or cancels automatic backup work depending on current user preferences.
     */
    fun scheduleOrUpdate(context: Context) {
        val isConnected = GoogleDriveAuthManager.isDriveConnected(context)
        val frequency = GoogleDriveAuthManager.getAutoBackupFrequency(context)

        if (!isConnected || frequency.equals("OFF", ignoreCase = true)) {
            cancel(context)
            return
        }

        val wifiOnly = GoogleDriveAuthManager.isWifiOnlyRequired(context)
        val chargingOnly = GoogleDriveAuthManager.isChargingOnlyRequired(context)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .setRequiresBatteryNotLow(true)
            .build()

        val intervalDays = when (frequency.uppercase()) {
            "WEEKLY" -> 7L
            else -> 1L // Default DAILY
        }

        val periodicWorkRequest = PeriodicWorkRequestBuilder<GoogleDriveBackupWorker>(
            intervalDays,
            TimeUnit.DAYS,
            1, // flex interval: 1 hour flex
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag(TAG_AUTO_BACKUP)
            .build()

        try {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                GoogleDriveBackupWorker.WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWorkRequest
            )
        } catch (_: Exception) {
            // Context or WorkManager initialization fallback
        }
    }

    /**
     * Enqueues an immediate one-time background backup to Google Drive.
     */
    fun triggerImmediateBackup(context: Context): Operation? {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<GoogleDriveBackupWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(TAG_IMMEDIATE_BACKUP)
            .build()

        return try {
            WorkManager.getInstance(context).enqueueUniqueWork(
                GoogleDriveBackupWorker.WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cancels any scheduled periodic automatic backup work.
     */
    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(GoogleDriveBackupWorker.WORK_NAME_PERIODIC)
        } catch (_: Exception) {
            // Ignore if WorkManager is unavailable
        }
    }
}
