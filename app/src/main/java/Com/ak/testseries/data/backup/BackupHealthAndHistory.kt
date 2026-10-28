package Com.ak.testseries.data.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * Health status indicating whether user's account data is safely backed up.
 */
enum class BackupHealthStatus {
    GOOD,
    WARNING,
    CRITICAL
}

data class BackupHealth(
    val status: BackupHealthStatus,
    val daysSinceLastBackup: Int?,
    val title: String,
    val message: String
)

/**
 * Historical record of a successful backup export.
 */
data class BackupExportRecord(
    val timestamp: Long,
    val fileName: String,
    val statsSummary: String,
    val totalBytes: Long,
    val isComplete: Boolean
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("fileName", fileName)
        put("statsSummary", statsSummary)
        put("totalBytes", totalBytes)
        put("isComplete", isComplete)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): BackupExportRecord {
            return BackupExportRecord(
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                fileName = json.optString("fileName", "backup.aktsbak"),
                statsSummary = json.optString("statsSummary", ""),
                totalBytes = json.optLong("totalBytes", 0L),
                isComplete = json.optBoolean("isComplete", true)
            )
        }

        fun serializeList(records: List<BackupExportRecord>): String {
            val array = JSONArray()
            records.forEach { array.put(it.toJsonObject()) }
            return array.toString()
        }

        fun deserializeList(jsonStr: String?): List<BackupExportRecord> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonStr)
                (0 until array.length()).map { fromJsonObject(array.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

/**
 * Evaluates the safety health of the user's local data based on the elapsed time since last backup
 * and the amount of active, unpublished vault data.
 */
fun evaluateBackupHealth(
    lastBackupTimestamp: Long?,
    eligibleVaultTestsCount: Int,
    attemptsCount: Int,
    isHindi: Boolean
): BackupHealth {
    if (lastBackupTimestamp == null) {
        return if (eligibleVaultTestsCount == 0 && attemptsCount == 0) {
            BackupHealth(
                status = BackupHealthStatus.GOOD,
                daysSinceLastBackup = null,
                title = if (isHindi) "डेटा सुरक्षित है" else "No Data to Back Up",
                message = if (isHindi) "आपके पास अभी कोई नया निजी टेस्ट या प्रयास डेटा नहीं है।"
                else "You have no private vault tests or attempt records created yet."
            )
        } else {
            BackupHealth(
                status = BackupHealthStatus.CRITICAL,
                daysSinceLastBackup = null,
                title = if (isHindi) "बैकअप अत्यंत आवश्यक" else "Backup Highly Recommended",
                message = if (isHindi)
                    "आपने अभी तक अपने $eligibleVaultTestsCount निजी टेस्ट और $attemptsCount प्रयासों का कोई बैकअप नहीं बनाया है। तुरंत .aktsbak निर्यात करें।"
                else
                    "You have not backed up your $eligibleVaultTestsCount vault tests and $attemptsCount attempts yet. Create a backup now."
            )
        }
    }

    val elapsedMillis = System.currentTimeMillis() - lastBackupTimestamp
    val days = (elapsedMillis / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)

    return when {
        days <= 7 -> BackupHealth(
            status = BackupHealthStatus.GOOD,
            daysSinceLastBackup = days,
            title = if (isHindi) "बैकअप सुरक्षित व अद्यतित" else "Backup Up to Date",
            message = if (isHindi)
                "आपका डेटा $days दिन पहले सुरक्षित किया गया था। सब कुछ अद्यतित है।"
            else
                "Your account was backed up $days day(s) ago. Your data is well-protected."
        )
        days <= 30 -> BackupHealth(
            status = BackupHealthStatus.WARNING,
            daysSinceLastBackup = days,
            title = if (isHindi) "नया बैकअप अनुशंसित" else "New Backup Recommended",
            message = if (isHindi)
                "अंतिम बैकअप $days दिन पहले लिया गया था। नए टेस्ट और प्रयासों को सुरक्षित रखने के लिए एक ताज़ा बैकअप बनाएं।"
            else
                "Last backup was taken $days days ago. Export a fresh archive to protect recent tests and attempts."
        )
        else -> BackupHealth(
            status = BackupHealthStatus.CRITICAL,
            daysSinceLastBackup = days,
            title = if (isHindi) "बैकअप पुराना हो चुका है" else "Backup Outdated",
            message = if (isHindi)
                "अंतिम बैकअप $days दिन से अधिक पुराना है। डेटा सुरक्षा के लिए कृपया तुरंत नया .aktsbak निर्यात करें।"
            else
                "Last backup is more than $days days old. Please create a new .aktsbak archive immediately."
        )
    }
}

/**
 * Evaluates unified backup health by inspecting both local SAF .aktsbak exports
 * and Google Drive cloud sync timestamps.
 */
fun evaluateUnifiedBackupHealth(
    lastLocalBackupTimestamp: Long?,
    lastCloudSyncTimestamp: Long?,
    eligibleVaultTestsCount: Int,
    attemptsCount: Int,
    isHindi: Boolean
): BackupHealth {
    val mostRecentTimestamp = listOfNotNull(lastLocalBackupTimestamp, lastCloudSyncTimestamp).maxOrNull()
    return evaluateBackupHealth(
        lastBackupTimestamp = mostRecentTimestamp,
        eligibleVaultTestsCount = eligibleVaultTestsCount,
        attemptsCount = attemptsCount,
        isHindi = isHindi
    )
}
