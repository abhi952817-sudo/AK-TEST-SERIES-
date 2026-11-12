package Com.ak.testseries.data.repository

import Com.ak.testseries.domain.auth.AuthUser
import Com.ak.testseries.domain.repository.UserRepository
import Com.ak.testseries.domain.repository.UserDataBackup
import Com.ak.testseries.data.AttemptEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseUserRepositoryImpl : UserRepository {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            Com.ak.testseries.data.remote.FirestoreHelper.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun syncUserToDatabase(authUser: AuthUser): Result<Unit> {
        return try {
            if (firestore == null) {
                return Result.failure(Exception("Firebase Firestore is not initialized"))
            }
            val userRef = firestore!!.collection("Users_Data").document(authUser.uid)
            val snapshot = userRef.get().await()

            if (!snapshot.exists()) {
                // Create new user
                val userData = hashMapOf(
                    "uid" to authUser.uid,
                    "name" to authUser.name,
                    "email" to authUser.email,
                    "totalTestsAttempted" to 0,
                    "averageScore" to 0.0,
                    "joinedAt" to com.google.firebase.Timestamp.now()
                )
                userRef.set(userData).await()
            } else {
                // Update existing user's basic info
                val updateData = mapOf(
                    "name" to authUser.name,
                    "email" to authUser.email
                )
                userRef.update(updateData).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun backupUserData(uid: String, attempts: List<AttemptEntity>, bookmarkedTests: List<String>, bookmarkedQuestions: List<String>): Result<Unit> {
        return try {
            if (firestore == null) return Result.failure(Exception("Firestore not initialized"))
            val userRef = firestore!!.collection("Users_Data").document(uid)
            val updateData = mapOf(
                "bookmarkedTests" to bookmarkedTests,
                "bookmarkedQuestions" to bookmarkedQuestions,
                "lastBackupAt" to com.google.firebase.Timestamp.now()
            )
            userRef.update(updateData).await()
            
            val attemptsCollection = userRef.collection("attempts")
            for (attempt in attempts) {
                val token = attempt.attemptSessionToken.trim()
                if (attempt.verifiedServerSide) {
                    // Server-verified attempts already have one authoritative document keyed by session token.
                    // Remove any old offline mirror rather than writing a second copy over the server record.
                    if (token.isNotBlank()) attemptsCollection.document("offline_$token").delete().await()
                    continue
                }

                // Offline/unverified attempts use a separate prefixed document ID and can never
                // overwrite the backend's authoritative attempt document.
                val offlineDocumentId = if (token.isNotBlank()) {
                    "offline_$token"
                } else {
                    "offline_${attempt.testId}_${attempt.dateTaken}"
                }
                val offlineRecord = hashMapOf<String, Any>(
                    "userId" to uid,
                    "testId" to attempt.testId,
                    "testTitle" to attempt.testTitle,
                    "score" to attempt.score,
                    "maxScore" to attempt.maxScore,
                    "percentile" to attempt.percentile,
                    "accuracy" to attempt.accuracy,
                    "correctCount" to attempt.correctCount,
                    "incorrectCount" to attempt.incorrectCount,
                    "skippedCount" to attempt.skippedCount,
                    "timeSpentSeconds" to attempt.timeSpentSeconds,
                    "dateTaken" to attempt.dateTaken,
                    "answersJson" to attempt.answersJson,
                    "attemptSessionToken" to token,
                    "verifiedServerSide" to false,
                    "countsTowardRanking" to false,
                    "submissionStatus" to "OFFLINE"
                )
                attemptsCollection.document(offlineDocumentId)
                    .set(offlineRecord, com.google.firebase.firestore.SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreUserData(uid: String): Result<UserDataBackup> {
        return try {
            if (firestore == null) return Result.failure(Exception("Firestore not initialized"))
            val userRef = firestore!!.collection("Users_Data").document(uid)
            val snapshot = userRef.get().await()
            
            val bookmarkedTests = snapshot.get("bookmarkedTests") as? List<String> ?: emptyList()
            val bookmarkedQuestions = snapshot.get("bookmarkedQuestions") as? List<String> ?: emptyList()
            
            val attemptsCollection = userRef.collection("attempts").get().await()
            val attemptsBySession = linkedMapOf<String, AttemptEntity>()
            for (doc in attemptsCollection.documents) {
                try {
                    val testId = doc.getString("testId") ?: continue
                    val token = doc.getString("attemptSessionToken").orEmpty()
                    val testTitle = doc.getString("testTitle") ?: ""
                    val score = (doc.get("authoritativeScore") as? Number)?.toDouble()
                        ?: (doc.get("score") as? Number)?.toDouble()
                        ?: 0.0
                    val maxScore = (doc.get("maxScore") as? Number)?.toDouble() ?: 0.0
                    val percentile = (doc.get("percentile") as? Number)?.toDouble() ?: 0.0
                    val accuracy = (doc.get("accuracy") as? Number)?.toDouble() ?: 0.0
                    val correctCount = (doc.get("correctCount") as? Number)?.toInt() ?: 0
                    val incorrectCount = (doc.get("incorrectCount") as? Number)?.toInt() ?: 0
                    val skippedCount = (doc.get("skippedCount") as? Number)?.toInt() ?: 0
                    val timeSpentSeconds = (doc.get("timeSpentSeconds") as? Number)?.toLong() ?: 0L
                    val dateTaken = (doc.get("dateTaken") as? Number)?.toLong() ?: 0L
                    val answersJson = doc.getString("answersJson") ?: ""
                    val verified = doc.getBoolean("verifiedServerSide") == true
                    val submissionStatus = doc.getString("submissionStatus") ?: if (verified) "CONFIRMED" else "OFFLINE"

                    val attempt = AttemptEntity(
                        id = 0,
                        userId = uid,
                        testId = testId,
                        testTitle = testTitle,
                        score = score,
                        maxScore = maxScore,
                        percentile = percentile,
                        accuracy = accuracy,
                        correctCount = correctCount,
                        incorrectCount = incorrectCount,
                        skippedCount = skippedCount,
                        timeSpentSeconds = timeSpentSeconds,
                        dateTaken = dateTaken,
                        answersJson = answersJson,
                        attemptSessionToken = token,
                        verifiedServerSide = verified,
                        submissionStatus = submissionStatus
                    )
                    val dedupeKey = token.ifBlank { doc.id }
                    val previous = attemptsBySession[dedupeKey]
                    if (previous == null || (!previous.verifiedServerSide && verified)) {
                        attemptsBySession[dedupeKey] = attempt
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val attempts = attemptsBySession.values.toList()
            
            Result.success(UserDataBackup(attempts, bookmarkedTests, bookmarkedQuestions))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
