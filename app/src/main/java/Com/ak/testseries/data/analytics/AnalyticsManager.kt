package Com.ak.testseries.data.analytics

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import Com.ak.testseries.data.remote.FirestoreHelper

/**
 * Account-scoped analytics writer.
 *
 * Analytics is intentionally not written for signed-out/anonymous sessions because
 * Firestore Rules require an authenticated UID matching the event's userId.
 * All Firestore access goes through FirestoreHelper so the app uses the same
 * (default) database as the rest of the Android client.
 */
class AnalyticsManager(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val firestoreProvider: () -> FirebaseFirestore = {
        FirestoreHelper.getInstance()
    }
) {

    private fun logEvent(eventType: String, attributes: Map<String, Any>) {
        val uid = try {
            authProvider().currentUser?.uid
        } catch (e: Exception) {
            null
        } ?: return

        val safeAttributes = attributes.filterValues { value ->
            when (value) {
                is String -> value.isNotBlank() && value.length <= 200
                is Int -> value >= 0
                is Long -> value >= 0L
                is Boolean -> true
                else -> false
            }
        }

        val event = HashMap<String, Any>(safeAttributes).apply {
            put("userId", uid)
            put("eventType", eventType)
            put("timestamp", System.currentTimeMillis())
        }

        try {
            firestoreProvider()
                .collection("User_Analytics")
                .add(event)
                .addOnFailureListener { error ->
                    Log.w(TAG, "Analytics event write failed: $eventType", error)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Analytics event could not be queued: $eventType", e)
        }
    }

    fun logScreenTime(screenName: String, durationSeconds: Int) {
        if (durationSeconds < 0) return
        logEvent(
            "SCREEN_TIME",
            mapOf(
                "screenName" to screenName.take(MAX_FIELD_LENGTH),
                "durationSeconds" to durationSeconds
            )
        )
    }

    fun logFeatureUsage(featureName: String) {
        logEvent(
            "FEATURE_USAGE",
            mapOf("featureName" to featureName.take(MAX_FIELD_LENGTH))
        )
    }

    fun logTestDropOff(testId: String, completed: Boolean) {
        logEvent(
            "TEST_DROP_OFF",
            mapOf(
                "testId" to testId.take(MAX_FIELD_LENGTH),
                "completed" to completed
            )
        )
    }

    fun logWeakSubject(subject: String, errorCount: Int) {
        if (errorCount < 0) return
        logEvent(
            "WEAK_SUBJECT",
            mapOf(
                "subject" to subject.take(MAX_FIELD_LENGTH),
                "errorCount" to errorCount
            )
        )
    }

    private companion object {
        const val TAG = "AnalyticsManager"
        const val MAX_FIELD_LENGTH = 200
    }
}
