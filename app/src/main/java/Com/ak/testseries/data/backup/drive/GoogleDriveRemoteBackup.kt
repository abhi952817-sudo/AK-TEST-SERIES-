package Com.ak.testseries.data.backup.drive

import Com.ak.testseries.data.backup.BackupCompleteness
import Com.ak.testseries.data.backup.BackupStats
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Metadata representation of a verified `.aktsbak` backup archive hosted in
 * the user's private Google Drive application folder.
 *
 * Guarantees zero-trust containment:
 * - Backups are strictly tagged with the user's authenticated Firebase UID.
 * - Restorations and listing reject any foreign account backups.
 */
data class GoogleDriveRemoteBackup(
    val fileId: String,
    val name: String,
    val sizeBytes: Long,
    val createdTimeMillis: Long,
    val modifiedTimeMillis: Long,
    val ownerUid: String,
    val schemaVersion: Int,
    val appVersionName: String = "1.0",
    val checksumSha256: String? = null,
    val stats: BackupStats? = null,
    val completeness: BackupCompleteness = BackupCompleteness.COMPLETE_USER_DATA,
    val driveFolderId: String? = null
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            return sdf.format(Date(if (modifiedTimeMillis > 0) modifiedTimeMillis else createdTimeMillis))
        }

    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            return if (kb >= 1024.0) {
                String.format(Locale.US, "%.1f MB", kb / 1024.0)
            } else {
                String.format(Locale.US, "%.1f KB", kb)
            }
        }

    val statsSummary: String
        get() {
            if (stats == null) return "$name ($formattedSize)"
            return "${stats.privateTestsCount} Tests • ${stats.attemptsCount} Attempts • ${stats.diagramsCount} Diagrams"
        }

    fun toMetadataJson(): JSONObject {
        return JSONObject().apply {
            put("fileId", fileId)
            put("name", name)
            put("sizeBytes", sizeBytes)
            put("createdTimeMillis", createdTimeMillis)
            put("modifiedTimeMillis", modifiedTimeMillis)
            put("ownerUid", ownerUid)
            put("schemaVersion", schemaVersion)
            put("appVersionName", appVersionName)
            put("checksumSha256", checksumSha256 ?: JSONObject.NULL)
            put("completeness", completeness.name)
            stats?.let { put("stats", it.toJsonObject()) }
            driveFolderId?.let { put("driveFolderId", it) }
        }
    }

    companion object {
        fun fromDriveFileJson(fileJson: JSONObject): GoogleDriveRemoteBackup? {
            val fileId = fileJson.optString("id", "").trim()
            val fileName = fileJson.optString("name", "").trim()
            if (fileId.isBlank() || fileName.isBlank()) return null

            val sizeBytes = fileJson.optLong("size", 0L)
            val createdTimeMillis = parseIso8601Timestamp(fileJson.optString("createdTime", ""))
            val modifiedTimeMillis = parseIso8601Timestamp(fileJson.optString("modifiedTime", ""))

            var ownerUid = ""
            var schemaVersion = 19
            var appVersionName = "1.0"
            var checksumSha256: String? = null
            var completeness = BackupCompleteness.COMPLETE_USER_DATA
            var stats: BackupStats? = null

            // Try reading appProperties
            val appProps = fileJson.optJSONObject("appProperties")
            if (appProps != null) {
                ownerUid = appProps.optString("ownerUid", "").trim()
                schemaVersion = appProps.optInt("schemaVersion", 19)
                appVersionName = appProps.optString("appVersionName", "1.0")
                checksumSha256 = appProps.optString("checksumSha256", "").ifBlank { null }
                val compStr = appProps.optString("completeness", "")
                if (compStr.isNotBlank()) {
                    try { completeness = BackupCompleteness.valueOf(compStr) } catch (_: Exception) {}
                }
            }

            // Try reading description JSON if available
            val desc = fileJson.optString("description", "").trim()
            if (desc.startsWith("{") && desc.endsWith("}")) {
                try {
                    val descJson = JSONObject(desc)
                    if (ownerUid.isBlank()) {
                        ownerUid = descJson.optString("ownerUid", "").trim()
                    }
                    if (schemaVersion == 19 && descJson.has("schemaVersion")) {
                        schemaVersion = descJson.optInt("schemaVersion", 19)
                    }
                    if (checksumSha256 == null && descJson.has("checksumSha256")) {
                        checksumSha256 = descJson.optString("checksumSha256", "").ifBlank { null }
                    }
                    if (descJson.has("stats")) {
                        stats = BackupStats.fromJsonObject(descJson.getJSONObject("stats"))
                    }
                } catch (_: Exception) {}
            }

            // Strict Security Rule: Never extract ownerUid from fileName.
            // Filename spoofing cannot be trusted for account ownership.
            // If ownerUid is not in appProperties or description metadata, it remains blank/unverified.

            return GoogleDriveRemoteBackup(
                fileId = fileId,
                name = fileName,
                sizeBytes = sizeBytes,
                createdTimeMillis = createdTimeMillis,
                modifiedTimeMillis = modifiedTimeMillis,
                ownerUid = ownerUid,
                schemaVersion = schemaVersion,
                appVersionName = appVersionName,
                checksumSha256 = checksumSha256,
                stats = stats,
                completeness = completeness
            )
        }

        private fun parseIso8601Timestamp(isoString: String): Long {
            if (isoString.isBlank()) return System.currentTimeMillis()
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                sdf.parse(isoString)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }
                    sdf.parse(isoString)?.time ?: System.currentTimeMillis()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
            }
        }
    }
}
