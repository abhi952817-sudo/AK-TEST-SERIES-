package Com.ak.testseries.security

/**
 * Pure credential-selection policy. A signed-in non-admin user can only use their own key.
 * Admin-managed keys are available only to a verified Admin when no personal key is configured.
 */
object GeminiApiKeyPolicy {
    const val KEY_ROTATION_INTERVAL_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L

    fun isPersonalKeyFresh(lastUpdatedAtMillis: Long, nowMillis: Long): Boolean =
        lastUpdatedAtMillis > 0L &&
            nowMillis >= lastUpdatedAtMillis &&
            nowMillis - lastUpdatedAtMillis < KEY_ROTATION_INTERVAL_MILLIS

    fun selectKeys(
        uid: String,
        personalKey: String?,
        isVerifiedAdmin: Boolean,
        adminPrimaryKey: String?,
        adminSecondaryKey: String?
    ): List<Pair<Int, String>> {
        if (uid.isBlank()) return emptyList()
        if (!personalKey.isNullOrBlank()) return listOf(2 to personalKey)
        if (!isVerifiedAdmin) return emptyList()

        return buildList {
            if (!adminPrimaryKey.isNullOrBlank()) add(0 to adminPrimaryKey)
            if (!adminSecondaryKey.isNullOrBlank()) add(1 to adminSecondaryKey)
        }
    }
}
