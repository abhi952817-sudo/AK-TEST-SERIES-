package Com.ak.testseries.data.analytics

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AnalyticsManager {

    private val firestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val currentUserUid: String
        get() = try {
            FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous_user"
        } catch (e: Exception) {
            "anonymous_user"
        }

    fun logScreenTime(screenName: String, durationSeconds: Int) {
        val event = hashMapOf(
            "userId" to currentUserUid,
            "eventType" to "SCREEN_TIME",
            "screenName" to screenName,
            "durationSeconds" to durationSeconds,
            "timestamp" to System.currentTimeMillis()
        )
        try {
            firestore?.collection("User_Analytics")?.add(event)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logFeatureUsage(featureName: String) {
        val event = hashMapOf(
            "userId" to currentUserUid,
            "eventType" to "FEATURE_USAGE",
            "featureName" to featureName,
            "timestamp" to System.currentTimeMillis()
        )
        try {
            firestore?.collection("User_Analytics")?.add(event)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logTestDropOff(testId: String, completed: Boolean) {
        val event = hashMapOf(
            "userId" to currentUserUid,
            "eventType" to "TEST_DROP_OFF",
            "testId" to testId,
            "completed" to completed,
            "timestamp" to System.currentTimeMillis()
        )
        try {
            firestore?.collection("User_Analytics")?.add(event)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logWeakSubject(subject: String, errorCount: Int) {
        val event = hashMapOf(
            "userId" to currentUserUid,
            "eventType" to "WEAK_SUBJECT",
            "subject" to subject,
            "errorCount" to errorCount,
            "timestamp" to System.currentTimeMillis()
        )
        try {
            firestore?.collection("User_Analytics")?.add(event)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
