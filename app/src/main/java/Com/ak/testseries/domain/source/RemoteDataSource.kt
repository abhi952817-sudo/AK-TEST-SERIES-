package Com.ak.testseries.domain.source

import Com.ak.testseries.data.TestEntity
import kotlinx.coroutines.flow.Flow

interface RemoteDataSource {
    fun observeTests(): Flow<List<TestEntity>>
    suspend fun getUserProfile(userId: String): UserProfile
    suspend fun getAppConfig(): AppConfig
}

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val isPremium: Boolean = false
)

data class AppConfig(
    val latestVersion: String,
    val maintenanceMode: Boolean,
    val forceUpdate: Boolean = false
)
