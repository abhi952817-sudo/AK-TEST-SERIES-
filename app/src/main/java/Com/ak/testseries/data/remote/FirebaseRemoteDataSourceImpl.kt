package Com.ak.testseries.data.remote

import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.domain.source.AppConfig
import Com.ak.testseries.domain.source.RemoteDataSource
import Com.ak.testseries.domain.source.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRemoteDataSourceImpl : RemoteDataSource {

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    override fun observeTests(): Flow<List<TestEntity>> = callbackFlow {
        if (firestore == null) {
            close()
            return@callbackFlow
        }
        
        val testsList = mutableListOf<TestEntity>()
        
        // Canonical listener for Tests_Data collection
        val testsListener = firestore!!.collection("Tests_Data").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val newTests = snapshot.documents.mapNotNull { doc ->
                    try {
                        TestEntity(
                            id = doc.id,
                            title = doc.getString("title") ?: "Unknown Test",
                            description = doc.getString("description") ?: "",
                            institute = doc.getString("institute") ?: "Unknown Institute",
                            batchName = doc.getString("batchName") ?: "Unknown Batch",
                            facultyName = doc.getString("facultyName") ?: "Unknown Faculty",
                            language = doc.getString("language") ?: "English",
                            subject = doc.getString("subject") ?: "Unknown Subject",
                            difficulty = doc.getString("difficulty") ?: "Medium",
                            testType = doc.getString("testType") ?: "Mock Test",
                            questionsCount = doc.getLong("questionsCount")?.toInt() ?: 0,
                            isOfficial = doc.getBoolean("isOfficial") ?: true,
                            isVerifiedCommunity = doc.getBoolean("isVerifiedCommunity") ?: true,
                            countsTowardRanking = doc.getBoolean("countsTowardRanking") ?: true,
                            durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0,
                            classLevel = doc.getString("classLevel") ?: "11th",
                            chapter = doc.getString("chapter") ?: "",
                            topic = doc.getString("topic") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            imageUrl = doc.getString("imageUrl"),
                            questionsJson = doc.getString("questionsJson")
                        )
                    } catch (e: Exception) { null }
                }
                
                // Merge and emit
                testsList.removeAll { it.testType != "Live" }
                testsList.addAll(newTests)
                trySend(testsList.toList())
            }
        }
        
        // Listener for LiveTests collection
        val liveTestsListener = firestore!!.collection("LiveTests").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val newLiveTests = snapshot.documents.mapNotNull { doc ->
                    try {
                        TestEntity(
                            id = doc.id,
                            title = doc.getString("title") ?: "Unknown Live Test",
                            description = "Live Scheduled Test on " + (doc.getString("scheduledTime") ?: ""),
                            institute = "Admin Live",
                            batchName = doc.getString("classLevel") ?: "11th",
                            facultyName = "Admin",
                            language = "English",
                            subject = doc.getString("subject") ?: "Unknown Subject",
                            difficulty = "Medium",
                            testType = "Live",
                            questionsCount = doc.getLong("questionsCount")?.toInt() ?: 0,
                            isOfficial = true,
                            isVerifiedCommunity = true,
                            countsTowardRanking = doc.getBoolean("countsTowardRanking") ?: true,
                            classLevel = doc.getString("classLevel") ?: "11th",
                            chapter = doc.getString("chapter") ?: "",
                            topic = "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            imageUrl = null,
                            questionsJson = doc.getString("questionsJson")
                        )
                    } catch (e: Exception) { null }
                }
                
                // Merge and emit
                testsList.removeAll { it.testType == "Live" }
                testsList.addAll(newLiveTests)
                trySend(testsList.toList())
            }
        }
        
        awaitClose {
            testsListener.remove()
            liveTestsListener.remove()
        }
    }

    override suspend fun getUserProfile(userId: String): UserProfile {
        return try {
            if (firestore == null) {
                return UserProfile(id = userId, name = "Student Profile", email = "")
            }
            val snapshot = firestore!!.collection("Users_Data").document(userId).get().await()
            if (snapshot.exists()) {
                UserProfile(
                    id = snapshot.id,
                    name = snapshot.getString("name") ?: "Student Profile",
                    email = snapshot.getString("email") ?: "",
                    isPremium = snapshot.getBoolean("isPremium") ?: false
                )
            } else {
                UserProfile(id = userId, name = "Student Profile", email = "")
            }
        } catch (e: Exception) {
            UserProfile(id = userId, name = "Student Profile", email = "")
        }
    }

    override suspend fun getAppConfig(): AppConfig {
        return try {
            if (firestore == null) {
                return AppConfig(latestVersion = "1.0.0", maintenanceMode = false)
            }
            val snapshot = firestore!!.collection("Config").document("app_config").get().await()
            if (snapshot.exists()) {
                AppConfig(
                    latestVersion = snapshot.getString("latestVersion") ?: "1.0.0",
                    maintenanceMode = snapshot.getBoolean("maintenanceMode") ?: false,
                    forceUpdate = snapshot.getBoolean("forceUpdate") ?: false
                )
            } else {
                AppConfig(latestVersion = "1.0.0", maintenanceMode = false)
            }
        } catch (e: Exception) {
            AppConfig(latestVersion = "1.0.0", maintenanceMode = false)
        }
    }
}
