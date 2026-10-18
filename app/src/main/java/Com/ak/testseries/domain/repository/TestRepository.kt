package Com.ak.testseries.domain.repository

import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.domain.source.AppConfig
import Com.ak.testseries.domain.source.UserProfile
import kotlinx.coroutines.flow.Flow

interface TestRepository {
    // Expose local database as the single source of truth
    fun getAllTests(): Flow<List<TestEntity>>
    
    // Remote fetch operations
    suspend fun syncTests()
    suspend fun getUserProfile(userId: String): UserProfile
    suspend fun getAppConfig(): AppConfig
}
