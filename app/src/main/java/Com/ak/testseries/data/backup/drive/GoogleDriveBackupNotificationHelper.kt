package Com.ak.testseries.data.backup.drive

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Handles notification channels and user notifications for automatic and background
 * Google Drive backup events in AK TEST SERIES.
 */
object GoogleDriveBackupNotificationHelper {

    const val CHANNEL_ID = "ak_drive_backup_channel"
    private const val CHANNEL_NAME = "Drive Backup & Sync"
    private const val CHANNEL_DESCRIPTION = "Notifications for automatic and background Google Drive backup status."

    private const val NOTIFICATION_ID_SUCCESS = 7101
    private const val NOTIFICATION_ID_FAILURE = 7102
    private const val NOTIFICATION_ID_PROGRESS = 7103

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showBackupSuccessNotification(context: Context, statsSummary: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Cloud Backup Completed")
            .setContentText(statsSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your tests, attempts, and syllabus progress have been securely backed up to Google Drive.\n$statsSummary"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SUCCESS, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked or not granted
        }
    }

    fun showBackupFailureNotification(context: Context, errorMessage: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Cloud Backup Failed")
            .setContentText(errorMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Automatic Google Drive backup could not be completed: $errorMessage"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FAILURE, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked or not granted
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
