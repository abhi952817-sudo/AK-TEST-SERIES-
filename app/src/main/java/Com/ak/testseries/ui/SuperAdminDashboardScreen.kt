package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import Com.ak.testseries.viewmodel.AdminDashboardState
import Com.ak.testseries.viewmodel.RecentActivity
import Com.ak.testseries.viewmodel.SuperAdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminDashboardContentOnly(
    viewModel: SuperAdminViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryPurple = Color(0xFF512DA8)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardHeader(
            uiState = uiState,
            primaryColor = primaryPurple,
            onSelectDateRange = { viewModel.setDateRange(it) }
        )
        MetricCardsGrid(uiState, primaryPurple)
        SystemDiagnosticsSection(uiState, primaryPurple)
        StorageAndRevenueRow(uiState, primaryPurple)
        RecentActivitiesSection(
            uiState = uiState,
            primaryColor = primaryPurple,
            onViewAllClick = { viewModel.refreshDashboardData() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminDashboardScreen(
    viewModel: SuperAdminViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryPurple = Color(0xFF512DA8) // Modern purple
    val backgroundWhite = Color(0xFFF8F9FA) // Clean off-white background

    var selectedTab by remember { mutableIntStateOf(0) }
    var showTopMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = backgroundWhite,
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh Metrics") },
                                onClick = {
                                    viewModel.refreshDashboardData()
                                    showTopMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Diagnostics Report") },
                                onClick = {
                                    viewModel.refreshDashboardData()
                                    showTopMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Assessment, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryPurple
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryPurple,
                        selectedTextColor = primaryPurple,
                        indicatorColor = primaryPurple.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.People, contentDescription = "Users") },
                    label = { Text("Users") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryPurple,
                        selectedTextColor = primaryPurple,
                        indicatorColor = primaryPurple.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Assignment, contentDescription = "Tests") },
                    label = { Text("Tests") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryPurple,
                        selectedTextColor = primaryPurple,
                        indicatorColor = primaryPurple.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Forum, contentDescription = stringRes("Community")) },
                    label = { Text(stringRes("Community")) },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryPurple,
                        selectedTextColor = primaryPurple,
                        indicatorColor = primaryPurple.copy(alpha = 0.1f)
                    )
                )
            }
        }
    ) { paddingValues ->
        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    DashboardHeader(
                        uiState = uiState,
                        primaryColor = primaryPurple,
                        onSelectDateRange = { viewModel.setDateRange(it) }
                    )
                }
                item { MetricCardsGrid(uiState, primaryPurple) }
                item { SystemDiagnosticsSection(uiState, primaryPurple) }
                item { StorageAndRevenueRow(uiState, primaryPurple) }
                item {
                    RecentActivitiesSection(
                        uiState = uiState,
                        primaryColor = primaryPurple,
                        onViewAllClick = { viewModel.refreshDashboardData() }
                    )
                }
            }
        } else if (selectedTab == 1) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Student & User Management", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryPurple)
                            Spacer(Modifier.height(8.dp))
                            Text("Total Registered Users: ${uiState.totalUsersCount}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Active Session Users: ${uiState.activeUsersCount}", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            }
        } else if (selectedTab == 2) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Tests & Attempts Overview", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryPurple)
                            Spacer(Modifier.height(8.dp))
                            Text("Total Live & Vault Tests: ${uiState.totalTestsCount}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Total Student Attempts: ${uiState.totalAttemptsCount}", fontSize = 13.sp, color = Color.Gray)
                            Text("AI Queue Processing: ${uiState.aiQueueCount}", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Community & Moderation Hub", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryPurple)
                            Spacer(Modifier.height(8.dp))
                            Text("Community Moderation Status: Healthy", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Pending Flagged Posts: ${uiState.pendingReviewCount}", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(
    uiState: AdminDashboardState,
    primaryColor: Color,
    onSelectDateRange: (String) -> Unit = {}
) {
    var showReportDialog by remember { mutableStateOf(false) }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("System Report", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uiState.errorMessage != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(uiState.errorMessage ?: "Unknown error", color = Color.Red, fontSize = 14.sp)
                            }
                        }
                    } else {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.CheckCircleOutline, contentDescription = stringRes("OK"), tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("All systems operational.", color = Color(0xFF2E7D32), fontSize = 14.sp)
                            }
                        }
                    }
                    
                    HorizontalDivider()
                    
                    Text("Diagnostic Logs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.systemLogs) { log ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (log.isError) Color(0xFFFFF3E0) else Color(0xFFF5F5F5)
                                )
                            ) {
                                Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(log.component, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (log.isError) Color(0xFFE65100) else Color.DarkGray)
                                        Text(log.timestamp, fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(log.message, fontSize = 12.sp, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text(stringRes("Close"), color = primaryColor)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Welcome ${uiState.adminName} \uD83D\uDC4B",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Here's what's happening today.",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                Button(
                    onClick = { showReportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("View Report", color = primaryColor, fontWeight = FontWeight.SemiBold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            var showDateRangeDropdown by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { showDateRangeDropdown = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(uiState.selectedDateRange, color = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color.Gray)
                }
                DropdownMenu(
                    expanded = showDateRangeDropdown,
                    onDismissRequest = { showDateRangeDropdown = false }
                ) {
                    listOf("All Time", "Today", "Last 7 Days", "Last 30 Days", "Monthly").forEach { range ->
                        DropdownMenuItem(
                            text = { Text(range) },
                            onClick = {
                                onSelectDateRange(range)
                                showDateRangeDropdown = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCardsGrid(uiState: AdminDashboardState, primaryColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Total Users",
                value = uiState.totalUsersCount,
                trend = uiState.totalUsersTrendPercent,
                icon = Icons.Default.Person,
                iconTint = primaryColor
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Active Users",
                value = uiState.activeUsersCount,
                trend = uiState.activeUsersTrendPercent,
                icon = Icons.Default.Eco, // closest to avocado
                iconTint = Color(0xFF4CAF50)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Total Tests",
                value = uiState.totalTestsCount,
                trend = uiState.totalTestsTrendPercent,
                icon = Icons.Default.Assignment,
                iconTint = primaryColor
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = stringRes("Total Attempts"),
                value = uiState.totalAttemptsCount,
                trend = uiState.totalAttemptsTrendPercent,
                icon = Icons.Default.People,
                iconTint = Color(0xFFFF9800)
            )
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    trend: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 12.sp, color = Color.Gray)
                    Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Trend Up", tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(trend, fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
            }
        }
    }
}



@Composable
fun SystemDiagnosticsSection(uiState: AdminDashboardState, primaryColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (uiState.systemLogs.any { it.isError }) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = "Status",
                        tint = if (uiState.systemLogs.any { it.isError }) Color(0xFFF57C00) else Color(0xFF388E3C),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("System Diagnostics & Logs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Text(
                    text = "${uiState.systemLogs.count { it.isError }} Errors",
                    fontSize = 12.sp,
                    color = if (uiState.systemLogs.any { it.isError }) Color.Red else Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Logs List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.systemLogs.isEmpty()) {
                    Text("No logs available yet.", color = Color.Gray, fontSize = 14.sp)
                } else {
                    uiState.systemLogs.take(5).forEach { log ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (log.isError) Color(0xFFFFF3E0) else Color(0xFFF5F5F5)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(log.component, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (log.isError) Color(0xFFE65100) else Color.DarkGray)
                                    Text(log.timestamp, fontSize = 10.sp, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(log.message, fontSize = 13.sp, color = Color.Black)
                            }
                        }
                    }
                    if (uiState.systemLogs.size > 5) {
                        TextButton(
                            onClick = { /* Would open full logs screen */ },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("View All Logs", color = primaryColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageAndRevenueRow(uiState: AdminDashboardState, primaryColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Storage Usage", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("${uiState.usedStorageGB} / ${uiState.totalStorageGB}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uiState.storagePercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = primaryColor,
                    trackColor = primaryColor.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(uiState.storagePercentageText, fontSize = 10.sp, color = Color.Gray)
            }
        }
        
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ad Revenue", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(uiState.adRevenueAmount, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Trend Up", tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(uiState.adRevenueTrend, fontSize = 10.sp, color = Color(0xFF4CAF50))
                    }
                    // Sparkline Placeholder
                    Canvas(modifier = Modifier
                        .width(40.dp)
                        .height(20.dp)) {
                        val path = Path()
                        path.moveTo(0f, size.height)
                        path.lineTo(size.width * 0.3f, size.height * 0.5f)
                        path.lineTo(size.width * 0.6f, size.height * 0.7f)
                        path.lineTo(size.width, 0f)
                        drawPath(path, color = Color(0xFF4CAF50), style = Stroke(width = 2f))
                    }
                }
            }
        }
    }
}

@Composable
fun RecentActivitiesSection(
    uiState: AdminDashboardState,
    primaryColor: Color,
    onViewAllClick: () -> Unit = {}
) {
    var showAllActivitiesDialog by remember { mutableStateOf(false) }

    if (showAllActivitiesDialog) {
        AlertDialog(
            onDismissRequest = { showAllActivitiesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All System Activities", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.recentActivities.isEmpty()) {
                        item {
                            Text("No recent activities recorded.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        items(uiState.recentActivities) { act ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(act.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(act.subtitle, fontSize = 11.sp, color = Color.Gray)
                                }
                                Text(act.timeAgo, fontSize = 11.sp, color = Color.Gray)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllActivitiesDialog = false }) {
                    Text(stringRes("Close"), color = primaryColor)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Activities", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                TextButton(
                    onClick = {
                        showAllActivitiesDialog = true
                        onViewAllClick()
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringRes("View All"), color = primaryColor, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (uiState.recentActivities.isEmpty()) {
                    Text("No recent activities", color = Color.Gray, fontSize = 14.sp)
                } else {
                    uiState.recentActivities.forEach { activity ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF0F0F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                val icon = when(activity.icon) {
                                    "upload" -> Icons.Default.CloudUpload
                                    "file" -> Icons.Default.InsertDriveFile
                                    "user" -> Icons.Default.Person
                                    else -> Icons.Default.Info
                                }
                                Icon(icon, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(activity.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                                Text(activity.subtitle, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Text(activity.timeAgo, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                }
            }
        }
    }
}
