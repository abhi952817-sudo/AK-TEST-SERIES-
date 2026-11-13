package Com.ak.testseries.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import Com.ak.testseries.viewmodel.NEETViewModel

data class CommunityReportTarget(
    val postId: String? = null,
    val reportedUserId: String? = null,
)

@Composable
fun CommunityReportDialog(
    target: CommunityReportTarget,
    onDismiss: () -> Unit,
    onSubmit: (String, Uri?) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var evidenceUri by remember { mutableStateOf<Uri?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        evidenceUri = it
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Community content") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (!target.postId.isNullOrBlank()) "Report the selected post or its author."
                    else "Report this user.",
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 1200) reason = it },
                    label = { Text("Reason") },
                    placeholder = { Text("Describe the issue clearly") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                evidenceUri?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "Private report evidence preview",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).clip(RoundedCornerShape(10.dp))
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (evidenceUri == null) "Add one image (optional)" else "Change image")
                    }
                    if (evidenceUri != null) {
                        IconButton(onClick = { evidenceUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove evidence")
                        }
                    }
                }
                Text("Evidence is private and visible only to authorized moderators.", fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = { submitting = true; onSubmit(reason.trim(), evidenceUri) },
                enabled = reason.trim().isNotEmpty() && !submitting
            ) {
                Text(if (submitting) "Submitting..." else "Submit report")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private data class CommunityMcqResult(
    val selectedOptionIndex: Int,
    val correctOptionIndex: Int,
    val isCorrect: Boolean,
    val explanation: String,
)

@Composable
fun CommunityMcqAttemptCard(post: CommunityPost) {
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    var selectedIndex by remember(post.id) { mutableStateOf<Int?>(null) }
    var result by remember(post.id, uid) { mutableStateOf<CommunityMcqResult?>(null) }
    var submitting by remember(post.id) { mutableStateOf(false) }
    var errorText by remember(post.id) { mutableStateOf<String?>(null) }
    val options = post.quizOptions

    DisposableEffect(post.id, uid) {
        if (uid.isNullOrBlank()) return@DisposableEffect onDispose {}
        val registration = FirebaseFirestore.getInstance()
            .collection("Community_MCQ_Attempts").document(post.id)
            .collection("Users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && snapshot.exists()) {
                    val data = snapshot.data ?: emptyMap()
                    val selected = (data["selectedOptionIndex"] as? Number)?.toInt()
                    val correct = (data["correctOptionIndex"] as? Number)?.toInt()
                    if (selected != null && correct != null) {
                        result = CommunityMcqResult(
                            selectedOptionIndex = selected,
                            correctOptionIndex = correct,
                            isCorrect = data?.get("isCorrect") as? Boolean ?: (selected == correct),
                            explanation = data?.get("explanation") as? String ?: ""
                        )
                    }
                }
            }
        onDispose { registration.remove() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Single-question MCQ", style = MaterialTheme.typography.titleMedium)
            if (options.size !in 4..5) {
                Text("This question has invalid options and cannot be attempted.", color = MaterialTheme.colorScheme.error)
            } else if (result == null) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = uid != null && !submitting) { selectedIndex = index }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            enabled = uid != null && !submitting
                        )
                        Text(("${'A' + index}. ") + option, modifier = Modifier.padding(start = 6.dp), fontSize = 14.sp)
                    }
                }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Button(
                    onClick = {
                        val choice = selectedIndex ?: return@Button
                        submitting = true
                        errorText = null
                        FirebaseFunctions.getInstance()
                            .getHttpsCallable("submitCommunityMcqAttempt")
                            .call(mapOf("postId" to post.id, "selectedOptionIndex" to choice))
                            .addOnSuccessListener { task ->
                                val data = task.data as? Map<*, *>
                                val selected = (data?.get("selectedOptionIndex") as? Number)?.toInt()
                                val correct = (data?.get("correctOptionIndex") as? Number)?.toInt()
                                if (selected != null && correct != null) {
                                    result = CommunityMcqResult(
                                        selectedOptionIndex = selected,
                                        correctOptionIndex = correct,
                                        isCorrect = data?.get("isCorrect") as? Boolean ?: (selected == correct),
                                        explanation = data?.get("explanation") as? String ?: ""
                                    )
                                } else {
                                    errorText = "The server returned an invalid attempt result."
                                }
                                submitting = false
                            }
                            .addOnFailureListener {
                                errorText = it.localizedMessage ?: "Attempt could not be submitted."
                                submitting = false
                            }
                    },
                    enabled = uid != null && selectedIndex != null && !submitting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (submitting) "Submitting..." else "Submit answer") }
                if (uid == null) Text("Sign in to attempt this question.", fontSize = 12.sp)
                Text("Your answer is recorded once. The correct answer appears after submission.", fontSize = 11.sp)
            } else {
                val attempt = result!!
                Text(
                    if (attempt.isCorrect) "Correct answer" else "Your answer was incorrect",
                    color = if (attempt.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall
                )
                Text("Your choice: " + ('A' + attempt.selectedOptionIndex) + ". " + options.getOrNull(attempt.selectedOptionIndex).orEmpty())
                Text("Correct: " + ('A' + attempt.correctOptionIndex) + ". " + options.getOrNull(attempt.correctOptionIndex).orEmpty(), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                if (attempt.explanation.isNotBlank()) Text(attempt.explanation, fontSize = 13.sp)
                Text("Attempt submitted. A second attempt is not allowed.", fontSize = 11.sp)
            }
        }
    }
}

private data class CommunityAdminReport(
    val id: String,
    val reporterId: String,
    val postId: String?,
    val reportedUserId: String?,
    val reason: String,
    val evidencePath: String?,
    val status: String,
    val resolutionNote: String?,
    val createdAt: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityAdminReportsScreen(viewModel: NEETViewModel) {
    val reports = remember { mutableStateListOf<CommunityAdminReport>() }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var busyReportId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val registration = FirebaseFirestore.getInstance()
            .collection("Community_Reports")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    reports.clear()
                    reports.addAll(snapshot.documents.map { doc ->
                        val created = doc.get("createdAt")
                        val millis = when (created) {
                            is Timestamp -> created.toDate().time
                            is Number -> created.toLong()
                            else -> 0L
                        }
                        CommunityAdminReport(
                            id = doc.id,
                            reporterId = doc.getString("reporterId") ?: "",
                            postId = doc.getString("postId"),
                            reportedUserId = doc.getString("reportedUserId"),
                            reason = doc.getString("reason") ?: "",
                            evidencePath = doc.getString("evidenceImagePath"),
                            status = doc.getString("status") ?: "OPEN",
                            resolutionNote = doc.getString("resolutionNote"),
                            createdAt = millis
                        )
                    })
                } else if (error != null) {
                    statusMessage = error.localizedMessage ?: "Reports could not be loaded."
                }
            }
        onDispose { registration.remove() }
    }

    fun moderate(report: CommunityAdminReport, action: String) {
        busyReportId = report.id
        statusMessage = null
        FirebaseFunctions.getInstance()
            .getHttpsCallable("moderateCommunityReport")
            .call(mapOf("reportId" to report.id, "action" to action))
            .addOnSuccessListener {
                busyReportId = null
                statusMessage = "Moderation action saved."
            }
            .addOnFailureListener {
                busyReportId = null
                statusMessage = it.localizedMessage ?: "Moderation action failed."
            }
    }

    val openReports = reports.filter { it.status == "OPEN" }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Community Reports", style = MaterialTheme.typography.titleLarge)
                Text("${openReports.size} open of ${reports.size} loaded", fontSize = 12.sp)
            }
            Icon(Icons.Default.Flag, contentDescription = null)
        }
        statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No reports found.") }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reports, key = { it.id }) { report ->
                    var evidenceUrl by remember(report.id) { mutableStateOf<String?>(null) }
                    LaunchedEffect(report.evidencePath) {
                        val path = report.evidencePath
                        if (!path.isNullOrBlank()) {
                            FirebaseStorage.getInstance().reference.child(path).downloadUrl
                                .addOnSuccessListener { evidenceUrl = it.toString() }
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(report.status, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                Text(report.id.take(10), fontSize = 10.sp)
                            }
                            Text(report.reason)
                            Text("Reporter: ${report.reporterId}", fontSize = 11.sp)
                            report.postId?.let { Text("Post: $it", fontSize = 11.sp) }
                            report.reportedUserId?.let { Text("Reported user: $it", fontSize = 11.sp) }
                            evidenceUrl?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = "Private report evidence",
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(10.dp))
                                )
                            }
                            if (report.status == "OPEN") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { moderate(report, "RESOLVE") }, enabled = busyReportId != report.id, modifier = Modifier.weight(1f)) { Text("Resolve") }
                                    OutlinedButton(onClick = { moderate(report, "DISMISS") }, enabled = busyReportId != report.id, modifier = Modifier.weight(1f)) { Text("Dismiss") }
                                }
                                if (!report.postId.isNullOrBlank()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(onClick = { moderate(report, "HIDE_POST") }, enabled = busyReportId != report.id, modifier = Modifier.weight(1f)) { Text("Hide post") }
                                        OutlinedButton(onClick = { moderate(report, "REMOVE_POST") }, enabled = busyReportId != report.id, modifier = Modifier.weight(1f)) { Text("Remove post") }
                                    }
                                }
                            } else if (!report.postId.isNullOrBlank() && report.resolutionNote == "HIDE_POST") {
                                TextButton(
                                    onClick = { moderate(report, "RESTORE_POST") },
                                    enabled = busyReportId != report.id,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Text("Restore post")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


private data class CommunityAuditItem(
    val id: String,
    val action: String,
    val actor: String,
    val target: String,
    val details: String,
    val timestamp: Long,
)

@Composable
fun CommunityAdminAuditScreen() {
    val items = remember { mutableStateListOf<CommunityAuditItem>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val registration = FirebaseFirestore.getInstance()
            .collection("Admin_Audit_Logs")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    errorMessage = error.localizedMessage ?: "Audit log could not be loaded."
                } else if (snapshot != null) {
                    errorMessage = null
                    items.clear()
                    items.addAll(snapshot.documents.map { doc ->
                        val timestampValue = doc.get("timestamp")
                        val millis = when (timestampValue) {
                            is Timestamp -> timestampValue.toDate().time
                            is Number -> timestampValue.toLong()
                            else -> 0L
                        }
                        CommunityAuditItem(
                            id = doc.id,
                            action = doc.getString("action") ?: "UNKNOWN",
                            actor = doc.getString("performedBy") ?: "",
                            target = doc.getString("targetId") ?: "",
                            details = doc.get("details")?.toString() ?: "",
                            timestamp = millis
                        )
                    })
                }
            }
        onDispose { registration.remove() }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Moderation Audit", style = MaterialTheme.typography.titleLarge)
            Text("Latest 100 immutable Admin actions", fontSize = 12.sp)
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (items.isEmpty() && errorMessage == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No moderation audit entries found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.action, style = MaterialTheme.typography.titleSmall)
                            Text("Actor: ${item.actor}", fontSize = 11.sp)
                            if (item.target.isNotBlank()) Text("Target: ${item.target}", fontSize = 11.sp)
                            if (item.details.isNotBlank()) Text(item.details, fontSize = 11.sp)
                            Text(
                                if (item.timestamp > 0L) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.timestamp)) else "Time unavailable",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
