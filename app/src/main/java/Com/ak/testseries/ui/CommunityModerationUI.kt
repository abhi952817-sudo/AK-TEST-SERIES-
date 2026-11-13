package Com.ak.testseries.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import Com.ak.testseries.viewmodel.NEETViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun CommunityReportDialog(
    post: CommunityPost,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var target by remember(post.id) { mutableStateOf("POST") }
    var reason by remember(post.id) { mutableStateOf("") }
    var imageUri by remember(post.id) { mutableStateOf<Uri?>(null) }
    var isUploading by remember(post.id) { mutableStateOf(false) }
    var errorText by remember(post.id) { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
        errorText = null
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            try {
                val file = File(context.cacheDir, "community_report_${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                imageUri = Uri.fromFile(file)
                errorText = null
            } catch (error: Exception) {
                errorText = error.localizedMessage ?: "Camera image could not be saved."
            }
        }
    }

    fun saveReport(evidencePath: String?) {
        val reporterId = uid ?: run {
            isUploading = false
            errorText = "Please sign in before reporting."
            return
        }
        val reportId = UUID.randomUUID().toString()
        val payload = hashMapOf<String, Any>(
            "reporterId" to reporterId,
            "reason" to reason.trim(),
            "status" to "OPEN",
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (target == "POST") payload["postId"] = post.id
        if (target == "USER" && !post.creatorId.isNullOrBlank()) payload["reportedUserId"] = post.creatorId!!
        if (!evidencePath.isNullOrBlank()) payload["evidenceImagePath"] = evidencePath

        FirebaseFirestore.getInstance().collection("Community_Reports").document(reportId)
            .set(payload)
            .addOnSuccessListener {
                isUploading = false
                onSubmitted()
            }
            .addOnFailureListener { error ->
                isUploading = false
                errorText = error.localizedMessage ?: "Report could not be submitted."
            }
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("Report Community content") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What are you reporting?")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = target == "POST", onClick = { target = "POST" })
                    Text("This post")
                    Spacer(Modifier.size(12.dp))
                    RadioButton(
                        selected = target == "USER",
                        onClick = { target = "USER" },
                        enabled = !post.creatorId.isNullOrBlank()
                    )
                    Text("Author")
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(1200) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reason (required)") },
                    minLines = 3,
                    maxLines = 5
                )
                Text("Optional evidence image. One image, maximum 5 MB.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { cameraLauncher.launch(null) }, enabled = !isUploading && imageUri == null) {
                        androidx.compose.material3.Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Text("Camera")
                    }
                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, enabled = !isUploading && imageUri == null) {
                        androidx.compose.material3.Icon(Icons.Default.Image, contentDescription = null)
                        Text("Gallery")
                    }
                }
                imageUri?.let { uri ->
                    AsyncImage(model = uri, contentDescription = "Private report evidence", modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(10.dp)))
                    TextButton(onClick = { imageUri = null }, enabled = !isUploading) { Text("Remove image") }
                }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (isUploading) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text("Submitting report…")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = uid != null && reason.isNotBlank() && !isUploading &&
                    (target != "USER" || !post.creatorId.isNullOrBlank()),
                onClick = {
                    val uri = imageUri
                    if (uri == null) {
                        isUploading = true
                        saveReport(null)
                    } else {
                        val mime = context.contentResolver.getType(uri)
                            ?: if (uri.scheme == "file") "image/jpeg" else ""
                        val size = try {
                            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                        } catch (_: Exception) { -1L }
                        if (mime !in setOf("image/jpeg", "image/png", "image/webp") || size <= 0L || size > 5L * 1024L * 1024L) {
                            errorText = "Only JPEG, PNG or WebP images up to 5 MB are allowed."
                        } else {
                            val reporterId = uid ?: return@Button
                            val reportId = UUID.randomUUID().toString()
                            val objectPath = "community_report_evidence/$reporterId/$reportId"
                            isUploading = true
                            val storageRef = FirebaseStorage.getInstance().reference.child(objectPath)
                            val metadata = StorageMetadata.Builder().setContentType(mime).build()
                            storageRef.putFile(uri, metadata)
                                .addOnSuccessListener {
                                    val payload = hashMapOf<String, Any>(
                                        "reporterId" to reporterId,
                                        "reason" to reason.trim(),
                                        "status" to "OPEN",
                                        "createdAt" to FieldValue.serverTimestamp(),
                                        "evidenceImagePath" to objectPath
                                    )
                                    if (target == "POST") payload["postId"] = post.id
                                    if (target == "USER" && !post.creatorId.isNullOrBlank()) payload["reportedUserId"] = post.creatorId!!
                                    FirebaseFirestore.getInstance().collection("Community_Reports").document(reportId)
                                        .set(payload)
                                        .addOnSuccessListener { isUploading = false; onSubmitted() }
                                        .addOnFailureListener { error ->
                                            storageRef.delete()
                                            isUploading = false
                                            errorText = error.localizedMessage ?: "Report could not be submitted."
                                        }
                                }
                                .addOnFailureListener { error ->
                                    isUploading = false
                                    errorText = error.localizedMessage ?: "Evidence upload failed."
                                }
                        }
                    }
                }
            ) { Text("Submit report") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) { Text("Cancel") }
        }
    )
}

private data class CommunityReportRow(
    val id: String,
    val reporterId: String,
    val postId: String?,
    val reportedUserId: String?,
    val reason: String,
    val evidenceImagePath: String?,
    val status: String,
    val resolutionNote: String,
    val createdAt: Long,
    val reference: DocumentReference
)

private fun mapCommunityReport(doc: com.google.firebase.firestore.DocumentSnapshot) = CommunityReportRow(
    id = doc.id,
    reporterId = doc.getString("reporterId").orEmpty(),
    postId = doc.getString("postId"),
    reportedUserId = doc.getString("reportedUserId"),
    reason = doc.getString("reason").orEmpty(),
    evidenceImagePath = doc.getString("evidenceImagePath"),
    status = doc.getString("status") ?: "OPEN",
    resolutionNote = doc.getString("resolutionNote").orEmpty(),
    createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
    reference = doc.reference
)

@Composable
fun CommunityBlockedUsersDialog(
    blockedUserIds: List<String>,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked accounts") },
        text = {
            if (blockedUserIds.isEmpty()) {
                Text("You have not blocked any accounts.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(blockedUserIds, key = { it }) { blockedUid ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("User ${blockedUid.take(12)}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { onUnblock(blockedUid) }) { Text("Unblock") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}


private data class CommunityAuditRow(
    val id: String,
    val actorUid: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val createdAt: Long
)

@Composable
fun CommunityAdminAuditScreen(viewModel: NEETViewModel) {
    val rows = remember { mutableStateListOf<CommunityAuditRow>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(viewModel.isAdmin) {
        if (!viewModel.isAdmin) {
            loading = false
            error = "Admin access is required."
            return@DisposableEffect onDispose {}
        }
        val registration = FirebaseFirestore.getInstance()
            .collection("Community_Moderation_Audit")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    error = listenerError.localizedMessage ?: "Moderation audit could not be loaded."
                    loading = false
                } else if (snapshot != null) {
                    rows.clear()
                    rows.addAll(snapshot.documents.map { doc ->
                        CommunityAuditRow(
                            id = doc.id,
                            actorUid = doc.getString("actorUid").orEmpty(),
                            action = doc.getString("action").orEmpty(),
                            targetType = doc.getString("targetType").orEmpty(),
                            targetId = doc.getString("targetId").orEmpty(),
                            reason = doc.getString("reason").orEmpty(),
                            createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    }.sortedByDescending { it.createdAt })
                    loading = false
                    error = null
                }
            }
        onDispose { registration.remove() }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Community moderation audit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Read-only record of Admin moderation actions.", style = MaterialTheme.typography.bodySmall)
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            rows.isEmpty() -> Text("No moderation actions have been recorded.", modifier = Modifier.padding(20.dp))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.action, fontWeight = FontWeight.Bold)
                            Text("${row.targetType}: ${row.targetId}")
                            Text("Admin: ${row.actorUid}", style = MaterialTheme.typography.bodySmall)
                            if (row.reason.isNotBlank()) Text(row.reason, style = MaterialTheme.typography.bodySmall)
                            if (row.createdAt > 0L) {
                                Text(
                                    java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                                        .format(java.util.Date(row.createdAt)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityAdminReportsScreen(viewModel: NEETViewModel) {
    val db = remember { FirebaseFirestore.getInstance() }
    val adminUid = FirebaseAuth.getInstance().currentUser?.uid
    val reports = remember { mutableStateListOf<CommunityReportRow>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var evidenceUrl by remember { mutableStateOf<String?>(null) }
    var selectedEvidencePath by remember { mutableStateOf<String?>(null) }

    DisposableEffect(viewModel.isAdmin) {
        if (!viewModel.isAdmin) {
            loading = false
            error = "Admin access is required."
            return@DisposableEffect onDispose {}
        }
        val registration = db.collection("Community_Reports")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    error = listenerError.localizedMessage ?: "Reports could not be loaded."
                    loading = false
                } else if (snapshot != null) {
                    reports.clear()
                    reports.addAll(snapshot.documents.map(::mapCommunityReport).sortedByDescending { it.createdAt })
                    loading = false
                    error = null
                }
            }
        onDispose { registration.remove() }
    }

    fun commitModerationAction(
        report: CommunityReportRow,
        reportStatus: String,
        action: String,
        targetType: String,
        targetId: String,
        resolutionNote: String = "",
        targetWrite: (com.google.firebase.firestore.WriteBatch) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val actor = adminUid ?: return
        if (!viewModel.isAdmin) return
        val batch = db.batch()
        targetWrite(batch)
        batch.update(
            report.reference,
            mapOf(
                "status" to reportStatus,
                "resolvedBy" to actor,
                "resolvedAt" to FieldValue.serverTimestamp(),
                "resolutionNote" to resolutionNote
            )
        )
        batch.set(
            db.collection("Community_Moderation_Audit").document(),
            mapOf(
                "actorUid" to actor,
                "action" to action,
                "targetType" to targetType,
                "targetId" to targetId,
                "reportId" to report.id,
                "reason" to report.reason.take(1200),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error = it.localizedMessage ?: "Moderation action and audit could not be saved." }
    }

    fun updateReport(report: CommunityReportRow, status: String, action: String) {
        commitModerationAction(report, status, action, "REPORT", report.id)
    }

    fun moderatePost(report: CommunityReportRow, remove: Boolean, restore: Boolean = false) {
        val postId = report.postId ?: return
        val ref = db.collection("Community_Posts").document(postId)
        val action = when {
            remove -> "REMOVE_POST"
            restore -> "RESTORE_POST"
            else -> "HIDE_POST"
        }
        commitModerationAction(
            report = report,
            reportStatus = "RESOLVED",
            action = action,
            targetType = "COMMUNITY_POST",
            targetId = postId,
            resolutionNote = when {
                restore -> "RESTORED"
                remove -> "REMOVED"
                else -> "HIDDEN"
            },
            targetWrite = { batch ->
                batch.update(
                    ref,
                    mapOf(
                        "moderationState" to when {
                            restore -> "VISIBLE"
                            remove -> "REMOVED"
                            else -> "HIDDEN"
                        },
                        "moderatedBy" to adminUid.orEmpty(),
                        "moderatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        )
    }

    fun suspendReportedUser(report: CommunityReportRow) {
        val uid = report.reportedUserId ?: return
        val userRef = db.collection("Users_Data").document(uid)
        commitModerationAction(
            report, "RESOLVED", "SUSPEND_USER", "USER", uid,
            resolutionNote = "USER_SUSPENDED",
            targetWrite = { batch ->
                batch.update(
                    userRef,
                    mapOf(
                        "isBanned" to true,
                        "communitySuspendedAt" to FieldValue.serverTimestamp(),
                        "communitySuspendedBy" to adminUid.orEmpty()
                    )
                )
            },
            onSuccess = { viewModel.fetchAdminUsers() }
        )
    }

    fun refreshReports() {
        loading = true
        db.collection("Community_Reports")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                reports.clear()
                reports.addAll(snapshot.documents.map(::mapCommunityReport).sortedByDescending { it.createdAt })
                loading = false
                error = null
            }
            .addOnFailureListener {
                loading = false
                error = it.localizedMessage ?: "Reports could not be refreshed."
            }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Community report inbox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${reports.count { it.status == "OPEN" }} open reports", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.IconButton(onClick = { refreshReports() }) {
                androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            reports.isEmpty() -> Text("No reports have been submitted.", modifier = Modifier.padding(20.dp))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reports, key = { it.id }) { report ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Report ${report.id.take(8)}", fontWeight = FontWeight.Bold)
                                FilterChip(selected = report.status == "OPEN", onClick = {}, label = { Text(report.status) })
                            }
                            Text(report.reason)
                            Text("Reporter: ${report.reporterId}", style = MaterialTheme.typography.bodySmall)
                            report.postId?.let { Text("Post: $it", style = MaterialTheme.typography.bodySmall) }
                            report.reportedUserId?.let { Text("Reported user: $it", style = MaterialTheme.typography.bodySmall) }
                            report.evidenceImagePath?.let { path ->
                                TextButton(onClick = {
                                    selectedEvidencePath = path
                                    FirebaseStorage.getInstance().reference.child(path).downloadUrl
                                        .addOnSuccessListener { evidenceUrl = it.toString() }
                                        .addOnFailureListener { error = it.localizedMessage ?: "Evidence could not be opened." }
                                }) { Text("View private evidence") }
                            }
                            if (report.status == "OPEN") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (report.postId != null) {
                                        OutlinedButton(onClick = { moderatePost(report, remove = false) }) { Text("Hide post") }
                                        OutlinedButton(onClick = { moderatePost(report, remove = true) }) { Text("Remove from feed") }
                                    }
                                    if (report.reportedUserId != null) {
                                        OutlinedButton(onClick = { suspendReportedUser(report) }) { Text("Suspend user") }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { updateReport(report, "RESOLVED", "RESOLVE_REPORT") }) { Text("Resolve") }
                                    TextButton(onClick = { updateReport(report, "DISMISSED", "DISMISS_REPORT") }) { Text("Dismiss") }
                                }
                            } else if (report.postId != null && report.status == "RESOLVED" && report.resolutionNote in setOf("HIDDEN", "REMOVED")) {
                                TextButton(onClick = { moderatePost(report, remove = false, restore = true) }) { Text("Restore post") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (evidenceUrl != null) {
        AlertDialog(
            onDismissRequest = { evidenceUrl = null; selectedEvidencePath = null },
            title = { Text("Private report evidence") },
            text = { AsyncImage(model = evidenceUrl, contentDescription = "Private evidence", modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(10.dp))) },
            confirmButton = { TextButton(onClick = { evidenceUrl = null; selectedEvidencePath = null }) { Text("Close") } }
        )
    }
}


private data class CommunityMigrationPanelResult(
    val dryRun: Boolean,
    val scannedCount: Int,
    val readyCount: Int,
    val skippedCount: Int,
    val nextCursor: String?,
    val statusLines: List<String>,
    val removedAttachmentFieldCount: Int = 0
)

@Composable
fun CommunityAdminMigrationScreen(viewModel: NEETViewModel) {
    var sharedResult by remember { mutableStateOf<CommunityMigrationPanelResult?>(null) }
    var postResult by remember { mutableStateOf<CommunityMigrationPanelResult?>(null) }
    var sharedCursor by remember { mutableStateOf<String?>(null) }
    var postCursor by remember { mutableStateOf<String?>(null) }
    var sharedPreviewCursor by remember { mutableStateOf<String?>(null) }
    var postPreviewCursor by remember { mutableStateOf<String?>(null) }
    var sharedLoading by remember { mutableStateOf(false) }
    var postLoading by remember { mutableStateOf(false) }
    var showSharedConfirm by remember { mutableStateOf(false) }
    var showPostConfirm by remember { mutableStateOf(false) }
    var sharedCompleted by remember { mutableStateOf(false) }
    var postCompleted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun parseResult(data: Any?, readyKey: String): CommunityMigrationPanelResult {
        val payload = data as? Map<*, *> ?: emptyMap<Any, Any>()
        val rows = (payload["results"] as? List<*>)?.mapNotNull { item ->
            val row = item as? Map<*, *> ?: return@mapNotNull null
            val id = row["testId"] ?: row["postId"] ?: "Record"
            val status = row["status"] ?: "UNKNOWN"
            "$id: $status"
        } ?: emptyList()
        return CommunityMigrationPanelResult(
            dryRun = payload["dryRun"] as? Boolean ?: false,
            scannedCount = (payload["scannedCount"] as? Number)?.toInt() ?: 0,
            readyCount = (payload[readyKey] as? Number)?.toInt() ?: 0,
            skippedCount = (payload["skippedCount"] as? Number)?.toInt() ?: 0,
            nextCursor = payload["nextCursor"] as? String,
            statusLines = rows,
            removedAttachmentFieldCount = (payload["removedLegacyAttachmentFieldCount"] as? Number)?.toInt() ?: 0
        )
    }

    fun runMigration(
        functionName: String,
        dryRun: Boolean,
        pageSize: Int,
        cursor: String?,
        confirmation: String?,
        readyKey: String,
        onLoading: (Boolean) -> Unit,
        onResult: (CommunityMigrationPanelResult) -> Unit
    ) {
        if (!viewModel.isAdmin) {
            error = "Admin access is required."
            return
        }
        onLoading(true)
        error = null
        val args = hashMapOf<String, Any>(
            "dryRun" to dryRun,
            "pageSize" to pageSize
        )
        cursor?.let { args["startAfter"] = it }
        if (!dryRun && confirmation != null) args["confirm"] = confirmation
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable(functionName)
            .call(args)
            .addOnSuccessListener { result ->
                onResult(parseResult(result.data, readyKey))
                onLoading(false)
            }
            .addOnFailureListener { failure ->
                error = failure.localizedMessage ?: "Migration request failed."
                onLoading(false)
            }
    }

    fun previewShared() {
        sharedPreviewCursor = sharedCursor
        runMigration(
            "migrateLegacyCommunitySharedTests", true, 5, sharedCursor, null,
            "migratedOrReadyCount", { sharedLoading = it }, { sharedResult = it }
        )
    }

    fun applyShared() {
        runMigration(
            "migrateLegacyCommunitySharedTests", false, 5, sharedPreviewCursor,
            "MIGRATE_SHARED_COMMUNITY_TESTS", "migratedOrReadyCount",
            { sharedLoading = it },
            {
                sharedResult = it
                sharedCursor = it.nextCursor
                sharedCompleted = it.scannedCount == 0 || it.nextCursor == null
            }
        )
    }

    fun previewPosts() {
        postPreviewCursor = postCursor
        runMigration(
            "migrateLegacyCommunityPostVisibility", true, 50, postCursor, null,
            "readyOrUpdatedCount", { postLoading = it }, { postResult = it }
        )
    }

    fun applyPosts() {
        runMigration(
            "migrateLegacyCommunityPostVisibility", false, 50, postPreviewCursor,
            "MIGRATE_COMMUNITY_POST_VISIBILITY", "readyOrUpdatedCount",
            { postLoading = it },
            {
                postResult = it
                postCursor = it.nextCursor
                postCompleted = it.scannedCount == 0 || it.nextCursor == null
            }
        )
    }

    if (!viewModel.isAdmin) {
        Text("Admin access is required.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Community data migrations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "First run the exam registry sync from Settings. Each migration is paged. Preview a page, review its results, then explicitly apply that same page. Nothing runs automatically.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (error != null) {
            item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Shared test answer-key safety", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Moves legacy answers and explanations into private answer-key documents, sanitizes already-shared public questions, normalizes exam names, and quarantines tests whose private answer key cannot be verified.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    when {
                        sharedLoading -> CircularProgressIndicator()
                        sharedCompleted -> Text("All shared-test pages in this migration run are complete.")
                        sharedResult != null -> {
                            val result = sharedResult!!
                            Text(
                                "Mode: ${if (result.dryRun) "Preview only" else "Applied"} • Scanned: ${result.scannedCount} • Ready/updated: ${result.readyCount} • Skipped/quarantined: ${result.skippedCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            result.statusLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (result.dryRun && result.scannedCount > 0) {
                                Button(onClick = { showSharedConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Apply reviewed page")
                                }
                            } else if (!result.dryRun && result.nextCursor != null) {
                                OutlinedButton(onClick = { previewShared() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Preview next page")
                                }
                            }
                        }
                    }
                    if (!sharedLoading && sharedResult == null) {
                        Button(onClick = { previewShared() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Preview shared-test migration")
                        }
                    }
                    if (sharedResult != null && !sharedLoading && !sharedCompleted &&
                        !sharedResult!!.dryRun && sharedResult!!.nextCursor == null) {
                        Text("No more shared-test records remain.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Community post compatibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Normalizes legacy exam aliases, sets visibility only for registry-mapped posts, preserves HIDDEN/REMOVED moderation states, and removes old PDF/multi-image attachment metadata.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    when {
                        postLoading -> CircularProgressIndicator()
                        postCompleted -> Text("All Community-post pages in this migration run are complete.")
                        postResult != null -> {
                            val result = postResult!!
                            Text(
                                "Mode: ${if (result.dryRun) "Preview only" else "Applied"} • Scanned: ${result.scannedCount} • Ready/updated: ${result.readyCount} • Skipped: ${result.skippedCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (result.removedAttachmentFieldCount > 0) {
                                Text("Legacy attachment fields to remove: ${result.removedAttachmentFieldCount}", style = MaterialTheme.typography.bodySmall)
                            }
                            result.statusLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (result.dryRun && result.scannedCount > 0) {
                                Button(onClick = { showPostConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Apply reviewed page")
                                }
                            } else if (!result.dryRun && result.nextCursor != null) {
                                OutlinedButton(onClick = { previewPosts() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Preview next page")
                                }
                            }
                        }
                    }
                    if (!postLoading && postResult == null) {
                        Button(onClick = { previewPosts() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Preview Community-post migration")
                        }
                    }
                    if (postResult != null && !postLoading && !postCompleted &&
                        !postResult!!.dryRun && postResult!!.nextCursor == null) {
                        Text("No more Community-post records remain.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showSharedConfirm) {
        AlertDialog(
            onDismissRequest = { showSharedConfirm = false },
            title = { Text("Apply shared-test migration?") },
            text = { Text("Only the previewed page will be changed. Unsafe tests may be quarantined so students cannot read their question data until the private answer key is repaired.") },
            confirmButton = {
                TextButton(onClick = { showSharedConfirm = false; applyShared() }) { Text("Apply page") }
            },
            dismissButton = { TextButton(onClick = { showSharedConfirm = false }) { Text("Cancel") } }
        )
    }
    if (showPostConfirm) {
        AlertDialog(
            onDismissRequest = { showPostConfirm = false },
            title = { Text("Apply Community-post migration?") },
            text = { Text("Only the previewed page will be changed. Unknown exam mappings will be hidden, legacy attachment metadata will be removed, and existing HIDDEN/REMOVED states will be preserved.") },
            confirmButton = {
                TextButton(onClick = { showPostConfirm = false; applyPosts() }) { Text("Apply page") }
            },
            dismissButton = { TextButton(onClick = { showPostConfirm = false }) { Text("Cancel") } }
        )
    }
}
