package Com.ak.testseries.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import Com.ak.testseries.R

object FirestoreHelper {
    @Volatile
    private var cachedInstance: FirebaseFirestore? = null

    fun getInstance(context: Context? = null): FirebaseFirestore {
        cachedInstance?.let { return it }
        synchronized(this) {
            cachedInstance?.let { return it }

            // This app intentionally uses only the Firebase project's (default) database.
            // Fail closed if a resource is accidentally changed to a named database.
            val configuredDatabaseId = context?.getString(R.string.firestore_database_id) ?: "(default)"
            check(configuredDatabaseId == "(default)") {
                "Unexpected Firestore database ID: $configuredDatabaseId. Only (default) is allowed."
            }

            val app = FirebaseApp.getInstance()
            val instance = FirebaseFirestore.getInstance(app)
            cachedInstance = instance
            return instance
        }
    }
}