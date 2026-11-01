package Com.ak.testseries.crash

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Vault for safely persisting sanitized crash metadata in private app storage.
 * Eliminates Binder buffer overflow risk and keeps reports isolated from third-party apps.
 */
object CrashReportVault {

    private const val VAULT_DIR_NAME = "crash_vault"
    private const val MAX_STORED_REPORTS = 5

    private const val PREF_CRASH_LOOP = "crash_loop_sentinel_pref"
    private const val KEY_CRASH_TIMESTAMPS = "recent_crash_timestamps"

    fun saveReport(context: Context, report: CrashSanitizer.SanitizedCrash, isSynced: Boolean = false) {
        try {
            val dir = File(context.cacheDir, VAULT_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val json = JSONObject().apply {
                put("incidentId", report.incidentId)
                put("category", report.category)
                put("safeUserMessage", report.safeUserMessage)
                put("rootExceptionType", report.rootExceptionType)
                put("sanitizedDetails", report.sanitizedDetails)
                put("timestamp", report.timestamp)
                put("isSynced", isSynced)
                put("syncAttempts", 0)
            }

            val file = File(dir, "crash_${report.incidentId}.json")
            file.writeText(json.toString(), Charsets.UTF_8)

            pruneOldReports(dir)
        } catch (_: Throwable) {
            // Failsafe: Never crash while attempting to save a crash record
        }
    }

    fun loadReport(context: Context, incidentId: String?): CrashSanitizer.SanitizedCrash? {
        if (incidentId.isNullOrBlank()) return null
        return try {
            val dir = File(context.cacheDir, VAULT_DIR_NAME)
            val file = File(dir, "crash_$incidentId.json")
            if (!file.exists()) return null

            val json = JSONObject(file.readText(Charsets.UTF_8))
            CrashSanitizer.SanitizedCrash(
                incidentId = json.optString("incidentId", incidentId),
                category = json.optString("category", "Application Stability"),
                safeUserMessage = json.optString("safeUserMessage", "An unexpected state was handled."),
                rootExceptionType = json.optString("rootExceptionType", "UnknownException"),
                sanitizedDetails = json.optString("sanitizedDetails", "Diagnostic details unavailable."),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun loadLatestReport(context: Context): CrashSanitizer.SanitizedCrash? {
        return try {
            val dir = File(context.cacheDir, VAULT_DIR_NAME)
            val files = dir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }
            val latest = files?.firstOrNull() ?: return null
            val json = JSONObject(latest.readText(Charsets.UTF_8))
            CrashSanitizer.SanitizedCrash(
                incidentId = json.optString("incidentId", "UNKNOWN"),
                category = json.optString("category", "Application Stability"),
                safeUserMessage = json.optString("safeUserMessage", "An unexpected state was handled."),
                rootExceptionType = json.optString("rootExceptionType", "UnknownException"),
                sanitizedDetails = json.optString("sanitizedDetails", "Diagnostic details unavailable."),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Retrieves all sanitized crash reports stored on disk that have not yet been synced to remote endpoints.
     */
    fun getPendingReports(context: Context): List<CrashSanitizer.SanitizedCrash> {
        val list = mutableListOf<CrashSanitizer.SanitizedCrash>()
        try {
            val dir = File(context.cacheDir, VAULT_DIR_NAME)
            if (!dir.exists()) return emptyList()

            val files = dir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() } ?: return emptyList()

            for (file in files) {
                try {
                    val json = JSONObject(file.readText(Charsets.UTF_8))
                    val isSynced = json.optBoolean("isSynced", false)
                    if (!isSynced) {
                        list.add(
                            CrashSanitizer.SanitizedCrash(
                                incidentId = json.optString("incidentId", "UNKNOWN"),
                                category = json.optString("category", "Application Stability"),
                                safeUserMessage = json.optString("safeUserMessage", "An unexpected state was handled."),
                                rootExceptionType = json.optString("rootExceptionType", "UnknownException"),
                                sanitizedDetails = json.optString("sanitizedDetails", "Diagnostic details unavailable."),
                                timestamp = json.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                } catch (_: Throwable) {
                    // Skip malformed individual file
                }
            }
        } catch (_: Throwable) {
            // Failsafe
        }
        return list
    }

    /**
     * Marks a specific crash incident report as successfully synchronized.
     */
    fun markReportSynced(context: Context, incidentId: String) {
        if (incidentId.isBlank()) return
        try {
            val dir = File(context.cacheDir, VAULT_DIR_NAME)
            val file = File(dir, "crash_$incidentId.json")
            if (file.exists()) {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                json.put("isSynced", true)
                file.writeText(json.toString(), Charsets.UTF_8)
            }
        } catch (_: Throwable) {
            // Ignore
        }
    }

    /**
     * Records a launch crash timestamp and checks if repeated crashes exceeded the loop threshold
     * within the rolling window (e.g., 3 crashes within 60 seconds).
     */
    fun recordCrashAndCheckLoop(
        context: Context,
        maxCrashes: Int = 3,
        windowMillis: Long = 60_000L
    ): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREF_CRASH_LOOP, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val raw = prefs.getString(KEY_CRASH_TIMESTAMPS, "") ?: ""

            val timestamps = raw.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .filter { now - it in 0..windowMillis }
                .toMutableList()

            timestamps.add(now)

            prefs.edit()
                .putString(KEY_CRASH_TIMESTAMPS, timestamps.joinToString(","))
                .apply()

            return timestamps.size >= maxCrashes
        } catch (_: Throwable) {
            return false
        }
    }

    /**
     * Clears the rolling crash loop counter when the app stabilizes or user completes recovery.
     */
    fun resetCrashLoopCounter(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_CRASH_LOOP, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_CRASH_TIMESTAMPS).apply()
        } catch (_: Throwable) {
            // Ignore
        }
    }

    /**
     * Safely clears temporary cache files without touching persistent user credentials.
     */
    fun clearApplicationCache(context: Context): Boolean {
        return try {
            val cache = context.cacheDir
            if (cache != null && cache.exists()) {
                val children = cache.listFiles() ?: emptyArray()
                for (child in children) {
                    // Keep vault dir if possible, delete others
                    if (child.name != VAULT_DIR_NAME) {
                        child.deleteRecursively()
                    }
                }
            }
            context.codeCacheDir?.deleteRecursively()
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun pruneOldReports(dir: File) {
        val files = dir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() } ?: return

        if (files.size > MAX_STORED_REPORTS) {
            files.drop(MAX_STORED_REPORTS).forEach { it.delete() }
        }
    }
}
