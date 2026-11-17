package Com.ak.testseries.data.remote

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Single source of truth for the app's existing (default) Firestore database.
 *
 * All Android Firestore access must go through this helper so Admin, Student,
 * and other app features use the database that already contains production data.
 */
object FirestoreHelper {
    const val DATABASE_ID: String = "(default)"

    @Volatile
    private var cachedInstance: FirebaseFirestore? = null

    fun getInstance(): FirebaseFirestore {
        cachedInstance?.let { return it }
        synchronized(this) {
            cachedInstance?.let { return it }
            val app = FirebaseApp.getInstance()
            val instance = FirebaseFirestore.getInstance(app)
            cachedInstance = instance
            return instance
        }
    }
}
