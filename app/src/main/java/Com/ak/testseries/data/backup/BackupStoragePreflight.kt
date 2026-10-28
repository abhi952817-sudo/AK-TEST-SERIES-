package Com.ak.testseries.data.backup

import android.content.Context
import android.os.StatFs
import java.io.File

/**
 * Result of disk storage pre-flight evaluation before backup archive creation or download.
 */
sealed class StorageCheckResult {
    data class Sufficient(
        val availableBytes: Long,
        val requiredBytes: Long
    ) : StorageCheckResult()

    data class Insufficient(
        val availableBytes: Long,
        val requiredBytes: Long,
        val shortageBytes: Long,
        val message: String
    ) : StorageCheckResult()
}

/**
 * Pre-flight storage validator to prevent out-of-disk crashes (ENOSPC) during
 * local archive generation, Google Drive upload staging, or cloud restore downloads.
 */
object BackupStoragePreflight {

    /**
     * Minimum buffer overhead (15 MB) to ensure the Android system has adequate
     * workspace during archive compression, decompression, and temporary file staging.
     */
    const val MINIMUM_SAFETY_BUFFER_BYTES = 15L * 1024 * 1024 // 15 MB

    /**
     * Default estimated archive size when exact byte size cannot be known beforehand (e.g. before export).
     */
    const val DEFAULT_ESTIMATED_EXPORT_BYTES = 10L * 1024 * 1024 // 10 MB

    /**
     * Queries available bytes on the filesystem hosting the given directory.
     */
    fun getAvailableStorageBytes(directory: File): Long {
        return try {
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val stat = StatFs(directory.path)
            val available = stat.availableBytes
            if (available <= 0L) Long.MAX_VALUE else available
        } catch (_: Exception) {
            // Fallback for mock environments or unusual storage mount
            Long.MAX_VALUE
        }
    }

    /**
     * Validates whether sufficient disk space is available in [context.cacheDir] for creating
     * a local `.aktsbak` archive. Requires at least [estimatedBytes] + [MINIMUM_SAFETY_BUFFER_BYTES].
     */
    fun checkAvailableStorageForExport(
        context: Context,
        estimatedBytes: Long = DEFAULT_ESTIMATED_EXPORT_BYTES,
        targetDirectory: File = context.cacheDir
    ): StorageCheckResult {
        val availableBytes = getAvailableStorageBytes(targetDirectory)
        val requiredBytes = estimatedBytes + MINIMUM_SAFETY_BUFFER_BYTES

        return if (availableBytes >= requiredBytes) {
            StorageCheckResult.Sufficient(availableBytes, requiredBytes)
        } else {
            val shortage = requiredBytes - availableBytes
            val availableMb = availableBytes / (1024 * 1024)
            val requiredMb = requiredBytes / (1024 * 1024)
            StorageCheckResult.Insufficient(
                availableBytes = availableBytes,
                requiredBytes = requiredBytes,
                shortageBytes = shortage,
                message = "Insufficient device storage for backup creation. Available: ${availableMb}MB, Required: ${requiredMb}MB."
            )
        }
    }

    /**
     * Validates whether sufficient disk space is available in [context.cacheDir] for downloading
     * and staging a cloud backup archive of size [archiveSizeBytes].
     */
    fun checkAvailableStorageForDownload(
        context: Context,
        archiveSizeBytes: Long,
        targetDirectory: File = context.cacheDir
    ): StorageCheckResult {
        val availableBytes = getAvailableStorageBytes(targetDirectory)
        // Download requires space for the raw archive + uncompressed staging data + safety buffer
        val requiredBytes = (archiveSizeBytes * 2) + MINIMUM_SAFETY_BUFFER_BYTES

        return if (availableBytes >= requiredBytes) {
            StorageCheckResult.Sufficient(availableBytes, requiredBytes)
        } else {
            val shortage = requiredBytes - availableBytes
            val availableMb = availableBytes / (1024 * 1024)
            val requiredMb = requiredBytes / (1024 * 1024)
            StorageCheckResult.Insufficient(
                availableBytes = availableBytes,
                requiredBytes = requiredBytes,
                shortageBytes = shortage,
                message = "Insufficient device storage to download and stage backup. Available: ${availableMb}MB, Required: ${requiredMb}MB."
            )
        }
    }
}
