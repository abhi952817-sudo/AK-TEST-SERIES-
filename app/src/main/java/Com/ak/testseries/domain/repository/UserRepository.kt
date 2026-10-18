package Com.ak.testseries.domain.repository
import Com.ak.testseries.domain.auth.AuthUser
import Com.ak.testseries.data.AttemptEntity

interface UserRepository {
    suspend fun syncUserToDatabase(authUser: AuthUser): Result<Unit>
    suspend fun backupUserData(uid: String, attempts: List<AttemptEntity>, bookmarkedTests: List<String>, bookmarkedQuestions: List<String>): Result<Unit>
    suspend fun restoreUserData(uid: String): Result<UserDataBackup>
}

data class UserDataBackup(
    val attempts: List<AttemptEntity>,
    val bookmarkedTests: List<String>,
    val bookmarkedQuestions: List<String>
)
