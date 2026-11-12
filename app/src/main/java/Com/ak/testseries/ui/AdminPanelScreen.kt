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
import Com.ak.testseries.exam.UniversalExamRegistry
import Com.ak.testseries.exam.UniversalPdfPipeline
import androidx.compose.ui.platform.LocalContext






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
    val context = LocalContext.current
    var isAnalyzingPdf by remember { mutableStateOf(false) }
    var pdfReviewSummary by remember { mutableStateOf("") }

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
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            enabled = pdfUri != null && !isAnalyzingPdf,
            onClick = {
                val selectedUri = pdfUri
                if (selectedUri == null) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("पहले PDF चुनें।") }
                } else {
                    val examConfig = UniversalExamRegistry.getExam("NEET UG")
                    if (examConfig == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Exam configuration उपलब्ध नहीं है।")
                        }
                    } else {
                        isAnalyzingPdf = true
                        pdfReviewSummary = ""
                        coroutineScope.launch {
                            try {
                                val result = UniversalPdfPipeline.processAdminUploadedPdf(
                                    context = context,
                                    pdfUri = selectedUri,
                                    examConfig = examConfig,
                                    selectedSubject = subject,
                                    serverGenerate = { prompt, instruction ->
                                        viewModel.callGeminiApiForQuestionsWithFallback(prompt, instruction)
                                    }
                                )
                                pdfReviewSummary = buildString {
                                    append("स्थिति: ${result.status}\\n")
                                    result.error?.let { append("कारण: $it\\n") }
                                    result.routing?.let { append("\\nPDF Classification:\\n").append(it.toString(2)) }
                                    result.analysis?.let { append("\\nAI Draft (अभी प्रकाशित नहीं):\\n").append(it.toString(2)) }
                                }
                                snackbarHostState.showSnackbar(
                                    if (result.status == "DRAFT_REVIEW") "AI Draft तैयार है। Admin Review आवश्यक है।"
                                    else "PDF को Admin Review में भेजा गया।"
                                )
                            } catch (e: Exception) {
                                pdfReviewSummary = "PDF processing failed: ${e.message ?: "unknown error"}"
                                snackbarHostState.showSnackbar("PDF analysis असफल रहा।")
                            } finally {
                                isAnalyzingPdf = false
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isAnalyzingPdf) "Gemini PDF जाँच रहा है..." else "Gemini से PDF जाँचें (Draft Only)")
        }

        if (pdfReviewSummary.isNotBlank()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Admin Review Result", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(pdfReviewSummary, style = MaterialTheme.typography.bodySmall)
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
                        pdfUri = pdfUri,
                        onResult = { success, error ->
                            coroutineScope.launch {
                                val message = error ?: if (success) {
                                    "Live test successfully scheduled & uploaded!"
                                } else {
                                    "Live test scheduling failed."
                                }
                                snackbarHostState.showSnackbar(message)
                                if (success) {
                                    title = ""
                                    chapterName = ""
                                }
                            }
                        }
                    )
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
fun PushNotificationSection(viewModel: NEETViewModel? = null) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var targetExam by remember { mutableStateOf("All") }
    var expandedExamDropdown by remember { mutableStateOf(false) }
    var imageUrl by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var isPinned by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allExams = remember {
        listOf(
            "All", "NEET UG", "JEE Main", "JEE Advanced", "CUET UG",
            "UPSC Civil Services", "SSC CGL", "SSC CHSL", "SSC MTS", "SSC GD Constable", "SSC CPO",
            "RRB NTPC", "RRB Group D", "RRB ALP", "RRB Technician", "RRB JE",
            "IBPS PO", "IBPS Clerk", "SBI PO", "SBI Clerk",
            "CAT", "UGC NET", "CTET", "NDA & NA", "CDS", "GATE",
            "Class 11", "Class 12", "Dropper"
        )
    }

    AdminSectionCard(
        title = "Push Notifications & Announcements",
        icon = Icons.Rounded.Notifications,
        description = "Broadcast live real-time notifications to all users or specific target exams (20+ competitive exams supported)."
    ) {
        // Quick Presets
        Text("Quick Presets (त्वरित टेम्पलेट):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = {
                    title = "🔴 LIVE EXAM ALERT: All-India Test is Live!"
                    message = "The scheduled national mock exam is active now. Complete your attempt before the window closes."
                    linkUrl = "app://live_test"
                    isPinned = true
                },
                label = { Text("Live Test", fontSize = 10.sp) }
            )
            AssistChip(
                onClick = {
                    title = "📝 नया टेस्ट जारी: New Mock Available"
                    message = "A new authentic full-syllabus practice test has been uploaded to your exam catalog."
                    linkUrl = "app://tests"
                    isPinned = false
                },
                label = { Text("New Test", fontSize = 10.sp) }
            )
            AssistChip(
                onClick = {
                    title = "📢 महत्वपूर्ण सूचना / Announcement"
                    message = "Important exam updates and syllabus guidelines have been published."
                    linkUrl = "app://announcements"
                    isPinned = true
                },
                label = { Text("Notice", fontSize = 10.sp) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Target Exam Selector
        Text("Target Audience / Exam Group:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expandedExamDropdown = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (targetExam == "All") "👥 All Students (सभी छात्र)" else "🎯 $targetExam Aspirants",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expandedExamDropdown,
                onDismissRequest = { expandedExamDropdown = false }
            ) {
                allExams.forEach { exam ->
                    DropdownMenuItem(
                        text = { Text(if (exam == "All") "All Students (सभी छात्र)" else exam, fontSize = 12.sp) },
                        onClick = {
                            targetExam = exam
                            expandedExamDropdown = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Notification Title (शीर्षक)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Notification Message (संदेश)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = linkUrl,
            onValueChange = { linkUrl = it },
            label = { Text("Action / Deep Link URL (e.g. app://tests, app://live_test)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            label = { Text("Image URL (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isPinned, onCheckedChange = { isPinned = it })
            Text("Pin this message to the top (शीर्ष पर पिन करें)", fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (title.isBlank() || message.isBlank()) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Please enter both title and message.")
                    }
                    return@Button
                }
                isSending = true
                coroutineScope.launch {
                    try {
                        val cleanTarget = if (targetExam.startsWith("All")) "All" else targetExam
                        if (viewModel != null) {
                            viewModel.addNotification(
                                title = title.trim(),
                                message = message.trim(),
                                imageUrl = imageUrl.trim().takeIf { it.isNotBlank() },
                                linkUrl = linkUrl.trim().takeIf { it.isNotBlank() },
                                targetGroup = cleanTarget,
                                targetEmail = null,
                                isPinned = isPinned
                            )
                        } else {
                            val notifData = hashMapOf<String, Any>(
                                "title" to title.trim(),
                                "message" to message.trim(),
                                "timestamp" to System.currentTimeMillis(),
                                "targetGroup" to cleanTarget,
                                "isPinned" to isPinned
                            )
                            if (imageUrl.isNotBlank()) notifData["imageUrl"] = imageUrl.trim()
                            if (linkUrl.isNotBlank()) notifData["linkUrl"] = linkUrl.trim()
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("UserNotifications")
                                .add(notifData)
                        }
                        snackbarHostState.showSnackbar("✅ Notification broadcast successfully to $cleanTarget!")
                        title = ""
                        message = ""
                        linkUrl = ""
                        imageUrl = ""
                        isPinned = false
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error sending: ${e.localizedMessage}")
                    } finally {
                        isSending = false
                    }
                }
            },
            modifier = Modifier.align(Alignment.End),
            enabled = !isSending
        ) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text("Broadcast Live Notification")
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
    val status by viewModel.adminGeminiConnectorStatus.collectAsState()
    val models by viewModel.adminGeminiConnectorModels.collectAsState()
    val verifying by viewModel.adminGeminiConnectorVerifying.collectAsState()
    val promptTokens by viewModel.adminGeminiPromptTokens.collectAsState()
    val outputTokens by viewModel.adminGeminiOutputTokens.collectAsState()
    val totalTokens by viewModel.adminGeminiTotalTokens.collectAsState()
    val selectedModel by viewModel.activeAiModel.collectAsState()
    var modelMenuExpanded by remember { mutableStateOf(false) }

    AdminSectionCard(
        title = "Admin Gemini Connector",
        icon = Icons.Rounded.Star,
        description = "The Admin API key is stored only in Firebase Secret Manager. It is never entered or saved in this Android app."
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                if (models.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Available generation models", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(models.joinToString("\n"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (models.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { modelMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Model: $selectedModel",
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select Gemini model")
                }
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                viewModel.selectAdminGeminiModel(model)
                                modelMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = { viewModel.verifyAdminGeminiConnector() },
            enabled = !verifying,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (verifying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Verifying...")
            } else {
                Text("Verify Server-side Gemini Connector")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Last request tokens: input $promptTokens • output $outputTokens • total $totalTokens",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Configure AKTS_ADMIN_GEMINI_API_KEY with Firebase CLI before verification. Never paste the secret into GitHub source, Firestore, or this screen.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
