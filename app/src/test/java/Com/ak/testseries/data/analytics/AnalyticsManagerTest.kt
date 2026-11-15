package Com.ak.testseries.data.analytics

import org.junit.Assert.assertFalse
import org.junit.Test

class AnalyticsManagerTest {

    @Test
    fun signedOutSessionDoesNotAttemptFirestoreWrite() {
        var firestoreRequested = false
        val manager = AnalyticsManager(
            authProvider = { throw IllegalStateException("Firebase Auth unavailable") },
            firestoreProvider = {
                firestoreRequested = true
                throw AssertionError("Firestore must not be accessed for a signed-out session")
            }
        )

        manager.logScreenTime("Home", 5)
        manager.logFeatureUsage("Community")
        manager.logTestDropOff("test-1", completed = false)
        manager.logWeakSubject("Biology", 2)

        assertFalse("Anonymous analytics must not be sent to Firestore", firestoreRequested)
    }
}
