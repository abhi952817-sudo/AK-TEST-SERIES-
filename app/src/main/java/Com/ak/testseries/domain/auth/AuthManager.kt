package Com.ak.testseries.domain.auth

import android.content.Context
import android.content.Intent

data class AuthUser(
    val uid: String,
    val name: String,
    val email: String
)

interface AuthManager {
    suspend fun signInWithGoogle(context: Context, intent: Intent? = null): Result<AuthUser>
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signUpWithEmail(name: String, email: String, password: String): Result<AuthUser>
    suspend fun sendSignInLinkToEmail(email: String, context: Context): Result<Unit>
    suspend fun signInWithEmailLink(email: String, link: String): Result<AuthUser>
    fun getSignedInUser(): AuthUser?
    suspend fun signOut()
    fun getSignInIntent(context: Context): Intent?
}

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val score: Int,
    val uid: String = "",
    val targetExam: String = "NEET",
    val qualifyingTestsCount: Int = 0,
    val isOfficialAir: Boolean = false,
    val streak: Int = 0,
    val league: String = "Rookie",
    val vipScore: Int = 0
)
