package Com.ak.testseries.data.remote

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Single source of truth for the app's named Firestore database.
 *
 * All Android Firestore access must go through this helper. There is deliberately
 * no fallback to the unrelated (default) Firestore database.
 */
object FirestoreHelper {
    const val DATABASE_ID: String = "ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf"

    @Volatile
    private var cachedInstance: FirebaseFirestore? = null

    fun getInstance(): FirebaseFirestore {
        cachedInstance?.let { return it }
        synchronized(this) {
            cachedInstance?.let { return it }
            val app = FirebaseApp.getInstance()
            val instance = FirebaseFirestore.getInstance(app, DATABASE_ID)
            cachedInstance = instance
            return instance
        }
    }
}
