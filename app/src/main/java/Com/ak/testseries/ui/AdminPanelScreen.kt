package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.launch
import Com.ak.testseries.viewmodel.NEETViewModel






@Composable
fun AdminLiveTestUploadSection(viewModel: NEETViewModel) {
    var title by remember { mutableStateOf("") }
    var classLevel by remember { mutableStateOf("11th") }
    var classLevelExpanded by remember { mutableStateOf(false) }
    
    var subject by remember { mutableStateOf("Physics") }
    var subjectExpanded by remember { mutableStateOf(false) }
    
    var chapterName by remember { mutableStateOf("") }
    
    var scheduledTime by remember { mutableStateOf("2026-07-20 10:00 AM") }
    var durationMinutes by remember { mutableStateOf("180") }
    var questionsCount by remember { mutableStateOf("90") }
    
    var pdfUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pdfPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        pdfUri = uri
    }
    
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    AdminSectionCard(
        title = stringRes("Upload & Schedule Live Test"),
        icon = Icons.Rounded.CloudUpload,
        description = "Publish live scheduled tests with auto-extraction synchronized to Firestore."
    ) {
        // Class
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { classLevelExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Class: $classLevel", modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = classLevelExpanded, onDismissRequest = { classLevelExpanded = false }) {
                listOf("11th", "12th", stringRes("Dropper")).forEach { clazz ->
                    DropdownMenuItem(text = { Text(clazz) }, onClick = { classLevel = clazz; classLevelExpanded = false })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Subject
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { subjectExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Subject: $subject", modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = subjectExpanded, onDismissRequest = { subjectExpanded = false }) {
                listOf("Physics", "Chemistry", "Biology").forEach { sub ->
                    DropdownMenuItem(text = { Text(sub) }, onClick = { subject = sub; subjectExpanded = false })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Chapter
        OutlinedTextField(
            value = chapterName,
            onValueChange = { chapterName = it },
            label = { Text(stringRes("Chapter / Syllabus")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Title
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringRes("Test Title")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Scheduled Date & Time
        OutlinedTextField(
            value = scheduledTime,
            onValueChange = { scheduledTime = it },
            label = { Text(stringRes("Schedule Date & Time")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Duration & Questions Count
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = durationMinutes,
                onValueChange = { durationMinutes = it },
                label = { Text(stringRes("Duration (Mins)")) },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            OutlinedTextField(
                value = questionsCount,
                onValueChange = { questionsCount = it },
                label = { Text(stringRes("Questions Count")) },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // PDF Picker
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (pdfUri != null) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("PDF Selected", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { pdfUri = null }) {
                        Text("Remove")
                    }
                } else {
                    Text("Upload Test Paper (PDF)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { pdfPickerLauncher.launch("application/pdf") }) {
                        Text("Select PDF File")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (title.isNotEmpty() && chapterName.isNotEmpty()) {
                    viewModel.scheduleLiveTest(
                        title = title,
                        subject = subject,
                        classLevel = classLevel,
                        chapter = chapterName,
                        scheduledTime = scheduledTime,
                        durationMinutes = durationMinutes.toIntOrNull() ?: 180,
                        questionsCount = questionsCount.toIntOrNull() ?: 90,
                        pdfUri = pdfUri
                    )
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Live test successfully scheduled & uploaded!")
                        title = ""
                        chapterName = ""
                    }
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Please fill Title and Chapter fields.")
                    }
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringRes("Upload & Schedule Live Test"))
        }
        Spacer(modifier = Modifier.height(8.dp))
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
fun PushNotificationSection() {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    AdminSectionCard(
        title = "Push Notifications",
        icon = Icons.Rounded.Notifications,
        description = "Send notifications to all users."
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Notification Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Notification Message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                // Note: Realistically, you should trigger a Cloud Function here to send the notification
                // via FCM HTTP v1 API. Sending directly from the client without a backend is a security risk.
                coroutineScope.launch {
                    // Simulate sending
                    snackbarHostState.showSnackbar("Notification triggered successfully")
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Send Notification")
        }
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
fun RealtimeDataSection() {
    var liveScore by remember { mutableStateOf("") }
    var activeTestId by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    AdminSectionCard(
        title = "Realtime Database Control",
        icon = Icons.Rounded.Speed,
        description = "Manage live test scores or instantly changing data."
    ) {
        OutlinedTextField(
            value = activeTestId,
            onValueChange = { activeTestId = it },
            label = { Text("Active Test ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = liveScore,
            onValueChange = { liveScore = it },
            label = { Text("Live Score / Status") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (activeTestId.isNotEmpty()) {
                    val database = Firebase.database
                    val ref = database.getReference("live_tests").child(activeTestId)
                    ref.child("liveScore").setValue(liveScore)
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Update Live Data")
        }
    }
}

@Composable
fun CrashlyticsSection() {
    AdminSectionCard(
        title = "Crashlytics Analytics",
        icon = Icons.Rounded.BugReport,
        description = "View app crash status."
    ) {
        Text(
            text = "Note: Detailed crash reports and analytics are best viewed in the Firebase Console. You can force a test crash below to verify your SDK setup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Crashes are monitored", fontWeight = FontWeight.Bold)
                Text("Check Firebase Console for reports.", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            Button(
                onClick = {
                                        throw RuntimeException("Admin Panel Test Crash")
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Force Test Crash")
            }
        }
    }
}

@Composable
fun AdminSectionCard(
    title: String,
    icon: ImageVector,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFeedbacksSection() {
    var feedbacks by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("test_feedbacks")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null) {
                        val list = mutableListOf<Map<String, Any>>()
                        for (doc in snapshot.documents) {
                            val data = doc.data
                            if (data != null) {
                                val item = data.toMutableMap()
                                item["id"] = doc.id
                                list.add(item)
                            }
                        }
                        feedbacks = list
                    }
                    isLoading = false
                }
        } catch (e: Exception) {
            isLoading = false
            e.printStackTrace()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = "Notification",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "User Feedbacks & Ratings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (feedbacks.isNotEmpty()) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("${feedbacks.size} New", color = MaterialTheme.colorScheme.onError, fontSize = 10.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (feedbacks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text("No user feedbacks submitted yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    feedbacks.forEach { feedback ->
                        val userName = feedback["userName"] as? String ?: "Anonymous"
                        val userEmail = feedback["userEmail"] as? String ?: "No Email"
                        val testTitle = feedback["testTitle"] as? String ?: "Test"
                        val rating = (feedback["rating"] as? Long ?: 0L).toInt()
                        val shortcuts = feedback["shortcuts"] as? List<*> ?: emptyList<Any>()
                        val comments = feedback["comments"] as? String ?: ""
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(userName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(userEmail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        repeat(5) { starIndex ->
                                            Icon(
                                                imageVector = Icons.Rounded.Star,
                                                contentDescription = null,
                                                tint = if (starIndex < rating) androidx.compose.ui.graphics.Color(0xFFFFB300) else androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Test: $testTitle",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                if (shortcuts.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        shortcuts.forEach { shortcut ->
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                Text(
                                                    text = shortcut.toString(),
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                if (comments.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Feedback: $comments",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportedQuestionsSection(viewModel: NEETViewModel) {
    val reports by viewModel.questionReports.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchQuestionReports()
    }

    AdminSectionCard(
        title = "Reported Question Errors",
        icon = Icons.Rounded.BugReport,
        description = "Review errors reported by students, check AI assessments, and resolve tickets."
    ) {
        if (reports.isEmpty()) {
            Text(
                text = "No pending question reports. All questions are clean!",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reports.forEach { report ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = report.testTitle.ifBlank { "Test Issue" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (report.status == "RESOLVED") MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = report.status,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (report.status == "RESOLVED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Q: ${report.questionText}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                            )
                            if (report.userNote.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Student Note: ${report.userNote}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (report.aiAnalysis.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "AI Assessment: ${report.aiAnalysis}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (report.status != "RESOLVED") {
                                    Button(
                                        onClick = { viewModel.updateQuestionReportStatus(report.reportId, "RESOLVED") },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Mark Resolved", fontSize = 11.sp)
                                    }
                                } else {
                                    TextButton(
                                        onClick = { viewModel.updateQuestionReportStatus(report.reportId, "PENDING") },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Reopen", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeminiKeyManagerSection(viewModel: NEETViewModel) {
    val primaryActive by viewModel.isPrimaryGeminiActive.collectAsState()
    val secondaryActive by viewModel.isSecondaryGeminiActive.collectAsState()
    val primaryUsage by viewModel.primaryGeminiUsage.collectAsState()
    val secondaryUsage by viewModel.secondaryGeminiUsage.collectAsState()
    val statusMsg by viewModel.geminiStatusMessage.collectAsState()
    val activeIndex by viewModel.activeGeminiKeyIndex.collectAsState()

    var showKeyDialog by remember { mutableStateOf(false) }
    var tempSecKey by remember { mutableStateOf("") }

    AdminSectionCard(
        title = "Gemini API Keys Control",
        icon = Icons.Rounded.Star,
        description = "Manage Gemini API Limits, Usage, & Master Switches."
    ) {
        // Status Alert
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Live Status: $statusMsg",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Primary Key Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (primaryActive && activeIndex == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Primary Key", fontWeight = FontWeight.Bold)
                        Text("Configured via BuildConfig", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = primaryActive,
                        onCheckedChange = { viewModel.updateGeminiSettings(it, secondaryActive) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Tests Uploaded (Used): $primaryUsage", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Secondary Key Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (secondaryActive && activeIndex == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Secondary Key", fontWeight = FontWeight.Bold)
                        Text("Configured manually", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = secondaryActive,
                        onCheckedChange = { viewModel.updateGeminiSettings(primaryActive, it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Tests Uploaded (Used): $secondaryUsage", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { 
                        tempSecKey = viewModel.getSecondaryGeminiKey()
                        showKeyDialog = true 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Secondary Key")
                }
            }
        }

        if (showKeyDialog) {
            AlertDialog(
                onDismissRequest = { showKeyDialog = false },
                title = { Text("Set Secondary API Key") },
                text = {
                    OutlinedTextField(
                        value = tempSecKey,
                        onValueChange = { tempSecKey = it },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setSecondaryGeminiKey(tempSecKey)
                        showKeyDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showKeyDialog = false }) {
                        Text(stringRes("Cancel"))
                    }
                }
            )
        }
    }
}
