package Com.ak.testseries.data.remote

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Provides the app's single Firestore instance: the project's (default) database.
 * Keep this as the shared entry point so application code cannot accidentally
 * select one of the unused named databases.
 */
object FirestoreHelper {
    @Volatile
    private var cachedInstance: FirebaseFirestore? = null

    @Suppress("UNUSED_PARAMETER")
    fun getInstance(context: Context? = null): FirebaseFirestore {
        cachedInstance?.let { return it }
        return synchronized(this) {
            cachedInstance ?: FirebaseFirestore.getInstance().also { cachedInstance = it }
        }
    }
}
