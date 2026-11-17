package Com.ak.testseries.data.remote

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreHelper {
    @Volatile
    private var cachedInstance: FirebaseFirestore? = null

    fun getInstance(@Suppress("UNUSED_PARAMETER") context: Context? = null): FirebaseFirestore {
        cachedInstance?.let { return it }
        synchronized(this) {
            cachedInstance?.let { return it }
            // FirebaseFirestore.getInstance() always selects the project's (default) database.
            // Do not fall back to a named database if initialization fails.
            val instance = FirebaseFirestore.getInstance()
            cachedInstance = instance
            return instance
        }
    }
}
