package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.CustomBadge
import Com.ak.testseries.BilingualText
import Com.ak.testseries.viewmodel.AppLanguage

@Composable
fun CompactTestCard(
    test: TestEntity,
    currentUserAttempts: Int = 0,
    onStartTest: () -> Unit,
    onViewSyllabus: () -> Unit,
    currentUserId: String = "",
    onDeleteTest: (() -> Unit)? = null,
    onReportIssue: (() -> Unit)? = null,
    onShareToCommunity: (() -> Unit)? = null
) {
    val subjectColor = when (test.subject.lowercase()) {
        "physics" -> Color(0xFF2196F3)
        "chemistry" -> Color(0xFF009688)
        "biology" -> Color(0xFFE91E63)
        "botany" -> Color(0xFF4CAF50)
        "zoology" -> Color(0xFFFF5722)
        else -> MaterialTheme.colorScheme.primary
    }

    val subjectIcon = when (test.subject.lowercase()) {
        "physics" -> Icons.Default.Bolt
        "chemistry" -> Icons.Default.Science
        "biology", "botany", "zoology" -> Icons.Default.Eco
        else -> Icons.Default.MenuBook
    }

    val isLightTheme = MaterialTheme.colorScheme.background != Color(0xFF090A10)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isLightTheme) 2.dp else 1.dp,
            color = if (isLightTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
        ),
        elevation = if (isLightTheme) CardDefaults.cardElevation(defaultElevation = 4.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val timeString = java.text.SimpleDateFormat("dd MMM • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(test.timestamp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CustomBadge(containerColor = subjectColor.copy(alpha = 0.1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = subjectIcon,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = subjectColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(test.subject, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = subjectColor)
                    }
                }
                Text(timeString, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = test.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("${test.questionsCount} Qs • ${test.difficulty}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            // Community Counters
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Global Attempts: ${test.attemptsCount}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = subjectColor)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val context = LocalContext.current
                IconButton(onClick = {
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Attempt this test: ${test.title} on AK TEST SERIES!\n\n👉 Open Test directly in App:\nhttps://aktestseries.app/test/${test.id}\n\n👉 If you don't have the app, download it and join us here:\nhttps://t.me/AKTESTSERIESapp")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (currentUserId.isNotEmpty() && currentUserId == test.uploaderId) {
                            DropdownMenuItem(
                                text = { Text(stringRes("Edit")) },
                                onClick = { expanded = false }
                            )
                            if (onShareToCommunity != null) {
                                DropdownMenuItem(
                                    text = { Text(stringRes("Share to Community")) },
                                    onClick = {
                                        expanded = false
                                        onShareToCommunity.invoke()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringRes("Delete"), color = Color.Red) },
                                onClick = {
                                    expanded = false
                                    onDeleteTest?.invoke()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringRes("Report Issue")) },
                                onClick = {
                                    expanded = false
                                    onReportIssue?.invoke()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringRes("Suggest Edit")) },
                                onClick = {
                                    expanded = false
                                    onReportIssue?.invoke()
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                
                OutlinedButton(
                    onClick = onViewSyllabus,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp).weight(1f)
                ) {
                    Text("Syllabus", fontSize = 10.sp)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                val attemptText = if (currentUserAttempts == 0) "Attempt" else "Re-attempt ($currentUserAttempts)"
                Button(
                    onClick = onStartTest,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = subjectColor),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp).weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(attemptText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
