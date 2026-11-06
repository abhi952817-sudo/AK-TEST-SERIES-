package Com.ak.testseries.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AdminDashboardState(
    val adminName: String = "Admin",
    val selectedDateRange: String = "All Time",
    
    val totalUsersCount: String = "00",
    val totalUsersTrendPercent: String = "+0%",
    
    val activeUsersCount: String = "—",
    val activeUsersTrendPercent: String = "—",
    
    val totalTestsCount: String = "00",
    val totalTestsTrendPercent: String = "+0%",
    
    val totalAttemptsCount: String = "—",
    val totalAttemptsTrendPercent: String = "—",
    
    val aiQueueCount: String = "—",
    val pendingReviewCount: String = "—",
    val serverStatus: String = "Not verified",
    
    val analyticsTimeframe: String = "Monthly",
    
    val usedStorageGB: String = "—",
    val totalStorageGB: String = "—",
    val storagePercentage: Float = 0f,
    val storagePercentageText: String = "Unavailable",
    
    val adRevenueAmount: String = "Not configured",
    val adRevenueTrend: String = "—",
    
    val recentActivities: List<RecentActivity> = emptyList(),
    val errorMessage: String? = null,
    val systemLogs: List<SystemLog> = emptyList()
)

data class SystemLog(
    val timestamp: String,
    val component: String,
    val message: String,
    val isError: Boolean
)

data class RecentActivity(
    val title: String,
    val subtitle: String,
    val timeAgo: String,
    val icon: String
)

class SuperAdminViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AdminDashboardState())
    val uiState: StateFlow<AdminDashboardState> = _uiState.asStateFlow()

    private val exceptionHandler = Com.ak.testseries.crash.AppCoroutineExceptionHandler.create(
        tag = "SuperAdminViewModel"
    )

    init {
        fetchDashboardData()
    }

    private fun fetchDashboardData() {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val logs = mutableListOf<SystemLog>()
            val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            
            fun addLog(component: String, message: String, isError: Boolean) {
                logs.add(SystemLog(timeFormat.format(java.util.Date()), component, message, isError))
            }
            
            addLog("System", "Dashboard initialized. Starting system diagnostics...", false)

            try {
                val db = Com.ak.testseries.data.remote.FirestoreHelper.getInstance()
                
                // Fetch Users Count
                var usersCount = 0
                try {
                    val usersSnapshot = db.collection("Users_Data").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await()
                    usersCount = usersSnapshot.count.toInt()
                    addLog("Firestore", "Users_Data connected. Real-time sync active.", false)
                } catch (e: Exception) { 
                    e.printStackTrace() 
                    addLog("Firestore", "Failed to connect to Users_Data: ${e.message}", true)
                }
                
                // Fetch Tests Count
                var testsCount = 0
                try {
                    val testsSnapshot = db.collection("Tests_Data").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await()
                    testsCount = testsSnapshot.count.toInt()
                    addLog("Firestore", "Tests_Data connected. Tests loaded.", false)
                } catch (e: Exception) { 
                    e.printStackTrace() 
                    addLog("Firestore", "Failed to connect to Tests_Data: ${e.message}", true)
                }
                
                // AI provider health is intentionally not reported as healthy here.
                // A real authenticated provider health check is not wired into this dashboard yet.
                addLog("Gemini API", "Health status not verified by a live provider check.", true)

                _uiState.value = _uiState.value.copy(
                    totalUsersCount = usersCount.toString(),
                    activeUsersCount = "—", 
                    totalTestsCount = testsCount.toString(),
                    errorMessage = if (logs.any { it.isError }) "System reported errors. Check logs." else null,
                    systemLogs = logs
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to fetch overview data: ${e.message}")
            }
        }
    }

    fun setDateRange(range: String) {
        _uiState.value = _uiState.value.copy(selectedDateRange = range)
        fetchDashboardData()
    }

    fun refreshDashboardData() {
        fetchDashboardData()
    }
}
