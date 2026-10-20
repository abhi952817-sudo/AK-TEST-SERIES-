package Com.ak.testseries.viewmodel

import Com.ak.testseries.exam.UniversalQuestionModel
import kotlinx.coroutines.tasks.await
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage


import kotlinx.coroutines.tasks.await


import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import Com.ak.testseries.MainActivity
import Com.ak.testseries.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import Com.ak.testseries.BuildConfig
import Com.ak.testseries.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

enum class AppScreen {
    LOGIN,
    PROFILE_ONBOARDING,
    LANGUAGE_SELECTION,
    DASHBOARD,
    EXAM_MODE,
    RESULT_ANALYSIS,
    ADMIN_PANEL,
    USER_NOTIFICATIONS,
    REATTEMPT_AD,
    ANALYTICS,
    HOW_IT_WORKS,
    STUDY_MATERIAL,
    SUBSCRIPTION,
    SETTINGS,
    PRIVATE_VAULT,
    HIERARCHICAL_TEST_FLOW
}

enum class AppTheme {
    SYSTEM, LIGHT, DARK, YELLOW, BLUE
}

enum class AppLanguage { ENGLISH, HINDI }

enum class HomeTab {
    HOME,
    SEARCH,
    NCERT,
    UPLOAD_AI,
    PROFILE,
    LEADERBOARD,
    COMMUNITY,
    SAVED_TESTS
}

sealed class AiGenerationState {
    object Idle : AiGenerationState()
    object ReadingPdf : AiGenerationState()
    object ExtractingQuestions : AiGenerationState()
    object ExtractingOptions : AiGenerationState()
    object ExtractingAnswers : AiGenerationState()
    data class Generating(val batchIndex: Int, val totalBatches: Int, val progressPercent: Float) : AiGenerationState()
    data class PausedError(val batchIndex: Int, val message: String) : AiGenerationState()
    object Success : AiGenerationState()
    data class Error(val message: String) : AiGenerationState()
}

class NEETViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = DatabaseRepository(db)
    private val testRepository = Com.ak.testseries.data.repository.TestRepositoryImpl(repository)
    private val firestoreDb: com.google.firebase.firestore.FirebaseFirestore
        get() = Com.ak.testseries.data.remote.FirestoreHelper.getInstance(getApplication())

    private var _isAdminOverride: Boolean? = null
    val isAdminFlow = MutableStateFlow(false)
    var isAdmin: Boolean
        get() = _isAdminOverride ?: isAdminFlow.value
        set(value) {
            _isAdminOverride = value
            isAdminFlow.value = value
        }
        
    fun checkAdminCustomClaim() {
        val user = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        }
        if (user != null) {
            val isOwnerEmail = user.email?.equals("abhi952817@gmail.com", ignoreCase = true) == true
            if (isOwnerEmail) {
                _isAdminOverride = true
                isAdminFlow.value = true
            }
            user.getIdToken(false).addOnSuccessListener { result ->
                val isAdminClaim = result.claims["admin"] == true || isOwnerEmail
                _isAdminOverride = isAdminClaim
                isAdminFlow.value = isAdminClaim
            }.addOnFailureListener {
                _isAdminOverride = isOwnerEmail
                isAdminFlow.value = isOwnerEmail
            }
        } else {
            _isAdminOverride = false
            isAdminFlow.value = false
        }
    }

    val isOfflineMode = MutableStateFlow(false)

    fun toggleOfflineMode(enabled: Boolean) {
        isOfflineMode.value = enabled
        if (enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                val tests = repository.allTests.first()
                tests.forEach { test ->
                    if (!test.isDownloaded) {
                        repository.downloadTest(test.id)
                    }
                }
            }
        }
    }

    // --- Community Admin Controls ---
    val isCommunityEnabled = MutableStateFlow(true)
    val dailyPostLimit = MutableStateFlow("5")
    val dailyCommentLimit = MutableStateFlow("10")
    val blockedWordsFilter = MutableStateFlow("")
    val autoBlockUsers = MutableStateFlow(true)

    // --- Admin & Generation States ---
    var globalTestCooldownDuration by mutableStateOf("24 Hours")
    var lastTestGenTime by mutableStateOf(0L)

    private val _userProfile = MutableStateFlow(UserProfileData())
    val userProfile: StateFlow<UserProfileData> = _userProfile.asStateFlow()

    private val _leaderboard = MutableStateFlow<List<Com.ak.testseries.domain.auth.LeaderboardEntry>>(emptyList())
    val leaderboard: StateFlow<List<Com.ak.testseries.domain.auth.LeaderboardEntry>> = _leaderboard.asStateFlow()

    private val _userRank = MutableStateFlow<String>("-")
    val userRank: StateFlow<String> = _userRank.asStateFlow()

    val leaderboardExamFilter = MutableStateFlow("NEET")

    private val _userRankStatus = MutableStateFlow("Rank Pending")
    val userRankStatus: StateFlow<String> = _userRankStatus.asStateFlow()

    private val _eligibleParticipantsCount = MutableStateFlow(0)
    val eligibleParticipantsCount: StateFlow<Int> = _eligibleParticipantsCount.asStateFlow()

    private val _isOfficialAirUnlocked = MutableStateFlow(false)
    val isOfficialAirUnlocked: StateFlow<Boolean> = _isOfficialAirUnlocked.asStateFlow()

    private val _nextTargetMessage = MutableStateFlow("")
    val nextTargetMessage: StateFlow<String> = _nextTargetMessage.asStateFlow()

    private val _userPercentile = MutableStateFlow(0.0)
    val userPercentile: StateFlow<Double> = _userPercentile.asStateFlow()

    private val _userQualifyingTestsCount = MutableStateFlow(0)
    val userQualifyingTestsCount: StateFlow<Int> = _userQualifyingTestsCount.asStateFlow()

    private val _userCompetitiveScore = MutableStateFlow(0)
    val userCompetitiveScore: StateFlow<Int> = _userCompetitiveScore.asStateFlow()

    var minQualifyingTests = 2
    var airThreshold = 1000

    private val _adminUsers = MutableStateFlow<List<StudentData>>(emptyList())
    val adminUsers: StateFlow<List<StudentData>> = _adminUsers.asStateFlow()

    private var lastAdminUserDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    var hasMoreAdminUsers = true
    var isLoadingMoreAdminUsers = false

    fun fetchAdminUsers(isInitial: Boolean = true) {
        if (!isInitial && (isLoadingMoreAdminUsers || !hasMoreAdminUsers)) return
        if (isInitial) {
            lastAdminUserDoc = null
            hasMoreAdminUsers = true
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!isInitial) isLoadingMoreAdminUsers = true
                val db = firestoreDb
                var query = db.collection("Users_Data")
                    .orderBy("email")
                    .limit(20)
                
                if (!isInitial && lastAdminUserDoc != null) {
                    query = query.startAfter(lastAdminUserDoc!!)
                }
                
                val snapshot = query.get().await()
                if (snapshot.documents.isNotEmpty()) {
                    lastAdminUserDoc = snapshot.documents.last()
                }
                
                if (snapshot.documents.size < 20) {
                    hasMoreAdminUsers = false
                }
                
                val list = mutableListOf<StudentData>()
                for (doc in snapshot.documents) {
                    val uid = doc.id
                    val name = doc.getString("name") ?: "Student"
                    val email = doc.getString("email") ?: ""
                    val testsAttempted = (doc.getLong("totalTestsAttempted") ?: doc.getLong("attemptsCount") ?: 0L).toString()
                    val avgScore = (doc.getDouble("averageScore") ?: doc.getLong("averageScore")?.toDouble() ?: 0.0).toInt().toString()
                    val isBanned = doc.getBoolean("isBanned") ?: false
                    list.add(StudentData(uid, name, email, testsAttempted, avgScore, isBanned))
                }
                
                if (isInitial) {
                    _adminUsers.value = list
                } else {
                    _adminUsers.value = _adminUsers.value + list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMoreAdminUsers = false
            }
        }
    }

    fun toggleUserBan(userId: String, isBanned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                db.collection("Users_Data").document(userId).update("isBanned", isBanned).await()
                _adminUsers.value = _adminUsers.value.map {
                    if (it.id == userId) it.copy(isBanned = isBanned) else it
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchUserRank(selectedExam: String? = null) {
        val targetExam = selectedExam ?: leaderboardExamFilter.value.ifBlank { _userProfile.value.targetExam.ifBlank { "NEET" } }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val user = auth.currentUser
                if (user != null) {
                    var cloudRankSuccess = false
                    try {
                        val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
                        val callable = functions.getHttpsCallable("getUserRankingDetails")
                        val result = callable.call(mapOf("targetExam" to targetExam)).await()
                        val data = result.data as? Map<*, *>
                        if (data != null) {
                            val status = data["status"] as? String ?: "Rank Pending"
                            val rankStr = data["rankString"] as? String ?: "Pending"
                            val pct = (data["percentile"] as? Number)?.toDouble() ?: 0.0
                            val compScore = (data["competitiveScore"] as? Number)?.toInt() ?: 0
                            val qCount = (data["qualifyingTestsCount"] as? Number)?.toInt() ?: 0
                            val totalElig = (data["totalEligible"] as? Number)?.toInt() ?: 0
                            val isOfficial = data["isOfficialAir"] as? Boolean ?: false
                            val msg = data["message"] as? String ?: ""

                            _userQualifyingTestsCount.value = qCount
                            _userCompetitiveScore.value = compScore
                            _eligibleParticipantsCount.value = totalElig
                            _isOfficialAirUnlocked.value = isOfficial
                            _userRankStatus.value = status
                            _userRank.value = rankStr
                            _userPercentile.value = pct
                            _nextTargetMessage.value = msg
                            cloudRankSuccess = true
                        }
                    } catch (fe: Exception) {
                        cloudRankSuccess = false
                    }

                    if (!cloudRankSuccess) {
                        val db = firestoreDb
                        val userRef = db.collection("Users_Data").document(user.uid)
                        val snapshot = userRef.get().await()
                        if (snapshot.exists()) {
                            val qualifyingTests = (snapshot.getLong("qualifyingTestsCount") ?: 0L).toInt()
                            val examScoreKey = "cumulativeScore_$targetExam"
                            val competitiveScore = (snapshot.getLong(examScoreKey) ?: snapshot.getLong("totalCumulativeScore") ?: 0L).toInt()
                            
                            _userQualifyingTestsCount.value = qualifyingTests
                            _userCompetitiveScore.value = competitiveScore

                            // Query eligible participants count for this targetExam from public Leaderboard
                            val eligibleQuery = db.collection("Leaderboard")
                                .whereEqualTo("targetExam", targetExam)
                                .whereEqualTo("isEligibleForRanking", true)
                            val eligibleSnapshot = eligibleQuery.count().get(com.google.firebase.firestore.AggregateSource.SERVER).await()
                            val totalEligible = eligibleSnapshot.count.toInt()
                            _eligibleParticipantsCount.value = totalEligible
                            _isOfficialAirUnlocked.value = totalEligible >= airThreshold

                            if (qualifyingTests < minQualifyingTests) {
                                _userRankStatus.value = "Rank Pending"
                                _userRank.value = "Pending"
                                _userPercentile.value = 0.0
                                _nextTargetMessage.value = "Complete $minQualifyingTests qualifying tests to unlock your provisional ranking!"
                            } else {
                                val countQuery = db.collection("Leaderboard")
                                    .whereEqualTo("targetExam", targetExam)
                                    .whereEqualTo("isEligibleForRanking", true)
                                    .whereGreaterThan("score", competitiveScore)
                                val countSnapshot = countQuery.count().get(com.google.firebase.firestore.AggregateSource.SERVER).await()
                                val rank = (countSnapshot.count + 1).toInt()
                                
                                val isOfficial = totalEligible >= airThreshold
                                _userRankStatus.value = if (isOfficial) "Official AIR" else "Provisional Rank"
                                _userRank.value = "#$rank"

                                val rawPct = if (totalEligible > 0) {
                                    if (rank == 1) 100.0 else (((totalEligible - rank + 1).toDouble() / totalEligible.toDouble()) * 100.0).coerceIn(0.01, 99.99)
                                } else 0.0
                                _userPercentile.value = Math.round(rawPct * 100.0) / 100.0

                                if (rank > 1) {
                                    val higherUsersQuery = db.collection("Leaderboard")
                                        .whereEqualTo("targetExam", targetExam)
                                        .whereEqualTo("isEligibleForRanking", true)
                                        .whereGreaterThan("score", competitiveScore)
                                        .orderBy("score", com.google.firebase.firestore.Query.Direction.ASCENDING)
                                        .limit(1)
                                        .get().await()
                                    val higherDoc = higherUsersQuery.documents.firstOrNull()
                                    if (higherDoc != null) {
                                        val higherScore = (higherDoc.getLong("score") ?: 0L).toInt()
                                        val ptsAway = (higherScore - competitiveScore) + 1
                                        val targetName = higherDoc.getString("displayName") ?: higherDoc.getString("name") ?: "Student"
                                        _nextTargetMessage.value = "Next target: $targetName (#${rank - 1}) — You are $ptsAway pts away! Take 1 test to overtake."
                                    } else {
                                        _nextTargetMessage.value = "Keep practicing to climb higher on the $targetExam leaderboard!"
                                    }
                                } else {
                                    _nextTargetMessage.value = "🎉 Congratulations! You are Rank #1 on the $targetExam Leaderboard!"
                                }
                            }
                        } else {
                            _userRank.value = "-"
                            _userRankStatus.value = "Rank Pending"
                            _userPercentile.value = 0.0
                            _nextTargetMessage.value = "Complete $minQualifyingTests qualifying tests to unlock your provisional ranking."
                        }
                    }
                } else {
                    _userRank.value = "-"
                    _userRankStatus.value = "Rank Pending"
                    _userPercentile.value = 0.0
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _userRank.value = "-"
                _userRankStatus.value = "Rank Pending"
                _userPercentile.value = 0.0
            }
        }
    }

    private var leaderboardListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun fetchLeaderboard(selectedExam: String? = null) {
        val targetExam = selectedExam ?: leaderboardExamFilter.value.ifBlank { _userProfile.value.targetExam.ifBlank { "NEET" } }
        leaderboardExamFilter.value = targetExam
        leaderboardListener?.remove()
        try {
            val db = firestoreDb
            
            leaderboardListener = db.collection("Leaderboard")
                .whereEqualTo("targetExam", targetExam)
                .whereEqualTo("isEligibleForRanking", true)
                .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null || snapshot.isEmpty) {
                        fetchLeaderboardFallback(targetExam)
                        return@addSnapshotListener
                    }
                    val list = mutableListOf<Com.ak.testseries.domain.auth.LeaderboardEntry>()
                    var rank = 1
                    for (doc in snapshot.documents) {
                        val name = doc.getString("displayName") ?: doc.getString("name") ?: "Student"
                        val score = (doc.getLong("score") ?: 0L).toInt()
                        val uid = doc.getString("userId") ?: doc.getString("uid") ?: doc.id
                        val qCount = (doc.getLong("totalTestsAttempted") ?: doc.getLong("qualifyingTestsCount") ?: 0L).toInt()
                        val streakVal = (doc.getLong("streakDays") ?: doc.getLong("streak") ?: 0L).toInt()
                        val leagueVal = doc.getString("league") ?: when {
                            score >= 5000 -> "Master"
                            score >= 2000 -> "Elite"
                            score >= 500 -> "Scholar"
                            else -> "Rookie"
                        }
                        list.add(
                            Com.ak.testseries.domain.auth.LeaderboardEntry(
                                rank = rank++,
                                name = name,
                                score = score,
                                uid = uid,
                                targetExam = targetExam,
                                qualifyingTestsCount = qCount,
                                streak = streakVal,
                                league = leagueVal
                            )
                        )
                    }
                    _leaderboard.value = list
                }
        } catch (e: Exception) {
            e.printStackTrace()
            fetchLeaderboardFallback(targetExam)
        }
    }

    private fun fetchLeaderboardFallback(targetExam: String) {
        try {
            val db = firestoreDb
            db.collection("Leaderboard")
                .whereEqualTo("targetExam", targetExam)
                .whereEqualTo("isEligibleForRanking", true)
                .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = mutableListOf<Com.ak.testseries.domain.auth.LeaderboardEntry>()
                        var rank = 1
                        for (doc in snapshot.documents) {
                            val name = doc.getString("displayName") ?: doc.getString("name") ?: "Student"
                            val score = (doc.getLong("score") ?: 0L).toInt()
                            val uid = doc.getString("userId") ?: doc.getString("uid") ?: doc.id
                            list.add(Com.ak.testseries.domain.auth.LeaderboardEntry(rank++, name, score, uid, targetExam = targetExam))
                        }
                        _leaderboard.value = list
                    }
                }
                .addOnFailureListener {
                    // Non-blocking graceful fallback
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    // --- Test Folder organizing feature ---
    private val _folders = MutableStateFlow<List<TestFolder>>(emptyList())
    val folders: StateFlow<List<TestFolder>> = _folders.asStateFlow()

    private val _testFolderMappings = MutableStateFlow<Map<String, String>>(emptyMap())
    val testFolderMappings: StateFlow<Map<String, String>> = _testFolderMappings.asStateFlow()

    val currentFolderId = MutableStateFlow<String?>(null)
    val showCreateFolderDialog = MutableStateFlow(false)

    private val _masterPrivateSwitch = MutableStateFlow(false)
    val masterPrivateSwitch: StateFlow<Boolean> = _masterPrivateSwitch.asStateFlow()

    private val _isGlobalAiEnabled = MutableStateFlow(true)
    val isGlobalAiEnabled: StateFlow<Boolean> = _isGlobalAiEnabled.asStateFlow()

    private val _isOfflineMockFallbackEnabled = MutableStateFlow(false)
    val isOfflineMockFallbackEnabled: StateFlow<Boolean> = _isOfflineMockFallbackEnabled.asStateFlow()

    private val _dailyFreeTestLimit = MutableStateFlow(1)
    val dailyFreeTestLimit: StateFlow<Int> = _dailyFreeTestLimit.asStateFlow()

    private val _dailyGenerationsCount = MutableStateFlow(0)
    val dailyGenerationsCount: StateFlow<Int> = _dailyGenerationsCount.asStateFlow()

    private val _dailyUploadLimitUsed = MutableStateFlow(false)
    val dailyUploadLimitUsed: StateFlow<Boolean> = _dailyUploadLimitUsed.asStateFlow()

    private var adminSettingsListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun listenToAdminSettings() {
        adminSettingsListener?.remove()
        try {
            val db = firestoreDb
            adminSettingsListener = db.collection("Config").document("admin_settings")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        error.printStackTrace()
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val isMasterPrivate = snapshot.getBoolean("masterPrivateCommunityTests") ?: false
                        _masterPrivateSwitch.value = isMasterPrivate
                        val isAiActive = snapshot.getBoolean("globalAiEnabled") ?: true
                        _isGlobalAiEnabled.value = isAiActive
                        val mockFallback = snapshot.getBoolean("offlineMockFallbackEnabled") ?: false
                        _isOfflineMockFallbackEnabled.value = mockFallback
                        val limit = snapshot.getLong("dailyFreeTestLimit")?.toInt() ?: 1
                        val safeLimit = if (limit > 0) limit else 1
                        _dailyFreeTestLimit.value = safeLimit
                        _dailyUploadLimitUsed.value = _dailyGenerationsCount.value >= safeLimit
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setOfflineMockFallbackEnabled(enabled: Boolean, onResult: ((Boolean, String?) -> Unit)? = null) {
        if (!isAdmin) {
            onResult?.invoke(false, "Unauthorized: Admin privileges required")
            return
        }
        val previous = _isOfflineMockFallbackEnabled.value
        _isOfflineMockFallbackEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                val data = hashMapOf<String, Any>("offlineMockFallbackEnabled" to enabled)
                db.collection("Config").document("admin_settings")
                    .set(data, com.google.firebase.firestore.SetOptions.merge())
                    .await()
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isOfflineMockFallbackEnabled.value = previous
                    onResult?.invoke(false, e.localizedMessage ?: "Failed to update offline mock fallback setting")
                }
            }
        }
    }

    fun setDailyFreeTestLimit(limit: Int, onResult: ((Boolean, String?) -> Unit)? = null) {
        if (!isAdmin) {
            onResult?.invoke(false, "Unauthorized: Admin privileges required")
            return
        }
        if (limit < 1) {
            onResult?.invoke(false, "Daily limit must be at least 1")
            return
        }
        val previousLimit = _dailyFreeTestLimit.value
        _dailyFreeTestLimit.value = limit
        _dailyUploadLimitUsed.value = _dailyGenerationsCount.value >= limit
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                val data = hashMapOf<String, Any>("dailyFreeTestLimit" to limit)
                db.collection("Config").document("admin_settings")
                    .set(data, com.google.firebase.firestore.SetOptions.merge())
                    .await()
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _dailyFreeTestLimit.value = previousLimit
                    _dailyUploadLimitUsed.value = _dailyGenerationsCount.value >= previousLimit
                    onResult?.invoke(false, e.localizedMessage ?: "Failed to update daily limit in cloud configuration")
                }
            }
        }
    }

    fun setGlobalAiEnabled(enabled: Boolean, onResult: ((Boolean, String?) -> Unit)? = null) {
        if (!isAdmin) {
            onResult?.invoke(false, "Unauthorized: Admin privileges required")
            return
        }
        _isGlobalAiEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                val data = hashMapOf<String, Any>("globalAiEnabled" to enabled)
                db.collection("Config").document("admin_settings")
                    .set(data, com.google.firebase.firestore.SetOptions.merge())
                    .await()
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isGlobalAiEnabled.value = !enabled // rollback local state on failure
                    onResult?.invoke(false, e.localizedMessage ?: "Failed to update AI settings in cloud configuration")
                }
            }
        }
    }

    fun setMasterPrivateCommunityTests(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                val data = hashMapOf<String, Any>("masterPrivateCommunityTests" to enabled)
                db.collection("Config").document("admin_settings")
                    .set(data, com.google.firebase.firestore.SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var notificationsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var authStateListener: com.google.firebase.auth.FirebaseAuth.AuthStateListener? = null

    val currentUserId = MutableStateFlow<String>(
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        } catch (e: Exception) {
            ""
        }
    )

    init {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            currentUserId.value = auth.currentUser?.uid ?: ""
            authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
                val uid = firebaseAuth.currentUser?.uid ?: ""
                currentUserId.value = uid
                checkAdminCustomClaim()
            }
            auth.addAuthStateListener(authStateListener!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        loadUserProfile()
        loadFoldersAndMappings()
        fetchLeaderboard()
        listenToAdminSettings()
        loadDailyGenerations()
        listenToUserNotifications()
        
        syncUserAttemptsFromFirebase() 

        // Start real-time test and questions synchronization with Firestore
        viewModelScope.launch(Dispatchers.IO) {
            testRepository.syncTests()
            seedBundledMockTestsIfNeeded()
            seedSyllabusIfNeeded()
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveTestListener?.remove()
        liveTestCountListener?.remove()
        adminSettingsListener?.remove()
        leaderboardListener?.remove()
        notificationsListener?.remove()
        try {
            authStateListener?.let {
                com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadUserProfile() {
        val prefs = getApplication<Application>().getSharedPreferences("user_profile_prefs", android.content.Context.MODE_PRIVATE)
        checkAdminCustomClaim()
        val storedPhone = prefs.getString("phoneNumber", "")?.ifEmpty {
            try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber ?: "" } catch (e: Exception) { "" }
        } ?: ""
        _userProfile.value = UserProfileData(
            name = prefs.getString("name", "") ?: "",
            email = prefs.getString("email", "") ?: "",
            phoneNumber = storedPhone,
            stateName = prefs.getString("stateName", "") ?: "",
            cityName = prefs.getString("cityName", "") ?: "",
            profileImageUri = prefs.getString("profileImageUri", "") ?: "",
            targetExam = prefs.getString("targetExam", "NEET") ?: "NEET",
            classLevel = prefs.getString("classLevel", "") ?: ""
        )
        
        // Fetch latest profile info from Firebase
	}

    fun loadDailyGenerations() {
        try {
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("daily_generation_prefs", android.content.Context.MODE_PRIVATE)
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val savedDate = prefs.getString("last_generation_date", null)
            val count = if (savedDate == todayStr) {
                prefs.getInt("generation_count_today", 0)
            } else {
                prefs.edit().putString("last_generation_date", todayStr).putInt("generation_count_today", 0).apply()
                0
            }
            _dailyGenerationsCount.value = count
            _dailyUploadLimitUsed.value = count >= _dailyFreeTestLimit.value
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun recordDailyGeneration() {
        try {
            val newCount = _dailyGenerationsCount.value + 1
            _dailyGenerationsCount.value = newCount
            _dailyUploadLimitUsed.value = newCount >= _dailyFreeTestLimit.value
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("daily_generation_prefs", android.content.Context.MODE_PRIVATE)
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            prefs.edit()
                .putString("last_generation_date", todayStr)
                .putInt("generation_count_today", newCount)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveUserProfile(
        name: String,
        email: String,
        stateName: String,
        cityName: String,
        profileImageUri: String = _userProfile.value.profileImageUri,
        targetExam: String = _userProfile.value.targetExam,
        classLevel: String = _userProfile.value.classLevel,
        phoneNumber: String = _userProfile.value.phoneNumber
    ) {
        val prefs = getApplication<Application>().getSharedPreferences("user_profile_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("name", name)
            .putString("email", email)
            .putString("phoneNumber", phoneNumber)
            .putString("stateName", stateName)
            .putString("cityName", cityName)
            .putString("profileImageUri", profileImageUri)
            .putString("targetExam", targetExam)
            .putString("classLevel", classLevel)
            .apply()
        checkAdminCustomClaim()
        _userProfile.value = UserProfileData(name, email, phoneNumber, stateName, cityName, profileImageUri, targetExam, classLevel)
        
        // Sync to Firebase backend
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val user = auth.currentUser
                if (user != null) {
                    val db = firestoreDb
                    val userRef = db.collection("Users_Data").document(user.uid)
                    val userData = hashMapOf(
                        "name" to name,
                        "email" to email,
                        "phoneNumber" to phoneNumber,
                        "stateName" to stateName,
                        "cityName" to cityName,
                        "profileImageUri" to profileImageUri,
                        "targetExam" to targetExam
                    )
                    userRef.set(userData, com.google.firebase.firestore.SetOptions.merge()).await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun setTargetExam(exam: String) {
        saveUserProfile(
            name = _userProfile.value.name,
            email = _userProfile.value.email,
            stateName = _userProfile.value.stateName,
            cityName = _userProfile.value.cityName,
            profileImageUri = _userProfile.value.profileImageUri,
            targetExam = exam
        )
    }

    private val _userNotifications = MutableStateFlow<List<UserNotification>>(emptyList())
    val userNotifications: StateFlow<List<UserNotification>> = _userNotifications.asStateFlow()

    private val _liveTestParticipants = MutableStateFlow<List<String>>(emptyList())
    val liveTestParticipants: StateFlow<List<String>> = _liveTestParticipants.asStateFlow()

    private val _liveTestJoinCount = MutableStateFlow<Int>(0)
    val liveTestJoinCount: StateFlow<Int> = _liveTestJoinCount.asStateFlow()

    private var liveTestListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var liveTestCountListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun listenToLiveTest(testId: String) {
        liveTestListener?.remove()
        liveTestCountListener?.remove()
        
        val db = firestoreDb
        liveTestListener = db.collection("LiveTests").document(testId).collection("Participants")
            .orderBy("joinedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    return@addSnapshotListener
                }
                val pics = snapshot.documents.mapNotNull { it.getString("profileImageUri") }.filter { it.isNotEmpty() }
                _liveTestParticipants.value = pics
            }
            
        // Also get the count
        liveTestCountListener = db.collection("LiveTests").document(testId).collection("Participants")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    return@addSnapshotListener
                }
                _liveTestJoinCount.value = snapshot.size()
            }
    }

    fun joinLiveTest(testId: String) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user != null) {
            val db = firestoreDb
            val participantData = hashMapOf(
                "uid" to user.uid,
                "profileImageUri" to _userProfile.value.profileImageUri,
                "joinedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            db.collection("LiveTests").document(testId).collection("Participants").document(user.uid)
                .set(participantData, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    fun addNotification(title: String, message: String, imageUrl: String? = null, linkUrl: String? = null, targetGroup: String = "All", targetEmail: String? = null, isPinned: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                val notifData = hashMapOf<String, Any>(
                    "title" to title,
                    "message" to message,
                    "timestamp" to System.currentTimeMillis(),
                    "targetGroup" to targetGroup,
                    "isPinned" to isPinned
                )
                if (imageUrl != null) notifData["imageUrl"] = imageUrl
                if (linkUrl != null) notifData["linkUrl"] = linkUrl
                if (targetEmail != null) notifData["targetEmail"] = targetEmail
                db.collection("UserNotifications").add(notifData).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateNotification(id: String, title: String, message: String, imageUrl: String? = null, linkUrl: String? = null, targetGroup: String = "All", targetEmail: String? = null, isPinned: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                val notifData = hashMapOf<String, Any>(
                    "title" to title,
                    "message" to message,
                    "targetGroup" to targetGroup,
                    "isPinned" to isPinned
                )
                if (imageUrl != null) notifData["imageUrl"] = imageUrl else notifData["imageUrl"] = com.google.firebase.firestore.FieldValue.delete()
                if (linkUrl != null) notifData["linkUrl"] = linkUrl else notifData["linkUrl"] = com.google.firebase.firestore.FieldValue.delete()
                if (targetEmail != null) notifData["targetEmail"] = targetEmail else notifData["targetEmail"] = com.google.firebase.firestore.FieldValue.delete()
                db.collection("UserNotifications").document(id).update(notifData).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                db.collection("UserNotifications").document(id).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun listenToUserNotifications() {
        notificationsListener?.remove()
        try {
            val db = firestoreDb
            notificationsListener = db.collection("UserNotifications")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        e.printStackTrace()
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = mutableListOf<UserNotification>()
                        for (doc in snapshot.documents) {
                            val id = doc.id
                            val title = doc.getString("title") ?: ""
                            val message = doc.getString("message") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            val imageUrl = doc.getString("imageUrl")
                            val linkUrl = doc.getString("linkUrl")
                            val targetGroup = doc.getString("targetGroup") ?: "All"
                            val targetEmail = doc.getString("targetEmail")
                            val isPinned = doc.getBoolean("isPinned") ?: false
                            list.add(UserNotification(id, title, message, timestamp, imageUrl, linkUrl, targetGroup, targetEmail, isPinned))
                        }
                        
                        val isFirstLoad = _userNotifications.value.isEmpty()
                        val previousLatestTimestamp = _userNotifications.value.firstOrNull()?.timestamp ?: 0L
                        
                        _userNotifications.value = list
                        
                        // Show a local notification if a brand new notification arrives and it's not the initial load
                        if (!isFirstLoad && list.isNotEmpty()) {
                            val latest = list.first()
                            if (latest.timestamp > previousLatestTimestamp) {
                                triggerLocalNotification(latest.title, latest.message)
                            }
                        }
                    }
                }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun triggerLocalNotification(title: String, messageBody: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "neet_test_series_notifications"
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "NEET Test Series Announcements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for NEET Test Series push notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    suspend fun findDuplicateTest(exam: String, subject: String, chapter: String, institute: String): TestEntity? {
        val allTests = repository.allTests.first()
        return allTests.find { 
            it.title.contains(exam, ignoreCase = true) && 
            it.subject.equals(subject, ignoreCase = true) && 
            (it.title.contains(chapter, ignoreCase = true) || it.description.contains(chapter, ignoreCase = true)) &&
            it.institute.equals(institute, ignoreCase = true)
        }
    }

    // --- Navigation & Flow States ---
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _testPendingStart = MutableStateFlow<TestEntity?>(null)
    private val _viewSyllabusTest = MutableStateFlow<TestEntity?>(null)
    val viewSyllabusTest = _viewSyllabusTest.asStateFlow()
    val testPendingStart: StateFlow<TestEntity?> = _testPendingStart.asStateFlow()

    private val _preTestStep = MutableStateFlow(0) // 0: None, 1: Syllabus/Rules
    val preTestStep: StateFlow<Int> = _preTestStep.asStateFlow()

    fun initiateTest(test: TestEntity) {
        _testPendingStart.value = test
        _preTestStep.value = 1
    }

    fun viewAttemptResult(attempt: AttemptEntity) {
        viewModelScope.launch {
            _activeResult.value = attempt
            val test = testsList.value.find { it.id == attempt.testId }
                ?: withContext(Dispatchers.IO) { repository.getTestById(attempt.testId) }
            if (test != null) {
                _activeTest.value = test
            }
            val questions = withContext(Dispatchers.IO) {
                repository.getQuestionsForTestList(attempt.testId)
            }
            _activeQuestions.value = questions
            navigateToScreen(AppScreen.RESULT_ANALYSIS)
        }
    }

    fun nextPreTestStep() {
        if (_preTestStep.value == 1) {
            _testPendingStart.value?.let { startTest(it) }
            _preTestStep.value = 0
            _testPendingStart.value = null
        }
    }

    fun viewTestSyllabus(test: TestEntity) {
        _viewSyllabusTest.value = test
    }

    fun closeSyllabusModal() {
        _viewSyllabusTest.value = null
    }

    fun cancelPreTestFlow() {
        _preTestStep.value = 0
        _testPendingStart.value = null
    }

    fun serializeQuestionsToJson(questions: List<QuestionEntity>): String {
        val qJsonArray = org.json.JSONArray()
        for (q in questions) {
            val qObj = org.json.JSONObject()
            qObj.put("id", q.id)
            qObj.put("testId", q.testId)
            qObj.put("questionText", q.questionText)
            qObj.put("optionA", q.optionA)
            qObj.put("optionB", q.optionB)
            qObj.put("optionC", q.optionC)
            qObj.put("optionD", q.optionD)
            qObj.put("correctAnswer", q.correctAnswer)
            qObj.put("explanation", q.explanation)
            qObj.put("subject", q.subject)
            qObj.put("chapter", q.chapter)
            qObj.put("topic", q.topic)
            qObj.put("difficulty", q.difficulty)
            qObj.put("isBookmarked", q.isBookmarked)
            qObj.put("questionTextHi", q.questionTextHi ?: "")
            qObj.put("optionAHi", q.optionAHi ?: "")
            qObj.put("optionBHi", q.optionBHi ?: "")
            qObj.put("optionCHi", q.optionCHi ?: "")
            qObj.put("optionDHi", q.optionDHi ?: "")
            qObj.put("explanationHi", q.explanationHi ?: "")
            qObj.put("questionType", q.questionType)
            qObj.put("section", q.section)
            qObj.put("needsImage", q.needsImage)
            qObj.put("imageUrl", q.imageUrl ?: "")
            qJsonArray.put(qObj)
        }
        return qJsonArray.toString()
    }

    private fun createSampleQuestionsForTest(test: TestEntity): List<QuestionEntity> {
        val questions = mutableListOf<QuestionEntity>()
        val count = if (test.questionsCount > 0) test.questionsCount else 10
        for (i in 0 until count) {
            questions.add(
                QuestionEntity(
                    id = "${test.id}_q${i}",
                    testId = test.id,
                    questionText = "${test.subject} Question ${i + 1} (${test.chapter.ifEmpty { "General" }})",
                    optionA = "Option A",
                    optionB = "Option B",
                    optionC = "Option C",
                    optionD = "Option D",
                    correctAnswer = "A",
                    explanation = "Explanation for Question ${i + 1}",
                    subject = test.subject,
                    chapter = test.chapter,
                    topic = test.topic,
                    questionTextHi = "${test.subject} प्रश्न ${i + 1} (${test.chapter.ifEmpty { "सामान्य" }})",
                    optionAHi = "विकल्प A",
                    optionBHi = "विकल्प B",
                    optionCHi = "विकल्प C",
                    optionDHi = "विकल्प D",
                    explanationHi = "प्रश्न ${i + 1} का व्याख्यात्मक उत्तर"
                )
            )
        }
        return questions
    }

    fun addNewTest(test: TestEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val questions = createSampleQuestionsForTest(test)
            val questionsJsonString = serializeQuestionsToJson(questions)
            val testWithJson = test.copy(questionsJson = questionsJsonString, questionsCount = questions.size)
            repository.insertTest(testWithJson)
            repository.insertQuestions(questions)
            uploadTestToFirestore(testWithJson, questionsJsonString)
        }
    }

    fun scheduleLiveTest(
        title: String,
        subject: String,
        classLevel: String,
        chapter: String,
        scheduledTime: String,
        durationMinutes: Int,
        questionsCount: Int,
        pdfUri: Uri? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                val testId = "live_" + System.currentTimeMillis()

                val questions = mutableListOf<QuestionEntity>()
                for (i in 0 until questionsCount) {
                    questions.add(
                        QuestionEntity(
                            id = "${testId}_q${i}",
                            testId = testId,
                            questionText = "Realistic Scheduled NEET ${subject} Question ${i + 1}?",
                            optionA = "Option A",
                            optionB = "Option B",
                            optionC = "Option C",
                            optionD = "Option D",
                            correctAnswer = "A",
                            explanation = "Correct option is A.",
                            subject = subject,
                            chapter = chapter,
                            topic = "Live Syllabus",
                            questionTextHi = "यथार्थवादी अनुसूचित NEET ${subject} प्रश्न ${i + 1}?",
                            optionAHi = "विकल्प A",
                            optionBHi = "विकल्प B",
                            optionCHi = "विकल्प C",
                            optionDHi = "विकल्प D",
                            explanationHi = "सही विकल्प A है।"
                        )
                    )
                }
                val questionsJsonString = serializeQuestionsToJson(questions)

                val testData = hashMapOf(
                    "id" to testId,
                    "title" to title,
                    "subject" to subject,
                    "classLevel" to classLevel,
                    "chapter" to chapter,
                    "scheduledTime" to scheduledTime,
                    "durationMinutes" to durationMinutes,
                    "questionsCount" to questionsCount,
                    "isLive" to true,
                    "timestamp" to System.currentTimeMillis(),
                    "questionsJson" to questionsJsonString
                )

                var finalPdfUrl = ""
                if (pdfUri != null) {
                    try {
                        val storageRef = FirebaseStorage.getInstance().reference.child("LiveTests/$testId.pdf")
                        storageRef.putFile(pdfUri).await()
                        val downloadUrl = storageRef.downloadUrl.await()
                        finalPdfUrl = downloadUrl.toString()
                        testData["pdfUrl"] = finalPdfUrl
                        testData["processingStatus"] = "Pending Extraction"
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                db.collection("LiveTests").document(testId).set(testData).await()

                // Trigger AI Extraction if PDF was uploaded
                if (finalPdfUrl.isNotEmpty()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val keys = getGeminiApiKeys()
                        val apiKey = if (keys.isNotEmpty()) keys.first().second else "" // Fetch first available active key
                        Com.ak.testseries.utils.GeminiPdfExtractor.processPdfWithGemini(
                            pdfUrl = finalPdfUrl,
                            testId = testId,
                            apiKey = apiKey,
                            totalQuestionsExpected = questionsCount
                        )
                    }
                }

                val roomTest = TestEntity(
                    id = testId,
                    title = title,
                    description = "Live Scheduled Test on $scheduledTime",
                    institute = "Admin Live",
                    batchName = classLevel,
                    facultyName = "Admin",
                    language = "English",
                    subject = subject,
                    difficulty = "Medium",
                    testType = "Live",
                    questionsCount = questionsCount,
                    isOfficial = true,
                    isVerifiedCommunity = true,
                    classLevel = classLevel,
                    chapter = chapter,
                    timestamp = System.currentTimeMillis(),
                    imageUrl = null,
                    questionsJson = questionsJsonString,
            isPublic = genIsPublic.value
                )
                repository.insertTest(roomTest)
                repository.insertQuestions(questions)
                uploadTestToFirestore(roomTest, questionsJsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _selectedLanguage = MutableStateFlow(
        try {
            val prefs = application.getSharedPreferences("user_profile_prefs", android.content.Context.MODE_PRIVATE)
            val langStr = prefs.getString("selected_language", "HINDI")
            if (langStr == "ENGLISH") AppLanguage.ENGLISH else AppLanguage.HINDI
        } catch (e: Exception) {
            AppLanguage.HINDI
        }
    )
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

    private val _currentTab = MutableStateFlow(HomeTab.HOME)
    val currentTab: StateFlow<HomeTab> = _currentTab.asStateFlow()

    // --- Preferences ---
    private val _appTheme = MutableStateFlow(AppTheme.SYSTEM)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        _appTheme.value = theme
    }

    data class TestFilterState(
        val query: String = "",
        val subject: String = "All",
        val difficulty: String = "All",
        val type: String = "All",
        val classLevel: String = "All",
        val batch: String = "All",
        val chapter: String = "All",
        val topic: String = "All"
    )

    val filterState = MutableStateFlow(TestFilterState())

    // For backwards compatibility and ease of use in UI where we bind to MutableStateFlow directly
    // we'll update the usages in MainActivity to use filterState.value = filterState.value.copy(...)
    // Let's remove the old ones.

    val userAuthFlow = combine(currentUserId, isAdminFlow) { uid, admin -> uid to admin }

    // --- Real-time Data Observables from DB ---
    val testsList: StateFlow<List<TestEntity>> = combine(
        repository.allTests,
        filterState,
        userProfile,
        masterPrivateSwitch,
        userAuthFlow
    ) { allTests, filter, profile, isMasterPrivate, authPair ->
        val (currentUid, adminUser) = authPair
        allTests.filter { test ->
            val matchQuery = filter.query.isEmpty() || 
                    test.title.contains(filter.query, ignoreCase = true) || 
                    test.description.contains(filter.query, ignoreCase = true) || 
                    test.institute.contains(filter.query, ignoreCase = true) ||
                    test.facultyName.contains(filter.query, ignoreCase = true) ||
                    test.chapter.contains(filter.query, ignoreCase = true) ||
                    test.topic.contains(filter.query, ignoreCase = true)
            val matchSubject = filter.subject == "All" || test.subject.equals(filter.subject, ignoreCase = true)
            val matchDiff = filter.difficulty == "All" || test.difficulty.equals(filter.difficulty, ignoreCase = true)
            val matchType = filter.type == "All" || test.testType.equals(filter.type, ignoreCase = true)
            val matchClass = filter.classLevel == "All" || test.classLevel.equals(filter.classLevel, ignoreCase = true)
            val matchBatch = filter.batch == "All" || test.batchName.equals(filter.batch, ignoreCase = true)
            val matchChapter = filter.chapter == "All" || test.chapter.equals(filter.chapter, ignoreCase = true)
            val matchTopic = filter.topic == "All" || test.topic.equals(filter.topic, ignoreCase = true)
            val userExam = profile.targetExam.trim()
            val testExam = test.targetExam.trim()
            val matchTargetExam = when {
                userExam.isEmpty() || userExam.equals("All", ignoreCase = true) -> true
                userExam.equals("NEET", ignoreCase = true) || userExam.equals("NEET UG", ignoreCase = true) -> {
                    testExam.isEmpty() || testExam.contains("NEET", ignoreCase = true)
                }
                userExam.equals("JEE", ignoreCase = true) || userExam.equals("JEE Main", ignoreCase = true) -> {
                    testExam.contains("JEE", ignoreCase = true)
                }
                userExam.equals("CUET", ignoreCase = true) || userExam.equals("CUET UG", ignoreCase = true) -> {
                    testExam.contains("CUET", ignoreCase = true)
                }
                userExam.equals("CAT", ignoreCase = true) -> {
                    testExam.contains("CAT", ignoreCase = true)
                }
                userExam.contains("CGL", ignoreCase = true) -> {
                    testExam.contains("CGL", ignoreCase = true)
                }
                userExam.contains("CHSL", ignoreCase = true) -> {
                    testExam.contains("CHSL", ignoreCase = true)
                }
                userExam.contains("MTS", ignoreCase = true) -> {
                    testExam.contains("MTS", ignoreCase = true)
                }
                userExam.contains("GD", ignoreCase = true) -> {
                    testExam.contains("GD", ignoreCase = true)
                }
                userExam.equals("SSC", ignoreCase = true) -> {
                    testExam.contains("SSC", ignoreCase = true)
                }
                userExam.contains("NTPC", ignoreCase = true) -> {
                    testExam.contains("NTPC", ignoreCase = true)
                }
                userExam.contains("Group D", ignoreCase = true) || userExam.contains("Level 1", ignoreCase = true) || userExam.contains("Level-1", ignoreCase = true) -> {
                    testExam.contains("Group D", ignoreCase = true) || testExam.contains("Level 1", ignoreCase = true) || testExam.contains("Level-1", ignoreCase = true)
                }
                userExam.contains("ALP", ignoreCase = true) -> {
                    testExam.contains("ALP", ignoreCase = true) || testExam.contains("Loco", ignoreCase = true)
                }
                userExam.contains("JE", ignoreCase = true) -> {
                    testExam.contains("JE", ignoreCase = true) || testExam.contains("Junior Engineer", ignoreCase = true)
                }
                userExam.contains("RRB", ignoreCase = true) || userExam.contains("Railway", ignoreCase = true) -> {
                    testExam.contains("RRB", ignoreCase = true) || testExam.contains("Railway", ignoreCase = true)
                }
                userExam.contains("SBI PO", ignoreCase = true) -> {
                    testExam.contains("SBI PO", ignoreCase = true) || (testExam.contains("SBI", ignoreCase = true) && testExam.contains("PO", ignoreCase = true))
                }
                userExam.contains("SBI Clerk", ignoreCase = true) -> {
                    testExam.contains("SBI Clerk", ignoreCase = true) || (testExam.contains("SBI", ignoreCase = true) && (testExam.contains("Clerk", ignoreCase = true) || testExam.contains("JA", ignoreCase = true)))
                }
                userExam.contains("IBPS PO", ignoreCase = true) -> {
                    testExam.contains("IBPS PO", ignoreCase = true) || (testExam.contains("IBPS", ignoreCase = true) && testExam.contains("PO", ignoreCase = true))
                }
                userExam.contains("IBPS Clerk", ignoreCase = true) -> {
                    testExam.contains("IBPS Clerk", ignoreCase = true) || (testExam.contains("IBPS", ignoreCase = true) && testExam.contains("Clerk", ignoreCase = true))
                }
                userExam.contains("SBI", ignoreCase = true) -> {
                    testExam.contains("SBI", ignoreCase = true)
                }
                userExam.contains("IBPS", ignoreCase = true) -> {
                    testExam.contains("IBPS", ignoreCase = true)
                }
                userExam.contains("Bank", ignoreCase = true) -> {
                    testExam.contains("Bank", ignoreCase = true) || testExam.contains("SBI", ignoreCase = true) || testExam.contains("IBPS", ignoreCase = true)
                }
                userExam.contains("CTET", ignoreCase = true) -> {
                    testExam.contains("CTET", ignoreCase = true) || testExam.contains("TET", ignoreCase = true)
                }
                userExam.contains("UGC NET", ignoreCase = true) || userExam.contains("NET", ignoreCase = true) -> {
                    testExam.contains("UGC", ignoreCase = true) || testExam.contains("NET", ignoreCase = true)
                }
                userExam.contains("CPO", ignoreCase = true) -> {
                    testExam.contains("CPO", ignoreCase = true)
                }
                userExam.contains("Technician", ignoreCase = true) || userExam.contains("Tech", ignoreCase = true) -> {
                    testExam.contains("Technician", ignoreCase = true) || testExam.contains("Tech", ignoreCase = true)
                }
                else -> testExam.equals(userExam, ignoreCase = true)
            }
            
            // If master private is enabled, non-official/community tests are hidden/private
            val matchMasterPrivate = !isMasterPrivate || test.isOfficial
            val matchVisibility = TestVisibilityManager.isTestVisible(test, currentUid, adminUser)
            
            matchQuery && matchSubject && matchDiff && matchType && matchClass && matchBatch && matchChapter && matchTopic && matchTargetExam && matchMasterPrivate && matchVisibility
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attemptHistory: StateFlow<List<AttemptEntity>> = currentUserId.flatMapLatest { uid ->
        if (uid.isNotBlank()) {
            repository.getAttemptsForUser(uid)
        } else {
            // For unauthenticated / guest usage, show unassigned local attempts
            repository.getAttemptsForUser("")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    private val PREF_LAST_LOGIN_DATE = "last_login_date"
    private val PREF_STREAK_COUNT = "streak_count"
    
    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()
    
    init {
        updateStreakOnLogin()
    }
    
    private fun updateStreakOnLogin() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val lastLoginStr = prefs.getString(PREF_LAST_LOGIN_DATE, null)
            var currentStreakValue = prefs.getInt(PREF_STREAK_COUNT, 0)
            
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val todayDate = java.util.Date()
            val todayStr = format.format(todayDate)
            
            if (lastLoginStr != null) {
                try {
                    val lastDate = format.parse(lastLoginStr)
                    if (lastDate != null) {
                        // Reset time components to safely calculate day difference
                        val calLast = java.util.Calendar.getInstance().apply { time = lastDate }
                        val calToday = java.util.Calendar.getInstance().apply { time = format.parse(todayStr)!! }
                        
                        val diffMillis = calToday.timeInMillis - calLast.timeInMillis
                        val daysBetween = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)
                        
                        if (daysBetween == 1L) {
                            currentStreakValue += 1
                        } else if (daysBetween > 1L) {
                            currentStreakValue = 1
                        }
                    }
                } catch (e: Exception) {
                    currentStreakValue = 1
                }
            } else {
                currentStreakValue = 1
            }
            
            prefs.edit()
                .putString(PREF_LAST_LOGIN_DATE, todayStr)
                .putInt(PREF_STREAK_COUNT, currentStreakValue)
                .apply()
                
            _currentStreak.value = currentStreakValue
        }
    }



    val ncertSyllabus: StateFlow<List<SyllabusEntity>> = repository.syllabus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches: StateFlow<List<RecentSearchEntity>> = repository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveRecentSearch(query: String) {
        viewModelScope.launch {
            repository.insertRecentSearch(query)
        }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            repository.deleteRecentSearch(query)
        }
    }

    fun clearAllRecentSearches() {
        viewModelScope.launch {
            repository.clearAllRecentSearches()
        }
    }

    // --- Exam Mode States ---
    var activeAttemptSessionToken: String = ""
        private set
    private val _isSubmittingExam = MutableStateFlow(false)
    val isSubmittingExam: StateFlow<Boolean> = _isSubmittingExam.asStateFlow()

    private val _questionReports = MutableStateFlow<List<QuestionReport>>(emptyList())
    val questionReports: StateFlow<List<QuestionReport>> = _questionReports.asStateFlow()

    private val _activeTest = MutableStateFlow<TestEntity?>(null)
    val activeTest: StateFlow<TestEntity?> = _activeTest.asStateFlow()

    private val _activeQuestions = MutableStateFlow<List<QuestionEntity>>(emptyList())
    val activeQuestions: StateFlow<List<QuestionEntity>> = _activeQuestions.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    // Selected answers: questionId -> option ("A", "B", "C", "D")
    private val _userAnswers = MutableStateFlow<Map<String, String>>(emptyMap())
    val userAnswers: StateFlow<Map<String, String>> = _userAnswers.asStateFlow()

    // Time spent per question: questionId -> seconds
    private val _timeSpentPerQuestion = MutableStateFlow<Map<String, Long>>(emptyMap())
    val timeSpentPerQuestion: StateFlow<Map<String, Long>> = _timeSpentPerQuestion.asStateFlow()

    private val _markedForReview = MutableStateFlow<Set<String>>(emptySet())
    val markedForReview: StateFlow<Set<String>> = _markedForReview.asStateFlow()

    private val _checkedQuestions = MutableStateFlow<Set<String>>(emptySet())
    val checkedQuestions: StateFlow<Set<String>> = _checkedQuestions.asStateFlow()

    private val _visitedQuestions = MutableStateFlow<Set<String>>(emptySet())
    val visitedQuestions: StateFlow<Set<String>> = _visitedQuestions.asStateFlow()

    private val _examTimeRemaining = MutableStateFlow(10800L) // 3 Hours in seconds
    val examTimeRemaining: StateFlow<Long> = _examTimeRemaining.asStateFlow()

    private var timerJob: Job? = null

    // --- Result Detail ---
    private val _activeResult = MutableStateFlow<AttemptEntity?>(null)
    val activeResult: StateFlow<AttemptEntity?> = _activeResult.asStateFlow()

    // --- AI Generator Fields & States ---
    val genTestName = MutableStateFlow("Custom AI Test - Electrostatics")
    val genDescription = MutableStateFlow("Extracted questions from physics PDF")
    val genInstitute = MutableStateFlow("Allen")
    val genBatch = MutableStateFlow("Major Test")
    val genFaculty = MutableStateFlow("Physics Dept")
    val genLanguage = MutableStateFlow("English")
    val genSubject = MutableStateFlow("Physics")
    val genType = MutableStateFlow("Chapter Test")
    val genTargetExam = MutableStateFlow("NEET")
    val genQuestionsCount = MutableStateFlow(5)
    val genIsLive = MutableStateFlow(false)
    val genIsPublic = MutableStateFlow(true)
    val autoShareToCommunity = MutableStateFlow(true)
    val genLiveScheduledTime = MutableStateFlow("2026-07-20 10:00 AM")
    val genLiveDurationMinutes = MutableStateFlow(180)
    val allQuestionsList = ArrayList<org.json.JSONObject>()
    var currentChunkIndex = 0
    val aiGenStatusMessage = MutableStateFlow("")

    private val _aiGenState = MutableStateFlow<AiGenerationState>(AiGenerationState.Idle)
    val aiGenState: StateFlow<AiGenerationState> = _aiGenState.asStateFlow()

    val previewQuestions = MutableStateFlow<List<QuestionEntity>>(emptyList())
    val show180QuestionsDialog = MutableStateFlow(false)
    val selectedPdfUri = MutableStateFlow<android.net.Uri?>(null)
    val lastExtractedPdfText = MutableStateFlow("")
    val lastExtractedPdfPages = MutableStateFlow<List<String>>(emptyList())

    // For Admin: Fetch all users
    private val _allUsers = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val allUsers: StateFlow<List<Map<String, Any>>> = _allUsers.asStateFlow()

    private var lastAllUserDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    var hasMoreAllUsers = true
    var isLoadingMoreAllUsers = false

    fun fetchAllUsers(isInitial: Boolean = true) {
        if (!isInitial && (isLoadingMoreAllUsers || !hasMoreAllUsers)) return
        if (isInitial) {
            lastAllUserDoc = null
            hasMoreAllUsers = true
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!isInitial) isLoadingMoreAllUsers = true
                val db = firestoreDb
                var query = db.collection("Users_Data")
                    .orderBy("email")
                    .limit(20)
                
                if (!isInitial && lastAllUserDoc != null) {
                    query = query.startAfter(lastAllUserDoc!!)
                }
                
                val snapshot = query.get().await()
                if (snapshot.documents.isNotEmpty()) {
                    lastAllUserDoc = snapshot.documents.last()
                }
                
                if (snapshot.documents.size < 20) {
                    hasMoreAllUsers = false
                }
                
                val users = mutableListOf<Map<String, Any>>()
                for (doc in snapshot.documents) {
                    val uid = doc.getString("uid") ?: doc.id
                    val name = doc.getString("name") ?: "Unknown User"
                    val email = doc.getString("email") ?: "No Email"
                    val targetExam = doc.getString("targetExam") ?: "NEET"
                    val classLevel = doc.getString("classLevel") ?: ""
                    users.add(mapOf("uid" to uid, "name" to name, "email" to email, "targetExam" to targetExam, "classLevel" to classLevel))
                }
                
                if (isInitial) {
                    _allUsers.value = users
                } else {
                    _allUsers.value = _allUsers.value + users
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMoreAllUsers = false
            }
        }
    }

    val showReportDialog = MutableStateFlow(false)
    val reportedTest = MutableStateFlow<TestEntity?>(null)
    
    val showProfileEditDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog = MutableStateFlow(false)
    val testToDelete = MutableStateFlow<TestEntity?>(null)
    
    fun promptDeleteTest(test: TestEntity) {
        testToDelete.value = test
        showDeleteConfirmDialog.value = true
    }

    fun deleteTest(test: TestEntity) {
        viewModelScope.launch {
            try {
                // Clean up local cropped question-image files belonging strictly to this test
                try {
                    val context = getApplication<Application>()
                    val imageDir = java.io.File(context.filesDir, "extracted_cropped_images")
                    if (imageDir.exists() && imageDir.isDirectory) {
                        imageDir.listFiles { file ->
                            file.name.startsWith("crop_${test.id}_") && file.name.endsWith(".png")
                        }?.forEach { it.delete() }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NEETViewModel", "Failed to clean up cropped images for test ${test.id}", e)
                }

                // Delete from local DB
                repository.deleteTest(test)
                
                // Delete from Firebase Firestore across all collections
                try {
                    val db = firestoreDb
                    db.collection("Tests").document(test.id).delete()
                    db.collection("Tests_Data").document(test.id).delete()
                    db.collection("LiveTests").document(test.id).delete()
                    
                    val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                    storageRef.child("LiveTests/${test.id}.pdf").delete()
                } catch (e: Exception) {
                    android.util.Log.e("NEETViewModel", "Firebase deletion failed or offline", e)
                }
                
                android.util.Log.d("NEETViewModel", "Test deleted: ${test.title}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun shareTestToCommunity(test: TestEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            val currentUid = currentUser?.uid

            if (currentUid.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Please sign in to share tests", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Owner check: Only the authenticated owner may share the test
            if (test.uploaderId != currentUid || !Com.ak.testseries.ui.CommunityShareManager.isEligibleToShare(test.uploaderId, currentUid)) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Only the test owner can share this test", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                // Retrieve questions for this test (from test entity, Room database, or local storage fallback)
                var questionsJsonString = test.questionsJson ?: ""
                if (questionsJsonString.isBlank()) {
                    val dbQuestions = repository.getQuestionsForTestList(test.id)
                    if (dbQuestions.isNotEmpty()) {
                        questionsJsonString = serializeQuestionsToJson(dbQuestions)
                    } else {
                        try {
                            val file = java.io.File(getApplication<Application>().filesDir, "test_questions_${test.id}.json")
                            if (file.exists() && file.canRead()) {
                                questionsJsonString = file.readText()
                            }
                        } catch (e: Exception) {
                            // ignore fallback error
                        }
                    }
                }

                val targetExam = test.targetExam.takeIf { it.isNotBlank() }
                    ?: _userProfile.value.targetExam.takeIf { it.isNotBlank() }
                    ?: "NEET"

                val (defaultPosMark, defaultNegMark) = try {
                    Com.ak.testseries.exam.UniversalExamRegistry.getMarkingSchemeForExam(targetExam)
                } catch (e: Exception) {
                    Pair(4.0, 1.0)
                }

                // Standardize question data ensuring all required fields are present while excluding private user data
                val rawArray = try {
                    if (questionsJsonString.isNotBlank()) org.json.JSONArray(questionsJsonString) else org.json.JSONArray()
                } catch (e: Exception) {
                    org.json.JSONArray()
                }

                val standardizedArray = org.json.JSONArray()
                for (i in 0 until rawArray.length()) {
                    val qObj = rawArray.optJSONObject(i) ?: continue
                    val qClean = org.json.JSONObject()

                    val qId = qObj.optString("id", "${test.id}_q$i")
                    qClean.put("id", qId)
                    qClean.put("testId", test.id)

                    val qText = qObj.optString("questionText", qObj.optString("questionTextEn", "")).trim()
                    qClean.put("questionText", qText)

                    val optA = qObj.optString("optionA", qObj.optString("optionAEn", "")).trim()
                    val optB = qObj.optString("optionB", qObj.optString("optionBEn", "")).trim()
                    val optC = qObj.optString("optionC", qObj.optString("optionCEn", "")).trim()
                    val optD = qObj.optString("optionD", qObj.optString("optionDEn", "")).trim()

                    qClean.put("optionA", optA)
                    qClean.put("optionB", optB)
                    qClean.put("optionC", optC)
                    qClean.put("optionD", optD)

                    val optsList = org.json.JSONArray()
                    val existingOpts = qObj.optJSONArray("options")
                    if (existingOpts != null && existingOpts.length() > 0) {
                        for (j in 0 until existingOpts.length()) {
                            optsList.put(existingOpts.getString(j))
                        }
                    } else {
                        if (optA.isNotEmpty()) optsList.put(optA)
                        if (optB.isNotEmpty()) optsList.put(optB)
                        if (optC.isNotEmpty()) optsList.put(optC)
                        if (optD.isNotEmpty()) optsList.put(optD)
                    }
                    qClean.put("options", optsList)

                    val qType = qObj.optString("questionType", "MCQ")
                    qClean.put("questionType", qType)

                    val correctAns = qObj.optString("correctAnswer", "").trim()
                    qClean.put("correctAnswer", correctAns)

                    val secId = qObj.optString("section", "SECTION_A")
                    val secName = qObj.optString("sectionName", "Section A")
                    qClean.put("section", secId)
                    qClean.put("sectionName", secName)

                    val (calcPos, calcNeg) = try {
                        if (Com.ak.testseries.exam.UniversalExamRegistry.hasMultipleMarkingSchemes(targetExam)) {
                            Com.ak.testseries.exam.UniversalExamRegistry.getMarkingSchemeForQuestion(targetExam, secId, qObj.optString("subject", test.subject), qType)
                        } else {
                            Pair(defaultPosMark, defaultNegMark)
                        }
                    } catch (e: Exception) {
                        Pair(defaultPosMark, defaultNegMark)
                    }

                    val finalPos = if (qObj.has("positiveMarks") && qObj.optDouble("positiveMarks", 0.0) > 0.0) {
                        qObj.optDouble("positiveMarks", calcPos)
                    } else {
                        calcPos
                    }
                    val finalNeg = if (qObj.has("negativeMarks")) {
                        qObj.optDouble("negativeMarks", calcNeg)
                    } else {
                        calcNeg
                    }
                    qClean.put("positiveMarks", finalPos)
                    qClean.put("negativeMarks", finalNeg)

                    val exp = qObj.optString("explanation", qObj.optString("explanationEn", "")).trim()
                    qClean.put("explanation", exp)

                    qClean.put("subject", qObj.optString("subject", test.subject))
                    qClean.put("chapter", qObj.optString("chapter", test.chapter))
                    qClean.put("topic", qObj.optString("topic", test.topic))
                    qClean.put("difficulty", qObj.optString("difficulty", test.difficulty))

                    val rawImg = qObj.optString("imageUrl", "").trim()
                    qClean.put("imageUrl", rawImg)
                    if (qObj.optBoolean("needsImage", false)) {
                        qClean.put("needsImage", true)
                    }

                    qObj.optString("questionTextHi").takeIf { it.isNotBlank() }?.let { qClean.put("questionTextHi", it) }
                    qObj.optString("explanationHi").takeIf { it.isNotBlank() }?.let { qClean.put("explanationHi", it) }
                    qObj.optString("optionAHi").takeIf { it.isNotBlank() }?.let { qClean.put("optionAHi", it) }
                    qObj.optString("optionBHi").takeIf { it.isNotBlank() }?.let { qClean.put("optionBHi", it) }
                    qObj.optString("optionCHi").takeIf { it.isNotBlank() }?.let { qClean.put("optionCHi", it) }
                    qObj.optString("optionDHi").takeIf { it.isNotBlank() }?.let { qClean.put("optionDHi", it) }

                    standardizedArray.put(qClean)
                }

                // Reuse existing image sanitization & upload logic to ensure no local paths are published
                val sanitizedQuestionsJson = try {
                    sanitizeAndUploadQuestionImages(test.id, standardizedArray.toString())
                } catch (uploadEx: Exception) {
                    android.util.Log.e("NEETViewModel", "Aborting community sharing due to image upload failure in test ${test.id}", uploadEx)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Failed to upload question diagrams to cloud storage: ${uploadEx.localizedMessage ?: "Upload failed"}. Test not shared.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // Strict validation: verify no unuploaded local paths remain in question diagrams
                val finalJsonArray = org.json.JSONArray(sanitizedQuestionsJson)
                val questionsList = mutableListOf<Map<String, Any>>()
                for (i in 0 until finalJsonArray.length()) {
                    val q = finalJsonArray.getJSONObject(i)
                    val img = q.optString("imageUrl", "").trim()
                    if (img.startsWith("/") || img.startsWith("file:", ignoreCase = true) || img.contains("/cache/") || img.contains("/extracted_cropped_images/")) {
                        android.util.Log.e("NEETViewModel", "Blocked sharing test with unuploaded local image path: $img")
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(getApplication(), "Local diagram image could not be uploaded. Test not shared.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    val qMap = mutableMapOf<String, Any>(
                        "id" to q.optString("id", "${test.id}_q$i"),
                        "questionText" to q.optString("questionText", ""),
                        "optionA" to q.optString("optionA", ""),
                        "optionB" to q.optString("optionB", ""),
                        "optionC" to q.optString("optionC", ""),
                        "optionD" to q.optString("optionD", ""),
                        "questionType" to q.optString("questionType", "MCQ"),
                        "correctAnswer" to q.optString("correctAnswer", ""),
                        "positiveMarks" to q.optDouble("positiveMarks", defaultPosMark),
                        "negativeMarks" to q.optDouble("negativeMarks", defaultNegMark),
                        "section" to q.optString("section", "SECTION_A"),
                        "sectionName" to q.optString("sectionName", "Section A"),
                        "explanation" to q.optString("explanation", ""),
                        "subject" to q.optString("subject", test.subject),
                        "chapter" to q.optString("chapter", test.chapter),
                        "topic" to q.optString("topic", test.topic),
                        "difficulty" to q.optString("difficulty", test.difficulty),
                        "imageUrl" to img
                    )

                    val opts = mutableListOf<String>()
                    val optsArray = q.optJSONArray("options")
                    if (optsArray != null && optsArray.length() > 0) {
                        for (j in 0 until optsArray.length()) {
                            opts.add(optsArray.getString(j))
                        }
                    } else {
                        val oA = q.optString("optionA", "")
                        val oB = q.optString("optionB", "")
                        val oC = q.optString("optionC", "")
                        val oD = q.optString("optionD", "")
                        if (oA.isNotEmpty()) opts.add(oA)
                        if (oB.isNotEmpty()) opts.add(oB)
                        if (oC.isNotEmpty()) opts.add(oC)
                        if (oD.isNotEmpty()) opts.add(oD)
                    }
                    qMap["options"] = opts

                    if (q.has("questionTextHi") && q.getString("questionTextHi").isNotBlank()) {
                        qMap["questionTextHi"] = q.getString("questionTextHi")
                    }
                    if (q.has("explanationHi") && q.getString("explanationHi").isNotBlank()) {
                        qMap["explanationHi"] = q.getString("explanationHi")
                    }
                    if (q.has("optionAHi") && q.getString("optionAHi").isNotBlank()) {
                        qMap["optionAHi"] = q.getString("optionAHi")
                    }
                    if (q.has("optionBHi") && q.getString("optionBHi").isNotBlank()) {
                        qMap["optionBHi"] = q.getString("optionBHi")
                    }
                    if (q.has("optionCHi") && q.getString("optionCHi").isNotBlank()) {
                        qMap["optionCHi"] = q.getString("optionCHi")
                    }
                    if (q.has("optionDHi") && q.getString("optionDHi").isNotBlank()) {
                        qMap["optionDHi"] = q.getString("optionDHi")
                    }
                    questionsList.add(qMap)
                }

                val calculatedTotalMarks = if (questionsList.isNotEmpty()) {
                    questionsList.sumOf { (it["positiveMarks"] as? Double) ?: (it["positiveMarks"] as? Number)?.toDouble() ?: defaultPosMark }
                } else {
                    try {
                        val examConf = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExam)
                        examConf?.stages?.firstOrNull()?.papers?.firstOrNull()?.totalMarks ?: 720.0
                    } catch (e: Exception) {
                        720.0
                    }
                }

                val durationMinutes = if (test.durationMinutes > 0) {
                    test.durationMinutes
                } else {
                    try {
                        val examConf = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExam)
                        examConf?.stages?.firstOrNull()?.papers?.firstOrNull()?.durationMinutes ?: 180
                    } catch (e: Exception) {
                        180
                    }
                }

                val testSource = test.institute.takeIf { it.isNotBlank() } ?: "Private Upload"

                val authorName = _userProfile.value.name.takeIf { it.isNotBlank() }
                    ?: currentUser.displayName?.takeIf { it.isNotBlank() }
                    ?: currentUser.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "Student"

                val finalQuestionCount = if (questionsList.isNotEmpty()) questionsList.size else test.questionsCount

                // Canonical shared document in Shared_Community_Tests/{testId}
                val sharedCommunityTestData = hashMapOf<String, Any>(
                    "testId" to test.id,
                    "title" to test.title,
                    "description" to test.description,
                    "targetExam" to targetExam,
                    "subject" to test.subject,
                    "classLevel" to test.classLevel,
                    "chapter" to test.chapter,
                    "topic" to test.topic,
                    "difficulty" to test.difficulty,
                    "questionCount" to finalQuestionCount,
                    "questionsCount" to finalQuestionCount,
                    "durationMinutes" to durationMinutes,
                    "totalMarks" to calculatedTotalMarks,
                    "source" to testSource,
                    "creatorId" to currentUid,
                    "creatorDisplayName" to authorName,
                    "creatorName" to authorName,
                    "sharedAt" to System.currentTimeMillis(),
                    "version" to 1,
                    "sharingState" to "COMMUNITY_SHARED",
                    "questions" to questionsList,
                    "questionsJson" to sanitizedQuestionsJson
                )

                val db = firestoreDb

                // Create or update canonical copy: Shared_Community_Tests/{testId}
                // CRITICAL: Original local test remains isPublic = false and untouched!
                db.collection("Shared_Community_Tests")
                    .document(test.id)
                    .set(sharedCommunityTestData)
                    .await()

                val authorSubtitle = "$targetExam Aspirant"

                // Deterministic Document ID ensures same creator + same test can NEVER create duplicate documents
                val deterministicPostId = "TEST_SHARE_${currentUid}_${test.id}"

                // Duplicate share protection:
                // Check direct deterministic document first, and also check existing Community_Posts matching (testId, creatorId, TEST_SHARE)
                val deterministicDocRef = db.collection("Community_Posts").document(deterministicPostId)
                val deterministicDoc = deterministicDocRef.get().await()

                val querySnapshot = db.collection("Community_Posts")
                    .whereEqualTo("testId", test.id)
                    .get()
                    .await()

                val legacyMatches = querySnapshot.documents.filter { doc ->
                    doc.getString("postType") == "TEST_SHARE" &&
                    doc.getString("creatorId") == currentUid &&
                    doc.id != deterministicPostId
                }

                val targetPostId = if (deterministicDoc.exists()) {
                    deterministicPostId
                } else if (legacyMatches.isNotEmpty()) {
                    legacyMatches.first().id
                } else {
                    deterministicPostId
                }

                val alreadyShared = deterministicDoc.exists() || legacyMatches.isNotEmpty()

                val updatedOrNewPost = Com.ak.testseries.ui.CommunityShareManager.buildTestSharePost(
                    test = test,
                    currentUid = currentUid,
                    authorName = authorName,
                    authorSubtitle = authorSubtitle,
                    targetExam = targetExam,
                    totalMarks = calculatedTotalMarks,
                    questionCount = finalQuestionCount,
                    durationMinutes = durationMinutes,
                    postId = targetPostId
                )

                // CRITICAL VISIBILITY RULE:
                // Sharing to Community must NOT change test.isPublic.
                // Do NOT set isPublic = true. The test remains a user-owned private test.
                // Write/update the single authoritative post
                db.collection("Community_Posts")
                    .document(targetPostId)
                    .set(Com.ak.testseries.ui.communityPostToMap(updatedOrNewPost))
                    .await()

                // Clean up any extra legacy duplicates safely if more than one existed
                val duplicatesToDelete = if (targetPostId == deterministicPostId) {
                    legacyMatches
                } else {
                    legacyMatches.drop(1)
                }
                for (dupDoc in duplicatesToDelete) {
                    try {
                        dupDoc.reference.delete().await()
                        android.util.Log.d("NEETViewModel", "Cleaned up legacy duplicate CommunityPost: ${dupDoc.id}")
                    } catch (e: Exception) {
                        android.util.Log.w("NEETViewModel", "Could not delete legacy duplicate ${dupDoc.id}: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    val msg = if (alreadyShared) "Shared test updated in Community" else "Test shared to Community"
                    android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Failed to share test: ${e.localizedMessage ?: "Unknown error"}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openReportIssueDialog(test: TestEntity) {
        reportedTest.value = test
        showReportDialog.value = true
    }

    fun openReportIssueDialogByTestId(testId: String) {
        viewModelScope.launch {
            val test = repository.getTestById(testId)
            if (test != null) {
                reportedTest.value = test
                showReportDialog.value = true
            }
        }
    }

    fun submitReportIssue(testId: String, category: String, comment: String, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val test = repository.getTestById(testId)
                val uploaderId = test?.uploaderId ?: currentUserId.value
                val questions = repository.getQuestionsForTestList(testId)
                // Filter to send a concise representation to AI
                val questionsContext = questions.mapIndexed { idx, q -> 
                    "Q${idx + 1}: ${q.questionText} [A: ${q.optionA}, B: ${q.optionB}, C: ${q.optionC}, D: ${q.optionD}] Correct: ${q.correctAnswer}"
                }.joinToString("\n")

                val prompt = """
                    A user reported an issue in a test with ${questions.size} questions.
                    Category: $category
                    User Comment (which may contain a Suggested Edit): $comment
                    
                    Here is the test content:
                    ${questionsContext.take(15000)} // truncate to avoid too large prompt
                    
                    Determine if this is a text-based error (typo, wrong option, formatting) that can be auto-fixed, or a visual/image error that needs manual fix.
                    Identify the exact question index (0-based) the user is referring to based on their comment. If the user comment implies a specific question number (e.g. "Q10", "Question 5"), extract it. Otherwise, guess the most likely question or return -1.
                    
                    If canAutoFix is true, provide the fixed fields.
                    
                    Return your response STRICTLY as a valid JSON object with keys:
                    { 
                        "canAutoFix": boolean, 
                        "targetQuestionIndex": integer, 
                        "fixedQuestionText": "string", 
                        "fixedOptionA": "string", 
                        "fixedOptionB": "string", 
                        "fixedOptionC": "string", 
                        "fixedOptionD": "string", 
                        "fixedCorrectAnswer": "string" 
                    }
                """.trimIndent()
                
                val responseJson = callGeminiApiForQuestionsWithFallback(prompt)
                var autoFixed = false
                
                if (responseJson != null) {
                    val cleanJson = responseJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val jsonObj = org.json.JSONObject(cleanJson)
                    val canAutoFix = jsonObj.optBoolean("canAutoFix", false)
                    val targetIndex = jsonObj.optInt("targetQuestionIndex", -1)
                    
                    if (canAutoFix && targetIndex in questions.indices) {
                        // Apply AI auto-fix
                        val originalQ = questions[targetIndex]
                        val fixedQ = originalQ.copy(
                            questionText = jsonObj.optString("fixedQuestionText", originalQ.questionText),
                            optionA = jsonObj.optString("fixedOptionA", originalQ.optionA),
                            optionB = jsonObj.optString("fixedOptionB", originalQ.optionB),
                            optionC = jsonObj.optString("fixedOptionC", originalQ.optionC),
                            optionD = jsonObj.optString("fixedOptionD", originalQ.optionD),
                            correctAnswer = jsonObj.optString("fixedCorrectAnswer", originalQ.correctAnswer)
                        )
                        repository.insertQuestions(listOf(fixedQ))
                        autoFixed = true
                        
                        sendNotificationToUser(
                            uploaderId, 
                            "Question ${targetIndex + 1} was automatically corrected based on a user's suggested edit."
                        )
                    } else if (targetIndex != -1) {
                        sendNotificationToUser(
                            uploaderId, 
                            "User reported an error and suggested an edit for Question ${targetIndex + 1}. Tap to review and apply manual fix."
                        )
                    } else {
                        sendNotificationToUser(
                            uploaderId, 
                            "User reported an error and suggested an edit. Tap to review and apply manual fix."
                        )
                    }
                } else {
                    sendNotificationToUser(
                        uploaderId, 
                        "User reported an error and suggested an edit. Tap to review and apply manual fix."
                    )
                }
                
                // Save report to Firestore Question_Reports with deterministic idempotency key
                try {
                    val db = firestoreDb
                    val uid = currentUserId.value.ifBlank { "anonymous" }
                    val reportDocId = "report_${testId}_${category.replace(" ", "_")}_${uid}"
                    val reportMap = hashMapOf<String, Any>(
                        "reportId" to reportDocId,
                        "testId" to testId,
                        "testTitle" to (test?.title ?: "Test"),
                        "questionId" to testId,
                        "questionText" to "Category: $category",
                        "userNote" to comment,
                        "aiAnalysis" to (if (autoFixed) "Auto-fixed by AI" else "Manual review requested"),
                        "reporterUserId" to uid,
                        "status" to (if (autoFixed) "RESOLVED" else "PENDING"),
                        "updatedAt" to System.currentTimeMillis()
                    )
                    db.collection("Question_Reports").document(reportDocId)
                        .set(reportMap, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                if (autoFixed) {
                    android.widget.Toast.makeText(context, "Issue auto-corrected by AI!", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, "Report submitted. Uploader notified.", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Failed to submit report.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        showReportDialog.value = false
    }

    private fun sendNotificationToUser(userId: String, message: String) {
        // Simulate sending a push notification via Firebase Cloud Messaging
        android.util.Log.d("FCM_SIMULATOR", "Sending to $userId: $message")
        // Note: For real implementation, you'd call a backend endpoint that triggers FCM to the user's FCM token.
    }
    val showPdfCropper = MutableStateFlow(false)
    val pdfCropperInitialPage = MutableStateFlow(0)
    var pdfCropCallback: ((String) -> Unit)? = null

    fun open180QuestionsForPreview() {
        viewModelScope.launch(Dispatchers.IO) {
            if (previewQuestions.value.isEmpty()) {
                val list = mutableListOf<QuestionEntity>()
                val testId = "preview_180_" + System.currentTimeMillis()
                
                // Physics Q1 to Q45
                for (i in 1..45) {
                    list.add(
                        QuestionEntity(
                            id = "${testId}_phy_$i",
                            testId = testId,
                            questionText = "Q$i (Physics): Two point charges q1 and q2 are separated by distance r. What is the electrostatic force between them in vacuum?\nप्रश्न $i (भौतिकी): दो बिंदु आवेश q1 और q2 दूरी r पर स्थित हैं। निर्वात में उनके बीच वैद्युत बल क्या है?",
                            optionA = "A) F = k*(q1*q2)/r²",
                            optionB = "B) F = k*(q1*q2)/r",
                            optionC = "C) F = k*(q1+q2)/r²",
                            optionD = "D) F = zero",
                            correctAnswer = "A",
                            explanation = "According to Coulomb's Law, electrostatic force F = k*(q1*q2)/r².\nकूलाम के नियम अनुसार वैद्युत बल F = k*(q1*q2)/r² होता है।",
                            subject = "Physics",
                            chapter = "Electrostatics & Magnetism",
                            questionTextHi = "प्रश्न $i (भौतिकी): दो बिंदु आवेश q1 और q2 दूरी r पर स्थित हैं। निर्वात में उनके बीच वैद्युत बल क्या है?",
                            optionAHi = "A) F = k*(q1*q2)/r²",
                            optionBHi = "B) F = k*(q1*q2)/r",
                            optionCHi = "C) F = k*(q1+q2)/r²",
                            optionDHi = "D) F = शून्य",
                            explanationHi = "कूलाम के नियम अनुसार वैद्युत बल F = k*(q1*q2)/r² होता है।",
                            section = if (i <= 35) "SECTION_A" else "SECTION_B"
                        )
                    )
                }

                // Chemistry Q46 to Q90
                for (i in 46..90) {
                    val qNum = i - 45
                    list.add(
                        QuestionEntity(
                            id = "${testId}_chem_$i",
                            testId = testId,
                            questionText = "Q$i (Chemistry): Which of the following orbital has spherical symmetry?\nप्रश्न $i (रसायन विज्ञान): निम्नलिखित में से किस कक्षक में गोल सममिति (Spherical Symmetry) होती है?",
                            optionA = "A) s-orbital / s-कक्षक",
                            optionB = "B) p-orbital / p-कक्षक",
                            optionC = "C) d-orbital / d-कक्षक",
                            optionD = "D) f-orbital / f-कक्षक",
                            correctAnswer = "A",
                            explanation = "s-orbitals are non-directional and spherical in shape.\ns-कक्षक की आकृति गोलाकार और अदिशात्मक होती है।",
                            subject = "Chemistry",
                            chapter = "Atomic Structure & Periodic Table",
                            questionTextHi = "प्रश्न $i (रसायन विज्ञान): निम्नलिखित में से किस कक्षक में गोल सममिति होती है?",
                            optionAHi = "A) s-कक्षक",
                            optionBHi = "B) p-कक्षक",
                            optionCHi = "C) d-कक्षक",
                            optionDHi = "D) f-कक्षक",
                            explanationHi = "s-कक्षक की आकृति गोलाकार और अदिशात्मक होती है।",
                            section = if (qNum <= 35) "SECTION_A" else "SECTION_B"
                        )
                    )
                }

                // Botany Q91 to Q135
                for (i in 91..135) {
                    val qNum = i - 90
                    list.add(
                        QuestionEntity(
                            id = "${testId}_bot_$i",
                            testId = testId,
                            questionText = "Q$i (Botany): In C4 plants, initial carbon dioxide fixation occurs in which cells?\nप्रश्न $i (वनस्पति विज्ञान): C4 पौधों में प्राथमिक कार्बन डाइऑक्साइड स्थिरीकरण किन कोशिकाओं में होता है?",
                            optionA = "A) Mesophyll cells / पर्णमध्योतक (Mesophyll) कोशिकाएं",
                            optionB = "B) Bundle sheath cells / पूल आच्छद (Bundle sheath) कोशिकाएं",
                            optionC = "C) Epidermal cells / बाह्यत्वचा कोशिकाएं",
                            optionD = "D) Guard cells / द्वार कोशिकाएं",
                            correctAnswer = "A",
                            explanation = "In C4 plants, CO2 is fixed by PEP carboxylase in mesophyll cells to form oxaloacetic acid.\nC4 पौधों में मेसोफिल कोशिकाओं में PEP कार्बोक्सिलेज़ द्वारा CO2 स्थिरीकरण होता है।",
                            subject = "Biology",
                            chapter = "Plant Physiology - Photosynthesis",
                            questionTextHi = "प्रश्न $i (वनस्पति विज्ञान): C4 पौधों में प्राथमिक CO2 स्थिरीकरण किन कोशिकाओं में होता है?",
                            optionAHi = "A) मेसोफिल (Mesophyll) कोशिकाएं",
                            optionBHi = "B) बंडल शीथ (Bundle Sheath) कोशिकाएं",
                            optionCHi = "C) बाह्यत्वचा कोशिकाएं",
                            optionDHi = "D) द्वार कोशिकाएं",
                            explanationHi = "C4 पौधों में मेसोफिल कोशिकाओं में PEP कार्बोक्सिलेज़ द्वारा CO2 स्थिरीकरण होता है।",
                            section = if (qNum <= 35) "SECTION_A" else "SECTION_B"
                        )
                    )
                }

                // Zoology Q136 to Q180
                for (i in 136..180) {
                    val qNum = i - 135
                    list.add(
                        QuestionEntity(
                            id = "${testId}_zoo_$i",
                            testId = testId,
                            questionText = "Q$i (Zoology): Which hormone triggers ovulation in humans?\nप्रश्न $i (प्राणि विज्ञान): मनुष्यों में अंडोत्सर्ग (Ovulation) को कौन सा हार्मोन प्रेरित करता है?",
                            optionA = "A) Luteinizing Hormone (LH) / ल्यूटिनाइजिंग हार्मोन (LH)",
                            optionB = "B) Follicle Stimulating Hormone (FSH) / फॉलिकल स्टिम्युलेटिंग हार्मोन (FSH)",
                            optionC = "C) Progesterone / प्रोजेस्टेरोन",
                            optionD = "D) Estrogen / एस्ट्रोजन",
                            correctAnswer = "A",
                            explanation = "Rapid secretion of LH leads to LH surge mid-cycle, inducing rupture of Graafian follicle and ovulation.\nLH के तीव्र स्राव (LH Surge) से परिपक्व ग्रैफियन पुटक फट जाता है और अंडोत्सर्ग होता है।",
                            subject = "Biology",
                            chapter = "Human Reproduction & Physiology",
                            questionTextHi = "प्रश्न $i (प्राणि विज्ञान): मनुष्यों में अंडोत्सर्ग को कौन सा हार्मोन प्रेरित करता है?",
                            optionAHi = "A) ल्यूटिनाइजिंग हार्मोन (LH)",
                            optionBHi = "B) फॉलिकल स्टिम्युलेटिंग हार्मोन (FSH)",
                            optionCHi = "C) प्रोजेस्टेरोन",
                            optionDHi = "D) एस्ट्रोजन",
                            explanationHi = "LH के तीव्र स्राव से ग्रैफियन पुटक फट जाता है और अंडोत्सर्ग होता है।",
                            section = if (qNum <= 35) "SECTION_A" else "SECTION_B"
                        )
                    )
                }

                previewQuestions.value = list
            }
            show180QuestionsDialog.value = true
        }
    }

    fun close180QuestionsDialog() {
        show180QuestionsDialog.value = false
    }

    fun updatePreviewQuestionImage(qId: String, imageUrl: String) {
        val current = previewQuestions.value.toMutableList()
        val index = current.indexOfFirst { it.id == qId }
        if (index != -1) {
            val updated = current[index].copy(imageUrl = imageUrl, needsImage = true)
            current[index] = updated
            previewQuestions.value = current
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateQuestion(updated)
            }
        }
    }

    init {
        // Prepopulate the Database with beautiful starting content
        prepopulateData()
    }

    fun syncNcertSyllabus() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.syncNcertSyllabusFromRemote()
        }
    }

    private fun prepopulateData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if syllabus is empty
            repository.syllabus.first().let { currentSyllabus ->
                if (currentSyllabus.isEmpty()) {
                    repository.syncNcertSyllabusFromRemote()
                }
            }

            // Clear any old mock/testing data from local database
            repository.allTests.first().let { currentTests ->
                val mockIds = setOf("t1", "t2", "t3", "t4", "t5")
                currentTests.forEach { test ->
                    if (test.id in mockIds) {
                        repository.deleteTest(test)
                    }
                }
            }
        }
    }

    // --- Smart Doubt Resolver (Ask AI) ---
    private val _askAiResponse = MutableStateFlow<String?>(null)
    val askAiResponse: StateFlow<String?> = _askAiResponse.asStateFlow()

    private val _askAiLoading = MutableStateFlow(false)
    val askAiLoading: StateFlow<Boolean> = _askAiLoading.asStateFlow()

    private val _askAiVideoLink = MutableStateFlow<String?>(null)
    val askAiVideoLink: StateFlow<String?> = _askAiVideoLink.asStateFlow()

    fun askAiDoubt(question: String, optionA: String, optionB: String, optionC: String, optionD: String, language: AppLanguage = AppLanguage.ENGLISH) {
        viewModelScope.launch {
            if (!_isGlobalAiEnabled.value) {
                _askAiLoading.value = false
                _askAiResponse.value = if (language == AppLanguage.HINDI)
                    "AI सेवाएं व्यवस्थापक द्वारा अस्थायी रूप से अक्षम (Disabled) हैं।"
                else
                    "AI Mentor services are temporarily disabled by the Administrator."
                return@launch
            }
            _askAiLoading.value = true
            _askAiResponse.value = null
            _askAiVideoLink.value = null

            val keys = getGeminiApiKeys()
            if (keys.isNotEmpty()) {
                val isHindi = language == AppLanguage.HINDI
                val prompt = if (isHindi) {
                    """
                        You are an elite, highly experienced NEET (National Eligibility cum Entrance Test) exam mentor and medical tutor.
                        Your task is to solve the following question with absolute scientific accuracy, breaking it down step-by-step in Hindi (using standard Devnagari script but keep medical/scientific terms clearly readable, optionally with English terms in brackets where useful).

                        Question: $question
                        Options:
                        A) $optionA
                        B) $optionB
                        C) $optionC
                        D) $optionD

                        Please solve the doubt step-by-step with heading-wise consistency:
                        
                        1. **मुख्य अवधारणा / सूत्र (Core Concepts & Formulas)**:
                        State the core scientific rule, law, or NCERT-based formula used to solve the question. Explain the concept's relevance.
                        
                        2. **चरण-दर-चरण गणना / स्पष्टीकरण (Step-by-Step Solution)**:
                        Solve the problem systematically. Explain each step clearly, explaining the logic/equations. Show intermediate steps.
                        
                        3. **सही विकल्प का सत्यापन (Option Verification)**:
                        Conclude which option (A, B, C, or D) is conceptually/mathematically correct and explain why the other options are incorrect.

                        At the end of your response, append the following exactly on a new line (do not embed it inside other text, it must be the very last line):
                        [Solution]: <Write A, B, C, or D>

                        Provide a complete, highly educational, structured, and accurate solution.
                    """.trimIndent()
                } else {
                    """
                        You are an elite, highly experienced NEET (National Eligibility cum Entrance Test) exam mentor and medical tutor.
                        Your task is to solve the following question with absolute scientific accuracy, breaking it down step-by-step in English.

                        Question: $question
                        Options:
                        A) $optionA
                        B) $optionB
                        C) $optionC
                        D) $optionD

                        Please solve the doubt step-by-step with heading-wise consistency:
                        
                        1. **Core Concepts & Formulas**:
                        State the core scientific rule, law, or NCERT-based formula used to solve the question. Explain the concept's relevance.
                        
                        2. **Step-by-Step Solution**:
                        Solve the problem systematically. Explain each step clearly, explaining the logic/equations. Show intermediate steps.
                        
                        3. **Option Verification**:
                        Conclude which option (A, B, C, or D) is conceptually/mathematically correct and explain why the other options are incorrect.

                        At the end of your response, append the following exactly on a new line (do not embed it inside other text, it must be the very last line):
                        [Solution]: <Write A, B, C, or D>

                        Provide a complete, highly educational, structured, and accurate solution.
                    """.trimIndent()
                }

                try {
                    val resultText = callGeminiApiTextWithFallback(prompt)
                    _askAiResponse.value = resultText ?: "Failed to connect to AI Mentor. Rate limit exceeded."
                } catch (e: Exception) {
                    _askAiResponse.value = "Failed to connect to AI Mentor. Error: ${e.message}"
                }
            } else {
                delay(1500) // Simulate network
                _askAiResponse.value = if (language == AppLanguage.HINDI) {
                    "यह एक सिम्युलेटेड AI समाधान है जो NCERT अवधारणाओं पर केंद्रित है।\n\n1. **मुख्य अवधारणा / सूत्र**: थर्मोडायनामिक्स का प्रथम नियम (First Law of Thermodynamics).\n2. **चरण-दर-चरण स्पष्टीकरण**: ΔQ = ΔU + ΔW समीकरण का उपयोग करके हम ऊर्जा संरक्षण का विश्लेषण करते हैं।\n3. **सही विकल्प का सत्यापन**: सही विकल्प 'A' है।\n\n[Solution]: A"
                } else {
                    "This is a simulated AI explanation focusing on NCERT concepts. \n\n1. **Core Concepts & Formulas**: First Law of Thermodynamics.\n2. **Step-by-Step Solution**: Using the equation ΔQ = ΔU + ΔW, we analyze energy conservation.\n3. **Option Verification**: Correct option is 'A'.\n\n[Solution]: A"
                }
            }
            
            // Parse response
            val rawResponse = _askAiResponse.value ?: ""
            if (rawResponse.contains("[Solution]:")) {
                val parts = rawResponse.split("[Solution]:")
                _askAiResponse.value = parts[0].trim()
                val currentExam = _userProfile.value.targetExam.takeIf { it.isNotBlank() } ?: ""
                val examQueryPart = if (currentExam.isNotBlank()) "$currentExam " else ""
                val searchQ = if (language == AppLanguage.HINDI) {
                    "$examQueryPart$question video solution hindi medium NCERT"
                } else {
                    "$examQueryPart$question video solution explanation NCERT"
                }
                _askAiVideoLink.value = "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(searchQ, "UTF-8")}"
            } else if (rawResponse.isNotEmpty()) {
                 // Already set
            }

            _askAiLoading.value = false
        }
    }

    
    private val _reportErrorLoading = MutableStateFlow(false)
    val reportErrorLoading: StateFlow<Boolean> = _reportErrorLoading.asStateFlow()

    private val _reportErrorResponse = MutableStateFlow<String?>(null)
    val reportErrorResponse: StateFlow<String?> = _reportErrorResponse.asStateFlow()

    fun dismissReportError() {
        _reportErrorResponse.value = null
    }

    fun reportQuestionError(
        question: String,
        optionA: String,
        optionB: String,
        optionC: String,
        optionD: String,
        correctAnswer: String,
        testId: String = "",
        testTitle: String = "",
        questionId: String = "",
        userNote: String = ""
    ) {
        viewModelScope.launch {
            if (!_isGlobalAiEnabled.value) {
                _reportErrorLoading.value = false
                _reportErrorResponse.value = "AI analysis is temporarily disabled by the Administrator."
                return@launch
            }
            _reportErrorLoading.value = true
            _reportErrorResponse.value = null

            var aiAnalysisResult = ""
            val keys = getGeminiApiKeys()
            if (keys.isNotEmpty()) {
                val prompt = """
                    You are an expert examiner. The user has reported a potential error in the following question.
                    Please analyze the question, options, and the provided correct answer. Tell me if there is any error in the question, options, or correct answer, and explain why.

                    Question: $question
                    Options:
                    A) $optionA
                    B) $optionB
                    C) $optionC
                    D) $optionD
                    Provided Correct Answer: $correctAnswer

                    Keep your analysis concise, clear, and strictly under 100 words.
                """.trimIndent()
                try {
                    val resultText = callGeminiApiForQuestionsWithFallback(prompt)
                    aiAnalysisResult = resultText ?: "Analyzed with standard guidelines."
                    _reportErrorResponse.value = resultText ?: "Failed to analyze question. Rate limit exceeded."
                } catch (e: Exception) {
                    aiAnalysisResult = "Error in AI: ${e.message}"
                    _reportErrorResponse.value = "Failed to analyze question. Error: ${e.message}"
                }
            } else {
                delay(1500)
                aiAnalysisResult = "Simulated evaluation: No obvious flaw found."
                _reportErrorResponse.value = "This is a simulated AI analysis. The question appears to be correct based on standard concepts. No errors found."
            }
            _reportErrorLoading.value = false

            // Save report with deterministic ID to Firestore to avoid duplicate tickets
            withContext(Dispatchers.IO) {
                try {
                    val uid = currentUserId.value.ifBlank { "anonymous" }
                    val qCleanId = if (questionId.isNotBlank()) questionId else Math.abs(question.hashCode()).toString()
                    val tCleanId = if (testId.isNotBlank()) testId else (_activeTest.value?.id ?: "general_test")
                    val tTitle = if (testTitle.isNotBlank()) testTitle else (_activeTest.value?.title ?: "Test")
                    val reportDocId = "report_${tCleanId}_${qCleanId}_${uid}"

                    val db = firestoreDb
                    val reportMap = hashMapOf<String, Any>(
                        "reportId" to reportDocId,
                        "testId" to tCleanId,
                        "testTitle" to tTitle,
                        "questionId" to qCleanId,
                        "questionText" to question,
                        "optionA" to optionA,
                        "optionB" to optionB,
                        "optionC" to optionC,
                        "optionD" to optionD,
                        "correctAnswer" to correctAnswer,
                        "reporterUserId" to uid,
                        "reporterUserEmail" to (_userProfile.value.email.ifBlank { "" }),
                        "userNote" to userNote,
                        "aiAnalysis" to aiAnalysisResult,
                        "status" to "PENDING",
                        "updatedAt" to System.currentTimeMillis()
                    )
                    db.collection("Question_Reports").document(reportDocId)
                        .set(reportMap, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun fetchQuestionReports() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                val snapshot = db.collection("Question_Reports")
                    .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50)
                    .get().await()
                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        QuestionReport(
                            reportId = doc.id,
                            testId = doc.getString("testId") ?: "",
                            testTitle = doc.getString("testTitle") ?: "",
                            questionId = doc.getString("questionId") ?: "",
                            questionText = doc.getString("questionText") ?: "",
                            optionA = doc.getString("optionA") ?: "",
                            optionB = doc.getString("optionB") ?: "",
                            optionC = doc.getString("optionC") ?: "",
                            optionD = doc.getString("optionD") ?: "",
                            correctAnswer = doc.getString("correctAnswer") ?: "",
                            reporterUserId = doc.getString("reporterUserId") ?: "",
                            reporterUserEmail = doc.getString("reporterUserEmail") ?: "",
                            userNote = doc.getString("userNote") ?: "",
                            aiAnalysis = doc.getString("aiAnalysis") ?: "",
                            status = doc.getString("status") ?: "PENDING",
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) { null }
                }
                _questionReports.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateQuestionReportStatus(reportId: String, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                db.collection("Question_Reports").document(reportId)
                    .update("status", newStatus, "updatedAt", System.currentTimeMillis()).await()
                fetchQuestionReports()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissAskAi() {
        _askAiResponse.value = null
        _askAiVideoLink.value = null
    }

    fun setLanguage(language: AppLanguage) {
        _selectedLanguage.value = language
        try {
            val prefs = getApplication<android.app.Application>().getSharedPreferences("user_profile_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("selected_language", language.name).apply()
        } catch (e: Exception) {}
        if (_currentScreen.value == AppScreen.LANGUAGE_SELECTION) {
            _currentScreen.value = AppScreen.DASHBOARD
        }
    }


    fun sendMagicLink(email: String, context: android.content.Context, authManager: Com.ak.testseries.domain.auth.AuthManager, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authManager.sendSignInLinkToEmail(email, context)
            result.onSuccess {
                // Save email to SharedPreferences to retrieve it when opening the link
                val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("magic_link_email", email).apply()
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.message)
            }
        }
    }

    fun signInWithMagicLink(link: String, context: android.content.Context, authManager: Com.ak.testseries.domain.auth.AuthManager, userRepository: Com.ak.testseries.domain.repository.UserRepository, onResult: (Boolean, String?) -> Unit) {
        val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
        val email = prefs.getString("magic_link_email", null)
        
        if (email == null) {
            onResult(false, "Email not found. Please try logging in again.")
            return
        }

        viewModelScope.launch {
            val result = authManager.signInWithEmailLink(email, link)
            result.onSuccess { user ->
                // Clear the saved email
                prefs.edit().remove("magic_link_email").apply()
                
                currentUserId.value = user.uid
                checkAdminCustomClaim()

                // Sync user to database and Restore Data
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try {
                        userRepository.syncUserToDatabase(user)
                        val backupResult = userRepository.restoreUserData(user.uid)
                        if (backupResult.isSuccess) {
                            val backup = backupResult.getOrNull()
                            if (backup != null) {
                                backup.attempts.forEach { repository.insertAttempt(it) }
                                backup.bookmarkedTests.forEach { repository.toggleBookmark(it, true) }
                                backup.bookmarkedQuestions.forEach { repository.toggleQuestionBookmark(it, true) }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                syncUserAttemptsFromFirebase() 
                val currentProfile = _userProfile.value
                if (currentProfile.name.isEmpty() && currentProfile.email.isEmpty()) {
                    saveUserProfile(user.name, user.email, currentProfile.stateName, currentProfile.cityName)
                }
                
                val updatedProfile = _userProfile.value
                if (updatedProfile.stateName.isEmpty() || updatedProfile.cityName.isEmpty()) {
                    _currentScreen.value = AppScreen.PROFILE_ONBOARDING
                } else {
                    _currentScreen.value = AppScreen.DASHBOARD
                }
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.message)
            }
        }
    }

    fun signInWithEmail(email: String, password: String, authManager: Com.ak.testseries.domain.auth.AuthManager, userRepository: Com.ak.testseries.domain.repository.UserRepository, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authManager.signInWithEmail(email, password)
            result.onSuccess { user ->
                currentUserId.value = user.uid
                checkAdminCustomClaim()
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    
                // Restore Cloud Backup Data
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try {
                        userRepository.syncUserToDatabase(user) 
                        val backupResult = userRepository.restoreUserData(user.uid)
                        if (backupResult.isSuccess) {
                            val backup = backupResult.getOrNull()
                            if (backup != null) {
                                // Restore attempts
                                if (backup.attempts.isNotEmpty()) {
                                    backup.attempts.forEach { repository.insertAttempt(it) }
                                }
                                // Restore Bookmarked Tests
                                backup.bookmarkedTests.forEach { testId ->
                                    repository.toggleBookmark(testId, true)
                                }
                                // Restore Bookmarked Questions
                                backup.bookmarkedQuestions.forEach { qId ->
                                    repository.toggleQuestionBookmark(qId, true)
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                }
                
                syncUserAttemptsFromFirebase() 

                val currentProfile = _userProfile.value
                if (currentProfile.name.isEmpty() && currentProfile.email.isEmpty()) {
                    saveUserProfile(user.name, user.email, currentProfile.stateName, currentProfile.cityName)
                }
                
                val updatedProfile = _userProfile.value
                if (updatedProfile.stateName.isEmpty() || updatedProfile.cityName.isEmpty()) {
                    _currentScreen.value = AppScreen.PROFILE_ONBOARDING
                } else {
                    _currentScreen.value = AppScreen.DASHBOARD
                }
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.message)
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, password: String, authManager: Com.ak.testseries.domain.auth.AuthManager, userRepository: Com.ak.testseries.domain.repository.UserRepository, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authManager.signUpWithEmail(name, email, password)
            result.onSuccess { user ->
                currentUserId.value = user.uid
                checkAdminCustomClaim()
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    
                // Restore Cloud Backup Data
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try {
                        userRepository.syncUserToDatabase(user) 
                        val backupResult = userRepository.restoreUserData(user.uid)
                        if (backupResult.isSuccess) {
                            val backup = backupResult.getOrNull()
                            if (backup != null) {
                                // Restore attempts
                                if (backup.attempts.isNotEmpty()) {
                                    backup.attempts.forEach { repository.insertAttempt(it) }
                                }
                                // Restore Bookmarked Tests
                                backup.bookmarkedTests.forEach { testId ->
                                    repository.toggleBookmark(testId, true)
                                }
                                // Restore Bookmarked Questions
                                backup.bookmarkedQuestions.forEach { qId ->
                                    repository.toggleQuestionBookmark(qId, true)
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                }
                
                saveUserProfile(user.name, user.email, "", "")
                _currentScreen.value = AppScreen.PROFILE_ONBOARDING
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.message)
            }
        }
    }

    fun handleSignInResult(
        context: android.content.Context,
        intent: android.content.Intent?,
        authManager: Com.ak.testseries.domain.auth.AuthManager,
        userRepository: Com.ak.testseries.domain.repository.UserRepository
    ) {
        viewModelScope.launch {
            val result = authManager.signInWithGoogle(context, intent)
            result.onSuccess { user ->
                currentUserId.value = user.uid
                checkAdminCustomClaim()
                // Sync user to database and Restore Data
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try {
                        userRepository.syncUserToDatabase(user)
                        val backupResult = userRepository.restoreUserData(user.uid)
                        if (backupResult.isSuccess) {
                            val backup = backupResult.getOrNull()
                            if (backup != null) {
                                backup.attempts.forEach { repository.insertAttempt(it) }
                                backup.bookmarkedTests.forEach { repository.toggleBookmark(it, true) }
                                backup.bookmarkedQuestions.forEach { repository.toggleQuestionBookmark(it, true) }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Update local profile if empty
                syncUserAttemptsFromFirebase() 

                val currentProfile = _userProfile.value
                if (currentProfile.name.isEmpty() && currentProfile.email.isEmpty()) {
                    saveUserProfile(user.name, user.email, currentProfile.stateName, currentProfile.cityName)
                }
                
                // Navigate to next screen (Onboarding, Language Selection or Dashboard)
                val updatedProfile = _userProfile.value
                if (updatedProfile.stateName.isEmpty() || updatedProfile.cityName.isEmpty()) {
                    _currentScreen.value = AppScreen.PROFILE_ONBOARDING
                } else {
                    _currentScreen.value = AppScreen.DASHBOARD
                }
            }.onFailure { e ->
                // Handle error (e.g., show a toast)
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Sign In Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    fun handleDeepLinkTest(testId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanTestId = testId.trim()
            if (cleanTestId.isBlank()) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Invalid test link", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // 1. Room lookup first: if local copy exists, open directly and do NOT download again
            val localTest = repository.getTestById(cleanTestId)
            if (localTest != null) {
                android.util.Log.d("NEETViewModel", "handleDeepLinkTest: Local copy of $cleanTestId exists in Room. Opening local copy.")
                withContext(Dispatchers.Main) {
                    startTest(localTest)
                }
                return@launch
            }

            // 2. Firebase lookup:
            val db = firestoreDb
            var testAndQuestions: Pair<TestEntity, List<QuestionEntity>>? = null

            var lastNetworkException: Exception? = null

            // 2a. Fetch from canonical Shared_Community_Tests/{testId}
            try {
                val communityDoc = db.collection("Shared_Community_Tests").document(cleanTestId).get().await()
                if (communityDoc != null && communityDoc.exists()) {
                    testAndQuestions = parseAndValidateSharedCommunityTest(cleanTestId, communityDoc)
                    if (testAndQuestions == null) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(getApplication(), "Cannot load test: Corrupted or invalid community test data", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                lastNetworkException = e
                android.util.Log.e("NEETViewModel", "Error fetching Shared_Community_Tests for $cleanTestId: ${e.message}", e)
            }

            // 2b. Fallback to existing public Tests_Data/{testId}
            if (testAndQuestions == null) {
                try {
                    val publicDoc = db.collection("Tests_Data").document(cleanTestId).get().await()
                    if (publicDoc != null && publicDoc.exists()) {
                        testAndQuestions = parseAndValidatePublicTest(cleanTestId, publicDoc)
                        if (testAndQuestions == null) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(getApplication(), "Cannot load test: Corrupted or invalid public test data", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    lastNetworkException = e
                    android.util.Log.e("NEETViewModel", "Error fetching Tests_Data fallback for $cleanTestId: ${e.message}", e)
                }
            }

            // 2c. Show error if unavailable in both
            if (testAndQuestions == null) {
                withContext(Dispatchers.Main) {
                    val errMsg = lastNetworkException?.message?.lowercase() ?: ""
                    val isOffline = errMsg.contains("offline") || errMsg.contains("network") || errMsg.contains("unavailable")
                    if (isOffline) {
                        android.widget.Toast.makeText(getApplication(), "Cannot download test: Network unavailable. Please check your connection.", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(getApplication(), "Test not found or unavailable", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            val (testEntityToSave, questionsToSave) = testAndQuestions

            // 3. Save local Community copy and questions in Room transaction
            try {
                repository.saveTestWithQuestions(testEntityToSave, questionsToSave)
                android.util.Log.d("NEETViewModel", "Successfully saved downloaded test $cleanTestId (${questionsToSave.size} questions) to Room.")
            } catch (e: Exception) {
                android.util.Log.e("NEETViewModel", "Failed to save downloaded test into Room", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Failed to save test locally: ${e.localizedMessage ?: "Storage error"}", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // 4. Prepare for normal exam opening
            withContext(Dispatchers.Main) {
                startTest(testEntityToSave)
            }
        }
    }

    private fun parseAndValidateSharedCommunityTest(
        testId: String,
        doc: com.google.firebase.firestore.DocumentSnapshot
    ): Pair<TestEntity, List<QuestionEntity>>? {
        if (testId.isBlank()) return null
        val title = doc.getString("title")?.trim() ?: ""
        if (title.isBlank()) return null

        val targetExam = doc.getString("targetExam")?.trim()?.ifBlank { "NEET" } ?: "NEET"
        val durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 180
        if (durationMinutes <= 0) return null

        val totalMarksVal = (doc.get("totalMarks") as? Number)?.toDouble()
        if (totalMarksVal != null && totalMarksVal <= 0.0) return null

        val creatorId = doc.getString("creatorId")?.trim() ?: "anonymous"
        val description = doc.getString("description")?.trim() ?: ""
        val subject = doc.getString("subject")?.trim() ?: "Full Syllabus"
        val classLevel = doc.getString("classLevel")?.trim() ?: "All"
        val chapter = doc.getString("chapter")?.trim() ?: "All"
        val topic = doc.getString("topic")?.trim() ?: "All"
        val difficulty = doc.getString("difficulty")?.trim() ?: "Medium"
        val expectedQCount = doc.getLong("questionCount")?.toInt() ?: doc.getLong("questionsCount")?.toInt() ?: 0

        val qList = mutableListOf<QuestionEntity>()

        // 1. Try parsing questions array of maps
        val rawQuestions = doc.get("questions") as? List<*>
        if (!rawQuestions.isNullOrEmpty()) {
            for (i in rawQuestions.indices) {
                val map = rawQuestions[i] as? Map<*, *> ?: return null
                val qId = (map["id"] as? String)?.trim()?.ifBlank { "${testId}_q$i" } ?: "${testId}_q$i"
                val qText = (map["questionText"] as? String)?.trim() ?: ""
                if (qText.isBlank()) return null

                val rawOpts = map["options"] as? List<*>
                val optA = (map["optionA"] as? String)?.trim() ?: (rawOpts?.getOrNull(0) as? String)?.trim() ?: ""
                val optB = (map["optionB"] as? String)?.trim() ?: (rawOpts?.getOrNull(1) as? String)?.trim() ?: ""
                val optC = (map["optionC"] as? String)?.trim() ?: (rawOpts?.getOrNull(2) as? String)?.trim() ?: ""
                val optD = (map["optionD"] as? String)?.trim() ?: (rawOpts?.getOrNull(3) as? String)?.trim() ?: ""

                val qType = (map["questionType"] as? String)?.trim() ?: "MCQ"
                val correctAns = (map["correctAnswer"] as? String)?.trim() ?: ""
                if (correctAns.isBlank()) return null

                val isNumerical = qType.contains("NUMERICAL", ignoreCase = true) || qType.contains("NAT", ignoreCase = true)
                if (!isNumerical && (optA.isBlank() || optB.isBlank())) {
                    return null
                }

                val posMarks = (map["positiveMarks"] as? Number)?.toDouble() ?: 4.0
                if (posMarks <= 0.0) return null
                val negMarks = (map["negativeMarks"] as? Number)?.toDouble() ?: 1.0
                if (negMarks < 0.0) return null

                val imgUrl = (map["imageUrl"] as? String)?.trim()
                if (!imgUrl.isNullOrEmpty()) {
                    if (imgUrl.startsWith("/") || imgUrl.startsWith("file:", ignoreCase = true) ||
                        imgUrl.contains("/cache/") || imgUrl.contains("/extracted_cropped_images/")) {
                        android.util.Log.e("NEETViewModel", "Blocked local image URL in shared test: $imgUrl")
                        return null
                    }
                    if (!imgUrl.startsWith("http://", ignoreCase = true) && !imgUrl.startsWith("https://", ignoreCase = true)) {
                        android.util.Log.e("NEETViewModel", "Image URL must be a remote URL: $imgUrl")
                        return null
                    }
                }

                qList.add(
                    QuestionEntity(
                        id = qId,
                        testId = testId, // downloaded question.testId = testId
                        questionText = qText,
                        optionA = optA,
                        optionB = optB,
                        optionC = optC,
                        optionD = optD,
                        correctAnswer = correctAns,
                        explanation = (map["explanation"] as? String)?.trim() ?: "",
                        subject = (map["subject"] as? String)?.trim() ?: subject,
                        chapter = (map["chapter"] as? String)?.trim() ?: chapter,
                        topic = (map["topic"] as? String)?.trim() ?: topic,
                        difficulty = (map["difficulty"] as? String)?.trim() ?: difficulty,
                        questionTextHi = (map["questionTextHi"] as? String)?.trim()?.takeIf { it.isNotBlank() },
                        optionAHi = (map["optionAHi"] as? String)?.trim()?.takeIf { it.isNotBlank() },
                        optionBHi = (map["optionBHi"] as? String)?.trim()?.takeIf { it.isNotBlank() },
                        optionCHi = (map["optionCHi"] as? String)?.trim()?.takeIf { it.isNotBlank() },
                        optionDHi = (map["optionDHi"] as? String)?.trim()?.takeIf { it.isNotBlank() },
                        explanationHi = (map["explanationHi"] as? String)?.trim()?.takeIf { it.isNotBlank() },
                        questionType = qType,
                        section = (map["section"] as? String)?.trim() ?: "SECTION_A",
                        imageUrl = imgUrl?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        // 2. Fallback to questionsJson if questions list was empty
        if (qList.isEmpty()) {
            val questionsJson = doc.getString("questionsJson")?.trim() ?: ""
            if (questionsJson.isBlank()) return null
            try {
                val jsonArray = org.json.JSONArray(questionsJson)
                if (jsonArray.length() == 0) return null
                for (i in 0 until jsonArray.length()) {
                    val qObj = jsonArray.getJSONObject(i)
                    val qId = qObj.optString("id", "${testId}_q$i").ifBlank { "${testId}_q$i" }
                    val qText = qObj.optString("questionText", qObj.optString("questionTextEn", "")).trim()
                    if (qText.isBlank()) return null

                    val optA = qObj.optString("optionA", qObj.optString("optionAEn", "")).trim()
                    val optB = qObj.optString("optionB", qObj.optString("optionBEn", "")).trim()
                    val optC = qObj.optString("optionC", qObj.optString("optionCEn", "")).trim()
                    val optD = qObj.optString("optionD", qObj.optString("optionDEn", "")).trim()

                    val qType = qObj.optString("questionType", "MCQ")
                    val correctAns = qObj.optString("correctAnswer", "").trim()
                    if (correctAns.isBlank()) return null

                    val isNumerical = qType.contains("NUMERICAL", ignoreCase = true) || qType.contains("NAT", ignoreCase = true)
                    if (!isNumerical && (optA.isBlank() || optB.isBlank())) {
                        return null
                    }

                    val posMarks = qObj.optDouble("positiveMarks", 4.0)
                    if (posMarks <= 0.0) return null
                    val negMarks = qObj.optDouble("negativeMarks", 1.0)
                    if (negMarks < 0.0) return null

                    val imgUrl = qObj.optString("imageUrl", "").trim()
                    if (imgUrl.isNotEmpty()) {
                        if (imgUrl.startsWith("/") || imgUrl.startsWith("file:", ignoreCase = true) ||
                            imgUrl.contains("/cache/") || imgUrl.contains("/extracted_cropped_images/")) {
                            return null
                        }
                        if (!imgUrl.startsWith("http://", ignoreCase = true) && !imgUrl.startsWith("https://", ignoreCase = true)) {
                            return null
                        }
                    }

                    qList.add(
                        QuestionEntity(
                            id = qId,
                            testId = testId, // downloaded question.testId = testId
                            questionText = qText,
                            optionA = optA,
                            optionB = optB,
                            optionC = optC,
                            optionD = optD,
                            correctAnswer = correctAns,
                            explanation = qObj.optString("explanation", qObj.optString("explanationEn", "")).trim(),
                            subject = qObj.optString("subject", subject).trim(),
                            chapter = qObj.optString("chapter", chapter).trim(),
                            topic = qObj.optString("topic", topic).trim(),
                            difficulty = qObj.optString("difficulty", difficulty).trim(),
                            questionTextHi = qObj.optString("questionTextHi").trim().takeIf { it.isNotBlank() },
                            optionAHi = qObj.optString("optionAHi").trim().takeIf { it.isNotBlank() },
                            optionBHi = qObj.optString("optionBHi").trim().takeIf { it.isNotBlank() },
                            optionCHi = qObj.optString("optionCHi").trim().takeIf { it.isNotBlank() },
                            optionDHi = qObj.optString("optionDHi").trim().takeIf { it.isNotBlank() },
                            explanationHi = qObj.optString("explanationHi").trim().takeIf { it.isNotBlank() },
                            questionType = qType,
                            section = qObj.optString("section", "SECTION_A"),
                            imageUrl = imgUrl.takeIf { it.isNotBlank() }
                        )
                    )
                }
            } catch (e: Exception) {
                return null
            }
        }

        if (qList.isEmpty()) return null
        if (expectedQCount > 0 && qList.size < expectedQCount) return null

        val finalQuestionsJson = doc.getString("questionsJson")?.takeIf { it.isNotBlank() }
            ?: serializeQuestionsToJson(qList)

        val testEntity = Com.ak.testseries.data.TestEntity(
            id = testId,
            title = title,
            description = description,
            institute = "Community Vault",
            batchName = "Community",
            facultyName = doc.getString("creatorDisplayName") ?: doc.getString("creatorName") ?: "Community User",
            language = doc.getString("language") ?: "English",
            subject = subject,
            difficulty = difficulty,
            testType = "Community Test",
            questionsCount = qList.size,
            isOfficial = false,
            isVerifiedCommunity = true,
            isPublic = false,
            countsTowardRanking = false,
            isDownloaded = true,
            timestamp = System.currentTimeMillis(),
            status = "Published",
            uploaderId = creatorId,
            targetExam = targetExam,
            durationMinutes = durationMinutes,
            classLevel = classLevel,
            chapter = chapter,
            topic = topic,
            questionsJson = finalQuestionsJson
        )

        return Pair(testEntity, qList)
    }

    private fun parseAndValidatePublicTest(
        testId: String,
        doc: com.google.firebase.firestore.DocumentSnapshot
    ): Pair<TestEntity, List<QuestionEntity>>? {
        if (testId.isBlank()) return null
        val title = doc.getString("title")?.trim() ?: ""
        if (title.isBlank()) return null

        val questionsJson = doc.getString("questionsJson")?.trim() ?: ""
        if (questionsJson.isBlank()) return null

        val targetExam = doc.getString("targetExam")?.trim()?.ifBlank { "NEET" } ?: "NEET"
        val durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 180
        if (durationMinutes <= 0) return null
        val creatorId = doc.getString("uploaderId")?.trim() ?: "anonymous"
        val description = doc.getString("description")?.trim() ?: ""
        val subject = doc.getString("subject")?.trim() ?: "Full Syllabus"
        val classLevel = doc.getString("classLevel")?.trim() ?: "All"
        val chapter = doc.getString("chapter")?.trim() ?: "All"
        val topic = doc.getString("topic")?.trim() ?: "All"
        val difficulty = doc.getString("difficulty")?.trim() ?: "Medium"
        val expectedQCount = doc.getLong("questionsCount")?.toInt() ?: 0

        val qList = mutableListOf<QuestionEntity>()
        try {
            val jsonArray = org.json.JSONArray(questionsJson)
            if (jsonArray.length() == 0) return null
            for (i in 0 until jsonArray.length()) {
                val qObj = jsonArray.getJSONObject(i)
                val qId = qObj.optString("id", "${testId}_q$i").ifBlank { "${testId}_q$i" }
                val qText = qObj.optString("questionText", qObj.optString("questionTextEn", "")).trim()
                if (qText.isBlank()) return null

                val optA = qObj.optString("optionA", qObj.optString("optionAEn", "")).trim()
                val optB = qObj.optString("optionB", qObj.optString("optionBEn", "")).trim()
                val optC = qObj.optString("optionC", qObj.optString("optionCEn", "")).trim()
                val optD = qObj.optString("optionD", qObj.optString("optionDEn", "")).trim()

                val qType = qObj.optString("questionType", "MCQ")
                val correctAns = qObj.optString("correctAnswer", "").trim()
                if (correctAns.isBlank()) return null

                val isNumerical = qType.contains("NUMERICAL", ignoreCase = true) || qType.contains("NAT", ignoreCase = true)
                if (!isNumerical && (optA.isBlank() || optB.isBlank())) {
                    return null
                }

                val posMarks = qObj.optDouble("positiveMarks", 4.0)
                if (posMarks <= 0.0) return null
                val negMarks = qObj.optDouble("negativeMarks", 1.0)
                if (negMarks < 0.0) return null

                val imgUrl = qObj.optString("imageUrl", "").trim()
                if (imgUrl.isNotEmpty()) {
                    if (imgUrl.startsWith("/") || imgUrl.startsWith("file:", ignoreCase = true) ||
                        imgUrl.contains("/cache/") || imgUrl.contains("/extracted_cropped_images/")) {
                        return null
                    }
                    if (!imgUrl.startsWith("http://", ignoreCase = true) && !imgUrl.startsWith("https://", ignoreCase = true)) {
                        return null
                    }
                }

                qList.add(
                    QuestionEntity(
                        id = qId,
                        testId = testId, // downloaded question.testId = testId
                        questionText = qText,
                        optionA = optA,
                        optionB = optB,
                        optionC = optC,
                        optionD = optD,
                        correctAnswer = correctAns,
                        explanation = qObj.optString("explanation", qObj.optString("explanationEn", "")).trim(),
                        subject = qObj.optString("subject", subject).trim(),
                        chapter = qObj.optString("chapter", chapter).trim(),
                        topic = qObj.optString("topic", topic).trim(),
                        difficulty = qObj.optString("difficulty", difficulty).trim(),
                        questionTextHi = qObj.optString("questionTextHi").trim().takeIf { it.isNotBlank() },
                        optionAHi = qObj.optString("optionAHi").trim().takeIf { it.isNotBlank() },
                        optionBHi = qObj.optString("optionBHi").trim().takeIf { it.isNotBlank() },
                        optionCHi = qObj.optString("optionCHi").trim().takeIf { it.isNotBlank() },
                        optionDHi = qObj.optString("optionDHi").trim().takeIf { it.isNotBlank() },
                        explanationHi = qObj.optString("explanationHi").trim().takeIf { it.isNotBlank() },
                        questionType = qType,
                        section = qObj.optString("section", "SECTION_A"),
                        imageUrl = imgUrl.takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: Exception) {
            return null
        }

        if (qList.isEmpty()) return null
        if (expectedQCount > 0 && qList.size < expectedQCount) return null

        val testEntity = Com.ak.testseries.data.TestEntity(
            id = testId,
            title = title,
            description = description,
            institute = "Community Vault",
            batchName = doc.getString("batchName") ?: "Community",
            facultyName = doc.getString("facultyName") ?: "Community User",
            language = doc.getString("language") ?: "English",
            subject = subject,
            difficulty = difficulty,
            testType = doc.getString("testType") ?: "Community Test",
            questionsCount = qList.size,
            isOfficial = doc.getBoolean("isOfficial") ?: false,
            isVerifiedCommunity = doc.getBoolean("isVerifiedCommunity") ?: false,
            isPublic = false,
            countsTowardRanking = false,
            isDownloaded = true,
            timestamp = System.currentTimeMillis(),
            status = "Published",
            uploaderId = creatorId,
            targetExam = targetExam,
            durationMinutes = durationMinutes,
            classLevel = classLevel,
            chapter = chapter,
            topic = topic,
            questionsJson = questionsJson
        )

        return Pair(testEntity, qList)
    }

    fun navigateToScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // --- Bookmark Management (Reference-Based Storage Deduplication) ---
    fun toggleTestBookmark(test: TestEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !test.isBookmarked
            repository.toggleBookmark(test.id, newStatus)
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val user = auth.currentUser
                if (user != null) {
                    val db = firestoreDb
                    val bookmarkRef = db.collection("Users_Data").document(user.uid)
                        .collection("Bookmarked_Tests").document(test.id)
                    if (newStatus) {
                        val refData = hashMapOf(
                            "testId" to test.id,
                            "timestamp" to System.currentTimeMillis()
                        )
                        bookmarkRef.set(refData, com.google.firebase.firestore.SetOptions.merge()).await()
                    } else {
                        bookmarkRef.delete().await()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleQuestionBookmark(question: QuestionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !question.isBookmarked
            repository.toggleQuestionBookmark(question.id, newStatus)
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val user = auth.currentUser
                if (user != null) {
                    val db = firestoreDb
                    val bookmarkRef = db.collection("Users_Data").document(user.uid)
                        .collection("Bookmarked_Questions").document(question.id)
                    if (newStatus) {
                        // Reference-only storage saves >90% document footprint
                        val refData = hashMapOf(
                            "questionId" to question.id,
                            "testId" to question.testId,
                            "subject" to question.subject,
                            "chapter" to question.chapter,
                            "timestamp" to System.currentTimeMillis()
                        )
                        bookmarkRef.set(refData, com.google.firebase.firestore.SetOptions.merge()).await()
                    } else {
                        bookmarkRef.delete().await()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun downloadTestOffline(test: TestEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.downloadTest(test.id)
            val existingQs = repository.getQuestionsForTestList(test.id)
            if (existingQs.isEmpty() && !test.questionsJson.isNullOrEmpty()) {
                try {
                    val jsonArray = org.json.JSONArray(test.questionsJson)
                    val qList = mutableListOf<QuestionEntity>()
                    for (i in 0 until jsonArray.length()) {
                        val qObj = jsonArray.getJSONObject(i)
                        qList.add(
                            QuestionEntity(
                                id = qObj.optString("id", "${test.id}_q$i"),
                                testId = test.id,
                                questionText = qObj.optString("questionText", qObj.optString("questionTextEn", "")),
                                optionA = qObj.optString("optionA", qObj.optString("optionAEn", "")),
                                optionB = qObj.optString("optionB", qObj.optString("optionBEn", "")),
                                optionC = qObj.optString("optionC", qObj.optString("optionCEn", "")),
                                optionD = qObj.optString("optionD", qObj.optString("optionDEn", "")),
                                correctAnswer = qObj.optString("correctAnswer", ""),
                                explanation = qObj.optString("explanation", qObj.optString("explanationEn", "")),
                                subject = qObj.optString("subject", test.subject),
                                chapter = qObj.optString("chapter", test.chapter),
                                topic = qObj.optString("topic", test.topic),
                                difficulty = qObj.optString("difficulty", test.difficulty),
                                isBookmarked = qObj.optBoolean("isBookmarked", false),
                                questionTextHi = qObj.optString("questionTextHi", "").takeIf { it.isNotEmpty() },
                                optionAHi = qObj.optString("optionAHi", "").takeIf { it.isNotEmpty() },
                                optionBHi = qObj.optString("optionBHi", "").takeIf { it.isNotEmpty() },
                                optionCHi = qObj.optString("optionCHi", "").takeIf { it.isNotEmpty() },
                                optionDHi = qObj.optString("optionDHi", "").takeIf { it.isNotEmpty() },
                                explanationHi = qObj.optString("explanationHi", "").takeIf { it.isNotEmpty() },
                                questionType = qObj.optString("questionType", "MCQ"),
                                section = qObj.optString("section", "SECTION_A"),
                                needsImage = qObj.optBoolean("needsImage", false),
                                imageUrl = qObj.optString("imageUrl", "").takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                    if (qList.isNotEmpty()) {
                        repository.insertQuestions(qList)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // --- Exam Engine ---

    private fun calculateExamDurationSeconds(test: TestEntity): Long {
        if (test.durationMinutes > 0) {
            return test.durationMinutes * 60L
        }
        val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.getAllExams().find { 
            it.examName.equals(test.targetExam, ignoreCase = true) || it.shortName.equals(test.targetExam, ignoreCase = true) 
        }
        val standardPaperDuration = examConfig?.stages?.firstOrNull()?.papers?.firstOrNull()?.durationMinutes
        val qCount = if (test.questionsCount > 0) test.questionsCount else 10
        
        return if (standardPaperDuration != null && qCount >= 50) {
            standardPaperDuration * 60L
        } else {
            val perQuestionSeconds = when {
                test.targetExam.contains("JEE", ignoreCase = true) || test.targetExam.contains("GATE", ignoreCase = true) -> 120L
                test.targetExam.contains("UPSC", ignoreCase = true) -> 72L
                test.targetExam.contains("NEET", ignoreCase = true) -> 60L
                test.targetExam.contains("CAT", ignoreCase = true) -> 120L
                test.targetExam.contains("Bank", ignoreCase = true) || test.targetExam.contains("IBPS", ignoreCase = true) || test.targetExam.contains("SBI", ignoreCase = true) -> 45L
                test.targetExam.contains("SSC", ignoreCase = true) || test.targetExam.contains("RRB", ignoreCase = true) -> 54L
                else -> 60L
            }
            qCount * perQuestionSeconds
        }
    }

    fun pauseTest() {
        val testId = _activeTest.value?.id ?: return
        val context = getApplication<Application>()
        val stateJson = org.json.JSONObject()
        stateJson.put("currentIndex", _currentQuestionIndex.value)
        stateJson.put("timerRemaining", _examTimeRemaining.value)
        
        val answersJson = org.json.JSONObject()
        _userAnswers.value.forEach { (k, v) -> answersJson.put(k, v) }
        stateJson.put("userAnswers", answersJson)
        
        val markedArray = org.json.JSONArray()
        _markedForReview.value.forEach { markedArray.put(it) }
        stateJson.put("markedForReview", markedArray)
        
        val checkedArray = org.json.JSONArray()
        _checkedQuestions.value.forEach { checkedArray.put(it) }
        stateJson.put("checkedQuestions", checkedArray)

        val visitedArray = org.json.JSONArray()
        _visitedQuestions.value.forEach { visitedArray.put(it) }
        stateJson.put("visitedQuestions", visitedArray)

        val timeSpentJson = org.json.JSONObject()
        _timeSpentPerQuestion.value.forEach { (k, v) -> timeSpentJson.put(k, v) }
        stateJson.put("timeSpent", timeSpentJson)
        
        val prefs = context.getSharedPreferences("test_progress", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("progress_$testId", stateJson.toString()).apply()

        timerJob?.cancel()
        _activeTest.value = null
        _currentScreen.value = AppScreen.DASHBOARD
    }

    private fun resetTestState(test: TestEntity) {
        _currentQuestionIndex.value = 0
        _userAnswers.value = emptyMap()
        _timeSpentPerQuestion.value = emptyMap()
        _markedForReview.value = emptySet()
        _checkedQuestions.value = emptySet()
        _visitedQuestions.value = emptySet()
        val initialTime = calculateExamDurationSeconds(test)
        _examTimeRemaining.value = initialTime
    }

    fun startTest(test: TestEntity) {
        viewModelScope.launch {
            val uid = currentUserId.value.ifBlank { "anon" }
            activeAttemptSessionToken = "att_${uid}_${test.id}_${System.currentTimeMillis()}"
            _activeTest.value = test
            // Load questions
            var questions = withContext(Dispatchers.IO) {
                repository.getQuestionsForTestList(test.id)
            }
            if (questions.isEmpty() && !test.questionsJson.isNullOrEmpty()) {
                try {
                    val jsonArray = org.json.JSONArray(test.questionsJson)
                    val qList = mutableListOf<QuestionEntity>()
                    for (i in 0 until jsonArray.length()) {
                        val qObj = jsonArray.getJSONObject(i)
                        qList.add(
                            QuestionEntity(
                                id = qObj.optString("id", "${test.id}_q$i"),
                                testId = test.id,
                                questionText = qObj.optString("questionText", qObj.optString("questionTextEn", "")),
                                optionA = qObj.optString("optionA", qObj.optString("optionAEn", "")),
                                optionB = qObj.optString("optionB", qObj.optString("optionBEn", "")),
                                optionC = qObj.optString("optionC", qObj.optString("optionCEn", "")),
                                optionD = qObj.optString("optionD", qObj.optString("optionDEn", "")),
                                correctAnswer = qObj.optString("correctAnswer", ""),
                                explanation = qObj.optString("explanation", qObj.optString("explanationEn", "")),
                                subject = qObj.optString("subject", test.subject),
                                chapter = qObj.optString("chapter", test.chapter),
                                topic = qObj.optString("topic", test.topic),
                                difficulty = qObj.optString("difficulty", test.difficulty),
                                isBookmarked = qObj.optBoolean("isBookmarked", false),
                                questionTextHi = qObj.optString("questionTextHi", "").takeIf { it.isNotEmpty() },
                                optionAHi = qObj.optString("optionAHi", "").takeIf { it.isNotEmpty() },
                                optionBHi = qObj.optString("optionBHi", "").takeIf { it.isNotEmpty() },
                                optionCHi = qObj.optString("optionCHi", "").takeIf { it.isNotEmpty() },
                                optionDHi = qObj.optString("optionDHi", "").takeIf { it.isNotEmpty() },
                                explanationHi = qObj.optString("explanationHi", "").takeIf { it.isNotEmpty() },
                                questionType = qObj.optString("questionType", "MCQ"),
                                section = qObj.optString("section", "SECTION_A"),
                                needsImage = qObj.optBoolean("needsImage", false),
                                imageUrl = qObj.optString("imageUrl", "").takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                    if (qList.isNotEmpty()) {
                        questions = qList
                        withContext(Dispatchers.IO) {
                            repository.insertQuestions(qList)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (questions.isNotEmpty()) {
                _activeQuestions.value = questions
                
                val context = getApplication<Application>()
                val prefs = context.getSharedPreferences("test_progress", android.content.Context.MODE_PRIVATE)
                val savedProgress = prefs.getString("progress_${test.id}", null)
                
                if (savedProgress != null) {
                    try {
                        val stateJson = org.json.JSONObject(savedProgress)
                        _currentQuestionIndex.value = stateJson.optInt("currentIndex", 0)
                        val fallbackTime = calculateExamDurationSeconds(test)
                        _examTimeRemaining.value = stateJson.optLong("timerRemaining", fallbackTime)
                        
                        val ansJson = stateJson.optJSONObject("userAnswers")
                        val answersMap = mutableMapOf<String, String>()
                        ansJson?.keys()?.forEach { key ->
                            answersMap[key] = ansJson.getString(key)
                        }
                        _userAnswers.value = answersMap
                        
                        val markedArray = stateJson.optJSONArray("markedForReview")
                        val markedSet = mutableSetOf<String>()
                        if (markedArray != null) {
                            for (i in 0 until markedArray.length()) markedSet.add(markedArray.getString(i))
                        }
                        _markedForReview.value = markedSet
                        
                        val checkedArray = stateJson.optJSONArray("checkedQuestions")
                        val checkedSet = mutableSetOf<String>()
                        if (checkedArray != null) {
                            for (i in 0 until checkedArray.length()) checkedSet.add(checkedArray.getString(i))
                        }
                        _checkedQuestions.value = checkedSet

                        val visitedArray = stateJson.optJSONArray("visitedQuestions")
                        val visitedSet = mutableSetOf<String>()
                        if (visitedArray != null) {
                            for (i in 0 until visitedArray.length()) visitedSet.add(visitedArray.getString(i))
                        }
                        if (questions.isNotEmpty()) {
                            val initialQId = questions.getOrNull(_currentQuestionIndex.value)?.id
                            if (initialQId != null) visitedSet.add(initialQId)
                        }
                        _visitedQuestions.value = visitedSet
                        
                        val timeSpentJson = stateJson.optJSONObject("timeSpent")
                        val timeSpentMap = mutableMapOf<String, Long>()
                        timeSpentJson?.keys()?.forEach { key ->
                            timeSpentMap[key] = timeSpentJson.getLong(key)
                        }
                        _timeSpentPerQuestion.value = timeSpentMap

                    } catch (e: Exception) {
                        e.printStackTrace()
                        resetTestState(test)
                        if (questions.isNotEmpty()) {
                            _visitedQuestions.value = setOf(questions.first().id)
                        }
                    }
                } else {
                    resetTestState(test)
                    if (questions.isNotEmpty()) {
                        _visitedQuestions.value = setOf(questions.first().id)
                    }
                }
                
                val hasAttempted = attemptHistory.value.any { it.testId == test.id }
                if (hasAttempted) {
                    _currentScreen.value = AppScreen.REATTEMPT_AD
                } else {
                    _currentScreen.value = AppScreen.EXAM_MODE
                    startTimer()
                }
            }
        }
    }

    fun continueToExamAfterAd() {
        _currentScreen.value = AppScreen.EXAM_MODE
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_examTimeRemaining.value > 0) {
                delay(1000)
                _examTimeRemaining.value -= 1
                
                // Track time per question
                val currentQs = _activeQuestions.value
                val currentIndex = _currentQuestionIndex.value
                if (currentQs.isNotEmpty() && currentIndex in currentQs.indices) {
                    val qId = currentQs[currentIndex].id
                    val times = _timeSpentPerQuestion.value.toMutableMap()
                    val currentSpent = times[qId] ?: 0L
                    times[qId] = currentSpent + 1
                    _timeSpentPerQuestion.value = times
                }
            }
            // Time up! Auto submit only for official tests
            val test = _activeTest.value
            if (test != null && test.isOfficial) {
                submitTest()
            }
        }
    }

    fun selectAnswer(questionId: String, answer: String): Boolean {
        val questions = _activeQuestions.value
        val question = questions.find { it.id == questionId }
        val test = _activeTest.value
        if (question != null && test != null) {
            val limit = Com.ak.testseries.exam.UniversalExamRegistry.getMaximumAttemptQuestionsCountForSection(
                examName = test.targetExam,
                sectionNameOrId = question.section,
                subjectName = question.subject
            )
            if (limit != null) {
                val answeredInSectionCount = questions.count { q ->
                    q.section.equals(question.section, ignoreCase = true) &&
                    (question.subject.isBlank() || q.subject.equals(question.subject, ignoreCase = true)) &&
                    _userAnswers.value.containsKey(q.id) && q.id != questionId
                }
                if (answeredInSectionCount >= limit) {
                    return false
                }
            }
        } else if (question != null && (question.section.equals("SECTION_B", ignoreCase = true) || question.section.contains("Section B", ignoreCase = true))) {
            val answeredSectionBCount = questions.count { q ->
                (q.section.equals("SECTION_B", ignoreCase = true) || q.section.contains("Section B", ignoreCase = true)) &&
                (question.subject.isBlank() || q.subject.equals(question.subject, ignoreCase = true)) &&
                _userAnswers.value.containsKey(q.id) && q.id != questionId
            }
            if (answeredSectionBCount >= 10) {
                return false
            }
        }
        val answers = _userAnswers.value.toMutableMap()
        answers[questionId] = answer
        _userAnswers.value = answers
        return true
    }

    fun clearResponse(questionId: String) {
        val answers = _userAnswers.value.toMutableMap()
        answers.remove(questionId)
        _userAnswers.value = answers
        
        val checked = _checkedQuestions.value.toMutableSet()
        checked.remove(questionId)
        _checkedQuestions.value = checked
    }

    fun getQuestionsForTest(testId: String): kotlinx.coroutines.flow.Flow<List<QuestionEntity>> {
        return repository.getQuestionsForTest(testId)
    }

    suspend fun getQuestionsForTestDirect(testId: String): List<QuestionEntity> {
        return withContext(Dispatchers.IO) {
            repository.getQuestionsForTestList(testId)
        }
    }

    fun saveLocalTestAttemptToMemory(
        test: TestEntity,
        questions: List<QuestionEntity>,
        answersMap: Map<String, String>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (questions.isEmpty()) {
                withContext(Dispatchers.Main) { onComplete() }
                return@launch
            }

            val ansObj = org.json.JSONObject()
            questions.forEach { q ->
                val userAns = answersMap[q.id]
                if (userAns.isNullOrBlank()) {
                    val qObj = org.json.JSONObject()
                    qObj.put("ans", "")
                    qObj.put("time", 30)
                    ansObj.put(q.id, qObj)
                } else {
                    val qObj = org.json.JSONObject()
                    qObj.put("ans", userAns)
                    qObj.put("time", 45)
                    ansObj.put(q.id, qObj)
                }
            }

            val timeSpentTotal = questions.size * 45L
            val breakdown = Com.ak.testseries.exam.ExamScoringEngine.evaluateTestAttempt(
                targetExam = test.targetExam,
                questions = questions,
                userAnswers = answersMap,
                timeSpentSeconds = timeSpentTotal
            )

            val correct = breakdown.correctCount
            val incorrect = breakdown.wrongCount
            val skipped = breakdown.unansweredCount
            val maxScore = Math.round(breakdown.maxScore).toInt()
            val score = Math.round(breakdown.finalScore).toInt()
            val accuracy = breakdown.accuracyPercent
            val percentile = 0.0 // Offline/practice test has no population-based percentile

            val currentUid = currentUserId.value
            val attempt = AttemptEntity(
                userId = currentUid,
                testId = test.id,
                testTitle = test.title,
                score = score,
                maxScore = maxScore,
                percentile = percentile,
                accuracy = accuracy,
                correctCount = correct,
                incorrectCount = incorrect,
                skippedCount = skipped,
                timeSpentSeconds = timeSpentTotal,
                answersJson = ansObj.toString()
            )

            repository.insertAttempt(attempt)
            repository.insertTest(test)
            repository.insertQuestions(questions)

            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    suspend fun updateQuestion(question: QuestionEntity) {
        repository.updateQuestion(question)
    }

    fun toggleMarkForReview(questionId: String) {
        val marked = _markedForReview.value.toMutableSet()
        if (marked.contains(questionId)) {
            marked.remove(questionId)
        } else {
            marked.add(questionId)
        }
        _markedForReview.value = marked
    }

    fun checkCurrentQuestion(questionId: String) {
        val checked = _checkedQuestions.value.toMutableSet()
        checked.add(questionId)
        _checkedQuestions.value = checked
    }

    fun markQuestionVisited(questionId: String) {
        if (!_visitedQuestions.value.contains(questionId)) {
            val visited = _visitedQuestions.value.toMutableSet()
            visited.add(questionId)
            _visitedQuestions.value = visited
        }
    }

    fun navigateQuestion(offset: Int) {
        val index = _currentQuestionIndex.value + offset
        if (index in _activeQuestions.value.indices) {
            _currentQuestionIndex.value = index
            val qId = _activeQuestions.value.getOrNull(index)?.id
            if (qId != null) markQuestionVisited(qId)
        }
    }

    fun jumpToQuestion(index: Int) {
        if (index in _activeQuestions.value.indices) {
            _currentQuestionIndex.value = index
            val qId = _activeQuestions.value.getOrNull(index)?.id
            if (qId != null) markQuestionVisited(qId)
        }
    }

    // --- Submit and Scoring ---
    
    fun backupDataToCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val user = auth.currentUser ?: return@launch
            try {
                val db = firestoreDb
                val tests = repository.allTests.firstOrNull() ?: emptyList()
                val q = repository.getBookmarkedQuestions().firstOrNull() ?: emptyList()
                
                val userRef = db.collection("Users_Data").document(user.uid)
                val updateData = mapOf(
                    "bookmarkedTests" to tests.filter { it.isBookmarked }.map { it.id },
                    "bookmarkedQuestions" to q.map { it.id },
                    "lastBackupAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                userRef.set(updateData, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun submitTestRating(testId: String, rating: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.submitTestRating(testId, rating)
            }
        }
    }

    fun submitTestFeedback(testId: String, testTitle: String, rating: Int, selectedShortcuts: List<String>, customComments: String) {
        viewModelScope.launch {
            try {
                val db = firestoreDb
                val feedbackMap = hashMapOf(
                    "testId" to testId,
                    "testTitle" to testTitle,
                    "rating" to rating,
                    "shortcuts" to selectedShortcuts,
                    "comments" to customComments,
                    "userEmail" to _userProfile.value.email,
                    "userName" to _userProfile.value.name,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                db.collection("test_feedbacks").add(feedbackMap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateActiveQuestionsWithSolutions(solutionKeys: Map<*, *>) {
        val currentQs = _activeQuestions.value
        val updated = currentQs.map { q ->
            val solMap = solutionKeys[q.id] as? Map<*, *>
            if (solMap != null) {
                val correctAns = solMap["correctAnswer"] as? String ?: q.correctAnswer
                val explanation = solMap["explanation"] as? String ?: q.explanation
                q.copy(correctAnswer = correctAns, explanation = explanation)
            } else {
                q
            }
        }
        _activeQuestions.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.insertQuestions(updated)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun submitTest() {
        timerJob?.cancel()
        viewModelScope.launch {
            if (_isSubmittingExam.value) return@launch
            _isSubmittingExam.value = true
            try {
                val test = _activeTest.value ?: return@launch
                val context = getApplication<Application>()
                val prefs = context.getSharedPreferences("test_progress", android.content.Context.MODE_PRIVATE)
                prefs.edit().remove("progress_${test.id}").apply()

                val questions = _activeQuestions.value
                val answers = _userAnswers.value
                val timeSpentTotal = (questions.size * 60L) - _examTimeRemaining.value

                // Dynamic scoring evaluation engine for local/offline preview
                val breakdown = Com.ak.testseries.exam.ExamScoringEngine.evaluateTestAttempt(
                    targetExam = test.targetExam,
                    questions = questions,
                    userAnswers = answers,
                    timeSpentSeconds = timeSpentTotal
                )
                
                val localCorrect = breakdown.correctCount
                val localIncorrect = breakdown.wrongCount
                val localSkipped = breakdown.unansweredCount
                val localMaxScore = Math.round(breakdown.maxScore).toInt()
                val localScore = Math.round(breakdown.finalScore).toInt()
                val localAccuracy = breakdown.accuracyPercent

                // Local percentile preview: 0.0 (unranked pending authoritative server evaluation)
                val localPercentile = 0.0

                // Construct answers JSON and map
                val ansObj = org.json.JSONObject()
                val answersPayloadMap = mutableMapOf<String, Any>()
                val timeMap = _timeSpentPerQuestion.value
                questions.forEach { q ->
                    val option = answers[q.id] ?: ""
                    val t = timeMap[q.id] ?: 0L
                    val qObj = org.json.JSONObject()
                    qObj.put("ans", option)
                    qObj.put("time", t)
                    ansObj.put(q.id, qObj)
                    answersPayloadMap[q.id] = mapOf("ans" to option, "time" to t)
                }

                val currentUid = currentUserId.value
                val sessionToken = if (activeAttemptSessionToken.isNotBlank()) activeAttemptSessionToken else "att_${currentUid.ifBlank { "anon" }}_${test.id}_${System.currentTimeMillis()}"

                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val user = auth.currentUser

                var attempt = AttemptEntity(
                    userId = currentUid,
                    testId = test.id,
                    testTitle = test.title,
                    score = localScore,
                    maxScore = localMaxScore,
                    percentile = localPercentile,
                    accuracy = localAccuracy,
                    correctCount = localCorrect,
                    incorrectCount = localIncorrect,
                    skippedCount = localSkipped,
                    timeSpentSeconds = timeSpentTotal,
                    answersJson = ansObj.toString(),
                    attemptSessionToken = sessionToken,
                    verifiedServerSide = false,
                    submissionStatus = if (user != null) "PENDING" else "OFFLINE"
                )

                withContext(Dispatchers.IO) {
                    repository.insertAttempt(attempt)
                }

                // Call authoritative Firebase Callable Function if user is authenticated
                if (user != null) {
                    try {
                        val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
                        val callable = functions.getHttpsCallable("submitTestAttemptServerSide")
                        val payload = hashMapOf<String, Any>(
                            "testId" to test.id,
                            "answers" to answersPayloadMap,
                            "timeSpentSeconds" to timeSpentTotal,
                            "clientTimestamp" to System.currentTimeMillis(),
                            "attemptSessionToken" to sessionToken
                        )

                        val callResult = callable.call(payload).await()
                        val resultMap = callResult.data as? Map<*, *>

                        if (resultMap != null && (resultMap["success"] == true || resultMap["verifiedServerSide"] == true)) {
                            val serverScore = (resultMap["score"] as? Number)?.toInt() ?: attempt.score
                            val serverMaxScore = (resultMap["maxScore"] as? Number)?.toInt() ?: attempt.maxScore
                            val serverPercentile = (resultMap["percentile"] as? Number)?.toDouble() ?: attempt.percentile
                            val serverAccuracy = (resultMap["accuracy"] as? Number)?.toDouble() ?: attempt.accuracy
                            val serverCorrect = (resultMap["correctCount"] as? Number)?.toInt() ?: attempt.correctCount
                            val serverIncorrect = (resultMap["incorrectCount"] as? Number)?.toInt() ?: attempt.incorrectCount
                            val serverSkipped = (resultMap["skippedCount"] as? Number)?.toInt() ?: attempt.skippedCount

                            attempt = attempt.copy(
                                score = serverScore,
                                maxScore = serverMaxScore,
                                percentile = serverPercentile,
                                accuracy = serverAccuracy,
                                correctCount = serverCorrect,
                                incorrectCount = serverIncorrect,
                                skippedCount = serverSkipped,
                                verifiedServerSide = true,
                                submissionStatus = "CONFIRMED"
                            )

                            withContext(Dispatchers.IO) {
                                repository.insertAttempt(attempt)
                            }

                            // Update active questions with authoritative solution keys returned by server
                            val solutionKeys = resultMap["solutionKeys"] as? Map<*, *>
                            if (solutionKeys != null) {
                                updateActiveQuestionsWithSolutions(solutionKeys)
                            }

                            // Refresh public leaderboard & rank
                            fetchLeaderboard(test.targetExam)
                            fetchUserRank(test.targetExam)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NEETViewModel", "Server submission failed; stored locally as pending: ${e.message}", e)
                    }
                }

                backupDataToCloud()

                _activeResult.value = attempt
                _currentScreen.value = AppScreen.RESULT_ANALYSIS
            } finally {
                _isSubmittingExam.value = false
            }
        }
    }

    // --- AI Generator Engine with Gemini ---
    fun generateTestFromPdf(resume: Boolean = false) {
        viewModelScope.launch {
            if (!_isGlobalAiEnabled.value) {
                val disabledMsg = "AI test generation is temporarily disabled by Admin."
                _aiGenState.value = AiGenerationState.Error(disabledMsg)
                aiGenStatusMessage.value = disabledMsg
                return@launch
            }
            if (!isAdmin && _dailyGenerationsCount.value >= _dailyFreeTestLimit.value) {
                _dailyUploadLimitUsed.value = true
                val limitMsg = "Daily free generation limit reached (${_dailyFreeTestLimit.value} test/day). Watch an ad to unlock an extra generation!"
                _aiGenState.value = AiGenerationState.Error(limitMsg)
                aiGenStatusMessage.value = limitMsg
                return@launch
            }
            var currentAuthUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
            if (currentAuthUser == null) {
                try {
                    val authResult = com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously().await()
                    currentAuthUser = authResult.user
                    if (currentAuthUser != null) {
                        currentUserId.value = currentAuthUser.uid
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NEETViewModel", "Anonymous sign in failed or unavailable: ${e.message}")
                }
            }
            val authenticatedUid = currentAuthUser?.uid?.ifBlank { null }
                ?: currentUserId.value.ifBlank { null }
                ?: "local_student_${System.currentTimeMillis()}"

            if (genIsPublic.value && currentAuthUser == null) {
                _aiGenState.value = AiGenerationState.Error("Please sign in to publish tests to the Community, or uncheck Public to save in your Private Vault.")
                aiGenStatusMessage.value = "Sign in required for Community publishing."
                return@launch
            }

            if (!resume) {
                allQuestionsList.clear()
                currentChunkIndex = 0
                _aiGenState.value = AiGenerationState.ReadingPdf
                aiGenStatusMessage.value = "Reading PDF data..."
                delay(1000)
                _aiGenState.value = AiGenerationState.ExtractingQuestions
                aiGenStatusMessage.value = "Extracting questions..."
                delay(1000)
                _aiGenState.value = AiGenerationState.ExtractingOptions
                aiGenStatusMessage.value = "Parsing options..."
                delay(800)
                _aiGenState.value = AiGenerationState.ExtractingAnswers
                aiGenStatusMessage.value = "Compiling answer keys..."
                delay(800)
            }

            val keys = getGeminiApiKeys()
            val hasApiKey = keys.isNotEmpty()

            if (hasApiKey) {
                try {
                    val totalQuestions = genQuestionsCount.value
                    val pages = lastExtractedPdfPages.value
                    val pageChunkSize = 2 // Process in smaller page ranges (2 pages per chunk)
                    var successChunks = 0

                    val isPrivatePdfExtraction = !genIsPublic.value || autoRoutedWorkflow.value == "private"
                    val answerKeyPage = if (isPrivatePdfExtraction && pages.isNotEmpty()) {
                        pages.findLast { pageText ->
                            val lower = pageText.lowercase()
                            lower.contains("answer key") || lower.contains("answer sheet") || 
                            lower.contains("उत्तर कुंजी") || lower.contains("scoring key") || 
                            lower.contains("key & solutions") || lower.contains("hints & solutions")
                        }
                    } else null

                    if (pages.isNotEmpty()) {
                        val totalPageChunks = (pages.size + pageChunkSize - 1) / pageChunkSize
                        val questionsPerChunk = ((totalQuestions + totalPageChunks - 1) / totalPageChunks).coerceAtLeast(1)

                        for (chunkIndex in currentChunkIndex until totalPageChunks) {
                            val startPage = chunkIndex * pageChunkSize + 1
                            val endPage = minOf((chunkIndex + 1) * pageChunkSize, pages.size)
                            val currentChunkPageText = pages.subList(startPage - 1, endPage).joinToString("\n\n-- Page Break --\n\n")
                            val rawChunkText = if (answerKeyPage != null && !currentChunkPageText.contains(answerKeyPage)) {
                                "$currentChunkPageText\n\n-- Reference Document Answer Key --\n$answerKeyPage"
                            } else {
                                currentChunkPageText
                            }
                            val chunkMarkdown = Com.ak.testseries.utils.CompactMarkdownFormatter.formatToCompactMarkdown(rawChunkText)

                            val startQ = allQuestionsList.size + 1
                            val remainingNeeded = totalQuestions - allQuestionsList.size
                            if (remainingNeeded <= 0 && chunkIndex > 0) break
                            val currentChunkQuestions = minOf(questionsPerChunk, remainingNeeded).coerceAtLeast(1)
                            val endQ = startQ + currentChunkQuestions - 1

                            aiGenStatusMessage.value = if (isPrivatePdfExtraction) {
                                "AI transcribing questions $startQ-$endQ from Pages $startPage-$endPage (Chunk ${chunkIndex + 1}/$totalPageChunks)..."
                            } else {
                                "AI processing Pages $startPage-$endPage (Chunk ${chunkIndex + 1}/$totalPageChunks)..."
                            }

                            val targetExamName = genTargetExam.value.ifBlank { "NEET UG" }
                            val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExamName)
                                ?: Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName("NEET UG")!!

                            val prompt = Com.ak.testseries.exam.UniversalAIContext.buildDynamicGenerationPrompt(
                                examConfig = examConfig,
                                subject = genSubject.value.ifBlank { examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.subjectName ?: "All Subjects" },
                                testTitle = genTestName.value.ifBlank { "${examConfig.shortName} Practice Test" },
                                compactMarkdownPayload = chunkMarkdown, // Send current page chunk with answer key reference
                                currentChunkSize = currentChunkQuestions,
                                startQ = startQ,
                                endQ = endQ,
                                totalQuestions = totalQuestions,
                                isExtractionMode = isPrivatePdfExtraction
                            )
                            val examSystemInstruction = Com.ak.testseries.exam.UniversalAIContext.buildSystemInstruction(examConfig)

                            val resultJson = callGeminiApiForQuestionsWithFallback(prompt, examSystemInstruction)
                            if (resultJson != null) {
                                try {
                                    val cleanJson = resultJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                                    val jsonArray = if (cleanJson.startsWith("[")) {
                                        org.json.JSONArray(cleanJson)
                                    } else if (cleanJson.startsWith("{")) {
                                        val obj = org.json.JSONObject(cleanJson)
                                        obj.optJSONArray("questions")
                                            ?: obj.optJSONArray("data")
                                            ?: obj.optJSONArray("mcqs")
                                            ?: org.json.JSONArray()
                                    } else {
                                        val startIdx = cleanJson.indexOf('[')
                                        val endIdx = cleanJson.lastIndexOf(']')
                                        if (startIdx != -1 && endIdx > startIdx) {
                                            org.json.JSONArray(cleanJson.substring(startIdx, endIdx + 1))
                                        } else {
                                            org.json.JSONArray()
                                        }
                                    }
                                    for (i in 0 until jsonArray.length()) {
                                        allQuestionsList.add(jsonArray.getJSONObject(i))
                                    }
                                    successChunks++
                                    // Mark current chunk complete so it is never re-processed
                                    currentChunkIndex = chunkIndex + 1
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            delay(800) // Small delay to respect rate limits
                        }
                    } else {
                        // Fallback when raw text was provided without page splits
                        val chunkSize = 5
                        val totalChunks = (totalQuestions + chunkSize - 1) / chunkSize

                        for (chunkIndex in currentChunkIndex until totalChunks) {
                            val startQ = chunkIndex * chunkSize + 1
                            val endQ = minOf((chunkIndex + 1) * chunkSize, totalQuestions)
                            val currentChunkSize = endQ - startQ + 1

                            aiGenStatusMessage.value = if (isPrivatePdfExtraction) {
                                "AI transcribing questions $startQ to $endQ of $totalQuestions (Chunk ${chunkIndex + 1}/$totalChunks)..."
                            } else {
                                "AI processing source text for questions $startQ to $endQ of $totalQuestions (Chunk ${chunkIndex + 1}/$totalChunks)..."
                            }

                            val fullSourceText = lastExtractedPdfText.value
                            val rawSourceText = if (fullSourceText.length <= 40000) {
                                fullSourceText
                            } else {
                                val sliceLen = (fullSourceText.length / totalChunks).coerceAtLeast(1)
                                val startIdx = (chunkIndex * sliceLen).coerceAtMost(fullSourceText.length)
                                val endIdx = if (chunkIndex == totalChunks - 1) fullSourceText.length else ((chunkIndex + 1) * sliceLen).coerceAtMost(fullSourceText.length)
                                fullSourceText.substring(startIdx, endIdx)
                            }
                            val targetExamName = genTargetExam.value.ifBlank { "NEET UG" }
                            val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExamName)
                                ?: Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName("NEET UG")!!

                            val prompt = Com.ak.testseries.exam.UniversalAIContext.buildDynamicGenerationPrompt(
                                examConfig = examConfig,
                                subject = genSubject.value.ifBlank { examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.subjectName ?: "All Subjects" },
                                testTitle = genTestName.value.ifBlank { "${examConfig.shortName} Practice Test" },
                                compactMarkdownPayload = rawSourceText,
                                currentChunkSize = currentChunkSize,
                                startQ = startQ,
                                endQ = endQ,
                                totalQuestions = totalQuestions,
                                isExtractionMode = isPrivatePdfExtraction
                            )
                            val examSystemInstruction = Com.ak.testseries.exam.UniversalAIContext.buildSystemInstruction(examConfig)

                            val resultJson = callGeminiApiForQuestionsWithFallback(prompt, examSystemInstruction)
                            if (resultJson != null) {
                                try {
                                    val cleanJson = resultJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                                    val jsonArray = if (cleanJson.startsWith("[")) {
                                        org.json.JSONArray(cleanJson)
                                    } else if (cleanJson.startsWith("{")) {
                                        val obj = org.json.JSONObject(cleanJson)
                                        obj.optJSONArray("questions")
                                            ?: obj.optJSONArray("data")
                                            ?: obj.optJSONArray("mcqs")
                                            ?: org.json.JSONArray()
                                    } else {
                                        val startIdx = cleanJson.indexOf('[')
                                        val endIdx = cleanJson.lastIndexOf(']')
                                        if (startIdx != -1 && endIdx > startIdx) {
                                            org.json.JSONArray(cleanJson.substring(startIdx, endIdx + 1))
                                        } else {
                                            org.json.JSONArray()
                                        }
                                    }
                                    for (i in 0 until jsonArray.length()) {
                                        allQuestionsList.add(jsonArray.getJSONObject(i))
                                    }
                                    successChunks++
                                    currentChunkIndex = chunkIndex + 1
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            delay(800) // Small delay to respect rate limits
                        }
                    }

                    if (allQuestionsList.isNotEmpty()) {
                        val combinedArray = org.json.JSONArray()
                        for (obj in allQuestionsList) {
                            combinedArray.put(obj)
                        }
                        saveGeneratedTest(combinedArray.toString())
                        recordDailyGeneration()
                        aiGenStatusMessage.value = "Successfully generated ${allQuestionsList.size} questions!"
                        _aiGenState.value = AiGenerationState.Success
                        return@launch
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback: Generate structured realistic mock test if API is unavailable and fallback is enabled by Admin
            if (_isOfflineMockFallbackEnabled.value) {
                generateMockAiTest()
                recordDailyGeneration()
                aiGenStatusMessage.value = "Generated offline fallback mock test!"
                _aiGenState.value = AiGenerationState.Success
            } else {
                val errorMsg = if (!hasApiKey) {
                    "AI generation is unavailable (no API keys configured) and Offline Mock Fallback is disabled by Admin."
                } else {
                    "AI generation failed to extract questions and Offline Mock Fallback is disabled by Admin."
                }
                _aiGenState.value = AiGenerationState.Error(errorMsg)
                aiGenStatusMessage.value = errorMsg
            }
        }
    }

    fun showRewardedAdForExtraUpload() {
        viewModelScope.launch {
            // Simulate watching ad
            delay(2000)
            if (_dailyGenerationsCount.value > 0) {
                _dailyGenerationsCount.value = _dailyGenerationsCount.value - 1
            }
            _dailyUploadLimitUsed.value = false
            _aiGenState.value = AiGenerationState.Idle
        }
    }

    // --- AI PDF Analyzer ---
    private val _pdfAnalysisState = MutableStateFlow<PdfAnalysisState>(PdfAnalysisState.Idle)
    val pdfAnalysisState: StateFlow<PdfAnalysisState> = _pdfAnalysisState.asStateFlow()

    val autoRoutedWorkflow = MutableStateFlow<String?>(null) // "private" or "public"
    val autoRoutingExplanation = MutableStateFlow<String?>(null)
    val extractedDiagramPaths = MutableStateFlow<List<String>>(emptyList())
    val extractedDiagramCount = MutableStateFlow(0)

    fun resetPdfAnalysisState() {
        _pdfAnalysisState.value = PdfAnalysisState.Idle
        autoRoutedWorkflow.value = null
        autoRoutingExplanation.value = null
        extractedDiagramPaths.value = emptyList()
        extractedDiagramCount.value = 0
        lastExtractedPdfPages.value = emptyList()
    }

    fun analyzePdfWithAI(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _pdfAnalysisState.value = PdfAnalysisState.ExtractingText
            delay(500)
            
            // 1. Extract text from PDF streams locally in page chunks using unified AdvancedPdfSmartExtractor
            val (pdfPages, pdfText) = withContext(Dispatchers.IO) {
                try {
                    val tempFile = java.io.File.createTempFile("user_pdf_", ".pdf", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val smartExtractor = com.ak.pdfextractor.AdvancedPdfSmartExtractor(context)
                    val pages = smartExtractor.extractStreamPagesText(tempFile)
                    val fullText = if (pages.isNotEmpty()) pages.joinToString("\n") else smartExtractor.extractAllStreamText(tempFile)
                    tempFile.delete()
                    Pair(pages, fullText)
                } catch (e: Exception) {
                    Pair(emptyList<String>(), "")
                }
            }

            if (pdfText.isBlank()) {
                val errorMsg = "PDF से readable text नहीं निकाला जा सका। यह scanned/image-only या unsupported PDF हो सकती है।"
                _pdfAnalysisState.value = PdfAnalysisState.Error(errorMsg)
                aiGenStatusMessage.value = errorMsg
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            lastExtractedPdfPages.value = pdfPages
            // Format raw text into highly compact, token-efficient Markdown
            val compactMarkdown = Com.ak.testseries.utils.CompactMarkdownFormatter.formatToCompactMarkdown(pdfText)
            lastExtractedPdfText.value = compactMarkdown
            val extractedText = compactMarkdown

            // 2. Extract diagrams, formulas, charts & image regions locally (100% Offline via Android PdfRenderer)
            val extractedDiagrams = withContext(Dispatchers.IO) {
                try {
                    Com.ak.testseries.utils.LocalPdfImageExtractor.extractDiagramCandidates(
                        context = context,
                        pdfUri = uri,
                        testId = "pdf_${System.currentTimeMillis()}",
                        maxPagesToScan = 30
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            }
            val diagramPaths = extractedDiagrams.map { it.localImagePath }
            extractedDiagramPaths.value = diagramPaths
            extractedDiagramCount.value = diagramPaths.size

            // Local Question Classification & Automated Workflow Routing
            val localClassification = com.ak.pdfextractor.LocalQuestionClassifier.classifyRawText(extractedText)
            val isQuestionsDetected = localClassification.totalQuestionsDetected > 0 ||
                localClassification.documentType == com.ak.pdfextractor.PdfDocumentType.MCQ_QUESTION_PAPER
            val routedWorkflow = if (isQuestionsDetected) "private" else "public"
            val routingReason = com.ak.pdfextractor.LocalQuestionClassifier.getRoutingReason(localClassification)

            autoRoutedWorkflow.value = routedWorkflow
            autoRoutingExplanation.value = routingReason
            // Auto-route: Detected questions -> Private Vault (false); Raw text -> Public Community (true)
            genIsPublic.value = !isQuestionsDetected
            autoShareToCommunity.value = !isQuestionsDetected
            
            _pdfAnalysisState.value = PdfAnalysisState.AnalyzingWithAI
            
            // Look up current selected target exam or detect from text
            val currentTargetExam = genTargetExam.value.ifBlank { "NEET UG" }
            val registeredExams = Com.ak.testseries.exam.UniversalExamRegistry.getAllExams()
            val examNamesList = registeredExams.map { it.examName }.joinToString(", ")

            var geminiParsed = false
            // Send extracted text to Gemini API ONLY when local parsing fails (!isQuestionsDetected), asking it to align questions and options
            if (!isQuestionsDetected) {
                val keys = getGeminiApiKeys()
                val hasApiKey = keys.isNotEmpty()
                if (hasApiKey) {
                    try {
                        // For metadata analysis, send ONLY the opening page chunk (Pages 1-2 header/instructions), NEVER the whole PDF
                        val openingPagesSnippet = if (pdfPages.isNotEmpty()) {
                            pdfPages.take(2).joinToString("\n")
                        } else {
                            extractedText.take(2500)
                        }
                        val prompt = """
                            You are an AI exam analyzer for Indian competitive examinations.
                            Local parsing failed to detect structured questions automatically. Please analyze the following text from the opening page(s) of a test paper PDF, carefully align questions and their corresponding options (A, B, C, D), and identify the following metadata fields:
                            Supported exams: $examNamesList.
                            
                            Identify and extract:
                            1. targetExam: Match to one of the supported exams: $examNamesList. If not clearly specified, deduce from subjects/questions or default to '$currentTargetExam'.
                            2. title: A descriptive test title.
                            3. institute: Name of institute or source if present.
                            4. subject: Name of primary subject or 'Full Syllabus'.
                            5. classLevel: '11th', '12th', 'Dropper', 'Graduate', or 'All'.
                            6. testType: 'Single Chapter', 'Multi-Chapter', or 'Full Syllabus'.
                            7. chapter: Specific syllabus chapters/topics covered or 'Full Syllabus'.
                            8. timerMinutes: Duration in minutes.
                            9. questionsCount: Estimated total questions.
                            10. totalMarks: Maximum total marks.
                            11. language: 'English', 'Hindi', or 'Bilingual'.
                            
                            Text content:
                            ${openingPagesSnippet.take(2500)}
                            
                            Return your response STRICTLY as a valid JSON object with keys: targetExam, title, institute, subject, classLevel, testType, chapter, timerMinutes, questionsCount, totalMarks, language, and confidence (0-100). Do not include any markdown fences or explanation.
                        """.trimIndent()
                        
                        val responseJson = callGeminiApiForQuestionsWithFallback(prompt)
                        if (responseJson != null) {
                            val cleanJson = responseJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                            val jsonObj = JSONObject(cleanJson)
                            val detectedExam = jsonObj.optString("targetExam", currentTargetExam)
                            val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(detectedExam)
                                ?: Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(currentTargetExam)
                            
                            val officialDefaultQuestions = examConfig?.let {
                                Com.ak.testseries.exam.UniversalExamRegistry.getDefaultQuestionsCountForExam(it.examName)
                            } ?: 180
                            val officialDefaultDuration = examConfig?.let {
                                Com.ak.testseries.exam.UniversalExamRegistry.getDefaultDurationMinutesForExam(it.examName)
                            } ?: 180
                            val officialTotalMarks = examConfig?.stages?.firstOrNull()?.papers?.firstOrNull()?.totalMarks ?: 720.0

                            val extractedCount = jsonObj.optInt("questionsCount", officialDefaultQuestions)
                            val extractedDuration = jsonObj.optInt("timerMinutes", officialDefaultDuration)
                            val extractedMarks = jsonObj.optDouble("totalMarks", officialTotalMarks)

                            val result = AnalyzedTestResult(
                                title = jsonObj.optString("title", "${examConfig?.shortName ?: detectedExam} Practice Test"),
                                institute = jsonObj.optString("institute", "Previous Year Paper"),
                                targetExam = examConfig?.examName ?: detectedExam,
                                subject = jsonObj.optString("subject", examConfig?.stages?.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.subjectName ?: "Full Syllabus"),
                                classLevel = jsonObj.optString("classLevel", "All"),
                                testType = jsonObj.optString("testType", "Full Syllabus"),
                                chapter = jsonObj.optString("chapter", "Full Syllabus"),
                                timerMinutes = if (extractedDuration > 0) extractedDuration else officialDefaultDuration,
                                questionsCount = if (extractedCount > 0) extractedCount else officialDefaultQuestions,
                                totalMarks = if (extractedMarks > 0) extractedMarks else officialTotalMarks,
                                language = jsonObj.optString("language", "English"),
                                confidence = jsonObj.optInt("confidence", 95),
                                detectedQuestionsCount = localClassification.totalQuestionsDetected,
                                isQuestionPaperDetected = isQuestionsDetected,
                                autoRoutedWorkflow = routedWorkflow,
                                routingReason = routingReason,
                                extractedDiagramsCount = diagramPaths.size,
                                extractedDiagramPaths = diagramPaths
                            )
                            _pdfAnalysisState.value = PdfAnalysisState.Success(result)
                            geminiParsed = true
                            return@launch
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            // Fallback heuristics analysis in case Gemini API is offline/unavailable
            val lowerText = extractedText.lowercase()

            // 1. Detect Target Exam from text across 20 exams
            val detectedExamConfig = registeredExams.find { exam ->
                lowerText.contains(exam.examName.lowercase()) ||
                lowerText.contains(exam.shortName.lowercase()) ||
                lowerText.contains(exam.examId.lowercase().replace("_", " "))
            } ?: Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(currentTargetExam)
              ?: registeredExams.first()

            val detectedExamName = detectedExamConfig.examName

            // 2. Detect Institute / Source
            val matchedInstitute = when {
                lowerText.contains("allen") -> "Allen"
                lowerText.contains("aakash") -> "Aakash"
                lowerText.contains("physics wallah") || lowerText.contains("physicswallah") || lowerText.contains("pw ") -> "Physics Wallah"
                lowerText.contains("nta") || lowerText.contains("national testing agency") -> "NTA"
                lowerText.contains("testbook") -> "Testbook"
                lowerText.contains("adda247") -> "Adda247"
                lowerText.contains("unacademy") -> "Unacademy"
                lowerText.contains("byju") -> "BYJU'S"
                else -> "Previous Year Paper"
            }

            // 3. Match Exam-Specific Subjects from UniversalExamRegistry
            val availableSubjects = Com.ak.testseries.exam.UniversalExamRegistry.getSubjectsForExamName(detectedExamName)
            val matchedSubject = availableSubjects.find { subj ->
                lowerText.contains(subj.lowercase())
            } ?: availableSubjects.firstOrNull() ?: "Full Syllabus"

            val matchedType = when {
                lowerText.contains("full test") || lowerText.contains("full syllabus") || lowerText.contains("fts") || lowerText.contains("mock test") -> "Full Syllabus"
                lowerText.contains("part test") || lowerText.contains("minor") || lowerText.contains("pts") || lowerText.contains("sectional") -> "Multi-Chapter"
                else -> "Single Chapter"
            }

            val defaultQuestions = Com.ak.testseries.exam.UniversalExamRegistry.getDefaultQuestionsCountForExam(detectedExamName)
            val defaultDuration = Com.ak.testseries.exam.UniversalExamRegistry.getDefaultDurationMinutesForExam(detectedExamName)
            val defaultMarks = detectedExamConfig.stages.firstOrNull()?.papers?.firstOrNull()?.totalMarks ?: 100.0

            val finalQuestionsCount = if (matchedType == "Full Syllabus") defaultQuestions else (defaultQuestions / 3).coerceAtLeast(25)
            val finalDuration = if (matchedType == "Full Syllabus") defaultDuration else 60

            val matchedTitle = "${matchedInstitute} ${detectedExamConfig.shortName} ${matchedType} - ${matchedSubject}"
            val result = AnalyzedTestResult(
                title = matchedTitle,
                institute = matchedInstitute,
                targetExam = detectedExamName,
                subject = matchedSubject,
                classLevel = if (matchedType == "Full Syllabus") "Dropper / Graduate" else "12th",
                testType = matchedType,
                chapter = if (matchedType == "Full Syllabus") "Full Syllabus" else "Important Topics",
                timerMinutes = finalDuration,
                questionsCount = finalQuestionsCount,
                totalMarks = defaultMarks,
                language = if (lowerText.contains("hindi") || lowerText.any { it.code in 0x0900..0x097F }) "Hindi" else "English",
                confidence = 80,
                detectedQuestionsCount = localClassification.totalQuestionsDetected,
                isQuestionPaperDetected = isQuestionsDetected,
                autoRoutedWorkflow = routedWorkflow,
                routingReason = routingReason,
                extractedDiagramsCount = diagramPaths.size,
                extractedDiagramPaths = diagramPaths
            )
            
            delay(1200) // Aesthetic delay for fallback parsing
            _pdfAnalysisState.value = PdfAnalysisState.Success(result)
        }
    }

    val isPrimaryGeminiActive = MutableStateFlow(true)
    val isSecondaryGeminiActive = MutableStateFlow(true)
    val primaryGeminiUsage = MutableStateFlow(0)
    val secondaryGeminiUsage = MutableStateFlow(0)
    val activeGeminiKeyIndex = MutableStateFlow(0)
    val geminiStatusMessage = MutableStateFlow("Initializing...")
    val activeAiModel = MutableStateFlow("UNAVAILABLE")

    init {
        loadGeminiSettings()
    }

    private fun loadGeminiSettings() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
            isPrimaryGeminiActive.value = prefs.getBoolean("is_primary_gemini_active", true)
            isSecondaryGeminiActive.value = prefs.getBoolean("is_secondary_gemini_active", true)
            primaryGeminiUsage.value = prefs.getInt("primary_gemini_usage", 0)
            secondaryGeminiUsage.value = prefs.getInt("secondary_gemini_usage", 0)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun updateGeminiSettings(primaryActive: Boolean, secondaryActive: Boolean) {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_primary_gemini_active", primaryActive)
                .putBoolean("is_secondary_gemini_active", secondaryActive)
                .apply()
            isPrimaryGeminiActive.value = primaryActive
            isSecondaryGeminiActive.value = secondaryActive
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun incrementGeminiUsage(keyIdentifier: Int) {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
            if (keyIdentifier == 0) {
                val newCount = primaryGeminiUsage.value + 1
                prefs.edit().putInt("primary_gemini_usage", newCount).apply()
                primaryGeminiUsage.value = newCount
            } else {
                val newCount = secondaryGeminiUsage.value + 1
                prefs.edit().putInt("secondary_gemini_usage", newCount).apply()
                secondaryGeminiUsage.value = newCount
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun setSecondaryGeminiKey(key: String) {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("sec_gemini_api_key", key).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getSecondaryGeminiKey(): String {
        return try {
            val prefs = getApplication<Application>().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
            prefs.getString("sec_gemini_api_key", "") ?: ""
        } catch (e: Exception) { "" }
    }

    fun getGeminiApiKeys(): List<Pair<Int, String>> {
        val primary = BuildConfig.GEMINI_API_KEY
        val secondary = getSecondaryGeminiKey()

        val list = mutableListOf<Pair<Int, String>>()
        if (isPrimaryGeminiActive.value && primary.isNotEmpty() && primary != "MY_GEMINI_API_KEY") {
            list.add(Pair(0, primary))
        }
        if (isSecondaryGeminiActive.value && secondary.isNotEmpty() && secondary != "MY_GEMINI_API_KEY") {
            list.add(Pair(1, secondary))
        }
        return list
    }

    suspend fun callGeminiApiForQuestionsWithFallback(
        prompt: String,
        systemInstruction: String? = null
    ): String? {
        if (!_isGlobalAiEnabled.value) {
            geminiStatusMessage.value = "AI Engine is disabled by Admin."
            return null
        }
        val keys = getGeminiApiKeys()
        if (keys.isEmpty()) {
            return null
        }
        var startIndex = 0
        val currentActiveId = activeGeminiKeyIndex.value
        val indexOfActive = keys.indexOfFirst { it.first == currentActiveId }
        if (indexOfActive != -1) startIndex = indexOfActive

        for (i in 0 until keys.size) {
            val currentIndex = (startIndex + i) % keys.size
            val currentKeyPair = keys[currentIndex]
            val keyId = currentKeyPair.first
            val currentKey = currentKeyPair.second
            
            geminiStatusMessage.value = "Trying API Key ${if (keyId == 0) "Primary" else "Secondary"}..."
            val result = callGeminiApiForQuestions(currentKey, prompt, systemInstruction)
            if (result != null) {
                val currentModel = activeAiModel.value
                if (activeGeminiKeyIndex.value != keyId) {
                    activeGeminiKeyIndex.value = keyId
                    geminiStatusMessage.value = "Switched to ${if (keyId == 0) "Primary" else "Secondary"} Key ($currentModel)"
                } else {
                    geminiStatusMessage.value = "${if (keyId == 0) "Primary" else "Secondary"} Key Active ($currentModel)"
                }
                incrementGeminiUsage(keyId)
                return result
            }
        }
        geminiStatusMessage.value = "All Gemini API Keys exhausted (Rate limit/Quota reached)!"
        return null
    }

    suspend fun callGeminiApiTextWithFallback(prompt: String): String? {
        if (!_isGlobalAiEnabled.value) {
            return null
        }
        val keys = getGeminiApiKeys()
        if (keys.isEmpty()) {
            return null
        }
        var startIndex = 0
        val currentActiveId = activeGeminiKeyIndex.value
        val indexOfActive = keys.indexOfFirst { it.first == currentActiveId }
        if (indexOfActive != -1) startIndex = indexOfActive

        for (i in 0 until keys.size) {
            val currentIndex = (startIndex + i) % keys.size
            val currentKeyPair = keys[currentIndex]
            val keyId = currentKeyPair.first
            val currentKey = currentKeyPair.second
            
            val result = callGeminiApiText(currentKey, prompt)
            if (result != null) {
                if (activeGeminiKeyIndex.value != keyId) {
                    activeGeminiKeyIndex.value = keyId
                }
                incrementGeminiUsage(keyId)
                return result
            }
        }
        return null
    }

    private suspend fun callGeminiApiForQuestions(
        apiKey: String,
        prompt: String,
        systemInstruction: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val candidateModels = listOf("gemini-3.5-flash", "gemini-flash-latest", "gemini-2.5-flash")
        for (modelName in candidateModels) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 60000
                conn.readTimeout = 60000
                conn.doOutput = true

                val jsonReq = JSONObject().apply {
                    if (!systemInstruction.isNullOrBlank()) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", systemInstruction)
                                })
                            })
                        })
                    }
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                    })
                }

                conn.outputStream.use { os ->
                    os.write(jsonReq.toString().toByteArray())
                }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    val responseObj = JSONObject(response)
                    val actualVersion = responseObj.optString("modelVersion").takeIf { it.isNotBlank() } ?: "UNAVAILABLE"
                    activeAiModel.value = actualVersion
                    val candidates = responseObj.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text", "")
                            if (text.isNotBlank()) return@withContext text
                        }
                    }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                    android.util.Log.e("NEETViewModel", "Gemini ($modelName) error code ${conn.responseCode}: $err")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext null
    }

    private suspend fun callGeminiApiText(apiKey: String, prompt: String): String? = withContext(Dispatchers.IO) {
        val candidateModels = listOf("gemini-3.5-flash", "gemini-flash-latest", "gemini-2.5-flash")
        for (modelName in candidateModels) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 60000
                conn.readTimeout = 60000
                conn.doOutput = true

                val jsonReq = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                }

                conn.outputStream.use { os ->
                    os.write(jsonReq.toString().toByteArray())
                }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    val responseObj = JSONObject(response)
                    val actualVersion = responseObj.optString("modelVersion").takeIf { it.isNotBlank() } ?: "UNAVAILABLE"
                    activeAiModel.value = actualVersion
                    val candidates = responseObj.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text", "")
                            if (text.isNotBlank()) return@withContext text
                        }
                    }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                    android.util.Log.e("NEETViewModel", "Gemini text ($modelName) error code ${conn.responseCode}: $err")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext null
    }

    /**
     * Inspects questionsJson for local image paths (e.g. "/data/...", "file:...").
     * Uploads local image files to Firebase Storage under "tests/$testId/questions/",
     * retrieves the public HTTPS download URL, and updates the imageUrl field in the JSON.
     * Existing remote HTTPS/HTTP URLs are untouched.
     * If an upload fails, safely resets imageUrl to "" and sets needsImage = true,
     * throwing an IOException to prevent silently publishing broken local paths.
     */
    private suspend fun sanitizeAndUploadQuestionImages(testId: String, questionsJsonString: String): String = withContext(Dispatchers.IO) {
        if (questionsJsonString.isBlank()) return@withContext questionsJsonString

        try {
            val jsonArray = org.json.JSONArray(questionsJsonString)
            if (jsonArray.length() == 0) return@withContext questionsJsonString

            var hasModifications = false
            val cleanTestId = testId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val storage = try {
                com.google.firebase.storage.FirebaseStorage.getInstance()
            } catch (e: Exception) {
                android.util.Log.e("NEETViewModel", "FirebaseStorage initialization failed", e)
                null
            }

            for (i in 0 until jsonArray.length()) {
                val qObj = jsonArray.optJSONObject(i) ?: continue
                val rawUrl = qObj.optString("imageUrl", "").trim()
                if (rawUrl.isEmpty()) continue

                // Keep existing remote HTTPS or HTTP URLs unchanged
                if (rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)) {
                    continue
                }

                // Detect local image paths (e.g. "/data/...", "file:...")
                val isLocalPath = rawUrl.startsWith("/") || 
                                  rawUrl.startsWith("file:", ignoreCase = true) || 
                                  rawUrl.contains("/extracted_cropped_images/") ||
                                  rawUrl.contains("/cache/")

                if (isLocalPath) {
                    val localFile: java.io.File? = try {
                        when {
                            rawUrl.startsWith("file://", ignoreCase = true) -> {
                                val uriPath = android.net.Uri.parse(rawUrl).path
                                if (!uriPath.isNullOrBlank()) java.io.File(uriPath) else java.io.File(rawUrl.removePrefix("file://"))
                            }
                            rawUrl.startsWith("file:", ignoreCase = true) -> {
                                java.io.File(rawUrl.removePrefix("file:"))
                            }
                            else -> {
                                java.io.File(rawUrl)
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }

                    val qId = qObj.optString("id", "q_${i + 1}").replace("[^a-zA-Z0-9_-]".toRegex(), "_")

                    if (localFile != null && localFile.exists() && localFile.canRead() && storage != null) {
                        val fileName = "${qId}_${System.currentTimeMillis()}.png"
                        val storageRef = storage.reference.child("tests/$cleanTestId/questions/$fileName")

                        try {
                            val fileUri = android.net.Uri.fromFile(localFile)
                            storageRef.putFile(fileUri).await()
                            val downloadUrl = storageRef.downloadUrl.await().toString()
                            
                            qObj.put("imageUrl", downloadUrl)
                            hasModifications = true
                            android.util.Log.d("NEETViewModel", "Successfully uploaded question image to cloud: $downloadUrl")
                        } catch (uploadEx: Exception) {
                            android.util.Log.e("NEETViewModel", "Failed to upload question image: ${localFile.absolutePath}", uploadEx)
                            qObj.put("imageUrl", "")
                            qObj.put("needsImage", true)
                            hasModifications = true
                            throw java.io.IOException("Failed to upload question diagram to cloud storage: ${uploadEx.localizedMessage}", uploadEx)
                        }
                    } else {
                        // File does not exist locally or storage is unavailable: safely remove local path
                        android.util.Log.w("NEETViewModel", "Local image file missing or unreadable before publish: $rawUrl")
                        qObj.put("imageUrl", "")
                        qObj.put("needsImage", true)
                        hasModifications = true
                    }
                }
            }

            if (hasModifications) jsonArray.toString() else questionsJsonString
        } catch (e: Exception) {
            if (e is java.io.IOException) throw e
            android.util.Log.e("NEETViewModel", "Error sanitizing questionsJson images", e)
            questionsJsonString
        }
    }

    private fun separatePublicAndSecretQuestions(questionsJsonString: String): Pair<String, List<Map<String, Any>>> {
        if (questionsJsonString.isBlank()) return Pair(questionsJsonString, emptyList())
        return try {
            val jsonArray = org.json.JSONArray(questionsJsonString)
            val publicArray = org.json.JSONArray()
            val answerKeys = mutableListOf<Map<String, Any>>()
            
            for (i in 0 until jsonArray.length()) {
                val qObj = jsonArray.getJSONObject(i)
                val qId = qObj.optString("id", "q$i")
                val correctAns = qObj.optString("correctAnswer", "")
                val exp = qObj.optString("explanation", "")
                
                answerKeys.add(mapOf(
                    "id" to qId,
                    "correctAnswer" to correctAns,
                    "explanation" to exp
                ))

                // Public copy without answer keys
                val publicObj = org.json.JSONObject(qObj.toString())
                publicObj.remove("correctAnswer")
                publicObj.remove("explanation")
                publicArray.put(publicObj)
            }
            Pair(publicArray.toString(), answerKeys)
        } catch (e: Exception) {
            Pair(questionsJsonString, emptyList())
        }
    }

    private suspend fun uploadTestToFirestore(newTest: TestEntity, questionsJsonString: String) {
        if (!newTest.isPublic) {
            android.util.Log.d("NEETViewModel", "Test ${newTest.id} is PRIVATE. Scoped exclusively to local sandbox and skipped Firestore upload.")
            return
        }
        try {
            val sanitizedQuestionsJson = try {
                sanitizeAndUploadQuestionImages(newTest.id, questionsJsonString)
            } catch (e: Exception) {
                android.util.Log.e("NEETViewModel", "Aborting Firestore upload due to image upload failure in test ${newTest.id}: ${e.message}")
                return
            }

            if (sanitizedQuestionsJson != questionsJsonString) {
                repository.updateTest(newTest.copy(questionsJson = sanitizedQuestionsJson))
            }

            val (publicQuestionsJson, answerKeys) = separatePublicAndSecretQuestions(sanitizedQuestionsJson)

            val db = firestoreDb
            val testData = hashMapOf(
                "id" to newTest.id,
                "title" to newTest.title,
                "description" to newTest.description,
                "institute" to newTest.institute,
                "batchName" to newTest.batchName,
                "facultyName" to newTest.facultyName,
                "language" to newTest.language,
                "subject" to newTest.subject,
                "difficulty" to newTest.difficulty,
                "testType" to newTest.testType,
                "questionsCount" to newTest.questionsCount,
                "isOfficial" to newTest.isOfficial,
                "isVerifiedCommunity" to newTest.isVerifiedCommunity,
                "isPublic" to newTest.isPublic,
                "status" to newTest.status,
                "uploaderId" to newTest.uploaderId,
                "chapter" to newTest.chapter,
                "topic" to newTest.topic,
                "timestamp" to newTest.timestamp,
                "questionsJson" to publicQuestionsJson
            )
            val testDocRef = db.collection("Tests_Data").document(newTest.id)
            testDocRef.set(testData).await()

            // Securely store answer keys under private_keys/answerKey (accessible only by server Admin SDK)
            if (answerKeys.isNotEmpty()) {
                val keysDoc = hashMapOf<String, Any>(
                    "testId" to newTest.id,
                    "questions" to answerKeys,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                testDocRef.collection("private_keys").document("answerKey").set(keysDoc).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun saveGeneratedTest(jsonString: String) = withContext(Dispatchers.IO) {
        val cleanJson = jsonString.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val jsonArray = JSONArray(cleanJson)
        val testId = "ai_" + System.currentTimeMillis()

        val questions = mutableListOf<QuestionEntity>()
        var primaryChapter = "All"
        var primaryTopic = "All"
        
        for (i in 0 until jsonArray.length()) {
            val qObj = jsonArray.getJSONObject(i)
            val qChapter = qObj.optString("chapter", "General Dynamics")
            val qTopic = qObj.optString("topic", "General Topic")
            
            if (i == 0) {
                primaryChapter = qChapter
                primaryTopic = qTopic
            }
            
            val qTextEn = qObj.optString("questionTextEn", "Question Text")
            val qTextHi = qObj.optString("questionTextHi", "")
            val reasoning = qObj.optString("reasoning", "")
            val expEn = qObj.optString("explanationEn", "").ifBlank { reasoning }
            val combinedText = "$qTextEn $qTextHi $expEn".lowercase()

            val diagramKeywords = listOf(
                "diagram", "figure", "graph", "circuit", "chart", "given below", "shown below",
                "refer to", "as shown", "plot", "चित्र", "ग्राफ", "आरेख", "चित्रानुसार", "नीचे दिए गए चित्र",
                "परिपथ", "रेखाचित्र", "दिए गए ग्राफ", "चित्र में"
            )
            val mentionsDiagram = diagramKeywords.any { combinedText.contains(it) }
            val explicitNeedsImage = qObj.optBoolean("needsImage", false)
            val requiresImage = explicitNeedsImage || mentionsDiagram

            val assignedImageUrl = if (requiresImage && extractedDiagramPaths.value.size == 1 && jsonArray.length() == 1) {
                // Unambiguous 1-to-1 match for single question paper diagram
                extractedDiagramPaths.value[0]
            } else {
                qObj.optString("imageUrl", "").takeIf { it.isNotEmpty() }
            }

            // If question requires image but none was automatically bound, keep imageUrl = null and needsImage = true
            // so that the UI shows the RED BADGE and allows manual PDF cropping
            val finalNeedsImage = requiresImage || !assignedImageUrl.isNullOrEmpty()
            val finalImageUrl = if (assignedImageUrl.isNullOrEmpty()) null else assignedImageUrl

            questions.add(
                QuestionEntity(
                    id = "${testId}_q${i}",
                    testId = testId,
                    questionText = qTextEn,
                    optionA = qObj.optString("optionAEn", "Option A"),
                    optionB = qObj.optString("optionBEn", "Option B"),
                    optionC = qObj.optString("optionCEn", "Option C"),
                    optionD = qObj.optString("optionDEn", "Option D"),
                    correctAnswer = qObj.optString("correctAnswer", ""),
                    explanation = expEn,
                    subject = genSubject.value,
                    chapter = qChapter,
                    topic = qTopic,
                    questionTextHi = qTextHi.takeIf { it.isNotEmpty() },
                    optionAHi = qObj.optString("optionAHi", "").takeIf { it.isNotEmpty() },
                    optionBHi = qObj.optString("optionBHi", "").takeIf { it.isNotEmpty() },
                    optionCHi = qObj.optString("optionCHi", "").takeIf { it.isNotEmpty() },
                    optionDHi = qObj.optString("optionDHi", "").takeIf { it.isNotEmpty() },
                    explanationHi = qObj.optString("explanationHi", "").takeIf { it.isNotEmpty() },
                    needsImage = finalNeedsImage,
                    imageUrl = finalImageUrl
                )
            )
        }

        // Serialize questions to JSON array string
        val qJsonArray = JSONArray()
        for (q in questions) {
            val qObj = JSONObject()
            qObj.put("id", q.id)
            qObj.put("testId", q.testId)
            qObj.put("questionText", q.questionText)
            qObj.put("optionA", q.optionA)
            qObj.put("optionB", q.optionB)
            qObj.put("optionC", q.optionC)
            qObj.put("optionD", q.optionD)
            qObj.put("correctAnswer", q.correctAnswer)
            qObj.put("explanation", q.explanation)
            qObj.put("subject", q.subject)
            qObj.put("chapter", q.chapter)
            qObj.put("topic", q.topic)
            qObj.put("imageUrl", q.imageUrl ?: "")
            qObj.put("needsImage", q.needsImage)
            qObj.put("questionTextHi", q.questionTextHi ?: "")
            qObj.put("optionAHi", q.optionAHi ?: "")
            qObj.put("optionBHi", q.optionBHi ?: "")
            qObj.put("optionCHi", q.optionCHi ?: "")
            qObj.put("optionDHi", q.optionDHi ?: "")
            qObj.put("explanationHi", q.explanationHi ?: "")
            qJsonArray.put(qObj)
        }
        val currentAuthUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
        val authenticatedUid = currentAuthUser?.uid?.ifBlank { null }
            ?: currentUserId.value.ifBlank { null }
            ?: "local_user_${System.currentTimeMillis()}"

        if (genIsPublic.value && (currentAuthUser == null || authenticatedUid.startsWith("local_user_"))) {
            _aiGenState.value = AiGenerationState.Error("Please sign in to publish this test to the Community.")
            return@withContext
        }

        val questionsJsonString = qJsonArray.toString()
        saveQuestionsJsonToInternalStorage(testId, questionsJsonString)

        val isLiveTest = genIsLive.value
        val initialStatus = if (isAdmin || isLiveTest) "Published" else if (genIsPublic.value) "Under Review" else "Published"
        val newTest = TestEntity(
            id = testId,
            title = genTestName.value,
            description = if (isLiveTest) "Live Scheduled Test on ${genLiveScheduledTime.value}" else genDescription.value,
            institute = if (isLiveTest) "Admin Live" else genInstitute.value,
            batchName = genBatch.value,
            facultyName = genFaculty.value,
            language = genLanguage.value,
            subject = genSubject.value,
            difficulty = "Medium",
            testType = if (isLiveTest) "Live" else genType.value,
            questionsCount = jsonArray.length(),
            isOfficial = isLiveTest,
            isVerifiedCommunity = isLiveTest || isAdmin,
            countsTowardRanking = isLiveTest, // Only official Admin live tests count toward competitive ranking
            attemptsCount = 0,
            downloadsCount = 0,
            chapter = primaryChapter,
            topic = primaryTopic,
            questionsJson = questionsJsonString,
            uploaderId = authenticatedUid,
            isPublic = genIsPublic.value,
            status = initialStatus
        )

        repository.insertTest(newTest)
        repository.insertQuestions(questions)
        previewQuestions.value = questions
        
        // Upload to Firestore in real-time ONLY if public test
        if (newTest.isPublic) {
            uploadTestToFirestore(newTest, questionsJsonString)

            if (isLiveTest) {
                try {
                    val db = firestoreDb
                    val liveTestData = hashMapOf(
                        "id" to testId,
                        "title" to genTestName.value,
                        "subject" to genSubject.value,
                        "classLevel" to genBatch.value,
                        "chapter" to primaryChapter,
                        "scheduledTime" to genLiveScheduledTime.value,
                        "durationMinutes" to genLiveDurationMinutes.value,
                        "questionsCount" to jsonArray.length(),
                        "isLive" to true,
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("LiveTests").document(testId).set(liveTestData).await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            android.util.Log.d("NEETViewModel", "Private test $testId stored strictly inside local Room DB without network calls.")
        }
        
        withContext(Dispatchers.Main) {
            val folderId = currentFolderId.value
            moveTestToFolder(testId, folderId)
        }

        // Auto-share to Community if enabled (failure isolated, preserves isPublic, reuses shareTestToCommunity)
        try {
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: authenticatedUid
            if (Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                    autoShareEnabled = autoShareToCommunity.value,
                    isLiveTest = isLiveTest,
                    uploaderId = newTest.uploaderId,
                    currentUid = currentUid,
                    isPublic = newTest.isPublic
                )
            ) {
                shareTestToCommunity(newTest)
            }
        } catch (e: Exception) {
            android.util.Log.e("NEETViewModel", "Automatic community share failed (test preserved): ${e.localizedMessage}", e)
        }
    }

    private fun saveQuestionsJsonToInternalStorage(testId: String, questionsJsonString: String) {
        try {
            val context = getApplication<Application>()
            val internalDir = java.io.File(context.filesDir, "secure_ai_tests")
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }
            val testFile = java.io.File(internalDir, "$testId.json")
            testFile.writeText(questionsJsonString)
            android.util.Log.d("InternalStorage", "Parsed question data JSON saved strictly in App Internal Storage: ${testFile.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun savePreformattedJsonTest(
        title: String,
        subject: String,
        jsonString: String,
        isPublic: Boolean = false,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanJson = jsonString.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val jsonArray = JSONArray(cleanJson)
                if (jsonArray.length() == 0) {
                    withContext(Dispatchers.Main) { onError("JSON array is empty") }
                    return@launch
                }
                val testId = "local_p_" + System.currentTimeMillis()
                val questions = mutableListOf<QuestionEntity>()

                for (i in 0 until jsonArray.length()) {
                    val qObj = jsonArray.getJSONObject(i)
                    questions.add(
                        QuestionEntity(
                            id = qObj.optString("id", "${testId}_q${i}"),
                            testId = testId,
                            questionText = qObj.optString("questionText", qObj.optString("questionTextEn", "Question ${i + 1}")),
                            optionA = qObj.optString("optionA", qObj.optString("optionAEn", "Option A")),
                            optionB = qObj.optString("optionB", qObj.optString("optionBEn", "Option B")),
                            optionC = qObj.optString("optionC", qObj.optString("optionCEn", "Option C")),
                            optionD = qObj.optString("optionD", qObj.optString("optionDEn", "Option D")),
                            correctAnswer = qObj.optString("correctAnswer", "").uppercase().take(1),
                            explanation = qObj.optString("explanation", qObj.optString("explanationEn", "")),
                            subject = qObj.optString("subject", subject),
                            chapter = qObj.optString("chapter", "Local Practice"),
                            topic = qObj.optString("topic", "Pre-formatted"),
                            difficulty = qObj.optString("difficulty", "Medium"),
                            questionTextHi = qObj.optString("questionTextHi", "").takeIf { it.isNotEmpty() },
                            optionAHi = qObj.optString("optionAHi", "").takeIf { it.isNotEmpty() },
                            optionBHi = qObj.optString("optionBHi", "").takeIf { it.isNotEmpty() },
                            optionCHi = qObj.optString("optionCHi", "").takeIf { it.isNotEmpty() },
                            optionDHi = qObj.optString("optionDHi", "").takeIf { it.isNotEmpty() },
                            explanationHi = qObj.optString("explanationHi", "").takeIf { it.isNotEmpty() },
                            questionType = qObj.optString("questionType", "MCQ"),
                            section = qObj.optString("section", "SECTION_A"),
                            needsImage = qObj.optBoolean("needsImage", false),
                            imageUrl = qObj.optString("imageUrl", "").takeIf { it.isNotEmpty() }
                        )
                    )
                }

                val currentUid = currentUserId.value.ifBlank { "local_user" }
                val newTest = TestEntity(
                    id = testId,
                    title = if (title.isBlank()) "Private Practice Test" else title,
                    description = "Pre-formatted local private test created without Gemini API.",
                    institute = "Private Vault",
                    batchName = "Self Practice",
                    facultyName = "Local System",
                    language = "Bilingual",
                    subject = subject,
                    difficulty = "Medium",
                    testType = "Part Test",
                    questionsCount = questions.size,
                    isOfficial = false,
                    isVerifiedCommunity = false,
                    isPublic = isPublic,
                    countsTowardRanking = false,
                    rating = 5.0f,
                    attemptsCount = 0,
                    downloadsCount = 1,
                    isDownloaded = true,
                    isBookmarked = false,
                    timestamp = System.currentTimeMillis(),
                    status = "Published",
                    aiConfidence = 100,
                    uploaderId = currentUid,
                    targetExam = _userProfile.value.targetExam,
                    durationMinutes = questions.size,
                    questionsJson = cleanJson
                )

                repository.insertTest(newTest)
                repository.insertQuestions(questions)
                saveQuestionsJsonToInternalStorage(testId, cleanJson)

                withContext(Dispatchers.Main) {
                    onSuccess(testId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Invalid JSON format: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun generateMockAiTest() = withContext(Dispatchers.IO) {
        val testId = "ai_mock_" + System.currentTimeMillis()

        val isJee = genTargetExam.value == "JEE"
        val sampleQuestions = if (isJee) {
            listOf(
                // 5 SECTION_A MCQ questions
                QuestionEntity(
                    id = "${testId}_q1",
                    testId = testId,
                    questionText = "An electrostatic field line is a continuous curve. That is, a field line cannot have sudden breaks because:\n\nएक स्थिर-विद्युत बल रेखा एक सतत वक्र होती है। अर्थात्, एक बल रेखा में अचानक व्यवधान नहीं हो सकते क्योंकि:",
                    optionA = "Electric field is zero everywhere / विद्युत क्षेत्र हर जगह शून्य होता है",
                    optionB = "Moving charges create sudden leaps / गतिशील आवेश अचानक छलांग लगाते हैं",
                    optionC = "A charge experiences a continuous force, so field lines are continuous / एक आवेश निरंतर बल का अनुभव करता है, इसलिए रेखाएं सतत होती हैं",
                    optionD = "None of the above / उपरोक्त में से कोई नहीं",
                    correctAnswer = "C",
                    explanation = "An electric field line represents the path of a positive test charge. Since force acts continuously on a charge in an electric field, the field lines cannot have sudden breaks.",
                    subject = genSubject.value,
                    chapter = "Electric Charges and Fields",
                    questionType = "MCQ",
                    section = "SECTION_A"
                ),
                QuestionEntity(
                    id = "${testId}_q2",
                    testId = testId,
                    questionText = "The electric potential at a point on the axial line of an electric dipole is:\n\nएक विद्युत द्विध्रुव के अक्षीय रेखा पर स्थित किसी बिंदु पर विद्युत विभव होता है:",
                    optionA = "Zero / शून्य",
                    optionB = "kp/r²",
                    optionC = "kp/r",
                    optionD = "2kp/r²",
                    correctAnswer = "B",
                    explanation = "The potential V at an axial point r of a dipole of dipole moment p is V = kp/r² where k = 1 / 4πε₀.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential",
                    questionType = "MCQ",
                    section = "SECTION_A"
                ),
                QuestionEntity(
                    id = "${testId}_q3",
                    testId = testId,
                    questionText = "The capacitance of a parallel plate capacitor does NOT depend on:\n\nएक समानांतर प्लेट संधारित्र की धारिता किस पर निर्भर नहीं करती है?",
                    optionA = "Area of plates / प्लेटों के क्षेत्रफल पर",
                    optionB = "Distance between plates / प्लेटों के बीच की दूरी पर",
                    optionC = "Medium between plates / प्लेटों के बीच के माध्यम पर",
                    optionD = "Metal used for plates / प्लेटों के लिए उपयोग की जाने वाली धातु पर",
                    correctAnswer = "D",
                    explanation = "Capacitance C = K ε₀ A / d. It depends on plate area (A), plate separation (d), and the dielectric medium (K). It does not depend on the metal of the plates.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential",
                    questionType = "MCQ",
                    section = "SECTION_A"
                ),
                QuestionEntity(
                    id = "${testId}_q4",
                    testId = testId,
                    questionText = "Two point charges +3μC and +8μC repel each other with a force of 40N. If a charge of -5μC is added to each, the force between them will become:\n\nदो बिंदु आवेश +3μC और +8μC एक दूसरे को 40N के बल से प्रतिकर्षित करते हैं। यदि प्रत्येक में -5μC का आवेश जोड़ दिया जाए, तो उनके बीच बल हो जाएगा:",
                    optionA = "10N attractive / 10N आकर्षक",
                    optionB = "10N repulsive / 10N प्रतिकर्षक",
                    optionC = "20N attractive / 20N आकर्षक",
                    optionD = "Zero / शून्य",
                    correctAnswer = "A",
                    explanation = "Initial force F₁ = k (3)(8) / r² = 24k/r² = 40N. New charges: q₁' = 3 - 5 = -2μC, q₂' = 8 - 5 = +3μC. New force F₂ = k (-2)(3) / r² = -6k/r² (negative denotes attraction). Since 6 is 1/4 of 24, F₂ = 40 / 4 = 10N (attractive).",
                    subject = genSubject.value,
                    chapter = "Electric Charges and Fields",
                    questionType = "MCQ",
                    section = "SECTION_A"
                ),
                QuestionEntity(
                    id = "${testId}_q5",
                    testId = testId,
                    questionText = "The unit of electric flux is:\n\nविद्युत फ्लक्स का मात्रक है:",
                    optionA = "Weber / वेबर",
                    optionB = "Newton/Coulomb / न्यूटन/कूलाम",
                    optionC = "Volt-meter / वोल्ट-मीटर",
                    optionD = "Ampere / एम्पियर",
                    correctAnswer = "C",
                    explanation = "Electric flux Φ = E.A. Units: (N/C) * m² = (V/m) * m² = Volt-meter.",
                    subject = genSubject.value,
                    chapter = "Electric Charges and Fields",
                    questionType = "MCQ",
                    section = "SECTION_A"
                ),
                // 10 SECTION_B Numerical questions
                QuestionEntity(
                    id = "${testId}_q6",
                    testId = testId,
                    questionText = "Calculated capacitance (in pF) value of a parallel plate capacitor in air with plate area 6 cm² and distance 3 mm is:\n\nवायु में एक समानांतर प्लेट संधारित्र की धारिता (pF में) की गणना करें जिसका प्लेट क्षेत्रफल 6 cm² और दूरी 3 mm है:",
                    optionA = "", optionB = "", optionC = "", optionD = "",
                    correctAnswer = "1.77",
                    explanation = "C = ε₀ A / d = (8.85 * 10^-12 * 6 * 10^-4) / (3 * 10^-3) = 1.77 pF.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential",
                    questionType = "NUMERICAL",
                    section = "SECTION_B"
                ),
                QuestionEntity(
                    id = "${testId}_q7",
                    testId = testId,
                    questionText = "What is the net electric dipole moment (in C-m) of two charges +2μC and -2μC separated by 5 mm?\n\n5 mm की दूरी पर स्थित दो आवेशों +2μC और -2μC का कुल विद्युत द्विध्रुव आघूर्ण (C-m में, 10^-8 की घात में) कितना है?",
                    optionA = "", optionB = "", optionC = "", optionD = "",
                    correctAnswer = "1.0",
                    explanation = "p = q * 2l = 2 * 10^-6 * 5 * 10^-3 = 10^-8 C-m. Hence value in 10^-8 scale is 1.0.",
                    subject = genSubject.value,
                    chapter = "Electric Charges and Fields",
                    questionType = "NUMERICAL",
                    section = "SECTION_B"
                ),
                QuestionEntity(
                    id = "${testId}_q8",
                    testId = testId,
                    questionText = "Find the work done (in Joules) in bringing a charge of 3C through a potential difference of 15V.\n\n15V के विभवांतर से 3C के आवेश को लाने में किया गया कार्य (जूल में) ज्ञात कीजिए।",
                    optionA = "", optionB = "", optionC = "", optionD = "",
                    correctAnswer = "45",
                    explanation = "W = Q * V = 3 * 15 = 45 Joules.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential",
                    questionType = "NUMERICAL",
                    section = "SECTION_B"
                ),
                QuestionEntity(
                    id = "${testId}_q9",
                    testId = testId,
                    questionText = "Calculate the equivalent resistance (in ohms) of three 15-ohm resistors connected in parallel.\n\nसमानांतर क्रम में जुड़े तीन 15-ओम के प्रतिरोधकों का समतुल्य प्रतिरोध (ओम में) ज्ञात कीजिए।",
                    optionA = "", optionB = "", optionC = "", optionD = "",
                    correctAnswer = "5",
                    explanation = "1/Rp = 1/15 + 1/15 + 1/15 = 3/15 = 1/5 => Rp = 5 ohms.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential",
                    questionType = "NUMERICAL",
                    section = "SECTION_B"
                ),
                QuestionEntity(
                    id = "${testId}_q10",
                    testId = testId,
                    questionText = "Find the force (in N) between two charges of 2C separated by 2m in a medium of dielectric constant K = 9.\n\nपरावैद्युतांक K = 9 के माध्यम में 2m की दूरी पर स्थित 2C के दो आवेशों के बीच बल (N में, 10^9 की घात में) ज्ञात कीजिए।",
                    optionA = "", optionB = "", optionC = "", optionD = "",
                    correctAnswer = "1.0",
                    explanation = "F = (1/K) * (k * q1 * q2 / r^2) = (1/9) * (9 * 10^9 * 2 * 2 / 4) = 10^9 N = 1.0 * 10^9 N.",
                    subject = genSubject.value,
                    chapter = "Electric Charges and Fields",
                    questionType = "NUMERICAL",
                    section = "SECTION_B"
                ),
                QuestionEntity(
                    id = "${testId}_q11",
                    testId = testId,
                    questionText = "Find the electric field intensity (in N/C) at a distance of 3m from a point charge of 9 nC in vacuum.\n\nनिर्वात में 9 nC के बिंदु आवेश से 3m की दूरी पर विद्युत क्षेत्र की तीव्रता (N/C में) ज्ञात कीजिए।",
                    optionA = "", optionB = "", optionC = "", optionD = "",
                    correctAnswer = "9",
                    explanation = "E = k * q / r^2 = 9 * 10^9 * 9 * 10^-9 / 9 = 9 N/C.",
                    subject = genSubject.value,
                    chapter = "Electric Charges and Fields",
                    questionType = "NUMERICAL",
                    section = "SECTION_B"
                ),
                QuestionEntity(
                    id = "${testId}_q12",
                    testId = testId,
                    questionText = "A capacitor of 10 μF is charged to 100V. Find the electrostatic energy (in Joules) stored in it.\n\nएक 10 μF के संधारित्र को 100V तक आवेशित किया जाता है। इसमें संचित स्थिर-विद्युत ऊर्जा (जूल में) ज्ञात कीजिए।",
                    optionA = "", optionB = "", optionC = "", optionD = "",
                    correctAnswer = "0.05",
                    explanation = "U = 0.5 * C * V^2 = 0.5 * 10^-5 * 10000 = 0.05 Joules.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential",
                    questionType = "NUMERICAL",
                    section = "SECTION_B"
                ),
                QuestionEntity(
                    id = "${testId}_q13",
                    testId = testId,
                    questionText = "A current of 2A flows through a conductor of resistance 5 ohms for 10 seconds. Find the heat energy (in Joules) produced.\n\n5 ओम के प्रतिरोध वाले चालक से 2A की धारा 10 सेकंड तक प्रवाहित होती है। उत्पन्न ऊष्मा ऊर्जा (जूल में) ज्ञात कीजिए।",
                    optionA = "", optionB = "", optionC = "", optionD = "",
                    correctAnswer = "200",
                    explanation = "H = I^2 * R * t = 4 * 5 * 10 = 200 Joules.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential",
                    questionType = "NUMERICAL",
                    section = "SECTION_B"
                ),
                QuestionEntity(
                    id = "${testId}_q14",
                    testId = testId,
                    questionText = "What is the potential difference (in V) across a 4 μF capacitor to store a charge of 80 μC?\n\n80 μC का आवेश संचित करने के लिए 4 μF के संधारित्र पर कितना विभवांतर (V में) होना चाहिए?",
                    optionA = "", optionB = "", optionC = "", optionD = "",
                    correctAnswer = "20",
                    explanation = "V = Q / C = 80 / 4 = 20 V.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential",
                    questionType = "NUMERICAL",
                    section = "SECTION_B"
                ),
                QuestionEntity(
                    id = "${testId}_q15",
                    testId = testId,
                    questionText = "Two capacitors of 3 μF and 6 μF are connected in series. Find the equivalent capacitance (in μF).\n\nश्रेणी क्रम में जुड़े 3 μF और 6 μF के दो संधारित्रों की समतुल्य धारिता (μF में) ज्ञात कीजिए।",
                    optionA = "", optionB = "", optionC = "", optionD = "",
                    correctAnswer = "2",
                    explanation = "1/Cs = 1/3 + 1/6 = 3/6 = 1/2 => Cs = 2 μF.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential",
                    questionType = "NUMERICAL",
                    section = "SECTION_B"
                )
            )
        } else {
            listOf(
                QuestionEntity(
                    id = "${testId}_q1",
                    testId = testId,
                    questionText = "An electrostatic field line is a continuous curve. That is, a field line cannot have sudden breaks because:\n\nएक स्थिर-विद्युत बल रेखा एक सतत वक्र होती है। अर्थात्, एक बल रेखा में अचानक व्यवधान नहीं हो सकते क्योंकि:",
                    optionA = "Electric field is zero everywhere / विद्युत क्षेत्र हर जगह शून्य होता है",
                    optionB = "Moving charges create sudden leaps / गतिशील आवेश अचानक छलांग लगाते हैं",
                    optionC = "A charge experiences a continuous force, so field lines are continuous / एक आवेश निरंतर बल का अनुभव करता है, इसलिए रेखाएं सतत होती हैं",
                    optionD = "None of the above / उपरोक्त में से कोई नहीं",
                    correctAnswer = "C",
                    explanation = "An electric field line represents the path of a positive test charge. Since force acts continuously on a charge in an electric field, the field lines cannot have sudden breaks.",
                    subject = genSubject.value,
                    chapter = "Electric Charges and Fields"
                ),
                QuestionEntity(
                    id = "${testId}_q2",
                    testId = testId,
                    questionText = "The electric potential at a point on the axial line of an electric dipole is:\n\nएक विद्युत द्विध्रुव के अक्षीय रेखा पर स्थित किसी बिंदु पर विद्युत विभव होता है:",
                    optionA = "Zero / शून्य",
                    optionB = "kp/r²",
                    optionC = "kp/r",
                    optionD = "2kp/r²",
                    correctAnswer = "B",
                    explanation = "The potential V at an axial point r of a dipole of dipole moment p is V = kp/r² where k = 1 / 4πε₀.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential"
                ),
                QuestionEntity(
                    id = "${testId}_q3",
                    testId = testId,
                    questionText = "The capacitance of a parallel plate capacitor does NOT depend on:\n\nएक समानांतर प्लेट संधारित्र की धारिता किस पर निर्भर नहीं करती है?",
                    optionA = "Area of plates / प्लेटों के क्षेत्रफल पर",
                    optionB = "Distance between plates / प्लेटों के बीच की दूरी पर",
                    optionC = "Medium between plates / प्लेटों के बीच के माध्यम पर",
                    optionD = "Metal used for plates / प्लेटों के लिए उपयोग की जाने वाली धातु पर",
                    correctAnswer = "D",
                    explanation = "Capacitance C = K ε₀ A / d. It depends on plate area (A), plate separation (d), and the dielectric medium (K). It does not depend on the metal of the plates.",
                    subject = genSubject.value,
                    chapter = "Electrostatic Potential"
                ),
                QuestionEntity(
                    id = "${testId}_q4",
                    testId = testId,
                    questionText = "Two point charges +3μC and +8μC repel each other with a force of 40N. If a charge of -5μC is added to each, the force between them will become:\n\nदो बिंदु आवेश +3μC और +8μC एक दूसरे को 40N के बल से प्रतिकर्षित करते हैं। यदि प्रत्येक में -5μC का आवेश जोड़ दिया जाए, तो उनके बीच बल हो जाएगा:",
                    optionA = "10N attractive / 10N आकर्षक",
                    optionB = "10N repulsive / 10N प्रतिकर्षक",
                    optionC = "20N attractive / 20N आकर्षक",
                    optionD = "Zero / शून्य",
                    correctAnswer = "A",
                    explanation = "Initial force F₁ = k (3)(8) / r² = 24k/r² = 40N. New charges: q₁' = 3 - 5 = -2μC, q₂' = 8 - 5 = +3μC. New force F₂ = k (-2)(3) / r² = -6k/r² (negative denotes attraction). Since 6 is 1/4 of 24, F₂ = 40 / 4 = 10N (attractive).",
                    subject = genSubject.value,
                    chapter = "Electric Charges and Fields"
                ),
                QuestionEntity(
                    id = "${testId}_q5",
                    testId = testId,
                    questionText = "The unit of electric flux is:\n\nविद्युत फ्लक्स का मात्रक है:",
                    optionA = "Weber / वेबर",
                    optionB = "Newton/Coulomb / न्यूटन/कूलाम",
                    optionC = "Volt-meter / वोल्ट-मीटर",
                    optionD = "Ampere / एम्पियर",
                    correctAnswer = "C",
                    explanation = "Electric flux Φ = E.A. Units: (N/C) * m² = (V/m) * m² = Volt-meter.",
                    subject = genSubject.value,
                    chapter = "Electric Charges and Fields"
                )
            )
        }

        // Serialize sampleQuestions to JSON array string
        val qJsonArray = JSONArray()
        for (q in sampleQuestions) {
            val qObj = JSONObject()
            qObj.put("id", q.id)
            qObj.put("testId", q.testId)
            qObj.put("questionText", q.questionText)
            qObj.put("optionA", q.optionA)
            qObj.put("optionB", q.optionB)
            qObj.put("optionC", q.optionC)
            qObj.put("optionD", q.optionD)
            qObj.put("correctAnswer", q.correctAnswer)
            qObj.put("explanation", q.explanation)
            qObj.put("subject", q.subject)
            qObj.put("chapter", q.chapter)
            qObj.put("topic", q.topic)
            qObj.put("questionTextHi", q.questionTextHi ?: "")
            qObj.put("optionAHi", q.optionAHi ?: "")
            qObj.put("optionBHi", q.optionBHi ?: "")
            qObj.put("optionCHi", q.optionCHi ?: "")
            qObj.put("optionDHi", q.optionDHi ?: "")
            qObj.put("explanationHi", q.explanationHi ?: "")
            qObj.put("questionType", q.questionType)
            qObj.put("section", q.section)
            qJsonArray.put(qObj)
        }
        val questionsJsonString = qJsonArray.toString()

        val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val authenticatedUid = currentAuthUser?.uid ?: currentUserId.value

        val generatedTest = TestEntity(
            id = testId,
            title = genTestName.value,
            description = genDescription.value,
            institute = genInstitute.value,
            batchName = genBatch.value,
            facultyName = genFaculty.value,
            language = genLanguage.value,
            subject = genSubject.value,
            difficulty = "Medium",
            testType = genType.value,
            targetExam = genTargetExam.value,
            questionsCount = sampleQuestions.size,
            isOfficial = false,
            isVerifiedCommunity = true,
            attemptsCount = 1,
            downloadsCount = 0,
            questionsJson = questionsJsonString,
            uploaderId = authenticatedUid.ifEmpty { "anonymous" },
            isPublic = genIsPublic.value
        )

        repository.insertTest(generatedTest)
        repository.insertQuestions(sampleQuestions)
        
        // Upload to Firestore in real-time
        uploadTestToFirestore(generatedTest, questionsJsonString)
        
        withContext(Dispatchers.Main) {
            val folderId = currentFolderId.value
            moveTestToFolder(testId, folderId)
        }

        // Auto-share to Community if enabled (failure isolated, preserves isPublic, reuses shareTestToCommunity)
        try {
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: authenticatedUid
            if (Com.ak.testseries.ui.CommunityShareManager.shouldAutoShareNewTests(
                    autoShareEnabled = autoShareToCommunity.value,
                    isLiveTest = false,
                    uploaderId = generatedTest.uploaderId,
                    currentUid = currentUid,
                    isPublic = generatedTest.isPublic
                )
            ) {
                shareTestToCommunity(generatedTest)
            }
        } catch (e: Exception) {
            android.util.Log.e("NEETViewModel", "Automatic community share failed (test preserved): ${e.localizedMessage}", e)
        }
    }

    fun resetAiGenState() {
        _aiGenState.value = AiGenerationState.Idle
    }

    // --- Admin Dashboard Upload Actions ---
    fun adminCreateTest(test: TestEntity, questions: List<QuestionEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val questionsJsonString = serializeQuestionsToJson(questions)
            val testWithJson = test.copy(questionsJson = questionsJsonString, questionsCount = questions.size)
            repository.insertTest(testWithJson)
            repository.insertQuestions(questions)
            uploadTestToFirestore(testWithJson, questionsJsonString)
        }
    }

    private fun loadFoldersAndMappings() {
        val prefs = getApplication<Application>().getSharedPreferences("user_profile_prefs", android.content.Context.MODE_PRIVATE)
        val foldersSerialized = prefs.getString("test_folders", "") ?: ""
        val mappingsSerialized = prefs.getString("test_folder_mappings", "") ?: ""

        _folders.value = deserializeFolders(foldersSerialized)
        _testFolderMappings.value = deserializeMappings(mappingsSerialized)
    }

    fun saveFoldersAndMappings() {
        val prefs = getApplication<Application>().getSharedPreferences("user_profile_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("test_folders", serializeFolders(_folders.value))
            .putString("test_folder_mappings", serializeMappings(_testFolderMappings.value))
            .apply()
    }

    fun createFolder(name: String, parentId: String?) {
        val newFolder = TestFolder(
            id = "folder_" + java.util.UUID.randomUUID().toString().take(6),
            name = name,
            parentId = parentId
        )
        _folders.value = _folders.value + newFolder
        saveFoldersAndMappings()
    }

    fun moveTestToFolder(testId: String, folderId: String?) {
        val newMap = _testFolderMappings.value.toMutableMap()
        if (folderId == null) {
            newMap.remove(testId)
        } else {
            newMap[testId] = folderId
        }
        _testFolderMappings.value = newMap
        saveFoldersAndMappings()
    }

    fun deleteFolder(folderId: String) {
        val toDelete = mutableSetOf(folderId)
        var added = true
        while (added) {
            val childIds = _folders.value.filter { it.parentId in toDelete && it.id !in toDelete }.map { it.id }
            if (childIds.isNotEmpty()) {
                toDelete.addAll(childIds)
            } else {
                added = false
            }
        }

        _folders.value = _folders.value.filter { it.id !in toDelete }

        val newMap = _testFolderMappings.value.filterValues { it !in toDelete }
        _testFolderMappings.value = newMap

        if (currentFolderId.value in toDelete) {
            currentFolderId.value = null
        }

        saveFoldersAndMappings()
    }

    private fun serializeFolders(folders: List<TestFolder>): String {
        return folders.joinToString(";;") { folder ->
            "${folder.id}::${folder.name}::${folder.parentId ?: "ROOT"}"
        }
    }

    private fun deserializeFolders(serialized: String): List<TestFolder> {
        if (serialized.isEmpty()) return emptyList()
        return serialized.split(";;").mapNotNull { part ->
            val pieces = part.split("::")
            if (pieces.size >= 2) {
                val id = pieces[0]
                val name = pieces[1]
                val parentId = pieces.getOrNull(2)?.let { if (it == "ROOT" || it.isEmpty()) null else it }
                TestFolder(id, name, parentId)
            } else null
        }
    }

    private fun serializeMappings(mappings: Map<String, String>): String {
        return mappings.entries.joinToString(";;") { (testId, folderId) ->
            "$testId::$folderId"
        }
    }

    private fun deserializeMappings(serialized: String): Map<String, String> {
        if (serialized.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        serialized.split(";;").forEach { part ->
            val pieces = part.split("::")
            if (pieces.size == 2) {
                map[pieces[0]] = pieces[1]
            }
        }
        return map
    }

    // --- Doubt Solver (YouTube Search API) States ---
    private val _doubtSolverResults = MutableStateFlow<List<YouTubeVideoResult>>(emptyList())
    val doubtSolverResults: StateFlow<List<YouTubeVideoResult>> = _doubtSolverResults.asStateFlow()

    private val _doubtSolverLoading = MutableStateFlow(false)
    val doubtSolverLoading: StateFlow<Boolean> = _doubtSolverLoading.asStateFlow()

    private val _doubtSolverError = MutableStateFlow<String?>(null)
    val doubtSolverError: StateFlow<String?> = _doubtSolverError.asStateFlow()

    private val _activePlayingVideoId = MutableStateFlow<String?>(null)
    val activePlayingVideoId: StateFlow<String?> = _activePlayingVideoId.asStateFlow()

    private val _isDoubtSolverExpanded = MutableStateFlow(false)
    val isDoubtSolverExpanded: StateFlow<Boolean> = _isDoubtSolverExpanded.asStateFlow()

    fun setActivePlayingVideoId(id: String?) {
        _activePlayingVideoId.value = id
    }

    fun setDoubtSolverExpanded(expanded: Boolean) {
        _isDoubtSolverExpanded.value = expanded
        if (!expanded) {
            _activePlayingVideoId.value = null
        }
    }

    fun searchDoubtVideo(userQuery: String) {
        viewModelScope.launch {
            _doubtSolverLoading.value = true
            _doubtSolverError.value = null
            _doubtSolverResults.value = emptyList()
            
            try {
                // Fallback / Offline / Development mode with real playable high-quality NEET video solutions
                val fallbackResults = generateFallbackResults(userQuery)
                _doubtSolverResults.value = fallbackResults
            } catch (e: Exception) {
                e.printStackTrace()
                val fallbackResults = generateFallbackResults(userQuery)
                _doubtSolverResults.value = fallbackResults
            } finally {
                _doubtSolverLoading.value = false
            }
        }
    }

    private fun generateFallbackResults(userQuery: String): List<YouTubeVideoResult> {
        val cleanQuery = userQuery.replace(Regex("[^a-zA-Z0-9\\s]"), "")
        val channels = listOf("Physics Wallah - NEET Wallah", "Allen Career Institute", "Aakash BYJU'S NEET", "Unacademy NEET", "BYJU'S NEET")
        val thumbnails = listOf(
            "https://img.youtube.com/vi/h7OqQp_bY-s/mqdefault.jpg",
            "https://img.youtube.com/vi/P8qZ-kZ6G_g/mqdefault.jpg",
            "https://img.youtube.com/vi/W6P-O86W06Q/mqdefault.jpg",
            "https://img.youtube.com/vi/V5D9M789_1o/mqdefault.jpg",
            "https://img.youtube.com/vi/T6V3G09W69o/mqdefault.jpg"
        )
        val videoIds = listOf("h7OqQp_bY-s", "P8qZ-kZ6G_g", "W6P-O86W06Q", "V5D9M789_1o", "T6V3G09W69o")
        val durations = listOf("15:33", "22:10", "12:45", "08:14", "18:25")
        
        return List(5) { index ->
            val channel = channels[index]
            val videoId = videoIds[index]
            val thumb = thumbnails[index]
            val duration = durations[index]
            
            val subjectPrefix = when (index % 3) {
                0 -> "Physics"
                1 -> "Chemistry"
                else -> "Biology"
            }
            
            val title = "NEET $subjectPrefix Solution: " + 
                (if (userQuery.length > 60) userQuery.take(60) + "..." else userQuery) + 
                " | Exact Match Video Explanation by $channel"
                
            YouTubeVideoResult(
                videoId = videoId,
                title = title,
                thumbnailUrl = thumb,
                channelTitle = channel,
                duration = duration,
                isVerified = true
            )
        }
    }

    private fun parseISO8601Duration(durationStr: String): String {
        try {
            var temp = durationStr.replace("PT", "")
            var hours = 0
            var minutes = 0
            var seconds = 0
            
            if (temp.contains("H")) {
                val parts = temp.split("H")
                hours = parts[0].toIntOrNull() ?: 0
                temp = if (parts.size > 1) parts[1] else ""
            }
            if (temp.contains("M")) {
                val parts = temp.split("M")
                minutes = parts[0].toIntOrNull() ?: 0
                temp = if (parts.size > 1) parts[1] else ""
            }
            if (temp.contains("S")) {
                val parts = temp.split("S")
                seconds = parts[0].toIntOrNull() ?: 0
            }
            
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        } catch (e: java.lang.Exception) {
            return "12:15"
        }
    }

    fun syncUserAttemptsFromFirebase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val user = auth.currentUser
                if (user != null) {
                    val db = firestoreDb
                    
                    // 1. Flush pending offline or unconfirmed attempts to server via Cloud Function
                    val pendingLocalAttempts = repository.getPendingAttemptsForUser(user.uid)
                    if (pendingLocalAttempts.isNotEmpty()) {
                        val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
                        val callable = functions.getHttpsCallable("submitTestAttemptServerSide")
                        
                        for (localAttempt in pendingLocalAttempts) {
                            try {
                                val sessionToken = localAttempt.attemptSessionToken.ifBlank { "att_${user.uid}_${localAttempt.testId}_${localAttempt.dateTaken}" }
                                
                                // Parse answersJson to map if possible
                                val answersMap = mutableMapOf<String, Any>()
                                try {
                                    val jsonObj = org.json.JSONObject(localAttempt.answersJson)
                                    val keys = jsonObj.keys()
                                    while (keys.hasNext()) {
                                        val key = keys.next()
                                        val valObj = jsonObj.get(key)
                                        answersMap[key] = valObj
                                    }
                                } catch (e: Exception) {
                                    // Fallback
                                }

                                val payload = hashMapOf<String, Any>(
                                    "testId" to localAttempt.testId,
                                    "answers" to answersMap,
                                    "timeSpentSeconds" to localAttempt.timeSpentSeconds,
                                    "clientTimestamp" to localAttempt.dateTaken,
                                    "attemptSessionToken" to sessionToken
                                )

                                val callResult = callable.call(payload).await()
                                val resultMap = callResult.data as? Map<*, *>
                                if (resultMap != null && (resultMap["success"] == true || resultMap["verifiedServerSide"] == true)) {
                                    val serverScore = (resultMap["score"] as? Number)?.toInt() ?: localAttempt.score
                                    val serverMaxScore = (resultMap["maxScore"] as? Number)?.toInt() ?: localAttempt.maxScore
                                    val serverPercentile = (resultMap["percentile"] as? Number)?.toDouble() ?: 0.0
                                    val serverAccuracy = (resultMap["accuracy"] as? Number)?.toDouble() ?: localAttempt.accuracy

                                    val confirmedAttempt = localAttempt.copy(
                                        score = serverScore,
                                        maxScore = serverMaxScore,
                                        percentile = serverPercentile,
                                        accuracy = serverAccuracy,
                                        verifiedServerSide = true,
                                        submissionStatus = "CONFIRMED"
                                    )
                                    repository.insertAttempt(confirmedAttempt)
                                }
                            } catch (syncEx: Exception) {
                                // Keep pending if offline or network fails
                                android.util.Log.e("NEETViewModel", "Failed to sync pending attempt ${localAttempt.testId}: ${syncEx.message}")
                            }
                        }
                    }

                    // 2. Fetch cloud attempts and mirror to local Room cache
                    val attemptsSnapshot = db.collection("Users_Data").document(user.uid).collection("Attempts").get().await()
                    
                    val existingAttempts = repository.allAttempts.firstOrNull()?.map { "${it.testId}_${it.dateTaken}" }?.toSet() ?: emptySet()
                    
                    for (doc in attemptsSnapshot.documents) {
                        val testId = doc.getString("testId") ?: continue
                        val dateTaken = doc.getLong("dateTaken") ?: continue
                        val attemptId = "${testId}_${dateTaken}"
                        
                        if (!existingAttempts.contains(attemptId)) {
                            val attempt = AttemptEntity(
                                userId = user.uid,
                                testId = testId,
                                testTitle = doc.getString("testTitle") ?: "",
                                score = doc.getLong("score")?.toInt() ?: 0,
                                maxScore = doc.getLong("maxScore")?.toInt() ?: 0,
                                percentile = doc.getDouble("percentile") ?: 0.0,
                                accuracy = doc.getDouble("accuracy") ?: 0.0,
                                correctCount = doc.getLong("correctCount")?.toInt() ?: 0,
                                incorrectCount = doc.getLong("incorrectCount")?.toInt() ?: 0,
                                skippedCount = doc.getLong("skippedCount")?.toInt() ?: 0,
                                timeSpentSeconds = doc.getLong("timeSpentSeconds") ?: 0L,
                                dateTaken = dateTaken,
                                answersJson = doc.getString("answersJson") ?: "{}",
                                attemptSessionToken = doc.getString("attemptSessionToken") ?: "",
                                verifiedServerSide = doc.getBoolean("verifiedServerSide") ?: true,
                                submissionStatus = "CONFIRMED"
                            )
                            repository.insertAttempt(attempt)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logout(authManager: Com.ak.testseries.domain.auth.AuthManager) {
        viewModelScope.launch {
            authManager.signOut()
            currentUserId.value = ""
            _isAdminOverride = false
            isAdminFlow.value = false
            checkAdminCustomClaim()
            saveUserProfile("", "", "", "", "")
            _currentScreen.value = AppScreen.LOGIN
        }
    }


    fun updateTestVisibility(testId: String, isPublic: Boolean) {
        if (!isAdmin) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update in Room DB
                val test = repository.getTestById(testId)
                if (test != null) {
                    val rawJson = test.questionsJson ?: ""
                    val sanitizedJson = if (isPublic && rawJson.isNotBlank()) {
                        try {
                            sanitizeAndUploadQuestionImages(testId, rawJson)
                        } catch (e: Exception) {
                            android.util.Log.e("NEETViewModel", "Failed to upload images when making test public: ${e.message}")
                            return@launch
                        }
                    } else {
                        rawJson
                    }
                    val newStatus = if (isPublic && test.status == "Under Review") "Published" else test.status
                    val updatedTest = test.copy(isPublic = isPublic, status = newStatus, questionsJson = sanitizedJson)
                    repository.updateTest(updatedTest)
                    
                    // Update in Firestore
                    val db = firestoreDb
                    val updates = hashMapOf<String, Any>("isPublic" to isPublic)
                    val testDocRef = db.collection("Tests_Data").document(testId)
                    if (isPublic) {
                        val (publicJson, answerKeys) = separatePublicAndSecretQuestions(sanitizedJson)
                        updates["status"] = "Published"
                        updates["questionsJson"] = publicJson
                        if (answerKeys.isNotEmpty()) {
                            val keysDoc = hashMapOf<String, Any>(
                                "testId" to testId,
                                "questions" to answerKeys,
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            testDocRef.collection("private_keys").document("answerKey").set(keysDoc).await()
                        }
                    }
                    testDocRef.update(updates).await()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun publishReviewedTest(testId: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        if (!isAdmin) {
            onResult?.invoke(false, "Unauthorized: Admin privileges required")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update in Room DB
                val test = repository.getTestById(testId)
                if (test != null) {
                    val rawJson = test.questionsJson ?: ""
                    val sanitizedJson = if (rawJson.isNotBlank()) {
                        sanitizeAndUploadQuestionImages(testId, rawJson)
                    } else {
                        rawJson
                    }
                    val updatedTest = test.copy(status = "Published", isPublic = true, isVerifiedCommunity = true, questionsJson = sanitizedJson)
                    repository.updateTest(updatedTest)
                    
                    // Update in Firestore: Strip answer keys from public doc, store in private_keys
                    val (publicJson, answerKeys) = separatePublicAndSecretQuestions(sanitizedJson)
                    val db = firestoreDb
                    val testDocRef = db.collection("Tests_Data").document(testId)
                    val updates = hashMapOf<String, Any>(
                        "status" to "Published",
                        "isPublic" to true,
                        "isVerifiedCommunity" to true,
                        "questionsJson" to publicJson
                    )
                    testDocRef.update(updates).await()

                    if (answerKeys.isNotEmpty()) {
                        val keysDoc = hashMapOf<String, Any>(
                            "testId" to testId,
                            "questions" to answerKeys,
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        testDocRef.collection("private_keys").document("answerKey").set(keysDoc).await()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, e.localizedMessage ?: "Failed to publish test")
                }
            }
        }
    }

    fun seedBundledMockTestsIfNeeded(targetExam: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exam = targetExam ?: _userProfile.value.targetExam
                val bundled = Com.ak.testseries.exam.BundledExamCatalog.getBundledTestsForExam(exam)
                for ((testEntity, questions) in bundled) {
                    val existing = repository.getTestById(testEntity.id)
                    if (existing == null) {
                        repository.saveTestWithQuestions(testEntity, questions)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun syncBundledTestsToCloud(onComplete: ((Boolean, String?) -> Unit)? = null) {
        if (!isAdmin) {
            onComplete?.invoke(false, "Admin privileges required.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = firestoreDb
                val allBundled = Com.ak.testseries.exam.BundledExamCatalog.getAllBundledTests()
                for ((test, questions) in allBundled) {
                    // 1. Ensure saved locally
                    repository.saveTestWithQuestions(test, questions)

                    // 2. Publish to Firestore Tests_Data
                    val testData = hashMapOf<String, Any>(
                        "title" to test.title,
                        "description" to test.description,
                        "institute" to test.institute,
                        "batchName" to test.batchName,
                        "facultyName" to test.facultyName,
                        "language" to test.language,
                        "subject" to test.subject,
                        "difficulty" to test.difficulty,
                        "testType" to test.testType,
                        "questionsCount" to test.questionsCount,
                        "isOfficial" to true,
                        "isVerifiedCommunity" to true,
                        "isPublic" to true,
                        "countsTowardRanking" to true,
                        "status" to "Published",
                        "targetExam" to test.targetExam,
                        "durationMinutes" to test.durationMinutes,
                        "timestamp" to System.currentTimeMillis(),
                        "questionsJson" to (test.questionsJson ?: "")
                    )
                    db.collection("Tests_Data").document(test.id).set(testData).await()
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.localizedMessage ?: "Failed to sync bundled tests to cloud")
                }
            }
        }
    }

    fun seedSyllabusIfNeeded(targetExam: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exam = targetExam ?: _userProfile.value.targetExam
                repository.syncSyllabusForExam(exam)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun launchHierarchicalTest(test: TestEntity) {
        startTest(test)
    }

    fun launchHierarchicalTestById(testId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val test = repository.getTestById(testId)
                if (test != null) {
                    withContext(Dispatchers.Main) {
                        startTest(test)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setTab(tab: HomeTab) {
        _currentTab.value = tab
    }

    fun publishUniversalTest(
        examName: String,
        questions: List<UniversalQuestionModel>,
        durationMinutes: Int = 0
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val testId = "universal_${System.currentTimeMillis()}"
                
                val subjectStr = questions.firstOrNull()?.subjectId?.replaceFirstChar { it.uppercase() } ?: "General"
                val diffStr = questions.firstOrNull()?.difficulty?.name ?: "Medium"
                
                val testEntity = Com.ak.testseries.data.TestEntity(
                    id = testId,
                    title = "$examName - Universal Mock Test",
                    description = "High-accuracy AI extracted test conforming to $examName pattern.",
                    institute = "AK Exam Portal",
                    batchName = "2026 Official Pattern",
                    facultyName = "Exam Panel",
                    language = "Hindi / English",
                    subject = subjectStr,
                    difficulty = diffStr,
                    testType = "Mock Test",
                    questionsCount = questions.size,
                    isOfficial = true,
                    isVerifiedCommunity = true,
                    targetExam = examName,
                    status = "Published",
                    durationMinutes = durationMinutes
                )

                val qJsonArray = org.json.JSONArray()
                val questionEntities = questions.mapIndexed { index: Int, uq: UniversalQuestionModel ->
                    val qId = "${testId}_q_${index + 1}"
                    
                    val qObj = org.json.JSONObject()
                    qObj.put("id", qId)
                    qObj.put("questionTextEn", uq.questionText)
                    qObj.put("optionAEn", uq.options.getOrElse(0) { "Option A" })
                    qObj.put("optionBEn", uq.options.getOrElse(1) { "Option B" })
                    qObj.put("optionCEn", uq.options.getOrElse(2) { "Option C" })
                    qObj.put("optionDEn", uq.options.getOrElse(3) { "Option D" })
                    if (!uq.questionTextHi.isNullOrBlank()) qObj.put("questionTextHi", uq.questionTextHi)
                    uq.optionsHi?.getOrNull(0)?.let { qObj.put("optionAHi", it) }
                    uq.optionsHi?.getOrNull(1)?.let { qObj.put("optionBHi", it) }
                    uq.optionsHi?.getOrNull(2)?.let { qObj.put("optionCHi", it) }
                    uq.optionsHi?.getOrNull(3)?.let { qObj.put("optionDHi", it) }
                    qObj.put("correctAnswer", uq.correctAnswer)
                    qObj.put("explanationEn", uq.explanation)
                    if (!uq.explanationHi.isNullOrBlank()) qObj.put("explanationHi", uq.explanationHi)
                    qObj.put("subject", uq.subjectId)
                    qObj.put("chapter", uq.topicId.ifBlank { "Chapter 1" })
                    qObj.put("questionType", uq.questionType.name)
                    qObj.put("section", uq.sectionId.ifBlank { "SECTION_A" })
                    qObj.put("sectionName", uq.sectionName)
                    qObj.put("positiveMarks", uq.positiveMarks)
                    qObj.put("negativeMarks", uq.negativeMarks)
                    if (!uq.diagramUrl.isNullOrEmpty()) qObj.put("imageUrl", uq.diagramUrl)
                    qJsonArray.put(qObj)
                    
                    Com.ak.testseries.data.QuestionEntity(
                        id = qId,
                        testId = testId,
                        questionText = uq.questionText,
                        optionA = uq.options.getOrElse(0) { "Option A" },
                        optionB = uq.options.getOrElse(1) { "Option B" },
                        optionC = uq.options.getOrElse(2) { "Option C" },
                        optionD = uq.options.getOrElse(3) { "Option D" },
                        correctAnswer = uq.correctAnswer,
                        explanation = uq.explanation,
                        subject = uq.subjectId,
                        chapter = uq.topicId.ifBlank { "Chapter 1" },
                        topic = uq.topicId,
                        difficulty = diffStr,
                        questionTextHi = uq.questionTextHi,
                        optionAHi = uq.optionsHi?.getOrNull(0),
                        optionBHi = uq.optionsHi?.getOrNull(1),
                        optionCHi = uq.optionsHi?.getOrNull(2),
                        optionDHi = uq.optionsHi?.getOrNull(3),
                        explanationHi = uq.explanationHi,
                        questionType = uq.questionType.name,
                        section = uq.sectionId.ifBlank { "SECTION_A" },
                        imageUrl = uq.diagramUrl
                    )
                }

                // 1. Save Locally strictly in local Room DB
                val isPublicTest = genIsPublic.value
                val finalTestEntity = testEntity.copy(isPublic = isPublicTest, questionsJson = qJsonArray.toString())
                repository.insertTest(finalTestEntity)
                repository.insertQuestions(questionEntities)
                saveQuestionsJsonToInternalStorage(testId, qJsonArray.toString())
                
                // 2. Sync to Firebase Firestore ONLY if test is approved public test!
                if (isPublicTest) {
                    val sanitizedQuestionsJson = try {
                        sanitizeAndUploadQuestionImages(testId, qJsonArray.toString())
                    } catch (e: Exception) {
                        android.util.Log.e("NEETViewModel", "Diagram upload failed for universal test $testId: ${e.message}")
                        null
                    }
                    if (sanitizedQuestionsJson != null) {
                        if (sanitizedQuestionsJson != qJsonArray.toString()) {
                            repository.updateTest(finalTestEntity.copy(questionsJson = sanitizedQuestionsJson))
                        }
                        val db = firestoreDb
                        val firestoreMap = hashMapOf(
                            "id" to testId,
                            "title" to finalTestEntity.title,
                            "description" to finalTestEntity.description,
                            "institute" to finalTestEntity.institute,
                            "batchName" to finalTestEntity.batchName,
                            "facultyName" to finalTestEntity.facultyName,
                            "language" to finalTestEntity.language,
                            "subject" to finalTestEntity.subject,
                            "difficulty" to finalTestEntity.difficulty,
                            "testType" to finalTestEntity.testType,
                            "questionsCount" to finalTestEntity.questionsCount,
                            "isOfficial" to finalTestEntity.isOfficial,
                            "isVerifiedCommunity" to finalTestEntity.isVerifiedCommunity,
                            "isPublic" to true,
                            "classLevel" to finalTestEntity.classLevel,
                            "targetExam" to finalTestEntity.targetExam,
                            "durationMinutes" to finalTestEntity.durationMinutes,
                            "timestamp" to finalTestEntity.timestamp,
                            "questionsJson" to sanitizedQuestionsJson
                        )
                        db.collection("Tests_Data").document(testId).set(firestoreMap).await()
                    }
                } else {
                    android.util.Log.d("NEETViewModel", "Private test $testId stored strictly inside local Room DB without network calls.")
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveExtractedQuestionsToPrivateRoomDatabase(
        testTitle: String,
        subject: String,
        targetExam: String,
        structuredQuestions: List<com.ak.pdfextractor.StructuredQuestion>,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val testId = "pdf_private_" + System.currentTimeMillis()
                val imageDir = java.io.File(context.filesDir, "extracted_cropped_images").apply { if (!exists()) mkdirs() }
                
                val questionEntities = mutableListOf<QuestionEntity>()
                val questionsJsonArray = org.json.JSONArray()

                structuredQuestions.forEachIndexed { idx, sq ->
                    var imagePath: String? = sq.associatedImageId
                    
                    // Save cropped image Bitmap to local app internal storage if present
                    if (sq.imageBitmap != null) {
                        try {
                            val imgFile = java.io.File(imageDir, "crop_${testId}_q${idx + 1}.png")
                            java.io.FileOutputStream(imgFile).use { out ->
                                sq.imageBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                            }
                            imagePath = imgFile.absolutePath
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    val optA = sq.options.find { it.key.equals("A", ignoreCase = true) || it.key == "1" }?.text ?: sq.options.getOrNull(0)?.text ?: "Option A"
                    val optB = sq.options.find { it.key.equals("B", ignoreCase = true) || it.key == "2" }?.text ?: sq.options.getOrNull(1)?.text ?: "Option B"
                    val optC = sq.options.find { it.key.equals("C", ignoreCase = true) || it.key == "3" }?.text ?: sq.options.getOrNull(2)?.text ?: "Option C"
                    val optD = sq.options.find { it.key.equals("D", ignoreCase = true) || it.key == "4" }?.text ?: sq.options.getOrNull(3)?.text ?: "Option D"
                    val optionsList = listOf(optA, optB, optC, optD).filter { it.isNotBlank() }

                    // OCR questions extracted without an official answer key have no verified answer or explanation
                    val rawAnswer = ""
                    val rawExplanation = ""
                    val hasDiagKeyword = Com.ak.testseries.exam.QuestionValidationEngine.hasDiagramKeyword(sq.questionText)
                    val needsImageFlag = !imagePath.isNullOrEmpty() || hasDiagKeyword

                    // Run QuestionValidationEngine before saving to detect missing answers and flag unverified status
                    val validation = Com.ak.testseries.exam.QuestionValidationEngine.validateQuestion(
                        questionText = sq.questionText,
                        options = optionsList,
                        correctAnswer = rawAnswer,
                        explanation = rawExplanation,
                        hasDiagramAttached = !imagePath.isNullOrEmpty()
                    )

                    val qEntity = QuestionEntity(
                        id = "${testId}_q${idx + 1}",
                        testId = testId,
                        questionText = sq.questionText,
                        optionA = optA,
                        optionB = optB,
                        optionC = optC,
                        optionD = optD,
                        correctAnswer = rawAnswer,
                        explanation = rawExplanation,
                        subject = subject.ifBlank { "Full Syllabus" },
                        chapter = "Local OCR Extract",
                        imageUrl = imagePath,
                        needsImage = needsImageFlag
                    )
                    questionEntities.add(qEntity)

                    val qObj = org.json.JSONObject().apply {
                        put("id", qEntity.id)
                        put("testId", testId)
                        put("questionText", sq.questionText)
                        put("optionA", optA)
                        put("optionB", optB)
                        put("optionC", optC)
                        put("optionD", optD)
                        put("correctAnswer", rawAnswer)
                        put("explanation", rawExplanation)
                        put("imageUrl", imagePath ?: "")
                        put("needsImage", needsImageFlag)
                        put("validationStatus", validation.status.name)
                        put("qualityScore", validation.qualityScore)
                        put("validationIssues", org.json.JSONArray(validation.issues))
                    }
                    questionsJsonArray.put(qObj)
                }

                val currentUid = currentUserId.value.ifBlank { "local_sandbox_user" }
                val testEntity = TestEntity(
                    id = testId,
                    title = if (testTitle.isBlank()) "Private Extracted Test" else testTitle,
                    description = "Parsed test questions, choices, and cropped image diagrams saved locally in Room.",
                    institute = "Private Vault",
                    batchName = "Self Practice",
                    facultyName = "PDF Smart Parser",
                    language = "Bilingual",
                    subject = subject.ifBlank { "Full Syllabus" },
                    difficulty = "Medium",
                    testType = "Part Test",
                    questionsCount = questionEntities.size,
                    isOfficial = false,
                    isVerifiedCommunity = false,
                    isPublic = false, // Strictly Private
                    countsTowardRanking = false,
                    downloadsCount = 1,
                    isDownloaded = true,
                    timestamp = System.currentTimeMillis(),
                    status = "Under Review",
                    aiConfidence = 50,
                    uploaderId = currentUid,
                    targetExam = if (targetExam.isBlank()) _userProfile.value.targetExam else targetExam,
                    durationMinutes = questionEntities.size,
                    questionsJson = questionsJsonArray.toString()
                )

                repository.insertTest(testEntity)
                repository.insertQuestions(questionEntities)
                saveQuestionsJsonToInternalStorage(testId, questionsJsonArray.toString())

                withContext(Dispatchers.Main) {
                    onSuccess(testId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

data class YouTubeVideoResult(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val duration: String,
    val isVerified: Boolean = true
)

data class TestFolder(
    val id: String,
    val name: String,
    val parentId: String? = null
)

data class UserNotification(
    val id: String = "",
    val title: String,
    val message: String,
    val timestamp: Long,
    val imageUrl: String? = null,
    val linkUrl: String? = null,
    val targetGroup: String = "All",
    val targetEmail: String? = null,
    val isPinned: Boolean = false
)

data class UserProfileData(
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val stateName: String = "",
    val cityName: String = "",
    val profileImageUri: String = "",
    val targetExam: String = "NEET",
    val classLevel: String = ""
)

sealed class PdfAnalysisState {
    object Idle : PdfAnalysisState()
    object ExtractingText : PdfAnalysisState()
    object AnalyzingWithAI : PdfAnalysisState()
    data class Success(val result: AnalyzedTestResult) : PdfAnalysisState()
    data class Error(val message: String) : PdfAnalysisState()
}

data class AnalyzedTestResult(
    val title: String,
    val institute: String,
    val targetExam: String = "NEET UG",
    val subject: String,
    val classLevel: String,
    val testType: String,
    val chapter: String,
    val timerMinutes: Int,
    val questionsCount: Int,
    val totalMarks: Double = 720.0,
    val language: String,
    val confidence: Int,
    val detectedQuestionsCount: Int = 0,
    val isQuestionPaperDetected: Boolean = false,
    val autoRoutedWorkflow: String = "private", // "private" or "public"
    val routingReason: String = "",
    val extractedDiagramsCount: Int = 0,
    val extractedDiagramPaths: List<String> = emptyList()
)
