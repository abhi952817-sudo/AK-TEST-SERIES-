package Com.ak.testseries.data.backup

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Declares the scope of data encapsulated in the backup archive.
 */
enum class BackupScope {
    ACCOUNT_OWNED,
    STRUCTURAL_TEST,
    ASSETS_ONLY
}

/**
 * Distinguishes whether the archive is a structural verification template
 * or a fully packaged, authoritative user-data backup.
 */
enum class BackupCompleteness {
    STRUCTURAL_ONLY,
    COMPLETE_USER_DATA
}

/**
 * Account owner identity metadata.
 * Note: Never includes passwords, tokens, API keys, or credentials.
 */
data class BackupOwner(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("uid", uid)
            put("email", email ?: JSONObject.NULL)
            put("displayName", displayName ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): BackupOwner {
            return BackupOwner(
                uid = json.optString("uid", ""),
                email = if (json.has("email") && !json.isNull("email")) json.getString("email") else null,
                displayName = if (json.has("displayName") && !json.isNull("displayName")) json.getString("displayName") else null
            )
        }
    }
}

/**
 * Summary metrics of entries packaged inside the archive.
 */
data class BackupStats(
    val privateTestsCount: Int = 0,
    val questionsCount: Int = 0,
    val attemptsCount: Int = 0,
    val inProgressCount: Int = 0,
    val bookmarkedCount: Int = 0,
    val diagramsCount: Int = 0,
    val totalArchiveBytes: Long = 0L,
    val uncompressedBytes: Long = 0L,
    val entryCount: Int = 0
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("privateTestsCount", privateTestsCount)
            put("questionsCount", questionsCount)
            put("attemptsCount", attemptsCount)
            put("inProgressCount", inProgressCount)
            put("bookmarkedCount", bookmarkedCount)
            put("diagramsCount", diagramsCount)
            put("totalArchiveBytes", totalArchiveBytes)
            put("uncompressedBytes", uncompressedBytes)
            put("entryCount", entryCount)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): BackupStats {
            return BackupStats(
                privateTestsCount = json.optInt("privateTestsCount", 0),
                questionsCount = json.optInt("questionsCount", 0),
                attemptsCount = json.optInt("attemptsCount", 0),
                inProgressCount = json.optInt("inProgressCount", 0),
                bookmarkedCount = json.optInt("bookmarkedCount", 0),
                diagramsCount = json.optInt("diagramsCount", 0),
                totalArchiveBytes = json.optLong("totalArchiveBytes", 0L),
                uncompressedBytes = json.optLong("uncompressedBytes", 0L),
                entryCount = json.optInt("entryCount", 0)
            )
        }
    }
}

/**
 * Minimum version compatibility metadata.
 */
data class BackupCompatibility(
    val minSupportedAppVersion: Int = 1,
    val minSupportedSchemaVersion: Int = 15
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("minSupportedAppVersion", minSupportedAppVersion)
            put("minSupportedSchemaVersion", minSupportedSchemaVersion)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): BackupCompatibility {
            return BackupCompatibility(
                minSupportedAppVersion = json.optInt("minSupportedAppVersion", 1),
                minSupportedSchemaVersion = json.optInt("minSupportedSchemaVersion", 15)
            )
        }
    }
}

/**
 * The root manifest specification stored in `backup_manifest.json` at the root of every `.aktsbak` archive.
 */
data class BackupManifest(
    val formatVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersionCode: Int = 1,
    val appVersionName: String = "1.0",
    val databaseSchemaVersion: Int = 19,
    val scope: BackupScope = BackupScope.ACCOUNT_OWNED,
    val completeness: BackupCompleteness = BackupCompleteness.STRUCTURAL_ONLY,
    val owner: BackupOwner,
    val stats: BackupStats = BackupStats(),
    val compatibility: BackupCompatibility = BackupCompatibility(),
    val checksums: Map<String, String> = emptyMap()
) {
    fun toJsonString(): String {
        val root = JSONObject().apply {
            put("formatVersion", formatVersion)
            put("createdAt", createdAt)
            put("appVersionCode", appVersionCode)
            put("appVersionName", appVersionName)
            put("databaseSchemaVersion", databaseSchemaVersion)
            put("scope", scope.name)
            put("completeness", completeness.name)
            put("owner", owner.toJsonObject())
            put("stats", stats.toJsonObject())
            put("compatibility", compatibility.toJsonObject())

            val checksumsJson = JSONObject()
            checksums.forEach { (path, hash) ->
                checksumsJson.put(path, hash)
            }
            put("checksums", checksumsJson)
        }
        return root.toString(2)
    }

    companion object {
        fun fromJsonString(jsonString: String): BackupManifest {
            val root = JSONObject(jsonString)
            val ownerObj = root.getJSONObject("owner")
            val statsObj = root.optJSONObject("stats") ?: JSONObject()
            val compatObj = root.optJSONObject("compatibility") ?: JSONObject()
            val checksumsObj = root.optJSONObject("checksums") ?: JSONObject()

            val checksumsMap = mutableMapOf<String, String>()
            val keys = checksumsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                checksumsMap[key] = checksumsObj.getString(key)
            }

            return BackupManifest(
                formatVersion = root.getInt("formatVersion"),
                createdAt = root.optLong("createdAt", System.currentTimeMillis()),
                appVersionCode = root.optInt("appVersionCode", 1),
                appVersionName = root.optString("appVersionName", "1.0"),
                databaseSchemaVersion = root.getInt("databaseSchemaVersion"),
                scope = try {
                    BackupScope.valueOf(root.optString("scope", BackupScope.ACCOUNT_OWNED.name))
                } catch (e: Exception) {
                    BackupScope.ACCOUNT_OWNED
                },
                completeness = try {
                    BackupCompleteness.valueOf(root.optString("completeness", BackupCompleteness.STRUCTURAL_ONLY.name))
                } catch (e: Exception) {
                    BackupCompleteness.STRUCTURAL_ONLY
                },
                owner = BackupOwner.fromJsonObject(ownerObj),
                stats = BackupStats.fromJsonObject(statsObj),
                compatibility = BackupCompatibility.fromJsonObject(compatObj),
                checksums = checksumsMap
            )
        }
    }
}

/**
 * Result of validating an archive container without mutating live data.
 */
sealed class BackupValidationResult {
    data class Valid(
        val manifest: BackupManifest,
        val totalEntries: Int,
        val totalUncompressedBytes: Long,
        val isComplete: Boolean
    ) : BackupValidationResult()

    data class Invalid(
        val reason: String,
        val details: String? = null,
        val manifest: BackupManifest? = null
    ) : BackupValidationResult()
}
