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
            val dbId = try {
                context?.getString(R.string.firestore_database_id)
            } catch (e: Exception) {
                null
            } ?: "ai-studio-android-aktestse-9fa19369-81c5-4d42-bf79-ab4f9f40d4bf"

            val instance = try {
                val app = FirebaseApp.getInstance()
                if (dbId.isNotBlank() && dbId != "(default)") {
                    FirebaseFirestore.getInstance(app, dbId)
                } else {
                    FirebaseFirestore.getInstance(app)
                }
            } catch (e: Exception) {
                try {
                    FirebaseFirestore.getInstance()
                } catch (e2: Exception) {
                    throw e2
                }
            }
            cachedInstance = instance
            return instance
        }
    }
}
