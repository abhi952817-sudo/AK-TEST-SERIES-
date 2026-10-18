package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.MenuBook
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
import Com.ak.testseries.viewmodel.AppLanguage
import Com.ak.testseries.CustomBadge
import Com.ak.testseries.BilingualText

@Composable
fun TestCard(
    test: TestEntity,
    currentUserAttempts: Int = 0,
    selectedLanguage: AppLanguage = AppLanguage.HINDI,
    onStartTest: () -> Unit,
    onViewSyllabus: () -> Unit,
    onBookmark: () -> Unit,
    onDownload: () -> Unit,
    currentUserId: String = "",
    onDeleteTest: (() -> Unit)? = null,
    onReportIssue: (() -> Unit)? = null,
    onShareToCommunity: (() -> Unit)? = null
) {
    val subjectLower = test.subject.lowercase()
    val subjectColor = when {
        subjectLower.contains("physics") -> Color(0xFF2196F3)
        subjectLower.contains("chemistry") -> Color(0xFF009688)
        subjectLower.contains("biology") -> Color(0xFFE91E63)
        subjectLower.contains("botany") -> Color(0xFF4CAF50)
        subjectLower.contains("zoology") -> Color(0xFFFF5722)
        subjectLower.contains("math") || subjectLower.contains("quant") -> Color(0xFF9C27B0)
        subjectLower.contains("reason") || subjectLower.contains("logic") -> Color(0xFFFF9800)
        subjectLower.contains("aware") || subjectLower.contains("gk") || subjectLower.contains("ga") -> Color(0xFF00BCD4)
        subjectLower.contains("english") -> Color(0xFF3F51B5)
        subjectLower.contains("pedagogy") || subjectLower.contains("child") -> Color(0xFFE91E63)
        subjectLower.contains("environment") || subjectLower.contains("evs") -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.primary
    }

    val subjectIcon = when {
        subjectLower.contains("physics") -> Icons.Default.Bolt
        subjectLower.contains("chemistry") -> Icons.Default.Science
        subjectLower.contains("biology") || subjectLower.contains("botany") || subjectLower.contains("zoology") -> Icons.Default.Eco
        subjectLower.contains("environment") || subjectLower.contains("evs") -> Icons.Default.Park
        else -> Icons.Default.MenuBook
    }

    val isLightTheme = MaterialTheme.colorScheme.background != Color(0xFF090A10)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isLightTheme) 2.dp else 1.dp,
            color = if (isLightTheme) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
        ),
        elevation = if (isLightTheme) CardDefaults.cardElevation(defaultElevation = 4.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Decorative vertical strip
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterVertically)
                    .padding(vertical = 12.dp)
                    .background(subjectColor.copy(alpha = 0.8f), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .height(80.dp) // Minimum height for the strip
            )

            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                // Badges Line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (test.isOfficial) {
                            CustomBadge(containerColor = subjectColor) {
                                Text("OFFICIAL", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (test.isVerifiedCommunity) {
                            CustomBadge(containerColor = MaterialTheme.colorScheme.secondary) {
                                Text("VERIFIED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            CustomBadge(containerColor = Color(0xFFFF9100).copy(alpha = 0.1f)) {
                                Text("AI Acc ${test.aiConfidence}%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9100))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        CustomBadge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(test.difficulty, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row {
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
                            Box(modifier = Modifier.size(28.dp).background(Color(0xFF2196F3).copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onBookmark, modifier = Modifier.size(28.dp)) {
                            Box(modifier = Modifier.size(28.dp).background(if (test.isBookmarked) Color.Red.copy(alpha = 0.15f) else Color.Gray.copy(alpha=0.15f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (test.isBookmarked) Com.ak.testseries.ui.CustomSaveIconFilled else Com.ak.testseries.ui.CustomSaveIcon,
                                    contentDescription = "Bookmark",
                                    tint = if (test.isBookmarked) Color.Red else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                            val dlColor = if (test.isDownloaded) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            Box(modifier = Modifier.size(28.dp).background(dlColor.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (test.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = dlColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { expanded = true }, modifier = Modifier.size(28.dp)) {
                                Box(modifier = Modifier.size(28.dp).background(Color.Gray.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                val timeString = java.text.SimpleDateFormat("dd MMM, yyyy • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(test.timestamp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Uploaded: $timeString", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(text = "Global Attempts: ${test.attemptsCount}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = subjectColor)
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Title
                Text(
                    text = test.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.typography.titleMedium.color
                )
                // Description
                Text(
                    text = test.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Sub-details (Subject, Batch, Institute)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomBadge(containerColor = subjectColor.copy(alpha = 0.1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = subjectIcon,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = subjectColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(test.subject, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = subjectColor)
                        }
                    }
                    Text("🏫 ${test.institute} • ${test.batchName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(8.dp))
                // Actions (Attempt stats & Test button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Marks: 720  |  Qns: ${test.questionsCount}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = subjectColor
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onViewSyllabus,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Syllabus & Rules", fontSize = 11.sp)
                        }

                        val attemptText = if (currentUserAttempts == 0) "Attempt" else "Re-attempt ($currentUserAttempts)"
                        Button(
                            onClick = onStartTest,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = subjectColor),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Test", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(attemptText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
