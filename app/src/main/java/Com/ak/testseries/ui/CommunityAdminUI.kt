package Com.ak.testseries.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import Com.ak.testseries.viewmodel.NEETViewModel
import Com.ak.testseries.data.StudentData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityAdminScreen(viewModel: NEETViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Settings & Users") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Manage Posts") })
        }
        
        if (selectedTab == 0) {
            CommunityAdminSettingsScreen(viewModel)
        } else {
            // Show the actual Community Feed in Admin mode
            Com.ak.testseries.ui.CommunityScreen(
                isAdmin = true,
                targetExam = "NEET",
                onOpenTest = { testId -> viewModel.handleDeepLinkTest(testId) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityAdminSettingsScreen(viewModel: NEETViewModel) {

    val deepPurple = Color(0xFF5E35B1)
    val lightPurple = Color(0xFFF3E5F5)
    val neonGreen = Color(0xFF2E7D32)
    val warningRed = Color(0xFFC62828)
    
    var masterSwitchEnabled by remember { mutableStateOf(true) }
    var maintenanceMode by remember { mutableStateOf(false) }
    var retentionDays by remember { mutableStateOf("30 Days") }
    var dailyPostLimit by remember { mutableStateOf("5") }
    var dailyCommentLimit by remember { mutableStateOf("10") }
    var blockedWords by remember { mutableStateOf("spam, abuse, fake") }
    
    var rewardExpanded by remember { mutableStateOf(false) }
    var selectedReward by remember { mutableStateOf("+1 Test Upload Limit") }
    val rewardOptions = listOf("+1 Test Upload Limit", "Star Badge (Profile)", "Top Contributor Role", "+50 Reward Coins")
    
    var retentionExpanded by remember { mutableStateOf(false) }
    val retentionOptions = listOf("7 Days", "30 Days", "60 Days", "90 Days", "Never Delete (Not Recommended)")
    var announcementText by remember { mutableStateOf("") }
    
    // Search and filters for REAL users
    var searchKeyword by remember { mutableStateOf("") }
    var selectedTabFilter by remember { mutableStateOf("All") } // "All", "Active", "Suspended"
    
    // Real users state from NEETViewModel
    val users by viewModel.adminUsers.collectAsStateWithLifecycle()
    val activeGeminiKeyIndex by viewModel.activeGeminiKeyIndex.collectAsStateWithLifecycle()
    val geminiStatusMessage by viewModel.geminiStatusMessage.collectAsStateWithLifecycle()
    
    // Fetch users in real-time
    LaunchedEffect(Unit) {
        viewModel.fetchAdminUsers()
        
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("Config").document("community_settings").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    masterSwitchEnabled = snapshot.getBoolean("masterSwitchEnabled") ?: true
                    maintenanceMode = snapshot.getBoolean("maintenanceMode") ?: false
                    retentionDays = snapshot.getString("retentionDays") ?: "30 Days"
                    dailyPostLimit = snapshot.getString("dailyPostLimit") ?: "5"
                    dailyCommentLimit = snapshot.getString("dailyCommentLimit") ?: "10"
                    blockedWords = snapshot.getString("blockedWords") ?: "spam, abuse, fake"
                    announcementText = snapshot.getString("announcementText") ?: ""
                }
            }
    }
    
    fun saveCommunitySettings(key: String, value: Any) {
        if (!viewModel.isAdmin) return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("Config").document("community_settings")
            .set(mapOf(key to value), com.google.firebase.firestore.SetOptions.merge())
    }
    
    // Filtered users lists
    val filteredUsers = remember(users, searchKeyword, selectedTabFilter) {
        users.filter { user ->
            val matchesSearch = user.name.contains(searchKeyword, ignoreCase = true) || 
                                user.email.contains(searchKeyword, ignoreCase = true)
            val matchesTab = when (selectedTabFilter) {
                "Active" -> !user.isBanned
                "Suspended" -> user.isBanned
                else -> true
            }
            matchesSearch && matchesTab
        }
    }
    
    // Compute Real metrics
    val totalUsersCount = users.size
    val activeUsersCount = users.count { !it.isBanned }
    val suspendedUsersCount = users.count { it.isBanned }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dynamic Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = deepPurple),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = "Admin",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Community Control Center",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Manage community settings, moderate real registered students, configure APIs, and broadcast global updates.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Gemini Status badge
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Gemini Status",
                                tint = Color.Yellow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dual-Key Status: $geminiStatusMessage",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        // Real-Time Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatMetricCard(
                    title = "Total Students",
                    value = totalUsersCount.toString(),
                    icon = Icons.Rounded.People,
                    tint = deepPurple,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Active Members",
                    value = activeUsersCount.toString(),
                    icon = Icons.Rounded.VerifiedUser,
                    tint = neonGreen,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Suspended",
                    value = suspendedUsersCount.toString(),
                    icon = Icons.Rounded.GppBad,
                    tint = warningRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // 1. Core Controls Section
        item {
            AdminSectionCard(
                title = "Core Configurations",
                subtitle = "Control general platform-wide access rules",
                icon = Icons.Rounded.SettingsInputComponent,
                tint = deepPurple
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Community Section", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Toggle to turn on/off students community feed tab.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = masterSwitchEnabled,
                        onCheckedChange = { 
                            masterSwitchEnabled = it 
                            saveCommunitySettings("masterSwitchEnabled", it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = deepPurple)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Maintenance Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Blocks students feed and displays an maintenance banner.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = maintenanceMode,
                        onCheckedChange = { 
                            maintenanceMode = it 
                            saveCommunitySettings("maintenanceMode", it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = deepPurple)
                    )
                }
            }
        }

        // 2. Limits & Quotas
        item {
            AdminSectionCard(
                title = "Daily Limits & Security Policies",
                subtitle = "Moderate spam and secure API consumption quotas",
                icon = Icons.Rounded.Security,
                tint = deepPurple
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily Post Limit", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp), color = Color.Gray)
                        OutlinedTextField(
                            value = dailyPostLimit,
                            onValueChange = { dailyPostLimit = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = deepPurple)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily Comment Limit", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp), color = Color.Gray)
                        OutlinedTextField(
                            value = dailyCommentLimit,
                            onValueChange = { dailyCommentLimit = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = deepPurple)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        saveCommunitySettings("dailyPostLimit", dailyPostLimit)
                        saveCommunitySettings("dailyCommentLimit", dailyCommentLimit)
                        saveCommunitySettings("blockedWords", blockedWords)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = deepPurple),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Security Policies", fontWeight = FontWeight.Bold)
                }
            }
        }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Blocked Profanity Filter Words", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp), color = Color.Gray)
                OutlinedTextField(
                    value = blockedWords,
                    onValueChange = { blockedWords = it },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = deepPurple)
                )
            }
        }

        // 3. Data Retention & Gamification
        item {
            AdminSectionCard(
                title = "Data Saver & Rewards Policy",
                subtitle = "Auto-cleanup and student performance badging",
                icon = Icons.Rounded.PieChart,
                tint = deepPurple
            ) {
                Text("Auto-Delete Old Inactive Posts After", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp), color = Color.Gray)
                Box {
                    OutlinedTextField(
                        value = retentionDays,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { retentionExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, "Dropdown")
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = deepPurple)
                    )
                    DropdownMenu(
                        expanded = retentionExpanded,
                        onDismissRequest = { retentionExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        retentionOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { 
                                    retentionDays = option
                                    saveCommunitySettings("retentionDays", option)
                                    retentionExpanded = false
                                }
                            )
                        }
                    }
                }
                Text("Purges posts older than configured threshold to minimize Firebase Firestore query billing costs.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Gamification Reward Trigger at 10k Views", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp), color = Color.Gray)
                Box {
                    OutlinedTextField(
                        value = selectedReward,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { rewardExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, "Dropdown")
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = deepPurple)
                    )
                    DropdownMenu(
                        expanded = rewardExpanded,
                        onDismissRequest = { rewardExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        rewardOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { 
                                    selectedReward = option
                                    saveCommunitySettings("selectedReward", option)
                                    rewardExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Global Announcements
        item {
            AdminSectionCard(
                title = "📌 Pinned Announcements",
                subtitle = "Broadcast a global alert to all community sections",
                icon = Icons.Rounded.Campaign,
                tint = deepPurple
            ) {
                OutlinedTextField(
                    value = announcementText,
                    onValueChange = { announcementText = it },
                    placeholder = { Text("Write a global alert or dynamic broadcast notice for students...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = deepPurple)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { 
                        saveCommunitySettings("announcementText", announcementText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = deepPurple),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pin Broadcast Notice", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. REAL-TIME USER MODERATION PANEL (Replaced mock Vikram, Pooja, Rahul data)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Real Student Management",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "Moderate real platform accounts in real-time",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    
                    IconButton(onClick = { viewModel.fetchAdminUsers() }) {
                        Icon(Icons.Default.Refresh, "Refresh Users", tint = deepPurple)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Search field
                OutlinedTextField(
                    value = searchKeyword,
                    onValueChange = { searchKeyword = it },
                    placeholder = { Text("Search by name or email...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (searchKeyword.isNotEmpty()) {
                            IconButton(onClick = { searchKeyword = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = deepPurple
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Filter tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Active", "Suspended").forEach { tab ->
                        val isSelected = selectedTabFilter == tab
                        val bg = if (isSelected) deepPurple else Color.White
                        val tc = if (isSelected) Color.White else Color.DarkGray
                        val border = if (isSelected) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, Color.LightGray)
                        
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTabFilter = tab },
                            color = bg,
                            shape = RoundedCornerShape(10.dp),
                            border = border
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tc
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // List of filtered users from Firestore database
        if (filteredUsers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PersonOff, null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No matching students found in Firestore", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            items(filteredUsers) { student ->
                StudentModerationCard(
                    student = student,
                    deepPurple = deepPurple,
                    warningRed = warningRed,
                    lightPurple = lightPurple,
                    onToggleBan = { isBanned ->
                        viewModel.toggleUserBan(student.id, isBanned)
                    }
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun StatMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AdminSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Surface(
                    color = tint.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                    Text(subtitle, fontSize = 11.sp, color = Color.Gray)
                }
            }
            HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(bottom = 14.dp))
            content()
        }
    }
}

@Composable
fun StudentModerationCard(
    student: StudentData,
    deepPurple: Color,
    warningRed: Color,
    lightPurple: Color,
    onToggleBan: (Boolean) -> Unit
) {
    val neonGreen = Color(0xFF2E7D32)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (student.isBanned) Color(0xFFFFF1F2) else Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp, 
            if (student.isBanned) Color(0xFFFECDD3) else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Avatar with initial
                Surface(
                    color = if (student.isBanned) Color(0xFFFDA4AF) else lightPurple,
                    shape = CircleShape,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val initial = if (student.name.isNotEmpty()) student.name.first().toString().uppercase() else "S"
                        Text(
                            text = initial, 
                            fontWeight = FontWeight.Bold, 
                            color = if (student.isBanned) warningRed else deepPurple,
                            fontSize = 16.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (student.isBanned) warningRed else Color.Black
                    )
                    Text(
                        text = student.email,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Attempted: ${student.testsAttempted} Tests",
                                fontSize = 10.sp,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Avg Score: ${student.avgScore}%",
                                fontSize = 10.sp,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Suspension Action
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = if (student.isBanned) warningRed.copy(alpha = 0.15f) else neonGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (student.isBanned) "Suspended" else "Active",
                        color = if (student.isBanned) warningRed else neonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                TextButton(
                    onClick = { onToggleBan(!student.isBanned) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (student.isBanned) "Unban Account" else "Suspend Account",
                        color = if (student.isBanned) deepPurple else warningRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
